import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { api, unwrap, type Schemas } from "../api/client";
import { ListaPrecios } from "../features/rips/ListaPrecios";
import { Lotes } from "../features/rips/Lotes";
import { Pagadores } from "../features/rips/ListaPagadores";
import { nombrePropio, useBusquedaReferencia, useReferencia, type Referencia } from "../features/rips/referencias";

type Pestana = "lotes" | "prestador" | "profesionales" | "pagadores" | "precios";
const PESTANAS: [Pestana, string][] = [["lotes", "Lotes"], ["prestador", "Prestador"], ["profesionales", "Profesionales"], ["pagadores", "Pagadores"], ["precios", "Precios"]];

/**
 * Módulo de RIPS: los lotes (el archivo de cada factura) y su configuración: el consultorio como prestador, el documento
 * de cada médico, los pagadores y los precios.
 */
export function RipsPage() {
  const [params, setParams] = useSearchParams();
  const pestana = (PESTANAS.find(([p]) => p === params.get("pestana"))?.[0] ?? "lotes") as Pestana;
  return (
    <section className="stack">
      <div className="page-header"><h1>RIPS</h1></div>
      <nav className="tabs">
        {PESTANAS.map(([p, nombre]) => (
          <button key={p} className={pestana === p ? "active" : ""} onClick={() => setParams({ pestana: p })}>{nombre}</button>
        ))}
      </nav>
      {pestana === "lotes" && <Lotes loteId={params.get("lote")} onAbrir={(id) => setParams(id ? { pestana: "lotes", lote: id } : { pestana: "lotes" })} />}
      {pestana === "prestador" && <DatosPrestador />}
      {pestana === "profesionales" && <Profesionales />}
      {pestana === "pagadores" && <Pagadores />}
      {pestana === "precios" && <ListaPrecios />}
    </section>
  );
}

function Seleccion({ etiqueta, tabla, valor, onChange, ayuda, filtro }: {
  etiqueta: string; tabla: string; valor: string; onChange: (v: string) => void; ayuda?: string;
  filtro?: (r: Referencia) => boolean;
}) {
  const lista = useReferencia(tabla);
  return (
    <label>{etiqueta}
      <select value={valor} onChange={(e) => onChange(e.target.value)}>
        <option value="">—</option>
        {lista.data?.filter(filtro ?? (() => true)).map((r) => <option key={r.codigo} value={r.codigo}>{r.codigo} · {nombrePropio(r.nombre)}</option>)}
      </select>
      {ayuda && <span className="muted small">{ayuda}</span>}
    </label>
  );
}

function DatosPrestador() {
  const queryClient = useQueryClient();
  const prestador = useQuery({ queryKey: ["rips-prestador"], queryFn: async () => unwrap(await api.GET("/api/v1/rips/prestador")) });
  if (prestador.isLoading) return <p className="muted">Cargando…</p>;
  if (prestador.error || !prestador.data) return <p className="alert alert-error">{prestador.error?.message}</p>;
  const poner = (p: Schemas["Prestador"]) => queryClient.setQueryData(["rips-prestador"], p);
  return (
    <div className="stack">
      <NitConsultorio nit={prestador.data.nit} onGuardado={poner} />
      <FormularioPrestador inicial={prestador.data} onGuardado={poner} />
    </div>
  );
}

/** El NIT del consultorio va en cada RIPS. Si no se dio al registrarse, se escribe aquí una vez. */
function NitConsultorio({ nit, onGuardado }: { nit?: string; onGuardado: (p: Schemas["Prestador"]) => void }) {
  const [valor, setValor] = useState("");
  const guardar = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/rips/prestador/nit", { body: { nit: valor } })),
    onSuccess: onGuardado,
  });
  if (nit) return <p className="card small">NIT del consultorio: <strong>{nit}</strong> <span className="muted">(para corregirlo, escriba a soporte)</span></p>;
  return (
    <form className="card stack-sm" onSubmit={(e) => { e.preventDefault(); guardar.mutate(); }}>
      <p className="alert alert-warn">El consultorio no tiene NIT registrado y el RIPS lo exige. Escríbalo con su dígito de verificación; después no se puede cambiar desde aquí.</p>
      <div className="row">
        <label>NIT
          <input className="campo-medio" required maxLength={20} value={valor} placeholder="900123456-7"
            onChange={(e) => setValor(e.target.value.replace(/[^0-9.-]/g, ""))} />
        </label>
        <button className="btn" disabled={guardar.isPending || !valor}>Guardar NIT</button>
      </div>
      {guardar.error && <span className="field-error">{guardar.error.message}</span>}
    </form>
  );
}

