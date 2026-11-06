import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, expectOk, subirArchivo, unwrap, type Schemas } from "../../api/client";
import { BotonArchivo } from "../historia/Documentos";
import { pesos } from "./modulos";
import { nombrePropio, useReferencia } from "./referencias";

type Nota = Schemas["NotaRipsVista"];
type Servicio = Schemas["ServicioVista"];
type Tipo = NonNullable<Nota["tipo"]>;

const TIPO: Record<Tipo, string> = { NC: "Nota crédito", ND: "Nota débito", NA: "Nota de ajuste RIPS" };
const ESTADO: Record<string, [string, string]> = {
  BORRADOR: ["Borrador", "badge-warn"], GENERADA: ["Generada", "badge-ok"], ANULADA: ["Anulada", "badge-danger"],
};

/** Lo que una nota puede corregir de cada servicio, con su tabla de SISPRO (sin tabla: se escribe). Como en el servidor. */
const CAMPOS: Record<string, [string, string, string?][]> = {
  consultas: [["codDiagnosticoPrincipal", "Diagnóstico principal"], ["codDiagnosticoRelacionado1", "Relacionado 1"],
    ["codDiagnosticoRelacionado2", "Relacionado 2"], ["codDiagnosticoRelacionado3", "Relacionado 3"],
    ["tipoDiagnosticoPrincipal", "Tipo de diagnóstico", "tipo_diagnostico"], ["finalidadTecnologiaSalud", "Finalidad", "finalidad"],
    ["causaMotivoAtencion", "Causa externa", "causa_externa"], ["modalidadGrupoServicioTecSal", "Modalidad", "modalidad_atencion"],
    ["grupoServicios", "Grupo de servicios", "grupo_servicios"], ["codServicio", "Servicio (código)"], ["numAutorizacion", "Autorización"]],
  procedimientos: [["codDiagnosticoPrincipal", "Diagnóstico principal"], ["codDiagnosticoRelacionado", "Diagnóstico relacionado"],
    ["finalidadTecnologiaSalud", "Finalidad", "finalidad"], ["modalidadGrupoServicioTecSal", "Modalidad", "modalidad_atencion"],
    ["grupoServicios", "Grupo de servicios", "grupo_servicios"], ["codServicio", "Servicio (código)"],
    ["viaIngresoServicioSalud", "Vía de ingreso", "via_ingreso"], ["numAutorizacion", "Autorización"]],
};

const texto = (v: unknown) => (v === null || v === undefined ? "" : String(v));
const numero = (v: string) => (v.trim() === "" ? undefined : Number(v.replace(/[.\s$]/g, "").replace(",", ".")));

/**
 * Notas crédito, débito y de ajuste de un lote generado. Se arman sobre el RIPS que se generó: se marcan los servicios
 * afectados y su valor o sus datos corregidos.
 */
