import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, expectOk, unwrap, type Schemas } from "../../api/client";
import { BuscadorCups, type Cups } from "./BuscadorCups";
import { pesos } from "./modulos";
import { TIPO_PAGADOR, tipoDeEntidad, usePagadores, type Pagador, type TipoPagador } from "./pagadores";
import { nombrePropio, useBusquedaReferencia, useReferencia } from "./referencias";

type Datos = Schemas["DatosPagador"];
const numero = (v: string) => Number(v.replace(/[.\s$]/g, "").replace(",", "."));
/** Cobertura o plan de beneficios más común para cada tipo (tabla coberturaPlanBeneficios). */
const COBERTURA: Partial<Record<TipoPagador, string>> = { EPS: "01", PREPAGADA: "11", ARL: "05", SOAT: "04", REGIMEN_ESPECIAL: "13" };

/** EPS, prepagadas, ARL, SOAT y el particular: lo que pide la factura de salud y las tarifas de cada convenio. */
export function Pagadores() {
  const lista = usePagadores();
  const [nuevo, setNuevo] = useState(false);
  return (
    <section className="stack">
      <p className="muted">
        Quién paga las atenciones. La secretaria elige el pagador del paciente y el de cada cita. El contrato (CUCON), la
        modalidad de pago y la cobertura los necesita quien emite la factura electrónica de salud.
      </p>
      {!nuevo ? <div><button className="btn" onClick={() => setNuevo(true)}>Agregar pagador</button></div>
        : <FormularioPagador onListo={() => setNuevo(false)} />}
      {lista.isLoading && <p className="muted">Cargando…</p>}
      {lista.error && <p className="alert alert-error">{lista.error.message}</p>}
      {lista.data?.map((p) => <TarjetaPagador key={p.id} pagador={p} />)}
    </section>
  );
}

function TarjetaPagador({ pagador: p }: { pagador: Pagador }) {
  const [editando, setEditando] = useState(false);
  const [tarifas, setTarifas] = useState(false);
  const coberturas = useReferencia("cobertura");
  const modalidades = useReferencia("modalidad_pago");
  if (editando) return <FormularioPagador pagador={p} onListo={() => setEditando(false)} />;
  const nombre = (lista: typeof coberturas.data, c?: string) => lista?.find((x) => x.codigo === c)?.nombre;
  return (
    <div className={`card stack-sm${p.activo ? "" : " inactivo"}`}>
      <div className="row between">
        <h3>{p.nombre} <span className="badge">{TIPO_PAGADOR[p.tipo!]}</span>
          {!p.activo && <span className="badge">Desactivado</span>}
          {p.exigeAutorizacion && <span className="badge badge-info">Exige autorización</span>}</h3>
        <div className="row">
          {p.tipo !== "PARTICULAR" && <button className="btn-link" onClick={() => setTarifas((v) => !v)}>{tarifas ? "Ocultar tarifas" : "Tarifas"}</button>}
          <button className="btn-link" onClick={() => setEditando(true)}>Editar</button>
        </div>
      </div>
      <p className="small muted">
        {[p.nit && `NIT ${p.nit}`, p.codigoEntidad && `código ${p.codigoEntidad}`, nombre(coberturas.data, p.cobertura),
          nombre(modalidades.data, p.modalidadPago), p.cucon && `CUCON ${p.cucon}`, p.numeroPoliza && `póliza ${p.numeroPoliza}`]
          .filter(Boolean).join(" · ") || "Sin datos de contrato"}
      </p>
      {tarifas && <Tarifas pagadorId={p.id!} />}
    </div>
  );
}

