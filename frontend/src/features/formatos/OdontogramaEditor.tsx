import { useState } from "react";
import {
  DENTICIONES, HALLAZGOS_DIENTE, HALLAZGOS_SUPERFICIE, SUPERFICIES, dientesDe, filas, lineasHallazgos, orientacion,
  type Denticion, type Diente, type Superficie, type ValorOdontograma,
} from "./odontograma";

const T = 34; // lado de cada diente en el SVG
const ESP = 6; // espacio entre dientes
const ALTO_FILA = T + 30; // número arriba y siglas abajo
const LINEA = 10; // separación en la línea media

type Herramienta = { tipo: "superficie" | "diente"; clave: string } | { tipo: "borrar" };

/** Odontograma interactivo: se elige un hallazgo de la paleta y se hace clic en la superficie o en el diente. */
export function OdontogramaEditor({ value, onChange }: { value: ValorOdontograma | null; onChange: (v: ValorOdontograma | null) => void }) {
  const [herramienta, setHerramienta] = useState<Herramienta>({ tipo: "superficie", clave: "caries" });
  const [seleccionado, setSeleccionado] = useState<string | null>(null);

  if (!value?.denticion) {
    return (
      <div className="row">
        <span className="muted small">Dentición:</span>
        {(Object.keys(DENTICIONES) as Denticion[]).map((d) => (
          <button key={d} type="button" className="btn btn-secondary" onClick={() => onChange({ denticion: d, dientes: {} })}>{DENTICIONES[d]}</button>
        ))}
      </div>
    );
  }

  const v = value;
  const setDiente = (n: string, d: Diente | null) => {
    const dientes = { ...v.dientes };
    if (!d || (!d.hallazgos?.length && !Object.keys(d.superficies ?? {}).length && !d.nota)) delete dientes[n];
    else dientes[n] = d;
    onChange({ ...v, dientes });
  };

  const clicSuperficie = (n: string, s: Superficie) => {
    const d = v.dientes[n] ?? {};
    if (herramienta.tipo === "borrar") {
      const sup = { ...d.superficies };
      delete sup[s];
      return setDiente(n, { ...d, superficies: sup });
    }
    if (herramienta.tipo === "diente") return clicDiente(n);
    if (d.hallazgos?.includes("ausente")) return;
    const sup = { ...d.superficies };
    if (sup[s] === herramienta.clave) delete sup[s]; else sup[s] = herramienta.clave;
    setDiente(n, { ...d, superficies: sup });
  };

  const clicDiente = (n: string) => {
    const d = v.dientes[n] ?? {};
    if (herramienta.tipo === "borrar") return setDiente(n, null);
    if (herramienta.tipo !== "diente") return;
    const actuales = d.hallazgos ?? [];
    const quita = actuales.includes(herramienta.clave);
    // Un diente ausente no tiene otros hallazgos (lo mismo exige el servidor)
    if (herramienta.clave === "ausente" && !quita) return setDiente(n, { hallazgos: ["ausente"], nota: d.nota });
    const hallazgos = quita ? actuales.filter((h) => h !== herramienta.clave) : [...actuales.filter((h) => h !== "ausente"), herramienta.clave];
    setDiente(n, { ...d, hallazgos });
  };

  const cambiarDenticion = (nueva: Denticion) => {
    const permitidos = dientesDe(nueva);
    const fuera = Object.keys(v.dientes).filter((n) => !permitidos.has(n));
    if (fuera.length && !confirm(`Se quitarán los hallazgos de ${fuera.join(", ")}, que no están en la dentición ${DENTICIONES[nueva].toLowerCase()}. ¿Continuar?`)) return;
    const dientes = Object.fromEntries(Object.entries(v.dientes).filter(([n]) => permitidos.has(n)));
    setSeleccionado(null);
    onChange({ denticion: nueva, dientes });
  };

  const activo = (h: Herramienta) => JSON.stringify(h) === JSON.stringify(herramienta);
  const lineas = lineasHallazgos(v);
  const sel = seleccionado ? v.dientes[seleccionado] ?? {} : null;

  return (
    <div className="stack-sm">
      <div className="row between">
        <label className="row">Dentición
          <select value={v.denticion} onChange={(e) => cambiarDenticion(e.target.value as Denticion)}>
            {(Object.keys(DENTICIONES) as Denticion[]).map((d) => <option key={d} value={d}>{DENTICIONES[d]}</option>)}
          </select>
        </label>
      </div>
      <div className="paleta-odontograma" role="toolbar" aria-label="Hallazgos">
        {Object.entries(HALLAZGOS_SUPERFICIE).map(([k, h]) => (
          <button key={k} type="button" className={activo({ tipo: "superficie", clave: k }) ? "activa" : ""} aria-pressed={activo({ tipo: "superficie", clave: k })}
            onClick={() => setHerramienta({ tipo: "superficie", clave: k })}>
            <span className="muestra" style={{ background: h.color }} />{h.nombre}
          </button>
        ))}
        {Object.entries(HALLAZGOS_DIENTE).map(([k, h]) => (
          <button key={k} type="button" className={activo({ tipo: "diente", clave: k }) ? "activa" : ""} aria-pressed={activo({ tipo: "diente", clave: k })}
            onClick={() => setHerramienta({ tipo: "diente", clave: k })}>
            <span className="sigla">{h.sigla}</span>{h.nombre}
          </button>
        ))}
        <button type="button" className={activo({ tipo: "borrar" }) ? "activa" : ""} aria-pressed={activo({ tipo: "borrar" })} onClick={() => setHerramienta({ tipo: "borrar" })}>Borrar</button>
      </div>

      <div className="lienzo-odontograma">
        <DibujoOdontograma valor={v} seleccionado={seleccionado} onSuperficie={clicSuperficie}
          onDiente={(n) => { clicDiente(n); }} onNumero={(n) => setSeleccionado(seleccionado === n ? null : n)} />
      </div>
      <span className="muted small">Clic en el número del diente para agregarle una nota.</span>

      {seleccionado && sel && (
        <label>Nota del diente {seleccionado}
          <input value={sel.nota ?? ""} maxLength={300} autoFocus onChange={(e) => setDiente(seleccionado, { ...sel, nota: e.target.value })} />
        </label>
      )}
      {lineas.length ? <ul className="hallazgos-odontograma small">{lineas.map((l) => <li key={l}>{l}</li>)}</ul>
        : <p className="muted small">Sin hallazgos.</p>}
    </div>
  );
}

