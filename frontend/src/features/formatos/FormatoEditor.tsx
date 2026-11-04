import { OdontogramaEditor } from "./OdontogramaEditor";
import type { ValorOdontograma } from "./odontograma";
import { errorCampo, total, visible, type Campo, type Definicion, type Fila, type Respuestas } from "./motor";

/** Dibuja cualquier formato a partir de su definición. Un solo componente para todas las especialidades. */
export function FormatoEditor({ definicion, value, onChange }: {
  definicion: Definicion;
  value: Respuestas;
  onChange: (r: Respuestas) => void;
}) {
  return (
    <div className="stack">
      {definicion.secciones.map((s) => {
        const valores = value[s.clave] ?? {};
        const set = (clave: string, v: unknown) => onChange({ ...value, [s.clave]: { ...valores, [clave]: v } });
        return (
          <fieldset key={s.clave} className="seccion-formato">
            <legend>{s.titulo}</legend>
            <div className="form-grid">
              {s.campos.filter((c) => visible(c, valores)).map((c) => (
                <CampoEditor key={c.clave} campo={c} valor={valores[c.clave]} onChange={(v) => set(c.clave, v)} />
              ))}
            </div>
          </fieldset>
        );
      })}
    </div>
  );
}

const ANCHOS = new Set(["texto_largo", "multiple", "tabla", "escala", "odontograma"]);

function CampoEditor({ campo: c, valor, onChange }: { campo: Campo; valor: unknown; onChange: (v: unknown) => void }) {
  // Un solo bloque de texto: el label es flex en columna y la unidad no debe quedar en otra línea
  const etiqueta = <span>{c.etiqueta}{c.obligatorio && " *"}{c.unidad && <span className="muted small"> ({c.unidad})</span>}</span>;
  const error = errorCampo(c, valor);
  const ayuda = c.ayuda && <span className="muted small">{c.ayuda}</span>;
  const clase = ANCHOS.has(c.tipo) ? "wide" : undefined;

  switch (c.tipo) {
    case "texto":
      return <label className={clase}>{etiqueta}<input value={(valor as string) ?? ""} maxLength={500} onChange={(e) => onChange(e.target.value)} />{ayuda}</label>;
    case "texto_largo":
      return <label className={clase}>{etiqueta}<textarea rows={2} value={(valor as string) ?? ""} onChange={(e) => onChange(e.target.value)} />{ayuda}</label>;
    case "numero":
      return (
        <label className={clase}>{etiqueta}
          <input type="number" step="any" min={c.min} max={c.max} value={valor === undefined || valor === null ? "" : String(valor)}
            onChange={(e) => onChange(e.target.value === "" ? null : Number(e.target.value))} />
          {error ? <span className="field-error">{error}</span> : ayuda}
        </label>
      );
    case "fecha":
      return <label className={clase}>{etiqueta}<input type="date" value={(valor as string) ?? ""} onChange={(e) => onChange(e.target.value || null)} />{ayuda}</label>;
    case "seleccion":
      return (
        <label className={clase}>{etiqueta}
          <select value={(valor as string) ?? ""} onChange={(e) => onChange(e.target.value || null)}>
            <option value="">—</option>
            {c.opciones?.map((o) => <option key={o}>{o}</option>)}
          </select>{ayuda}
        </label>
      );
    case "si_no":
      return (
        <label className={clase}>{etiqueta}
          <select value={valor === true ? "si" : valor === false ? "no" : ""} onChange={(e) => onChange(e.target.value === "" ? null : e.target.value === "si")}>
            <option value="">—</option><option value="si">Sí</option><option value="no">No</option>
          </select>{ayuda}
        </label>
      );
    case "multiple": {
      const elegidas = (valor as string[]) ?? [];
      return (
        <div className={`stack-sm ${clase}`}>
          <span className="etiqueta-campo">{etiqueta}</span>
          <div className="row opciones-multiples">
            {c.opciones?.map((o) => (
              <label key={o} className="check">
                <input type="checkbox" checked={elegidas.includes(o)}
                  onChange={(e) => onChange(e.target.checked ? [...elegidas, o] : elegidas.filter((x) => x !== o))} />{o}
              </label>
            ))}
          </div>{ayuda}
        </div>
      );
    }
    case "escala": {
      const puntajes = (valor as Record<string, number>) ?? {};
      const t = total(c, puntajes);
      return (
        <div className={`stack-sm ${clase}`}>
          <span className="etiqueta-campo">{etiqueta}</span>
          <div className="form-grid">
            {c.items?.map((i) => (
              <label key={i.clave}>{(c.items?.length ?? 0) > 1 ? i.etiqueta : <span className="muted small">{i.etiqueta}</span>}
                <select value={puntajes[i.clave] ?? ""} onChange={(e) => {
                  const resto = { ...puntajes };
                  if (e.target.value === "") delete resto[i.clave]; else resto[i.clave] = Number(e.target.value);
                  onChange(resto);
                }}>
                  <option value="">—</option>
                  {i.opciones.map((o) => <option key={o.puntaje} value={o.puntaje}>{o.etiqueta}</option>)}
                </select>
              </label>
            ))}
          </div>
          {t && <span><strong>Total: {t.puntaje} / {t.maximo}</strong>{t.interpretacion && ` · ${t.interpretacion}`}</span>}
          {ayuda}
        </div>
      );
    }
    case "odontograma":
      return (
        <div className={`stack-sm ${clase}`}>
          <span className="etiqueta-campo">{etiqueta}</span>{ayuda}
          <OdontogramaEditor value={(valor as ValorOdontograma) ?? null} onChange={onChange} />
        </div>
      );
    case "tabla":
      return <TablaEditor campo={c} etiqueta={etiqueta} filas={(valor as Fila[]) ?? []} onChange={onChange} />;
  }
}

