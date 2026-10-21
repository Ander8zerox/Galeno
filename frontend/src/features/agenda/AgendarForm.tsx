import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, unwrap } from "../../api/client";
import { aIso, hora } from "./tiempo";

interface Props {
  medicoId: string;
  fecha: string;
  zona: string;
  /** Cupo elegido; sin él, se agenda un sobrecupo a la hora que se indique. */
  inicio?: string;
  onCerrar: () => void;
}

export function AgendarForm({ medicoId, fecha, zona, inicio, onCerrar }: Props) {
  const queryClient = useQueryClient();
  const sobrecupo = !inicio;
  const [q, setQ] = useState("");
  const [paciente, setPaciente] = useState<{ id: string; nombre: string; incompleto: boolean } | null>(null);
  const [motivo, setMotivo] = useState("");
  const [horaManual, setHoraManual] = useState("07:00");
  const [duracion, setDuracion] = useState(20);

  const busqueda = useQuery({
    queryKey: ["pacientes", q, "agendar"],
    enabled: q.trim().length >= 2 && !paciente,
    queryFn: async () => unwrap(await api.GET("/api/v1/pacientes", { params: { query: { q, size: 8 } } })),
  });

  const agendar = useMutation({
    mutationFn: async () =>
      unwrap(await api.POST("/api/v1/agenda/citas", {
        body: {
          medicoId,
          pacienteId: paciente!.id,
          inicio: inicio ?? aIso(fecha, horaManual, zona),
          duracionMinutos: sobrecupo ? duracion : undefined,
          motivo: motivo || undefined,
          sobrecupo,
        },
      })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["citas"] });
      queryClient.invalidateQueries({ queryKey: ["disponibilidad"] });
      onCerrar();
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (paciente) agendar.mutate();
  }

  return (
    <form className="card stack agendar" onSubmit={onSubmit}>
      <div className="row between">
        <h3>{sobrecupo ? "Agendar sobrecupo" : `Agendar a las ${hora(inicio!, zona)}`}</h3>
        <button type="button" className="btn-link" onClick={onCerrar}>Cerrar</button>
      </div>
      {sobrecupo && (
        <div className="form-grid">
          <label>Hora<input type="time" value={horaManual} onChange={(e) => setHoraManual(e.target.value)} required /></label>
          <label>Duración (minutos)
            <select value={duracion} onChange={(e) => setDuracion(Number(e.target.value))}>
              {[10, 15, 20, 30, 40, 60].map((d) => <option key={d} value={d}>{d}</option>)}
            </select>
          </label>
        </div>
      )}
      {paciente ? (
        <div className="row">
          <span>Paciente: <strong>{paciente.nombre}</strong></span>
          <button type="button" className="btn-link" onClick={() => setPaciente(null)}>Cambiar</button>
          {paciente.incompleto && (
            <small className="badge badge-warn">
              Importado sin nombre: <Link to={`/pacientes/${paciente.id}`}>complete sus datos</Link> antes de la cita
            </small>
          )}
        </div>
      ) : (
        <label>Paciente (nombre o documento)
          <input value={q} onChange={(e) => setQ(e.target.value)} autoFocus placeholder="Escriba al menos 2 letras…" />
          {busqueda.data?.items?.length === 0 && <small className="muted">Sin resultados. Cree el paciente en la sección Pacientes.</small>}
          {!!busqueda.data?.items?.length && (
            <div className="resultados">
              {busqueda.data.items.map((p) => (
                <button type="button" key={p.id} className="opcion" onClick={() => setPaciente({ id: p.id!, nombre: `${p.nombres} ${p.apellidos}`, incompleto: !!p.datosIncompletos })}>
                  <strong>{p.nombres} {p.apellidos}</strong>{p.datosIncompletos && <span className="badge badge-warn">Datos incompletos</span>}
                  <span className="muted small">{p.tipoDocumento} {p.numeroDocumento}{p.telefono && ` · ${p.telefono}`}</span>
                </button>
              ))}
            </div>
          )}
        </label>
      )}
      <label>Motivo (opcional)<input value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Control, primera vez…" /></label>
      {agendar.error && <p className="alert alert-error">{agendar.error.message}</p>}
      <div><button className="btn" disabled={!paciente || agendar.isPending}>{agendar.isPending ? "Agendando…" : "Agendar cita"}</button></div>
    </form>
  );
}
