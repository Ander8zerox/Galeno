import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { api, unwrap, type Schemas } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { AgendarForm } from "./AgendarForm";
import { hora } from "./tiempo";

type Cita = Schemas["CitaVista"];
type Estado = NonNullable<Cita["estado"]>;
type Fila = { tipo: "cita"; inicio: string; cita: Cita } | { tipo: "libre"; inicio: string; fin: string };

export const ESTADO_NOMBRE: Record<Estado, string> = {
  PROGRAMADA: "Programada", CONFIRMADA: "Confirmada", ATENDIDA: "Atendida", NO_ASISTIO: "No asistió", CANCELADA: "Cancelada",
};
const ESTADO_CLASE: Record<Estado, string> = {
  PROGRAMADA: "badge-info", CONFIRMADA: "badge-primary", ATENDIDA: "badge-ok", NO_ASISTIO: "badge-warn", CANCELADA: "",
};

/** Un día de agenda: cupos libres y citas en una sola línea de tiempo. */
export function AgendaDia({ medicoId, fecha, zona }: { medicoId: string; fecha: string; zona: string }) {
  const [agendando, setAgendando] = useState<{ inicio?: string } | null>(null);
  const params = { path: { medicoId } };

  const citas = useQuery({
    queryKey: ["citas", medicoId, fecha],
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/medicos/{medicoId}/citas", { params: { ...params, query: { desde: fecha, hasta: fecha } } })),
  });
  const libres = useQuery({
    queryKey: ["disponibilidad", medicoId, fecha],
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/medicos/{medicoId}/disponibilidad", { params: { ...params, query: { fecha } } })),
  });

  const filas: Fila[] = [
    ...(citas.data ?? []).map((c): Fila => ({ tipo: "cita", inicio: c.inicio!, cita: c })),
    ...(libres.data ?? []).map((l): Fila => ({ tipo: "libre", inicio: l.inicio!, fin: l.fin! })),
  ].sort((a, b) => a.inicio.localeCompare(b.inicio) || (a.tipo === "cita" ? -1 : 1));

  const activas = (citas.data ?? []).filter((c) => c.estado !== "CANCELADA").length;

  return (
    <div className="stack">
      <div className="row between">
        <span className="muted">{activas} cita(s) · {libres.data?.length ?? 0} cupo(s) libre(s)</span>
        <button className="btn btn-secondary" onClick={() => setAgendando({})}>Sobrecupo / otra hora</button>
      </div>

      {agendando && (
        <AgendarForm medicoId={medicoId} fecha={fecha} zona={zona} inicio={agendando.inicio} onCerrar={() => setAgendando(null)} />
      )}

      {(citas.error || libres.error) && <p className="alert alert-error">{citas.error?.message ?? libres.error?.message}</p>}
      {(citas.isLoading || libres.isLoading) && <p className="muted">Cargando…</p>}
      {!citas.isLoading && !libres.isLoading && filas.length === 0 && (
        <p className="card muted center">No hay cupos libres este día (fuera del horario del médico, ya pasaron o están ocupados). Puede agendar un sobrecupo si es necesario.</p>
      )}

      <div className="timeline">
        {filas.map((f) => f.tipo === "libre" ? (
          <button key={`l-${f.inicio}`} className="slot" onClick={() => setAgendando({ inicio: f.inicio })}>
            <span className="slot-hora">{hora(f.inicio, zona)}</span>
            <span className="muted">Disponible</span>
            <span className="slot-accion">Agendar</span>
          </button>
        ) : (
          <CitaFila key={f.cita.id} cita={f.cita} zona={zona} />
        ))}
      </div>
    </div>
  );
}

function CitaFila({ cita, zona }: { cita: Cita; zona: string }) {
  const { me, puede } = useAuth();
  const queryClient = useQueryClient();
  const [cancelando, setCancelando] = useState(false);
  const [motivo, setMotivo] = useState("");
  const abierta = cita.estado === "PROGRAMADA" || cita.estado === "CONFIRMADA";
  const esMiCita = cita.medicoId === me?.membresiaId;
  const navigate = useNavigate();
  const atender = useMutation({
    mutationFn: async () =>
      unwrap(await api.POST("/api/v1/historia/atenciones", { body: { pacienteId: cita.pacienteId!, citaId: cita.id } })),
    onSuccess: (a) => navigate(`/pacientes/${cita.pacienteId}/atenciones/${a.id}`),
  });

  const cambiar = useMutation({
    mutationFn: async (body: { estado: Estado; motivoCancelacion?: string }) =>
      unwrap(await api.PUT("/api/v1/agenda/citas/{citaId}/estado", { params: { path: { citaId: cita.id! } }, body })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["citas"] });
      queryClient.invalidateQueries({ queryKey: ["disponibilidad"] });
      setCancelando(false);
    },
  });

  return (
    <div className={`cita cita-${cita.estado!.toLowerCase()}`}>
      <span className="slot-hora">{hora(cita.inicio!, zona)}</span>
      <div className="grow">
        <div className="row">
          <Link to={`/pacientes/${cita.pacienteId}`}><strong>{cita.pacienteNombre}</strong></Link>
          <span className={`badge ${ESTADO_CLASE[cita.estado!]}`}>{ESTADO_NOMBRE[cita.estado!]}</span>
          {cita.sobrecupo && <span className="badge badge-warn">Sobrecupo</span>}
        </div>
        <div className="muted small">
          {hora(cita.inicio!, zona)}–{hora(cita.fin!, zona)} · {cita.pacienteDocumento}
          {cita.pacienteTelefono && ` · ${cita.pacienteTelefono}`}
          {cita.motivo && ` · ${cita.motivo}`}
          {cita.motivoCancelacion && ` · Cancelada: ${cita.motivoCancelacion}`}
        </div>
        {cancelando && (
          <div className="row">
            <input className="grow" placeholder="Motivo de la cancelación" value={motivo} onChange={(e) => setMotivo(e.target.value)} autoFocus />
            <button className="btn" disabled={!motivo.trim() || cambiar.isPending} onClick={() => cambiar.mutate({ estado: "CANCELADA", motivoCancelacion: motivo })}>Cancelar cita</button>
            <button className="btn-link" onClick={() => setCancelando(false)}>Volver</button>
          </div>
        )}
        {(cambiar.error || atender.error) && <p className="alert alert-error">{(cambiar.error ?? atender.error)?.message}</p>}
      </div>
      {abierta && !cancelando && (
        <div className="row acciones">
          {cita.estado === "PROGRAMADA" && <button className="btn-link" onClick={() => cambiar.mutate({ estado: "CONFIRMADA" })}>Confirmar</button>}
          {puede("HISTORIA_REGISTRAR") && esMiCita && <button className="btn" disabled={atender.isPending} onClick={() => atender.mutate()}>Atender</button>}
          <button className="btn-link" onClick={() => cambiar.mutate({ estado: "NO_ASISTIO" })}>No asistió</button>
          <button className="btn-link danger" onClick={() => setCancelando(true)}>Cancelar</button>
        </div>
      )}
    </div>
  );
}