export function NotasLote({ loteId, activo }: { loteId: string; activo: boolean }) {
  const [abierta, setAbierta] = useState<string | null>(null);
  const notas = useQuery({
    queryKey: ["rips-notas", loteId],
    queryFn: async () => unwrap(await api.GET("/api/v1/rips/lotes/{id}/notas", { params: { path: { id: loteId } } })),
  });
  if (abierta) return <EditorNota id={abierta} loteId={loteId} onCerrar={() => setAbierta(null)} />;
  const vigentes = notas.data?.filter((n) => n.estado !== "ANULADA") ?? [];
  const total = vigentes.some((n) => n.total);
  return (
    <div className="card stack-sm">
      <h3>Notas</h3>
      <p className="muted small">
        Para corregir la factura después de radicarla: la nota crédito (glosa aceptada) y la débito las emite su
        facturador ante la DIAN; aquí se genera su RIPS. La de ajuste corrige datos sin tocar valores.
      </p>
      {total && <p className="alert alert-warn small">La factura tiene una nota crédito al 100%: no admite más notas.</p>}
      {!!notas.data?.length && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Nota</th><th>Número</th><th>Valor</th><th>Estado</th><th /></tr></thead>
            <tbody>
              {notas.data.map((n) => (
                <tr key={n.id}>
                  <td>{TIPO[n.tipo!]}{n.total && " al 100%"}<div className="muted small">{n.motivo}</div></td>
                  <td>{n.numero}</td>
                  <td>{n.valor === undefined || n.valor === null ? "—" : pesos(n.valor)}</td>
                  <td><span className={`badge ${ESTADO[n.estado!][1]}`}>{ESTADO[n.estado!][0]}</span></td>
                  <td><button className="btn-link" onClick={() => setAbierta(n.id!)}>Abrir</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {activo && !total && <NuevaNota loteId={loteId} onCreada={setAbierta} />}
    </div>
  );
}

function NuevaNota({ loteId, onCreada }: { loteId: string; onCreada: (id: string) => void }) {
  const queryClient = useQueryClient();
  const [abierto, setAbierto] = useState(false);
  const [form, setForm] = useState({ tipo: "NC" as Tipo, total: false, numero: "", motivo: "" });
  const crear = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/rips/lotes/{id}/notas", {
      params: { path: { id: loteId } }, body: { ...form, total: form.tipo === "NC" && form.total },
    })),
    onSuccess: (n) => {
      queryClient.invalidateQueries({ queryKey: ["rips-notas", loteId] });
      onCreada(n.id!);
    },
  });
  if (!abierto) return <div><button className="btn btn-secondary" onClick={() => setAbierto(true)}>Nueva nota</button></div>;
  return (
    <form className="stack-sm" onSubmit={(e) => { e.preventDefault(); crear.mutate(); }}>
      <div className="form-grid">
        <label>Tipo
          <select value={form.tipo} onChange={(e) => setForm({ ...form, tipo: e.target.value as Tipo })}>
            {(Object.keys(TIPO) as Tipo[]).map((t) => <option key={t} value={t}>{TIPO[t]}</option>)}
          </select>
        </label>
        <label>Número {form.tipo === "NA" ? "de la nota de ajuste" : "de la nota (el de la DIAN)"}
          <input required maxLength={20} value={form.numero} placeholder="Ej.: NC120"
            onChange={(e) => setForm({ ...form, numero: e.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, "") })} />
        </label>
        <label className="wide">Motivo
          <input required maxLength={300} value={form.motivo} placeholder="Ej.: glosa aceptada por tarifa"
            onChange={(e) => setForm({ ...form, motivo: e.target.value })} />
        </label>
      </div>
      {form.tipo === "NC" && (
        <label className="check">
          <input type="checkbox" checked={form.total} onChange={(e) => setForm({ ...form, total: e.target.checked })} />
          Por el 100% de la factura <span className="muted small">(la EPS la devolvió y se aceptó: va sin RIPS)</span>
        </label>
      )}
      <div className="row">
        <button className="btn" disabled={crear.isPending}>Crear nota</button>
        <button type="button" className="btn-link" onClick={() => setAbierto(false)}>Cancelar</button>
        {crear.error && <span className="field-error">{crear.error.message}</span>}
      </div>
    </form>
  );
}

type Marca = { valor: string; datos: Record<string, string> };

function EditorNota({ id, loteId, onCerrar }: { id: string; loteId: string; onCerrar: () => void }) {
  const nota = useQuery({
    queryKey: ["rips-nota", id],
    queryFn: async () => unwrap(await api.GET("/api/v1/rips/notas/{notaId}", { params: { path: { notaId: id } } })),
  });
  const servicios = useQuery({
    queryKey: ["rips-servicios", loteId],
    queryFn: async () => unwrap(await api.GET("/api/v1/rips/lotes/{id}/servicios", { params: { path: { id: loteId } } })),
  });
  if (nota.isLoading || servicios.isLoading) return <p className="muted">Cargando la nota…</p>;
  if (nota.error || !nota.data) return <p className="alert alert-error">{nota.error?.message}</p>;
  return <FormularioNota key={nota.data.estado} nota={nota.data} servicios={servicios.data ?? []} loteId={loteId} onCerrar={onCerrar} />;
}

