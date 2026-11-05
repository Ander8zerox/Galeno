import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, expectOk, subirArchivo, unwrap, type Schemas } from "../../api/client";
import { BotonArchivo } from "../historia/Documentos";
import { pesos } from "./modulos";
import { usePagadores } from "./pagadores";
import { nombrePropio, useReferencia } from "./referencias";

type Lote = Schemas["LoteRipsVista"];
type AtencionLote = Schemas["AtencionDelLote"];
type Hallazgo = Schemas["HallazgoRips"];

const ESTADO: Record<string, [string, string]> = {
  BORRADOR: ["Borrador", "badge-warn"], GENERADO: ["Generado", "badge-ok"], ANULADO: ["Anulado", "badge-danger"],
};
const fecha = (f?: string) => (f ? new Date(f.length === 10 ? f + "T12:00:00" : f).toLocaleDateString("es-CO") : "");

/** Primer y último día del mes anterior: el período que más se factura. */
function mesAnterior(): [string, string] {
  const hoy = new Date();
  const desde = new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1);
  const hasta = new Date(hoy.getFullYear(), hoy.getMonth(), 0);
  const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  return [iso(desde), iso(hasta)];
}

/**
 * Lotes del RIPS: las atenciones firmadas de un pagador en un período, revisadas, con el JSON listo para adjuntar a la
 * factura. El envío al Ministerio (MUV) llega después.
 */
export function Lotes({ loteId, onAbrir }: { loteId: string | null; onAbrir: (id: string | null) => void }) {
  if (loteId) return <DetalleLote id={loteId} onVolver={() => onAbrir(null)} />;
  return <ListaLotes onAbrir={onAbrir} />;
}

function ListaLotes({ onAbrir }: { onAbrir: (id: string) => void }) {
  const lotes = useQuery({ queryKey: ["rips-lotes"], queryFn: async () => unwrap(await api.GET("/api/v1/rips/lotes")) });
  return (
    <div className="stack">
      <p className="muted">
        Un lote reúne las atenciones firmadas de un pagador en un período: las que van en una factura. Galenum las revisa
        contra las reglas del Ministerio y genera el archivo JSON que se adjunta a la factura electrónica.
      </p>
      <NuevoLote onCreado={onAbrir} />
      {lotes.error && <p className="alert alert-error">{lotes.error.message}</p>}
      {!!lotes.data?.length && (
        <div className="card table-wrap">
          <table>
            <thead><tr><th>Pagador</th><th>Período</th><th>Factura</th><th>Atenciones</th><th>Estado</th><th /></tr></thead>
            <tbody>
              {lotes.data.map((l) => (
                <tr key={l.id}>
                  <td>{l.pagadorNombre}</td>
                  <td>{fecha(l.desde)} a {fecha(l.hasta)}</td>
                  <td>{l.sinFactura ? <span className="muted">Sin factura</span> : l.numeroFactura ?? <span className="muted">—</span>}</td>
                  <td>{l.atenciones}</td>
                  <td><span className={`badge ${ESTADO[l.estado!][1]}`}>{ESTADO[l.estado!][0]}</span></td>
                  <td><button className="btn-link" onClick={() => onAbrir(l.id!)}>Abrir</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {lotes.data?.length === 0 && <p className="muted">Todavía no hay lotes.</p>}
    </div>
  );
}

function NuevoLote({ onCreado }: { onCreado: (id: string) => void }) {
  const queryClient = useQueryClient();
  const pagadores = usePagadores();
  const [desde0, hasta0] = mesAnterior();
  const [form, setForm] = useState({ pagadorId: "", desde: desde0, hasta: hasta0, sinFactura: false });
  const pagador = pagadores.data?.find((p) => p.id === form.pagadorId);
  const crear = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/rips/lotes", {
      body: { ...form, sinFactura: pagador?.tipo === "PARTICULAR" && form.sinFactura },
    })),
    onSuccess: (l) => {
      queryClient.invalidateQueries({ queryKey: ["rips-lotes"] });
      onCreado(l.id!);
    },
  });
  return (
    <form className="card stack" onSubmit={(e) => { e.preventDefault(); crear.mutate(); }}>
      <h3>Nuevo lote</h3>
      <div className="form-grid">
        <label>Pagador
          <select required value={form.pagadorId} onChange={(e) => setForm({ ...form, pagadorId: e.target.value })}>
            <option value="">—</option>
            {pagadores.data?.map((p) => <option key={p.id} value={p.id}>{p.nombre}{p.activo ? "" : " (inactivo)"}</option>)}
          </select>
        </label>
        <label>Desde<input type="date" required value={form.desde} onChange={(e) => setForm({ ...form, desde: e.target.value })} /></label>
        <label>Hasta<input type="date" required value={form.hasta} onChange={(e) => setForm({ ...form, hasta: e.target.value })} /></label>
      </div>
      {pagador?.tipo === "PARTICULAR" && (
        <label className="check">
          <input type="checkbox" checked={form.sinFactura} onChange={(e) => setForm({ ...form, sinFactura: e.target.checked })} />
          RIPS sin factura <span className="muted small">(profesional independiente no obligado a facturar)</span>
        </label>
      )}
      <div className="row">
        <button className="btn" disabled={crear.isPending}>{crear.isPending ? "Buscando atenciones…" : "Crear lote"}</button>
        {crear.error && <span className="field-error">{crear.error.message}</span>}
      </div>
    </form>
  );
}

