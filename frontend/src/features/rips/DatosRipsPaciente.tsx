import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";
import { tipoUsuarioSugerido, usePagadores } from "./pagadores";
import { COLOMBIA, nombrePropio, useBusquedaReferencia, useReferencia } from "./referencias";

type Datos = Schemas["PacienteRips"];

/**
 * Datos que pide el RIPS y que la ficha no tiene (pagador, tipo de usuario, residencia, país de origen). Solo con el
 * módulo de RIPS activo. Los puede completar la secretaria al recibir al paciente.
 */
export function DatosRipsPaciente({ pacienteId, editar = false }: { pacienteId: string; editar?: boolean }) {
  const [editando, setEditando] = useState(editar);
  const datos = useQuery({
    queryKey: ["rips-paciente", pacienteId],
    queryFn: async () => unwrap(await api.GET("/api/v1/rips/pacientes/{pacienteId}", { params: { path: { pacienteId } } })),
  });
  const tipos = useReferencia("tipo_usuario");
  const zonas = useReferencia("zona");
  const paises = useReferencia("pais");
  const d = datos.data;
  if (!d) return null;
  const nombre = (lista: typeof tipos.data, codigo?: string | null) => lista?.find((x) => x.codigo === codigo)?.nombre;
  const faltan = d.faltantes ?? [];

  return (
    <div className="card stack-sm">
      <div className="row between">
        <h3>Datos para RIPS {faltan.length ? <span className="badge badge-warn">Faltan {faltan.length}</span> : <span className="badge badge-ok">Completos</span>}</h3>
        <button className="btn-link" onClick={() => setEditando((v) => !v)}>{editando ? "Cancelar" : faltan.length ? "Completar" : "Editar"}</button>
      </div>
      {!editando ? (
        <p className="small">
          {[d.pagadorNombre, nombre(tipos.data, d.tipoUsuario),
            d.municipioNombre && `${nombrePropio(d.municipioNombre)} (${nombrePropio(d.departamento)})`,
            d.paisResidencia && d.paisResidencia !== COLOMBIA && `Reside en ${nombrePropio(nombre(paises.data, d.paisResidencia))}`,
            nombre(zonas.data, d.zonaResidencia) && `zona ${nombre(zonas.data, d.zonaResidencia)?.toLowerCase()}`]
            .filter(Boolean).join(" · ") || <span className="muted">Sin datos</span>}
          {!!faltan.length && <span className="muted"> · Falta: {faltan.join(", ").toLowerCase()}</span>}
        </p>
      ) : (
        <FormularioRips pacienteId={pacienteId} datos={d} onGuardado={() => setEditando(false)} />
      )}
    </div>
  );
}