function FormularioNota({ nota: n, servicios, loteId, onCerrar }: { nota: Nota; servicios: Servicio[]; loteId: string; onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const borrador = n.estado === "BORRADOR" && !n.total;
  const inicial = () => Object.fromEntries((n.servicios ?? []).map((s) => [s.clave!, {
    valor: s.valor === undefined || s.valor === null ? "" : String(s.valor), datos: { ...(s.datos ?? {}) },
  }]));
  const [marcas, setMarcas] = useState<Record<string, Marca>>(inicial);
  const [base, setBase] = useState(() => JSON.stringify(inicial()));
  const [numeroNota, setNumeroNota] = useState(n.numero ?? "");
  const [motivo, setMotivo] = useState(n.motivo ?? "");
  const sucio = JSON.stringify(marcas) !== base || numeroNota !== n.numero || motivo !== n.motivo;
  const poner = (nueva: Nota) => {
    queryClient.setQueryData(["rips-nota", n.id], nueva);
    queryClient.invalidateQueries({ queryKey: ["rips-notas", loteId] });
    queryClient.invalidateQueries({ queryKey: ["rips-servicios", loteId] });
  };
  const guardar = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/rips/notas/{notaId}", {
      params: { path: { notaId: n.id! } },
      body: {
        numero: numeroNota, motivo,
        servicios: Object.entries(marcas).map(([clave, m]) => ({
          clave, valor: n.tipo === "NA" ? undefined : numero(m.valor), datos: Object.keys(m.datos).length ? m.datos : undefined,
        })),
      },
    })),
    onSuccess: (r) => { poner(r); setBase(JSON.stringify(marcas)); },
  });
  const generar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/rips/notas/{notaId}/generacion", { params: { path: { notaId: n.id! } } })),
    onSuccess: poner,
  });
  const eliminar = useMutation({
    mutationFn: async () => expectOk(await api.DELETE("/api/v1/rips/notas/{notaId}", { params: { path: { notaId: n.id! } } })),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["rips-notas", loteId] }); onCerrar(); },
  });
  const [anulando, setAnulando] = useState(false);
  const [motivoAnulacion, setMotivoAnulacion] = useState("");
  const anular = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/rips/notas/{notaId}/anulacion", {
      params: { path: { notaId: n.id! } }, body: { motivo: motivoAnulacion },
    })),
    onSuccess: (r) => { poner(r); setAnulando(false); },
  });
  const error = guardar.error ?? generar.error ?? eliminar.error ?? anular.error;
  const marcar = (clave: string, si: boolean) => {
    const copia = { ...marcas };
    if (si) copia[clave] = { valor: "", datos: {} };
    else delete copia[clave];
    setMarcas(copia);
  };
  const delNota = (clave: string) => (n.hallazgos ?? []).filter((h) => h.clave === clave);
  const generales = (n.hallazgos ?? []).filter((h) => !h.clave);
  const totalMarcado = Object.values(marcas).reduce((s, m) => s + (numero(m.valor) ?? 0), 0);

  return (
    <div className="card stack">
      <div className="row between">
        <button className="btn-link" onClick={onCerrar}>← Notas</button>
        <span className={`badge ${ESTADO[n.estado!][1]}`}>{ESTADO[n.estado!][0]}</span>
      </div>
      <h3>{TIPO[n.tipo!]}{n.total && " al 100%"} {n.numero} · factura {n.numeroFactura}</h3>
      {n.estado === "ANULADA" && <p className="alert alert-warn">Anulada: {n.motivoAnulacion}</p>}
      {n.total && <p className="muted">La factura se devolvió completa: esta nota va al Ministerio sin RIPS y deja el lote marcado como afectado al 100%.</p>}

      {borrador ? (
        <div className="form-grid">
          <label>Número<input maxLength={20} value={numeroNota} onChange={(e) => setNumeroNota(e.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, ""))} /></label>
          <label className="wide">Motivo<input maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} /></label>
        </div>
      ) : <p className="small">{n.motivo}</p>}

      {borrador && n.tipo !== "NA" && <XmlNota nota={n} onCambio={poner} />}

      {borrador && (
        <>
          <p className="muted small">
            {n.tipo === "NC" && "Marque los servicios glosados y escriba lo que se acredita de cada uno. Si la glosa también es de datos, corríjalos en el mismo servicio."}
            {n.tipo === "ND" && "Marque los servicios y escriba lo que se suma a cada uno."}
            {n.tipo === "NA" && "Marque los servicios y corrija sus datos. Los valores no cambian."}
          </p>
          <div className="stack-sm">
            {servicios.map((s) => (
              <FilaServicio key={s.clave} servicio={s} tipo={n.tipo!} marca={marcas[s.clave!]} hallazgos={delNota(s.clave!)}
                onMarcar={(si) => marcar(s.clave!, si)} onCambio={(m) => setMarcas({ ...marcas, [s.clave!]: m })} />
            ))}
          </div>
          {n.tipo !== "NA" && <p className="small">Total de la nota: <strong>{pesos(totalMarcado)}</strong></p>}
        </>
      )}

      {!borrador && !n.total && (
        <ul className="small">
          {(n.servicios ?? []).map((s) => {
            const sv = servicios.find((x) => x.clave === s.clave);
            return (
              <li key={s.clave}>{sv ? `${sv.codigo} · ${sv.paciente || sv.documento} · ${sv.fecha}` : s.clave}
                {s.valor !== undefined && s.valor !== null && ` · ${pesos(s.valor)}`}
                {s.datos && Object.keys(s.datos).length > 0 && ` · corrige ${Object.keys(s.datos).length} ${Object.keys(s.datos).length === 1 ? "dato" : "datos"}`}</li>
            );
          })}
        </ul>
      )}

      {borrador && (generales.length > 0 || (n.rechazos ?? 0) > 0) && (
        <div className="alert alert-error small">
          {(n.rechazos ?? 0) > 0 && `${n.rechazos} ${n.rechazos === 1 ? "problema impide" : "problemas impiden"} generar.`}
          {generales.length > 0 && <ul className="hallazgos">{generales.map((h, i) => <li key={i}>{h.mensaje}</li>)}</ul>}
        </div>
      )}

      <div className="row">
        {borrador && (
          <>
            <button className="btn btn-secondary" disabled={!sucio || guardar.isPending} onClick={() => guardar.mutate()}>Guardar y revisar</button>
            <button className="btn" disabled={sucio || generar.isPending || (n.rechazos ?? 0) > 0 || !(n.servicios ?? []).length}
              title={sucio ? "Guarde primero" : undefined} onClick={() => generar.mutate()}>Generar RIPS de la nota</button>
            <button className="btn-link" disabled={eliminar.isPending}
              onClick={() => confirm("¿Eliminar este borrador de nota?") && eliminar.mutate()}>Eliminar borrador</button>
          </>
        )}
        {n.estado === "GENERADA" && !n.total && (
          <BotonArchivo ruta={`/api/v1/rips/notas/${n.id}/json`} nombre={`RIPS_${n.numeroFactura}_${n.tipo}${n.numero}.json`} className="btn">Descargar JSON de la nota</BotonArchivo>
        )}
        {n.estado === "GENERADA" && !anulando && <button className="btn-link danger" onClick={() => setAnulando(true)}>Anular nota</button>}
        {error && <span className="field-error">{error.message}</span>}
      </div>
      {anulando && (
        <form className="stack-sm" onSubmit={(e) => { e.preventDefault(); anular.mutate(); }}>
          <label>Motivo de la anulación
            <input required maxLength={300} value={motivoAnulacion} onChange={(e) => setMotivoAnulacion(e.target.value)} />
          </label>
          <div className="row">
            <button className="btn btn-secondary" disabled={anular.isPending}>Anular nota</button>
            <button type="button" className="btn-link" onClick={() => setAnulando(false)}>Cancelar</button>
          </div>
        </form>
      )}
    </div>
  );
}

