import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, unwrap } from "../../api/client";
import { pesos } from "../rips/modulos";

const fecha = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium" });

/**
 * Planes de tratamiento aceptados con procedimientos pendientes y sin actividad en 60 días: para que la secretaría
 * llame al paciente. No aparece si el consultorio no usa el plan (404) o si no hay ninguno.
 */
export function SeguimientoPlanes() {
  const [abierto, setAbierto] = useState(false);
  const planes = useQuery({
    queryKey: ["seguimiento-planes"],
    retry: false,
    queryFn: async () => unwrap(await api.GET("/api/v1/tratamientos/seguimiento", { params: { query: { dias: 60 } } })),
  });
  if (!planes.data?.length) return null;
  return (
    <div className="card stack-sm">
      <div className="row between">
        <span><strong>Tratamientos sin continuar</strong> <span className="badge badge-warn">{planes.data.length}</span>
          <span className="muted small"> · planes aceptados sin actividad en 60 días</span></span>
        <button type="button" className="btn-link" onClick={() => setAbierto((v) => !v)}>{abierto ? "Ocultar" : "Ver"}</button>
      </div>
      {abierto && planes.data.map((p) => (
        <div key={p.planId} className="row between">
          <span>
            <Link to={`/pacientes/${p.pacienteId}`}>{p.paciente}</Link>
            {p.telefono && <span className="muted small"> · {p.telefono}</span>}
            <span className="muted small"> · {p.odontologo}</span>
          </span>
          <span className="small">
            {p.pendientes} pendiente{p.pendientes === 1 ? "" : "s"} · {pesos(p.valorPendiente)} · última actividad {fecha.format(new Date(p.ultimaActividad!))}
          </span>
        </div>
      ))}
    </div>
  );
}
