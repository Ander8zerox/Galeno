import { useQuery } from "@tanstack/react-query";
import { api, unwrap } from "../../api/client";
import { BotonArchivo } from "../historia/Documentos";

const fecha = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium", timeStyle: "short" });

export function HistorialFormulas({ pacienteId }: { pacienteId: string }) {
  const formulas = useQuery({
    queryKey: ["formulas", pacienteId],
    queryFn: async () => unwrap(await api.GET("/api/v1/formulacion/formulas", { params: { query: { pacienteId } } })),
  });

  if (formulas.isLoading) return <p className="muted">Cargando…</p>;
  if (formulas.error) return <p className="alert alert-error">{formulas.error.message}</p>;
  if (!formulas.data?.length) return <p className="muted">Este paciente no tiene fórmulas firmadas.</p>;

  return (
    <div className="stack">
      {formulas.data.map((f) => (
        <div key={f.id} className="card">
          <div className="row between">
            <strong>Fórmula del {f.firmadaEn ? fecha.format(new Date(f.firmadaEn)) : ""}</strong>
            <span className="row">
              {f.aiJobId && <span className="badge">Redactada con apoyo de IA</span>}
              <BotonArchivo ruta={`/api/v1/formulacion/formulas/${f.id}/pdf`} nombre={`formula-${f.id!.slice(0, 8)}.pdf`} className="btn btn-secondary">PDF para imprimir</BotonArchivo>
            </span>
          </div>
          <ol>
            {f.items?.map((i, idx) => (
              <li key={idx}>
                <strong>{i.medicamento} {i.concentracion}</strong> {i.formaFarmaceutica} {i.via && `vía ${i.via}`} — {i.dosis} {i.frecuencia} por {i.duracion}
                {i.cantidad && <> · Cantidad: {i.cantidad}</>}
                {i.indicaciones && <div className="muted small">{i.indicaciones}</div>}
              </li>
            ))}
          </ol>
          {f.observaciones && <p className="muted small">{f.observaciones}</p>}
        </div>
      ))}
    </div>
  );
}