function FilaServicio({ servicio: s, tipo, marca, hallazgos, onMarcar, onCambio }: {
  servicio: Servicio; tipo: Tipo; marca?: Marca; hallazgos: Schemas["HallazgoNota"][];
  onMarcar: (si: boolean) => void; onCambio: (m: Marca) => void;
}) {
  const [corrigiendo, setCorrigiendo] = useState(tipo === "NA" || Object.keys(marca?.datos ?? {}).length > 0);
  const disponible = (s.valor ?? 0) - (s.acreditado ?? 0);
  return (
    <div className={`card stack-sm ${hallazgos.some((h) => h.nivel === "RECHAZO") ? "con-rechazo" : ""}`}>
      <label className="check">
        <input type="checkbox" checked={!!marca} onChange={(e) => onMarcar(e.target.checked)} />
        <span><strong>{s.codigo}</strong> · {s.paciente || s.documento} · {s.fecha} · facturado {pesos(s.valor)}
          {!!s.acreditado && <span className="muted"> · acreditado {pesos(s.acreditado)}</span>}
          {!!s.debitado && <span className="muted"> · sumado {pesos(s.debitado)}</span>}</span>
      </label>
      {marca && (
        <div className="stack-sm">
          {tipo !== "NA" && (
            <label>{tipo === "NC" ? `Valor glosado (hasta ${pesos(disponible)})` : "Valor que se suma"}
              <input className="campo-medio" inputMode="numeric" value={marca.valor} placeholder="$"
                onChange={(e) => onCambio({ ...marca, valor: e.target.value })} />
            </label>
          )}
          {tipo === "NC" && !corrigiendo && <button type="button" className="btn-link" onClick={() => setCorrigiendo(true)}>Corregir también datos</button>}
          {tipo !== "ND" && corrigiendo && (
            <div className="form-grid">
              {CAMPOS[s.lista!].map(([campo, etiqueta, tabla]) => (
                <CampoDato key={campo} etiqueta={etiqueta} tabla={tabla} actual={texto(s.datos?.[campo])}
                  valor={campo in marca.datos ? marca.datos[campo] : texto(s.datos?.[campo])}
                  onChange={(v) => {
                    const datos = { ...marca.datos };
                    if (v === texto(s.datos?.[campo])) delete datos[campo];
                    else datos[campo] = v;
                    onCambio({ ...marca, datos });
                  }} />
              ))}
            </div>
          )}
        </div>
      )}
      {!!hallazgos.length && (
        <ul className="hallazgos">
          {hallazgos.map((h, i) => (
            <li key={i}><span className={`badge ${h.nivel === "RECHAZO" ? "badge-danger" : "badge-warn"}`}>{h.nivel === "RECHAZO" ? "Rechazo" : "Aviso"}</span> {h.mensaje}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

function CampoDato({ etiqueta, tabla, actual, valor, onChange }: {
  etiqueta: string; tabla?: string; actual: string; valor: string; onChange: (v: string) => void;
}) {
  const lista = useReferencia(tabla ?? "", !!tabla);
  const cambiado = valor !== actual;
  return (
    <label>{etiqueta} {cambiado && <span className="badge badge-info">antes: {actual || "vacío"}</span>}
      {tabla ? (
        <select value={valor} onChange={(e) => onChange(e.target.value)}>
          {!actual && <option value="">—</option>}
          {lista.data?.map((r) => <option key={r.codigo} value={r.codigo}>{r.codigo} · {nombrePropio(r.nombre)}</option>)}
        </select>
      ) : (
        <input value={valor} maxLength={30} onChange={(e) => onChange(e.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, ""))} />
      )}
    </label>
  );
}

/** El XML de la nota crédito o débito (opcional). Botón propio: el campo de archivo sale en el idioma del navegador. */
function XmlNota({ nota: n, onCambio }: { nota: Nota; onCambio: (n: Nota) => void }) {
  const archivo = useRef<HTMLInputElement>(null);
  const subir = useMutation({
    mutationFn: async (f: File) => {
      const datos = new FormData();
      datos.append("archivo", f);
      return subirArchivo<Nota>(`/api/v1/rips/notas/${n.id}/xml`, datos);
    },
    onSuccess: onCambio,
  });
  const quitar = useMutation({
    mutationFn: async () => unwrap(await api.DELETE("/api/v1/rips/notas/{notaId}/xml", { params: { path: { notaId: n.id! } } })),
    onSuccess: onCambio,
  });
  return (
    <div className="row">
      {n.xml ? (
        <span className="small">XML cargado: nota {n.xml.numero ?? "—"}, factura {n.xml.factura ?? "—"}, valor {pesos(n.xml.valor)} ·{" "}
          <button className="btn-link" onClick={() => quitar.mutate()}>Quitar</button></span>
      ) : (
        <>
          <input ref={archivo} type="file" accept=".xml,text/xml,application/xml" hidden
            onChange={(e) => { const f = e.target.files?.[0]; if (f) subir.mutate(f); e.target.value = ""; }} />
          <button type="button" className="btn btn-secondary" disabled={subir.isPending} onClick={() => archivo.current?.click()}>
            {subir.isPending ? "Leyendo el XML…" : "Cargar el XML de la nota"}
          </button>
          <span className="muted small">Opcional: Galenum cruza el número, la factura y el valor.</span>
        </>
      )}
      {(subir.error ?? quitar.error) && <span className="field-error">{(subir.error ?? quitar.error)!.message}</span>}
    </div>
  );
}
