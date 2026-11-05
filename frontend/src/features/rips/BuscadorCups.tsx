import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";
import { pesos } from "./modulos";

export type Cups = Schemas["CupsVista"];

/**
 * Busca en el catálogo CUPS por código o por nombre; muestra el precio del consultorio si lo tiene (desde una atención,
 * la tarifa de su pagador).
 */
export function BuscadorCups({ etiqueta, onElegir, atencionId }: { etiqueta: string; onElegir: (c: Cups) => void; atencionId?: string }) {
  const [q, setQ] = useState("");
  const busqueda = useQuery({
    queryKey: ["cups", q, atencionId],
    enabled: q.trim().length >= 2,
    queryFn: async () => unwrap(await api.GET("/api/v1/historia/cups", { params: { query: { q, atencionId } } })),
  });
  return (
    <div className="stack-sm">
      <label>{etiqueta}
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Ej.: 8902, consulta, resina, detartraje" />
      </label>
      {!!busqueda.data?.length && (
        <div className="resultados">
          {busqueda.data.map((c) => (
            <button type="button" key={c.codigo} className="opcion" onClick={() => { onElegir(c); setQ(""); }}>
              <span><strong>{c.codigo}</strong> {c.descripcion}</span>
              <span className="muted small">{c.valorUnitario !== undefined && c.valorUnitario !== null ? pesos(c.valorUnitario) : "Sin precio en la lista"}</span>
            </button>
          ))}
        </div>
      )}
      {busqueda.data?.length === 0 && <small className="muted">Sin resultados en el catálogo CUPS.</small>}
    </div>
  );
}