/** El dibujo (también se usa de solo lectura). Cada diente: cinco superficies y, encima, los hallazgos del diente. */
export function DibujoOdontograma({ valor, seleccionado, onSuperficie, onDiente, onNumero }: {
  valor: ValorOdontograma;
  seleccionado?: string | null;
  onSuperficie?: (n: string, s: Superficie) => void;
  onDiente?: (n: string) => void;
  onNumero?: (n: string) => void;
}) {
  const fs = filas(valor.denticion);
  const ancho = Math.max(...fs.map((f) => f.length)) * (T + ESP) + LINEA;
  const editable = !!onSuperficie;
  return (
    <svg viewBox={`0 0 ${ancho} ${fs.length * ALTO_FILA}`} width={ancho} className="odontograma-svg" role="img"
      aria-label={`Odontograma, dentición ${DENTICIONES[valor.denticion].toLowerCase()}`}>
      <line x1={ancho / 2} y1={0} x2={ancho / 2} y2={fs.length * ALTO_FILA} className="linea-media" />
      {fs.map((fila, i) => {
        // Las filas temporales son más cortas: centradas
        const margen = (ancho - LINEA - fila.length * (T + ESP)) / 2;
        return fila.map((n, j) => {
          const x = margen + j * (T + ESP) + ESP / 2 + (j >= fila.length / 2 ? LINEA : 0);
          const y = i * ALTO_FILA + 14;
          return (
            <DienteSvg key={n} numero={n} x={x} y={y} diente={valor.dientes[String(n)]} editable={editable}
              seleccionado={seleccionado === String(n)} onSuperficie={onSuperficie} onDiente={onDiente} onNumero={onNumero} />
          );
        });
      })}
    </svg>
  );
}

function DienteSvg({ numero, x, y, diente, editable, seleccionado, onSuperficie, onDiente, onNumero }: {
  numero: number; x: number; y: number; diente?: Diente; editable: boolean; seleccionado: boolean;
  onSuperficie?: (n: string, s: Superficie) => void; onDiente?: (n: string) => void; onNumero?: (n: string) => void;
}) {
  const n = String(numero);
  const o = orientacion(numero);
  const q = T / 4;
  const zonas: [Superficie, string][] = [
    [o.arriba, `0,0 ${T},0 ${T - q},${q} ${q},${q}`],
    [o.abajo, `${q},${T - q} ${T - q},${T - q} ${T},${T} 0,${T}`],
    [o.izquierda, `0,0 ${q},${q} ${q},${T - q} 0,${T}`],
    [o.derecha, `${T},0 ${T},${T} ${T - q},${T - q} ${T - q},${q}`],
    ["O", `${q},${q} ${T - q},${q} ${T - q},${T - q} ${q},${T - q}`],
  ];
  const h = diente?.hallazgos ?? [];
  const ausente = h.includes("ausente");
  const siglas = h.map((x) => HALLAZGOS_DIENTE[x]?.sigla).filter(Boolean).join(" ");
  return (
    <g transform={`translate(${x},${y})`} className={`diente ${ausente ? "ausente" : ""}`}>
      <text x={T / 2} y={-4} textAnchor="middle" className={`numero ${seleccionado ? "seleccionado" : ""}`}
        onClick={onNumero ? () => onNumero(n) : undefined} style={onNumero ? { cursor: "pointer" } : undefined}>{numero}{diente?.nota ? "*" : ""}</text>
      {zonas.map(([s, puntos]) => {
        const hallazgo = diente?.superficies?.[s];
        return (
          <polygon key={s} points={puntos} fill={hallazgo ? HALLAZGOS_SUPERFICIE[hallazgo]?.color : "var(--surface)"} className="superficie"
            onClick={editable ? () => onSuperficie?.(n, s) : undefined}>
            <title>{`${numero} ${SUPERFICIES[s]}${hallazgo ? `: ${HALLAZGOS_SUPERFICIE[hallazgo]?.nombre.toLowerCase()}` : ""}`}</title>
          </polygon>
        );
      })}
      {/* Hallazgos del diente encima de las superficies; reciben el clic como el diente entero */}
      <g className="marcas" onClick={editable ? () => onDiente?.(n) : undefined} style={{ pointerEvents: h.length ? "auto" : "none" }}>
        {(ausente || h.includes("extraccion_indicada")) && (
          <path d={`M2,2 L${T - 2},${T - 2} M${T - 2},2 L2,${T - 2}`} className={ausente ? "marca-ausente" : "marca-extraccion"} />
        )}
        {h.includes("corona") && <circle cx={T / 2} cy={T / 2} r={T / 2 + 1} className="marca-tratado" />}
        {h.includes("endodoncia") && <line x1={T / 2} y1={-1} x2={T / 2} y2={T + 1} className="marca-tratado" />}
      </g>
      <text x={T / 2} y={T + 12} textAnchor="middle" className="siglas">{siglas}</text>
    </g>
  );
}