function TablaEditor({ campo: c, etiqueta, filas, onChange }: { campo: Campo; etiqueta: React.ReactNode; filas: Fila[]; onChange: (v: Fila[]) => void }) {
  const columnas = c.columnas ?? [];
  // Filas fijas: una por cada fila de la definición; libres: las que vaya agregando
  const visibles: Fila[] = c.filas ? c.filas.map((f) => filas.find((x) => x.fila === f.clave) ?? { fila: f.clave }) : filas;
  const setCelda = (idx: number, col: string, v: unknown) => {
    const nuevas = visibles.map((f, i) => (i === idx ? { ...f, [col]: v } : f));
    onChange(nuevas);
  };
  return (
    <div className="wide stack-sm">
      <span className="etiqueta-campo">{etiqueta}</span>
      <div className="table-wrap">
        <table className="tabla-formato">
          <thead>
            <tr>{c.filas && <th />}{columnas.map((col) => <th key={col.clave}>{col.etiqueta}</th>)}{!c.filas && <th />}</tr>
          </thead>
          <tbody>
            {visibles.map((f, idx) => (
              <tr key={f.fila ?? idx}>
                {c.filas && <th scope="row">{c.filas.find((x) => x.clave === f.fila)?.etiqueta}</th>}
                {columnas.map((col) => (
                  <td key={col.clave}>
                    {col.tipo === "seleccion" ? (
                      <select aria-label={col.etiqueta} value={(f[col.clave] as string) ?? ""} onChange={(e) => setCelda(idx, col.clave, e.target.value || null)}>
                        <option value="">—</option>{col.opciones?.map((o) => <option key={o}>{o}</option>)}
                      </select>
                    ) : col.tipo === "si_no" ? (
                      <select aria-label={col.etiqueta} value={f[col.clave] === true ? "si" : f[col.clave] === false ? "no" : ""}
                        onChange={(e) => setCelda(idx, col.clave, e.target.value === "" ? null : e.target.value === "si")}>
                        <option value="">—</option><option value="si">Sí</option><option value="no">No</option>
                      </select>
                    ) : col.tipo === "numero" ? (
                      <input aria-label={col.etiqueta} type="number" step="any" value={f[col.clave] === undefined || f[col.clave] === null ? "" : String(f[col.clave])}
                        onChange={(e) => setCelda(idx, col.clave, e.target.value === "" ? null : Number(e.target.value))} />
                    ) : (
                      <input aria-label={col.etiqueta} value={(f[col.clave] as string) ?? ""} maxLength={500} onChange={(e) => setCelda(idx, col.clave, e.target.value)} />
                    )}
                  </td>
                ))}
                {!c.filas && <td><button type="button" className="btn-link danger" onClick={() => onChange(filas.filter((_, i) => i !== idx))}>Quitar</button></td>}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!c.filas && filas.length < 50 && <div><button type="button" className="btn-link" onClick={() => onChange([...filas, {}])}>+ Agregar fila</button></div>}
    </div>
  );
}