function FormularioPrestador({ inicial, onGuardado }: { inicial: Schemas["Prestador"]; onGuardado: (p: Schemas["Prestador"]) => void }) {
  const [form, setForm] = useState({
    codigoHabilitacion: inicial.codigoHabilitacion ?? "", servicio: inicial.servicio ?? "",
    modalidadAtencion: inicial.modalidadAtencion ?? "", grupoServicios: inicial.grupoServicios ?? "",
    finalidad: inicial.finalidad ?? "", causaExterna: inicial.causaExterna ?? "", viaIngreso: inicial.viaIngreso ?? "",
  });
  const [buscarServicio, setBuscarServicio] = useState("");
  const servicios = useBusquedaReferencia("servicio", buscarServicio);
  const servicioActual = useBusquedaReferencia("servicio", form.servicio.length >= 2 ? form.servicio : "");
  const guardar = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/rips/prestador", {
      body: Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v || undefined])) as Schemas["DatosPrestador"],
    })),
    onSuccess: onGuardado,
  });
  const set = (k: keyof typeof form) => (v: string) => setForm({ ...form, [k]: v });
  const nombreServicio = servicioActual.data?.find((s) => s.codigo === form.servicio)?.nombre;

  return (
    <form className="card stack" onSubmit={(e) => { e.preventDefault(); guardar.mutate(); }}>
      {!inicial.guardado && <p className="alert">Todavía no se han guardado los datos del prestador. Los valores que aparecen son sugeridos.</p>}
      <div className="form-grid">
        <label>Código de habilitación (12 dígitos)
          <input inputMode="numeric" maxLength={12} value={form.codigoHabilitacion}
            onChange={(e) => set("codigoHabilitacion")(e.target.value.replace(/\D/g, ""))} />
          <span className="muted small">El que aparece en el REPS para la sede: 10 dígitos del prestador y 2 de la sede</span>
        </label>
        <div className="stack-sm">
          <label>Servicio habilitado
            <input value={buscarServicio || (form.servicio ? `${form.servicio} · ${nombrePropio(nombreServicio)}` : "")} placeholder="Ej.: 328 o medicina general"
              onChange={(e) => { setBuscarServicio(e.target.value); set("servicio")(""); }} />
          </label>
          {!!buscarServicio && !!servicios.data?.length && (
            <div className="resultados">
              {servicios.data.map((s) => (
                <button type="button" key={s.codigo} className="opcion" onClick={() => { set("servicio")(s.codigo!); setBuscarServicio(""); }}>
                  <span><strong>{s.codigo}</strong> {nombrePropio(s.nombre)}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
      <h3>Valores por defecto de cada consulta</h3>
      <p className="muted small">Se proponen al generar el RIPS y se pueden cambiar por atención.</p>
      <div className="form-grid">
        <Seleccion etiqueta="Modalidad de atención" tabla="modalidad_atencion" valor={form.modalidadAtencion} onChange={set("modalidadAtencion")} />
        <Seleccion etiqueta="Grupo de servicios" tabla="grupo_servicios" valor={form.grupoServicios} onChange={set("grupoServicios")} />
        <Seleccion etiqueta="Finalidad" tabla="finalidad" valor={form.finalidad} onChange={set("finalidad")}
          filtro={(r) => r.extra?.Consultas !== "NO"} ayuda="Solo las que aplican a consultas" />
        <Seleccion etiqueta="Causa externa" tabla="causa_externa" valor={form.causaExterna} onChange={set("causaExterna")}
          filtro={(r) => r.extra?.Consultas !== "NO"} />
        <Seleccion etiqueta="Vía de ingreso (procedimientos)" tabla="via_ingreso" valor={form.viaIngreso} onChange={set("viaIngreso")}
          filtro={(r) => r.extra?.Procedimientos !== "NO"} />
      </div>
      <div className="row">
        <button className="btn" disabled={guardar.isPending}>Guardar</button>
        {guardar.isSuccess && <span className="muted small">Guardado</span>}
        {guardar.error && <span className="field-error">{guardar.error.message}</span>}
      </div>
    </form>
  );
}

function Profesionales() {
  const lista = useQuery({ queryKey: ["rips-profesionales"], queryFn: async () => unwrap(await api.GET("/api/v1/rips/profesionales")) });
  return (
    <div className="stack">
      <p className="muted">El RIPS lleva el documento de quien atiende cada consulta o procedimiento.</p>
      {lista.isLoading && <p className="muted">Cargando…</p>}
      {lista.error && <p className="alert alert-error">{lista.error.message}</p>}
      {!!lista.data?.length && (
        <div className="card table-wrap">
          <table>
            <thead><tr><th>Médico</th><th>Tipo</th><th>Número de documento</th><th /></tr></thead>
            <tbody>{lista.data.map((p) => <FilaProfesional key={p.usuarioId} profesional={p} />)}</tbody>
          </table>
        </div>
      )}
      {lista.data?.length === 0 && <p className="muted">El consultorio no tiene médicos activos.</p>}
    </div>
  );
}

const DOCUMENTOS: [string, string][] = [["CC", "Cédula de ciudadanía"], ["CE", "Cédula de extranjería"], ["PA", "Pasaporte"],
  ["PT", "Permiso por protección temporal"], ["PE", "Permiso especial de permanencia"], ["CD", "Carné diplomático"], ["DE", "Documento extranjero"]];

function FilaProfesional({ profesional: p }: { profesional: Schemas["Profesional"] }) {
  const queryClient = useQueryClient();
  const [tipo, setTipo] = useState(p.tipoDocumento ?? "CC");
  const [numero, setNumero] = useState(p.numeroDocumento ?? "");
  const guardar = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/rips/profesionales/{usuarioId}", {
      params: { path: { usuarioId: p.usuarioId! } }, body: { tipoDocumento: tipo, numeroDocumento: numero },
    })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["rips-profesionales"] }),
  });
  const cambio = tipo !== (p.tipoDocumento ?? "CC") || numero !== (p.numeroDocumento ?? "");
  return (
    <tr>
      <td>{p.nombre} {!p.numeroDocumento && <span className="badge badge-warn">Falta</span>}</td>
      <td><select aria-label="Tipo de documento" value={tipo} onChange={(e) => setTipo(e.target.value)}>
        {DOCUMENTOS.map(([c, n]) => <option key={c} value={c}>{c} · {n}</option>)}
      </select></td>
      <td><input aria-label="Número de documento" className="campo-medio" value={numero} onChange={(e) => setNumero(e.target.value.replace(/[^A-Za-z0-9]/g, ""))} />
        {guardar.error && <small className="field-error">{guardar.error.message}</small>}</td>
      <td><button className="btn-link" disabled={!cambio || !numero || guardar.isPending} onClick={() => guardar.mutate()}>Guardar</button></td>
    </tr>
  );
}
