import { useState } from "react";
import type { Schemas } from "../../api/client";
import { BuscadorCups } from "../rips/BuscadorCups";

export type OrdenForm = Schemas["OrdenRequest"];
export type FactorForm = Schemas["FactorRequest"];
type TipoOrden = NonNullable<OrdenForm["tipo"]>;
type CodigoFactor = NonNullable<FactorForm["codigo"]>;
type Clinicos = Schemas["Clinicos"];

export const TIPO_ORDEN: Record<TipoOrden, string> = {
  LABORATORIO: "Laboratorio", IMAGEN: "Imagen diagnóstica", PROCEDIMIENTO: "Procedimiento", INTERCONSULTA: "Interconsulta",
};

/** En el orden en que se muestran (el mismo del servidor). */
export const FACTORES: [CodigoFactor, string][] = [
  ["TABAQUISMO", "Tabaquismo"], ["ALCOHOL", "Consumo de alcohol"], ["SUSTANCIAS_PSICOACTIVAS", "Consumo de sustancias psicoactivas"],
  ["SEDENTARISMO", "Sedentarismo"], ["OBESIDAD", "Sobrepeso u obesidad"], ["HIPERTENSION", "Hipertensión arterial"],
  ["DIABETES", "Diabetes"], ["DISLIPIDEMIA", "Dislipidemia"], ["ANTECEDENTE_FAMILIAR_CARDIOVASCULAR", "Antecedente familiar cardiovascular"],
  ["EMBARAZO_ALTO_RIESGO", "Embarazo de alto riesgo"], ["RIESGO_CAIDAS", "Riesgo de caídas"], ["OTRO", "Otro"],
];
const NOMBRE_FACTOR = Object.fromEntries(FACTORES) as Record<CodigoFactor, string>;

export const ordenesDe = (a: Schemas["AtencionVista"]): OrdenForm[] => (a.ordenes ?? []).map((o) => ({
  tipo: o.tipo!, codigoCups: o.codigoCups ?? undefined, descripcion: o.descripcion, cantidad: o.cantidad ?? 1,
  indicaciones: o.indicaciones ?? undefined, prioridad: o.prioridad ?? "RUTINARIA",
}));
export const factoresDe = (a: Schemas["AtencionVista"]): FactorForm[] => (a.factoresRiesgo ?? []).map((f) => ({
  codigo: f.codigo!, observacion: f.observacion ?? undefined,
}));

/** Órdenes médicas (bloque 9 del RDA): con CUPS del catálogo o, para una interconsulta, solo la descripción. */
export function OrdenesEditor({ value, onChange, atencionId }: { value: OrdenForm[]; onChange: (o: OrdenForm[]) => void; atencionId: string }) {
  const [tipo, setTipo] = useState<TipoOrden>("LABORATORIO");
  const [libre, setLibre] = useState("");
  const actualizar = (i: number, cambio: Partial<OrdenForm>) => onChange(value.map((o, j) => (j === i ? { ...o, ...cambio } : o)));
  return (
    <div className="card stack">
      <h3>Órdenes médicas</h3>
      {value.map((o, i) => (
        <div key={i} className="form-grid compacto orden">
          <strong className="wide">{TIPO_ORDEN[o.tipo]}: {o.codigoCups && `${o.codigoCups} · `}{o.descripcion}</strong>
          <label>Cantidad<input type="number" min={1} max={99} value={o.cantidad ?? 1}
            onChange={(e) => actualizar(i, { cantidad: Math.max(1, Number(e.target.value) || 1) })} /></label>
          <label>Prioridad
            <select value={o.prioridad ?? "RUTINARIA"} onChange={(e) => actualizar(i, { prioridad: e.target.value as OrdenForm["prioridad"] })}>
              <option value="RUTINARIA">Rutinaria</option><option value="URGENTE">Urgente</option>
            </select>
          </label>
          <label className="wide">Indicaciones<input value={o.indicaciones ?? ""} maxLength={500} placeholder="En ayunas, control en 1 mes…"
            onChange={(e) => actualizar(i, { indicaciones: e.target.value || undefined })} /></label>
          <div className="wide"><button type="button" className="btn-link danger" onClick={() => onChange(value.filter((_, j) => j !== i))}>Quitar</button></div>
        </div>
      ))}
      <label>Tipo de orden
        <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoOrden)}>
          {(Object.keys(TIPO_ORDEN) as TipoOrden[]).map((t) => <option key={t} value={t}>{TIPO_ORDEN[t]}</option>)}
        </select>
      </label>
      <BuscadorCups etiqueta="Agregar por CUPS (código o nombre)" atencionId={atencionId}
        onElegir={(c) => onChange([...value, { tipo, codigoCups: c.codigo!, descripcion: c.descripcion, cantidad: 1, prioridad: "RUTINARIA" }])} />
      <div className="row">
        <input className="grow" value={libre} onChange={(e) => setLibre(e.target.value)} maxLength={400}
          placeholder="O escriba lo que ordena (por ejemplo, interconsulta con cardiología)" />
        <button type="button" className="btn btn-secondary" disabled={!libre.trim()}
          onClick={() => { onChange([...value, { tipo, descripcion: libre.trim(), cantidad: 1, prioridad: "RUTINARIA" }]); setLibre(""); }}>Agregar</button>
      </div>
      <p className="small muted">Se firman con la atención. Después de firmar se imprimen para el paciente.</p>
    </div>
  );
}

