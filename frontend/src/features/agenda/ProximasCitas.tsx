import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, unwrap } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { ESTADO_NOMBRE } from "./AgendaDia";
import { fechaHoraCorta } from "./tiempo";

/** Citas de hoy en adelante del paciente, con los médicos cuya agenda puede ver quien consulta. */
export function ProximasCitas({ pacienteId }: { pacienteId: string }) {
  const { me } = useAuth();
  const zona = me?.zonaHoraria ?? "America/Bogota";
  const citas = useQuery({
    queryKey: ["citas", "paciente", pacienteId],
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/pacientes/{pacienteId}/proximas", { params: { path: { pacienteId } } })),
  });
  const activas = (citas.data ?? []).filter((c) => c.estado !== "CANCELADA");

  return (
    <div className="card proximas">
      <div className="row between">
        <strong>Próximas citas</strong>
        <Link to="/agenda" className="small">Ir a la agenda</Link>
      </div>
      {citas.isLoading && <p className="muted small">Cargando…</p>}
      {!citas.isLoading && activas.length === 0 && <p className="muted small">Sin citas programadas.</p>}
      <ul>
        {activas.map((c) => (
          <li key={c.id}>
            {fechaHoraCorta(c.inicio!, zona)} · {c.medicoNombre} · {ESTADO_NOMBRE[c.estado!]}{c.motivo && ` · ${c.motivo}`}
          </li>
        ))}
      </ul>
    </div>
  );
}
