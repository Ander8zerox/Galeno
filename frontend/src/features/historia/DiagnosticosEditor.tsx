import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";

export type DiagnosticoForm = Schemas["DiagnosticoRequest"];
type Tipo = NonNullable<DiagnosticoForm["tipo"]>;

export const TIPO_DX: Record<Tipo, string> = {
  IMPRESION_DIAGNOSTICA: "Impresión diagnóstica",
  CONFIRMADO_NUEVO: "Confirmado nuevo",
  CONFIRMADO_REPETIDO: "Confirmado repetido",
};

/** Lista de diagnósticos con búsqueda CIE-10. El primero que se agrega queda como principal. */
export function DiagnosticosEditor({ value, onChange }: { value: DiagnosticoForm[]; onChange: (dx: DiagnosticoForm[]) => void }) {
  const [q, setQ] = useState("");
  const busqueda = useQuery({
    queryKey: ["cie10", q],
    enabled: q.trim().length >= 2,
    queryFn: async () => unwrap(await api.GET("/api/v1/historia/cie10", { params: { query: { q } } })),
  });

  function agregar(codigo: string, descripcion: string) {
    if (value.some((d) => d.codigoCie10 === codigo)) return;
    onChange([...value, { codigoCie10: codigo, descripcion, principal: value.length === 0, tipo: "IMPRESION_DIAGNOSTICA" }]);
    setQ("");
  }

  const actualizar = (i: number, cambio: Partial<DiagnosticoForm>) => onChange(value.map((d, j) => (j === i ? { ...d, ...cambio } : d)));
  const hacerPrincipal = (i: number) => onChange(value.map((d, j) => ({ ...d, principal: j === i })));

  return (
    <div className="stack">
      {value.map((d, i) => (
        <div key={d.codigoCie10} className="dx-fila">
          <label className="check" title="Diagnóstico principal">
            <input type="radio" name="dx-principal" checked={!!d.principal} onChange={() => hacerPrincipal(i)} /> Principal
          </label>
          <strong>{d.codigoCie10}</strong>
          <span className="grow">{d.descripcion}</span>
          <select value={d.tipo ?? "IMPRESION_DIAGNOSTICA"} onChange={(e) => actualizar(i, { tipo: e.target.value as Tipo })} aria-label="Tipo de diagnóstico">
            {Object.entries(TIPO_DX).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
          </select>
          <button type="button" className="btn-link danger" onClick={() => {
            const resto = value.filter((_, j) => j !== i);
            if (d.principal && resto.length) resto[0] = { ...resto[0], principal: true };
            onChange(resto);
          }}>Quitar</button>
        </div>
      ))}
      <label>Agregar diagnóstico (código o nombre CIE-10)
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Ej.: J02 o faringitis" />
      </label>
      {!!busqueda.data?.length && (
        <div className="resultados">
          {busqueda.data.map((c) => (
            <button type="button" key={c.codigo} className="opcion" onClick={() => agregar(c.codigo!, c.descripcion!)}>
              <span><strong>{c.codigo}</strong> {c.descripcion}</span>
            </button>
          ))}
        </div>
      )}
      {busqueda.data?.length === 0 && <small className="muted">Sin resultados en el catálogo cargado.</small>}
    </div>
  );
}