function DetalleLote({ id, onVolver }: { id: string; onVolver: () => void }) {
  const queryClient = useQueryClient();
  const lote = useQuery({
    queryKey: ["rips-lote", id],
    queryFn: async () => unwrap(await api.GET("/api/v1/rips/lotes/{id}", { params: { path: { id } } })),
  });
  const poner = (l: Lote) => {
    queryClient.setQueryData(["rips-lote", id], l);
    queryClient.invalidateQueries({ queryKey: ["rips-lotes"] });
  };
  const generar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/rips/lotes/{id}/generacion", { params: { path: { id } } })),
    onSuccess: poner,
    onError: () => lote.refetch(),
  });
  const eliminar = useMutation({
    mutationFn: async () => expectOk(await api.DELETE("/api/v1/rips/lotes/{id}", { params: { path: { id } } })),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["rips-lotes"] }); onVolver(); },
  });
  const [anulando, setAnulando] = useState(false);
  const [motivo, setMotivo] = useState("");
  const anular = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/rips/lotes/{id}/anulacion", { params: { path: { id } }, body: { motivo } })),
    onSuccess: (l) => { poner(l); setAnulando(false); },
  });

  if (lote.isLoading) return <p className="muted">Revisando el lote…</p>;
  if (lote.error || !lote.data) return <p className="alert alert-error">{lote.error?.message}</p>;
  const l = lote.data;
  const borrador = l.estado === "BORRADOR";
  const t = l.totales!;
  const archivo = l.sinFactura ? `RIPS_sin_factura_${l.desde}_${l.hasta}.json` : `RIPS_${l.numeroFactura}.json`;

  return (
    <div className="stack">
      <div className="row between">
        <button className="btn-link" onClick={onVolver}>← Lotes</button>
        <span className={`badge ${ESTADO[l.estado!][1]}`}>{ESTADO[l.estado!][0]}</span>
      </div>
      <div className="card stack-sm">
        <h3>{l.pagadorNombre} · {fecha(l.desde)} a {fecha(l.hasta)}{l.sinFactura && <span className="badge badge-info">RIPS sin factura</span>}</h3>
        <p className="small">
          {t.usuarios} {t.usuarios === 1 ? "paciente" : "pacientes"} · {t.consultas} {t.consultas === 1 ? "consulta" : "consultas"} ·{" "}
          {t.procedimientos} {t.procedimientos === 1 ? "procedimiento" : "procedimientos"} · servicios {pesos(t.valorServicios)} · pagos
          moderadores {pesos(t.pagosModeradores)}
        </p>
        {l.estado === "ANULADO" && <p className="alert alert-warn">Anulado el {fecha(l.anuladoEn)}: {l.motivoAnulacion}</p>}
        {!l.sinFactura && <Factura lote={l} onCambio={poner} />}
      </div>

      {borrador && <Revision lote={l} />}

      <div className="stack-sm">
        {l.atenciones?.map((a) => <FilaAtencion key={a.id} lote={l} atencion={a} onCambio={poner} />)}
      </div>

      <div className="card row between acciones-atencion">
        {borrador ? (
          <div className="row">
            <button className="btn" disabled={generar.isPending || (l.rechazos ?? 0) > 0} onClick={() => generar.mutate()}>
              {generar.isPending ? "Generando…" : "Generar RIPS"}
            </button>
            <button className="btn btn-secondary" disabled={eliminar.isPending}
              onClick={() => confirm("¿Eliminar este lote? Sus atenciones quedan libres para otro.") && eliminar.mutate()}>Eliminar borrador</button>
          </div>
        ) : (
          <div className="row">
            {l.estado === "GENERADO" && <BotonArchivo ruta={`/api/v1/rips/lotes/${id}/json`} nombre={archivo} className="btn">Descargar JSON</BotonArchivo>}
            <button className="btn btn-secondary" disabled title="El envío al Ministerio (MUV) llega en la siguiente fase">Enviar al Ministerio</button>
            {l.estado === "GENERADO" && !anulando && <button className="btn-link danger" onClick={() => setAnulando(true)}>Anular</button>}
          </div>
        )}
        {(generar.error || eliminar.error || anular.error) && <span className="field-error">{(generar.error ?? eliminar.error ?? anular.error)!.message}</span>}
      </div>
      {l.estado === "GENERADO" && <p className="muted small">El envío al Ministerio (MUV) llega en la siguiente fase. Mientras tanto, descargue el JSON y cárguelo en el MUV con el XML de la factura.</p>}
      {anulando && (
        <form className="card stack-sm" onSubmit={(e) => { e.preventDefault(); anular.mutate(); }}>
          <label>Motivo de la anulación
            <input required maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej.: la factura se anuló con nota crédito" />
          </label>
          <p className="muted small">Las atenciones quedan libres para otro lote. El JSON generado se conserva como constancia.</p>
          <div className="row">
            <button className="btn btn-secondary" disabled={anular.isPending}>Anular lote</button>
            <button type="button" className="btn-link" onClick={() => setAnulando(false)}>Cancelar</button>
          </div>
        </form>
      )}
    </div>
  );
}