function FormularioRips({ pacienteId, datos, onGuardado }: { pacienteId: string; datos: Datos; onGuardado: () => void }) {
  const queryClient = useQueryClient();
  const tipos = useReferencia("tipo_usuario");
  const zonas = useReferencia("zona");
  const paises = useReferencia("pais");
  const pagadores = usePagadores();
  // Lo más común es que viva y haya nacido en Colombia
  const [form, setForm] = useState({
    pagadorId: datos.pagadorId ?? "",
    tipoUsuario: datos.tipoUsuario ?? "",
    paisResidencia: datos.paisResidencia ?? COLOMBIA,
    municipioResidencia: datos.municipioResidencia ?? "",
    municipioTexto: datos.municipioNombre ? `${nombrePropio(datos.municipioNombre)} (${nombrePropio(datos.departamento)})` : "",
    zonaResidencia: datos.zonaResidencia ?? "",
    paisOrigen: datos.paisOrigen ?? COLOMBIA,
  });
  const [buscar, setBuscar] = useState("");
  const municipios = useBusquedaReferencia("municipio", buscar);
  const guardar = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/rips/pacientes/{pacienteId}", {
      params: { path: { pacienteId } },
      body: {
        pagadorId: form.pagadorId || undefined,
        tipoUsuario: form.tipoUsuario || undefined, paisResidencia: form.paisResidencia || undefined,
        municipioResidencia: form.municipioResidencia || undefined, zonaResidencia: form.zonaResidencia || undefined,
        paisOrigen: form.paisOrigen || undefined,
      },
    })),
    onSuccess: (d) => {
      queryClient.setQueryData(["rips-paciente", pacienteId], d);
      queryClient.invalidateQueries({ queryKey: ["rips-faltantes"] });
      onGuardado();
    },
  });
  const colombia = form.paisResidencia === COLOMBIA;
  const sugerido = tipoUsuarioSugerido(pagadores.data?.find((p) => p.id === form.pagadorId));
  // Al elegir el pagador se propone el tipo de usuario si aún no hay uno; si ya hay otro, se ofrece cambiarlo
  const elegirPagador = (pagadorId: string) => {
    const t = tipoUsuarioSugerido(pagadores.data?.find((p) => p.id === pagadorId));
    setForm({ ...form, pagadorId, tipoUsuario: form.tipoUsuario || t || "" });
  };
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  return (
    <form className="form-grid" onSubmit={(e) => { e.preventDefault(); guardar.mutate(); }}>
      <label>Pagador
        <select value={form.pagadorId} onChange={(e) => elegirPagador(e.target.value)}>
          <option value="">—</option>
          {pagadores.data?.filter((p) => p.activo || p.id === form.pagadorId).map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
        </select>
      </label>
      <label>Tipo de usuario
        <select value={form.tipoUsuario} onChange={set("tipoUsuario")}>
          <option value="">—</option>
          {tipos.data?.map((t) => <option key={t.codigo} value={t.codigo}>{t.nombre}</option>)}
        </select>
        {sugerido && form.tipoUsuario && sugerido !== form.tipoUsuario && (
          <span className="muted small">Con este pagador suele ser {tipos.data?.find((t) => t.codigo === sugerido)?.nombre?.toLowerCase()}.{" "}
            <button type="button" className="btn-link" onClick={() => setForm({ ...form, tipoUsuario: sugerido })}>Usar</button></span>
        )}
      </label>
      <label>País de residencia
        <select value={form.paisResidencia} onChange={set("paisResidencia")}>
          {paises.data?.map((p) => <option key={p.codigo} value={p.codigo}>{nombrePropio(p.nombre)}</option>)}
        </select>
      </label>
      {colombia && (
        <div className="stack-sm">
          <label>Municipio de residencia
            <input value={buscar || form.municipioTexto} placeholder="Escriba el municipio"
              onChange={(e) => { setBuscar(e.target.value); setForm({ ...form, municipioResidencia: "", municipioTexto: "" }); }} />
          </label>
          {!!buscar && !!municipios.data?.length && (
            <div className="resultados">
              {municipios.data.map((m) => (
                <button type="button" key={m.codigo} className="opcion" onClick={() => {
                  setForm({ ...form, municipioResidencia: m.codigo!, municipioTexto: `${nombrePropio(m.nombre)} (${nombrePropio(m.detalle)})` });
                  setBuscar("");
                }}>
                  <span>{nombrePropio(m.nombre)} <span className="muted small">· {nombrePropio(m.detalle)} · {m.codigo}</span></span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
      {colombia && (
        <label>Zona de residencia
          <select value={form.zonaResidencia} onChange={set("zonaResidencia")}>
            <option value="">—</option>
            {zonas.data?.map((z) => <option key={z.codigo} value={z.codigo}>{z.nombre}</option>)}
          </select>
        </label>
      )}
      <label>País de origen (nacimiento)
        <select value={form.paisOrigen} onChange={set("paisOrigen")}>
          {paises.data?.map((p) => <option key={p.codigo} value={p.codigo}>{nombrePropio(p.nombre)}</option>)}
        </select>
      </label>
      <div className="wide row">
        <button className="btn" disabled={guardar.isPending}>Guardar</button>
        {guardar.error && <span className="field-error">{guardar.error.message}</span>}
      </div>
    </form>
  );
}