function FormularioPagador({ pagador, onListo }: { pagador?: Pagador; onListo: () => void }) {
  const queryClient = useQueryClient();
  const coberturas = useReferencia("cobertura");
  const modalidades = useReferencia("modalidad_pago");
  const sinContrato = useReferencia("factura_sin_contrato");
  const [buscar, setBuscar] = useState("");
  const entidades = useBusquedaReferencia("eapb", buscar);
  const [form, setForm] = useState({
    nombre: pagador?.nombre ?? "", tipo: (pagador?.tipo ?? "EPS") as TipoPagador, codigoEntidad: pagador?.codigoEntidad ?? "",
    nit: pagador?.nit ?? "", cobertura: pagador?.cobertura ?? "", modalidadPago: pagador?.modalidadPago ?? "",
    cucon: pagador?.cucon ?? "", numeroPoliza: pagador?.numeroPoliza ?? "", facturaSinContrato: pagador?.facturaSinContrato ?? "",
    exigeAutorizacion: pagador?.exigeAutorizacion ?? false, activo: pagador?.activo ?? true,
  });
  const particular = form.tipo === "PARTICULAR";
  const guardar = useMutation({
    mutationFn: async () => {
      const body = Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v === "" ? undefined : v])) as unknown as Datos;
      return pagador
        ? unwrap(await api.PUT("/api/v1/rips/pagadores/{id}", { params: { path: { id: pagador.id! } }, body }))
        : unwrap(await api.POST("/api/v1/rips/pagadores", { body }));
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["rips-pagadores"] }); onListo(); },
  });
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  return (
    <form className="card stack" onSubmit={(e) => { e.preventDefault(); guardar.mutate(); }}>
      <h3>{pagador ? `Editar ${pagador.nombre}` : "Nuevo pagador"}</h3>
      {!particular && (
        <div className="stack-sm">
          <label>Buscar la entidad en el catálogo de SISPRO
            <input value={buscar} onChange={(e) => setBuscar(e.target.value)} placeholder="Ej.: Nueva EPS, Sura, Colsanitas, Positiva…" />
          </label>
          {!!buscar && !!entidades.data?.length && (
            <div className="resultados">
              {entidades.data.map((r) => (
                <button type="button" key={r.codigo} className="opcion" onClick={() => {
                  const tipo = tipoDeEntidad(r.extra?.tipo);
                  setForm({ ...form, nombre: nombrePropio(r.nombre), codigoEntidad: r.codigo!, nit: r.extra?.nit ?? "", tipo,
                    cobertura: form.cobertura || COBERTURA[tipo] || "" });
                  setBuscar("");
                }}>
                  <span><strong>{nombrePropio(r.nombre)}</strong> <span className="muted small">· {r.codigo} · NIT {r.extra?.nit}</span></span>
                  <span className="muted small">{nombrePropio(r.extra?.tipo?.replace(/^\d+\.\s*/, ""))}</span>
                </button>
              ))}
            </div>
          )}
          {!!buscar && entidades.data?.length === 0 && <small className="muted">No está en el catálogo: llene los datos a mano.</small>}
        </div>
      )}
      <div className="form-grid">
        <label>Nombre<input value={form.nombre} onChange={set("nombre")} required maxLength={150} /></label>
        <label>Tipo
          <select value={form.tipo} onChange={set("tipo")} disabled={particular}>
            {(Object.keys(TIPO_PAGADOR) as TipoPagador[]).filter((t) => t !== "PARTICULAR" || particular)
              .map((t) => <option key={t} value={t}>{TIPO_PAGADOR[t]}</option>)}
          </select>
        </label>
        {!particular && <label>NIT<input value={form.nit} onChange={set("nit")} placeholder="900156264-2" /></label>}
        {!particular && <label>Código de la entidad<input value={form.codigoEntidad} onChange={set("codigoEntidad")} placeholder="Del catálogo (p. ej. EPS037)" /></label>}
        <label>Cobertura o plan de beneficios
          <select value={form.cobertura} onChange={set("cobertura")}>
            <option value="">—</option>
            {coberturas.data?.map((c) => <option key={c.codigo} value={c.codigo}>{c.codigo} · {c.nombre}</option>)}
          </select>
        </label>
        <label>Modalidad de pago
          <select value={form.modalidadPago} onChange={set("modalidadPago")}>
            <option value="">—</option>
            {modalidades.data?.map((c) => <option key={c.codigo} value={c.codigo}>{c.nombre}</option>)}
          </select>
        </label>
        {!particular && <label>Contrato (CUCON)<input value={form.cucon} onChange={set("cucon")} maxLength={64} placeholder="Código único del contrato (SIIFA)" /></label>}
        {!particular && <label>Número de póliza<input value={form.numeroPoliza} onChange={set("numeroPoliza")} maxLength={30} placeholder="Si no hay contrato (SOAT, pólizas)" /></label>}
        <label>Factura sin contrato
          <select value={form.facturaSinContrato} onChange={set("facturaSinContrato")}>
            <option value="">— (hay contrato)</option>
            {sinContrato.data?.map((c) => <option key={c.codigo} value={c.codigo}>{c.codigo} · {nombrePropio(c.nombre)}</option>)}
          </select>
        </label>
        {!particular && (
          <label className="check"><input type="checkbox" checked={form.exigeAutorizacion}
            onChange={(e) => setForm({ ...form, exigeAutorizacion: e.target.checked })} /> Exige número de autorización</label>
        )}
        {pagador && !particular && (
          <label className="check"><input type="checkbox" checked={form.activo} onChange={(e) => setForm({ ...form, activo: e.target.checked })} /> Activo</label>
        )}
      </div>
      <div className="row">
        <button className="btn" disabled={guardar.isPending}>Guardar</button>
        <button type="button" className="btn-link" onClick={onListo}>Cancelar</button>
        {guardar.error && <span className="field-error">{guardar.error.message}</span>}
      </div>
    </form>
  );
}