/** Número de la factura y XML (opcional) para cruzarla. */
function Factura({ lote: l, onCambio }: { lote: Lote; onCambio: (l: Lote) => void }) {
  const [numero, setNumero] = useState(l.numeroFactura ?? "");
  const archivo = useRef<HTMLInputElement>(null);
  const borrador = l.estado === "BORRADOR";
  const guardar = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/rips/lotes/{id}/factura", { params: { path: { id: l.id! } }, body: { numeroFactura: numero } })),
    onSuccess: onCambio,
  });
  const subir = useMutation({
    mutationFn: async (archivo: File) => {
      const datos = new FormData();
      datos.append("archivo", archivo);
      return subirArchivo<Lote>(`/api/v1/rips/lotes/${l.id}/xml`, datos);
    },
    onSuccess: (r) => { onCambio(r); setNumero(r.numeroFactura ?? ""); },
  });
  const quitar = useMutation({
    mutationFn: async () => unwrap(await api.DELETE("/api/v1/rips/lotes/{id}/xml", { params: { path: { id: l.id! } } })),
    onSuccess: onCambio,
  });
  if (!borrador) return <p className="small">Factura <strong>{l.numeroFactura}</strong>{l.xml && " · XML cruzado"}</p>;
  const error = guardar.error ?? subir.error ?? quitar.error;
  return (
    <div className="stack-sm">
      <form className="row" style={{ alignItems: "flex-end" }} onSubmit={(e) => { e.preventDefault(); guardar.mutate(); }}>
        <label>Número de la factura electrónica
          <input className="campo-medio" maxLength={20} value={numero} placeholder="Ej.: FE1234"
            onChange={(e) => setNumero(e.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, ""))} />
        </label>
        <button className="btn btn-secondary" disabled={guardar.isPending || numero === (l.numeroFactura ?? "")}>
          {guardar.isPending ? "Guardando…" : "Guardar número"}
        </button>
        {numero !== (l.numeroFactura ?? "") ? <span className="badge badge-warn">Sin guardar</span>
          : guardar.isSuccess && <span className="muted small">Guardado</span>}
      </form>
      <div className="row">
        {l.xml ? (
          <span className="small">XML cargado: factura {l.xml.numero ?? "—"}, NIT {l.xml.nit ?? "—"}, {fecha(l.xml.desde)} a {fecha(l.xml.hasta)},
            pagos moderadores {pesos(l.xml.pagosModeradores)} · <button className="btn-link" onClick={() => quitar.mutate()}>Quitar</button></span>
        ) : (
          <>
            {/* Botón propio: el campo de archivo del navegador se muestra en el idioma del navegador */}
            <input ref={archivo} type="file" accept=".xml,text/xml,application/xml" hidden
              onChange={(e) => { const f = e.target.files?.[0]; if (f) subir.mutate(f); e.target.value = ""; }} />
            <button type="button" className="btn btn-secondary" disabled={subir.isPending} onClick={() => archivo.current?.click()}>
              {subir.isPending ? "Leyendo el XML…" : "Cargar el XML de la factura"}
            </button>
            <span className="muted small">Opcional: Galenum cruza NIT, número, período y pagos moderadores.</span>
          </>
        )}
      </div>
      {error && <span className="field-error">{error.message}</span>}
    </div>
  );
}

