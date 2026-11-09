import { DibujoOdontograma } from "./OdontogramaEditor";
import { lineasHallazgos, type ValorOdontograma } from "./odontograma";
import { textoValor, total, vacio, visible, type Campo, type Definicion, type Fila, type Respuestas } from "./motor";

/** Un formato diligenciado, de solo lectura: solo las secciones y campos con datos (como en el PDF). */
export function FormatoLectura({ nombre, definicion, respuestas }: { nombre?: string; definicion: Definicion; respuestas: Respuestas }) {
  const secciones = definicion.secciones
    .map((s) => ({ s, campos: s.campos.filter((c) => !vacio(respuestas[s.clave]?.[c.clave]) && visible(c, respuestas[s.clave])) }))
    .filter((x) => x.campos.length);
  if (!secciones.length) return null;
  return (
    <div className="stack-sm">
      <h3>{nombre}</h3>
      {secciones.map(({ s, campos }) => (
        <div key={s.clave} className="seccion-lectura">
          {s.titulo.toLowerCase() !== nombre?.toLowerCase() && <h4>{s.titulo}</h4>}
          <dl className="campos-lectura">
            {campos.map((c) => (
              <div key={c.clave} className={c.tipo === "tabla" || c.tipo === "odontograma" || c.tipo === "firma" ? "wide" : undefined}>
                <dt>{c.etiqueta}</dt>
                <dd><Valor campo={c} v={respuestas[s.clave][c.clave]} /></dd>
              </div>
            ))}
          </dl>
        </div>
      ))}
    </div>
  );
}

function Valor({ campo: c, v }: { campo: Campo; v: unknown }) {
  if (c.tipo === "escala") {
    const t = total(c, v);
    return t ? <><strong>{t.puntaje} / {t.maximo}</strong>{t.interpretacion && ` · ${t.interpretacion}`}</> : <span className="muted">Incompleta</span>;
  }
  if (c.tipo === "firma") {
    const f = v as { imagen?: string; nombre?: string; firmadaEn?: string };
    return (
      <div className="stack-sm">
        {f.imagen && <img className="firma-guardada" src={f.imagen} alt={`Firma${f.nombre ? ` de ${f.nombre}` : ""}`} />}
        <span className="muted small">
          {f.nombre}{f.nombre && f.firmadaEn ? " · " : ""}
          {f.firmadaEn && `Firmada el ${new Date(f.firmadaEn).toLocaleString("es-CO", { dateStyle: "medium", timeStyle: "short" })}`}
        </span>
      </div>
    );
  }
  if (c.tipo === "odontograma") {
    const o = v as ValorOdontograma;
    const lineas = lineasHallazgos(o);
    return (
      <div className="stack-sm">
        <div className="lienzo-odontograma"><DibujoOdontograma valor={o} /></div>
        {lineas.length ? <ul className="hallazgos-odontograma small">{lineas.map((l) => <li key={l}>{l}</li>)}</ul> : <span className="muted small">Sin hallazgos.</span>}
      </div>
    );
  }
  if (c.tipo === "tabla") {
    const columnas = c.columnas ?? [];
    return (
      <table className="tabla-formato">
        <thead><tr>{c.filas && <th />}{columnas.map((col) => <th key={col.clave}>{col.etiqueta}</th>)}</tr></thead>
        <tbody>
          {(v as Fila[]).map((f, i) => (
            <tr key={f.fila ?? i}>
              {c.filas && <th scope="row">{c.filas.find((x) => x.clave === f.fila)?.etiqueta}</th>}
              {columnas.map((col) => <td key={col.clave}>{vacio(f[col.clave]) ? "" : textoValor(col.tipo, f[col.clave])}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    );
  }
  return <span className="texto-clinico">{textoValor(c.tipo, v, c.unidad)}</span>;
}