/**
 * Factores de riesgo sugeridos por lo registrado: antecedentes (tóxicos, patológicos, familiares), IMC y tensión
 * arterial. El médico confirma cada uno.
 */
export function sugerirFactores(clinicos: Clinicos | undefined, signos: { pesoKg?: string; tallaCm?: string; taSistolica?: string; taDiastolica?: string }): CodigoFactor[] {
  const s = new Set<CodigoFactor>();
  for (const a of clinicos?.antecedentes ?? []) {
    if (a.vigente === false) continue;
    const d = (a.descripcion ?? "").toLowerCase();
    if (a.tipo === "TOXICO") {
      if (/tabac|cigarr|fuma/.test(d)) s.add("TABAQUISMO");
      if (/alcohol|licor|cerveza/.test(d)) s.add("ALCOHOL");
      if (/marihuana|coca[ií]na|droga|psicoactiv|spa\b/.test(d)) s.add("SUSTANCIAS_PSICOACTIVAS");
    }
    if (a.tipo === "PATOLOGICO") {
      if (/hipertens|\bhta\b/.test(d)) s.add("HIPERTENSION");
      if (/diabet/.test(d)) s.add("DIABETES");
      if (/dislipid|colesterol|triglic/.test(d)) s.add("DISLIPIDEMIA");
    }
    if (a.tipo === "FAMILIAR" && /infarto|cardi|coronari|acv|hipertens/.test(d)) s.add("ANTECEDENTE_FAMILIAR_CARDIOVASCULAR");
  }
  const peso = Number((signos.pesoKg ?? "").replace(",", "."));
  const talla = Number((signos.tallaCm ?? "").replace(",", ".")) / 100;
  if (peso > 0 && talla > 0 && peso / (talla * talla) >= 25) s.add("OBESIDAD");
  if (Number(signos.taSistolica) >= 140 || Number(signos.taDiastolica) >= 90) s.add("HIPERTENSION");
  return [...s];
}

/** Factores de riesgo identificados en la atención (bloque 10 del RDA). */
export function FactoresEditor({ value, onChange, sugeridos }: { value: FactorForm[]; onChange: (f: FactorForm[]) => void; sugeridos: CodigoFactor[] }) {
  const tiene = (c: CodigoFactor) => value.some((f) => f.codigo === c);
  const pendientes = sugeridos.filter((c) => !tiene(c));
  const [nuevo, setNuevo] = useState<CodigoFactor | "">("");
  return (
    <div className="card stack">
      <h3>Factores de riesgo</h3>
      {!!pendientes.length && (
        <div className="row">
          <span className="small muted">Sugeridos por lo registrado:</span>
          {pendientes.map((c) => (
            <button key={c} type="button" className="badge badge-warn badge-boton" onClick={() => onChange([...value, { codigo: c }])}>+ {NOMBRE_FACTOR[c]}</button>
          ))}
        </div>
      )}
      {value.map((f, i) => (
        <div key={i} className="row">
          <strong>{NOMBRE_FACTOR[f.codigo]}</strong>
          <input className="grow" value={f.observacion ?? ""} maxLength={300} placeholder="Observación (opcional)"
            onChange={(e) => onChange(value.map((x, j) => (j === i ? { ...x, observacion: e.target.value || undefined } : x)))} />
          <button type="button" className="btn-link danger" onClick={() => onChange(value.filter((_, j) => j !== i))}>Quitar</button>
        </div>
      ))}
      <div className="row">
        <select value={nuevo} onChange={(e) => setNuevo(e.target.value as CodigoFactor | "")}>
          <option value="">Agregar factor…</option>
          {FACTORES.filter(([c]) => c === "OTRO" || !tiene(c)).map(([c, n]) => <option key={c} value={c}>{n}</option>)}
        </select>
        <button type="button" className="btn btn-secondary" disabled={!nuevo} onClick={() => { if (nuevo) onChange([...value, { codigo: nuevo }]); setNuevo(""); }}>Agregar</button>
      </div>
    </div>
  );
}

/** Lectura de órdenes y factores en la atención firmada. */
export function OrdenesFactoresLectura({ a, pdf }: { a: Schemas["AtencionVista"]; pdf?: React.ReactNode }) {
  return (
    <>
      {!!a.ordenes?.length && (
        <div>
          <h3>Órdenes médicas {pdf}</h3>
          <ul>
            {a.ordenes.map((o, i) => (
              <li key={i}>{TIPO_ORDEN[o.tipo!]}: {o.codigoCups && <strong>{o.codigoCups} </strong>}{o.descripcion}
                {o.cantidad !== 1 && ` × ${o.cantidad}`}{o.prioridad === "URGENTE" && <span className="badge badge-danger">Urgente</span>}
                {o.indicaciones && <span className="muted"> · {o.indicaciones}</span>}</li>
            ))}
          </ul>
        </div>
      )}
      {!!a.factoresRiesgo?.length && (
        <p><strong>Factores de riesgo:</strong> {a.factoresRiesgo.map((f) => f.nombre + (f.observacion ? ` (${f.observacion})` : "")).join(", ")}</p>
      )}
    </>
  );
}