function Revision({ lote: l }: { lote: Lote }) {
  const rechazos = l.rechazos ?? 0;
  const avisos = l.avisos ?? 0;
  return (
    <div className={`alert ${rechazos ? "alert-error" : avisos ? "alert-warn" : ""}`}>
      {rechazos
        ? `${rechazos} ${rechazos === 1 ? "problema impide" : "problemas impiden"} generar: el Ministerio rechazaría el archivo. Corríjalos o quite esas atenciones.`
        : "Listo para generar."}
      {avisos > 0 && ` ${avisos} ${avisos === 1 ? "aviso" : "avisos"}: el Ministerio los notifica pero no rechaza.`}
      {!!l.hallazgos?.length && <ListaHallazgos hallazgos={l.hallazgos} />}
    </div>
  );
}

/** Dónde se corrige cada hallazgo. */
function enlace(h: Hallazgo, a?: AtencionLote): { a: string; texto: string } | null {
  switch (h.donde) {
    case "PACIENTE": return h.pacienteId ? { a: `/pacientes/${h.pacienteId}`, texto: "Ficha del paciente" } : null;
    case "PRESTADOR": return { a: "/rips?pestana=prestador", texto: "Prestador" };
    case "CONSULTORIO": return { a: "/rips?pestana=prestador", texto: "NIT en Prestador" };
    case "PROFESIONAL": return { a: "/rips?pestana=profesionales", texto: "Profesionales" };
    case "PAGADOR": return { a: "/rips?pestana=pagadores", texto: "Pagadores" };
    case "PRECIOS": return { a: "/rips?pestana=precios", texto: "Precios" };
    case "COBRO": case "ATENCION": return a ? { a: `/pacientes/${a.pacienteId}/atenciones/${a.id}`, texto: "Atención" } : null;
    default: return null;
  }
}

function ListaHallazgos({ hallazgos, atencion }: { hallazgos: Hallazgo[]; atencion?: AtencionLote }) {
  return (
    <ul className="hallazgos">
      {hallazgos.map((h, i) => {
        const e = enlace(h, atencion);
        return (
          <li key={i} className={h.nivel === "RECHAZO" ? "rechazo" : "aviso"}>
            <span className={`badge ${h.nivel === "RECHAZO" ? "badge-danger" : "badge-warn"}`}>{h.nivel === "RECHAZO" ? "Rechazo" : "Aviso"}</span>{" "}
            {h.mensaje}{h.regla && <span className="muted small"> ({h.regla})</span>}
            {e && <> · <Link to={e.a}>{e.texto}</Link></>}
          </li>
        );
      })}
    </ul>
  );
}