/** Precio por CUPS con este pagador. Sin tarifa, se usa la lista general (pestaña Precios). */
function Tarifas({ pagadorId }: { pagadorId: string }) {
  const queryClient = useQueryClient();
  const clave = ["rips-tarifas", pagadorId];
  const tarifas = useQuery({ queryKey: clave, queryFn: async () => unwrap(await api.GET("/api/v1/rips/pagadores/{id}/tarifas", { params: { path: { id: pagadorId } } })) });
  const [nuevo, setNuevo] = useState<Cups | null>(null);
  const [valor, setValor] = useState("");
  const refrescar = () => { queryClient.invalidateQueries({ queryKey: clave }); queryClient.invalidateQueries({ queryKey: ["cups"] }); };
  const fijar = useMutation({
    mutationFn: async ({ codigo, v }: { codigo: string; v: number }) => unwrap(await api.PUT("/api/v1/rips/pagadores/{id}/tarifas/{codigoCups}",
      { params: { path: { id: pagadorId, codigoCups: codigo } }, body: { valor: v } })),
    onSuccess: () => { setNuevo(null); setValor(""); refrescar(); },
  });
  const quitar = useMutation({
    mutationFn: async (codigo: string) => expectOk(await api.DELETE("/api/v1/rips/pagadores/{id}/tarifas/{codigoCups}",
      { params: { path: { id: pagadorId, codigoCups: codigo } } })),
    onSuccess: refrescar,
  });
  return (
    <div className="stack-sm">
      {!nuevo ? <BuscadorCups etiqueta="Agregar una tarifa del convenio" onElegir={(c) => { setNuevo(c); setValor(""); }} /> : (
        <form className="row" onSubmit={(e) => { e.preventDefault(); fijar.mutate({ codigo: nuevo.codigo!, v: numero(valor) }); }}>
          <span className="grow"><strong>{nuevo.codigo}</strong> {nuevo.descripcion}
            {nuevo.valorUnitario != null && <span className="muted small"> · lista general {pesos(nuevo.valorUnitario)}</span>}</span>
          <label className="row">Tarifa <input className="campo-medio" inputMode="numeric" autoFocus value={valor} onChange={(e) => setValor(e.target.value)} required /></label>
          <button className="btn" disabled={fijar.isPending || valor.trim() === ""}>Guardar</button>
          <button type="button" className="btn-link" onClick={() => setNuevo(null)}>Cancelar</button>
        </form>
      )}
      {(fijar.error || quitar.error) && <p className="alert alert-error">{(fijar.error ?? quitar.error)?.message}</p>}
      {tarifas.data?.length === 0 && <p className="muted small">Sin tarifas propias: se usa la lista general. Conviene empezar por las consultas de primera vez y de control (p. ej. 890201 y 890301), que Galenum sugiere en cada atención.</p>}
      {!!tarifas.data?.length && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>CUPS</th><th>Descripción</th><th>Tarifa</th><th /></tr></thead>
            <tbody>{tarifas.data.map((t) => (
              <tr key={t.codigoCups}>
                <td><strong>{t.codigoCups}</strong></td><td className="small">{t.descripcion}</td><td>{pesos(t.valor)}</td>
                <td><button className="btn-link danger" disabled={quitar.isPending} onClick={() => quitar.mutate(t.codigoCups!)}>Quitar</button></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
    </div>
  );
}