function FilaAtencion({ lote: l, atencion: a, onCambio }: { lote: Lote; atencion: AtencionLote; onCambio: (l: Lote) => void }) {
  const [ajustando, setAjustando] = useState(false);
  const borrador = l.estado === "BORRADOR";
  const quitar = useMutation({
    mutationFn: async () => unwrap(await api.DELETE("/api/v1/rips/lotes/{id}/atenciones/{atencionId}", { params: { path: { id: l.id!, atencionId: a.id! } } })),
    onSuccess: onCambio,
  });
  const hallazgos = a.hallazgos ?? [];
  const rechazos = hallazgos.filter((h) => h.nivel === "RECHAZO").length;
  return (
    <div className={`card stack-sm ${rechazos ? "con-rechazo" : ""}`}>
      <div className="row between">
        <div>
          <strong>{a.pacienteNombre}</strong> <span className="muted small">{a.documento}</span>
          <div className="small muted">{new Date(a.fecha!).toLocaleString("es-CO", { dateStyle: "medium", timeStyle: "short" })} · {a.medicoNombre}
            {a.incapacidad && " · con incapacidad"}{a.numeroAutorizacion && ` · aut. ${a.numeroAutorizacion}`}
            {!!a.valorPagoModerador && ` · pago moderador ${pesos(a.valorPagoModerador)}`}</div>
        </div>
        {borrador && (
          <div className="row">
            <button className="btn-link" onClick={() => setAjustando(!ajustando)}>{ajustando ? "Cerrar" : "Ajustar"}</button>
            <button className="btn-link" disabled={quitar.isPending} onClick={() => quitar.mutate()}>Quitar del lote</button>
          </div>
        )}
      </div>
      <ul className="small">{a.servicios?.map((s, i) => <li key={i}>{s}</li>)}</ul>
      {!!hallazgos.length && <ListaHallazgos hallazgos={hallazgos} atencion={a} />}
      {ajustando && <AjustesAtencion lote={l} atencion={a} onCambio={(r) => { onCambio(r); setAjustando(false); }} />}
      {quitar.error && <span className="field-error">{quitar.error.message}</span>}
    </div>
  );
}

/** Campo, etiqueta, tabla y a qué servicios aplica la opción (columna de SISPRO), como en la pestaña Prestador. */
const CAMPOS: [keyof Schemas["Ajustes"], string, string, string?][] = [
  ["finalidad", "Finalidad", "finalidad", "Consultas"], ["causaExterna", "Causa externa", "causa_externa", "Consultas"],
  ["modalidadAtencion", "Modalidad de atención", "modalidad_atencion"], ["grupoServicios", "Grupo de servicios", "grupo_servicios"],
  ["viaIngreso", "Vía de ingreso (procedimientos)", "via_ingreso", "Procedimientos"],
];

/** Para esta atención, valores distintos de los del prestador (vacío: el del prestador). */
function AjustesAtencion({ lote: l, atencion: a, onCambio }: { lote: Lote; atencion: AtencionLote; onCambio: (l: Lote) => void }) {
  const [form, setForm] = useState<Record<string, string>>(() =>
    Object.fromEntries([...CAMPOS.map(([k]) => k), "servicio"].map((k) => [k, (a.ajustes as Record<string, string | undefined>)?.[k] ?? ""])));
  const guardar = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/rips/lotes/{id}/atenciones/{atencionId}", {
      params: { path: { id: l.id!, atencionId: a.id! } },
      body: Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v || undefined])),
    })),
    onSuccess: onCambio,
  });
  return (
    <form className="stack-sm" onSubmit={(e) => { e.preventDefault(); guardar.mutate(); }}>
      <div className="form-grid">
        {CAMPOS.map(([k, etiqueta, tabla, aplica]) => (
          <SeleccionReferencia key={k} etiqueta={etiqueta} tabla={tabla} aplica={aplica} valor={form[k]} onChange={(v) => setForm({ ...form, [k]: v })} />
        ))}
        <label>Servicio (código)
          <input inputMode="numeric" maxLength={4} value={form.servicio} placeholder="Ej.: 328"
            onChange={(e) => setForm({ ...form, servicio: e.target.value.replace(/\D/g, "") })} />
        </label>
      </div>
      <div className="row">
        <button className="btn btn-secondary" disabled={guardar.isPending}>Guardar ajustes</button>
        {guardar.error && <span className="field-error">{guardar.error.message}</span>}
      </div>
    </form>
  );
}

function SeleccionReferencia({ etiqueta, tabla, aplica, valor, onChange }: {
  etiqueta: string; tabla: string; aplica?: string; valor: string; onChange: (v: string) => void;
}) {
  const lista = useReferencia(tabla);
  return (
    <label>{etiqueta}
      <select value={valor} onChange={(e) => onChange(e.target.value)}>
        <option value="">Del prestador</option>
        {lista.data?.filter((r) => !aplica || r.extra?.[aplica] !== "NO")
          .map((r) => <option key={r.codigo} value={r.codigo}>{r.codigo} · {nombrePropio(r.nombre)}</option>)}
      </select>
    </label>
  );
}
