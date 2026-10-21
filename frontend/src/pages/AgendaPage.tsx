import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, unwrap } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { AgendaDia } from "../features/agenda/AgendaDia";
import { HorarioEditor } from "../features/agenda/HorarioEditor";
import { fechaLarga, hoyEn, sumarDias } from "../features/agenda/tiempo";

const STORAGE_MEDICO = "galeno.agenda.medico";

export function AgendaPage() {
  const { me } = useAuth();
  const zona = me?.zonaHoraria ?? "America/Bogota";
  const [fecha, setFecha] = useState(() => hoyEn(zona));
  const [medicoId, setMedicoId] = useState<string | null>(() => {
    try {
      return localStorage.getItem(STORAGE_MEDICO);
    } catch {
      return null;
    }
  });
  const [editandoHorario, setEditandoHorario] = useState(false);

  const medicos = useQuery({
    queryKey: ["agenda-medicos"],
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/medicos")),
  });

  // Si el médico recordado ya no está disponible, se elige el propio o el primero
  useEffect(() => {
    const lista = medicos.data;
    if (!lista?.length || lista.some((m) => m.membresiaId === medicoId)) return;
    setMedicoId(lista.find((m) => m.membresiaId === me?.membresiaId)?.membresiaId ?? lista[0].membresiaId!);
  }, [medicos.data, medicoId, me?.membresiaId]);

  function elegirMedico(id: string) {
    setMedicoId(id);
    setEditandoHorario(false);
    try {
      localStorage.setItem(STORAGE_MEDICO, id);
    } catch {
      /* preferencia opcional */
    }
  }

  // Solo se usa el médico recordado si está entre los permitidos: así nunca se piden citas ajenas
  const medico = medicos.data?.find((m) => m.membresiaId === medicoId);

  if (medicos.isLoading) return <p className="muted">Cargando…</p>;
  if (medicos.error) return <p className="alert alert-error">{medicos.error.message}</p>;
  if (!medicos.data?.length) return <p className="muted">No hay médicos activos en este consultorio.</p>;

  return (
    <section className="stack">
      <div className="page-header">
        <h1>Agenda</h1>
        {medico?.puedeEditarHorario && (
          <button className="btn btn-secondary" onClick={() => setEditandoHorario((v) => !v)}>
            {editandoHorario ? "Volver a la agenda" : "Horario de atención"}
          </button>
        )}
      </div>

      <div className="card row between">
        {medicos.data.length > 1 ? (
          <label className="row">Médico
            <select value={medicoId ?? ""} onChange={(e) => elegirMedico(e.target.value)}>
              {medicos.data.map((m) => <option key={m.membresiaId} value={m.membresiaId}>{m.nombre}</option>)}
            </select>
          </label>
        ) : (
          <strong>{medico?.nombre}</strong>
        )}
        {!editandoHorario && (
          <div className="row">
            <button className="btn btn-secondary" aria-label="Día anterior" onClick={() => setFecha(sumarDias(fecha, -1))}>‹</button>
            <button className="btn btn-secondary" onClick={() => setFecha(hoyEn(zona))}>Hoy</button>
            <button className="btn btn-secondary" aria-label="Día siguiente" onClick={() => setFecha(sumarDias(fecha, 1))}>›</button>
            <input type="date" value={fecha} onChange={(e) => e.target.value && setFecha(e.target.value)} />
          </div>
        )}
      </div>

      {medico?.membresiaId && (editandoHorario
        ? <HorarioEditor medicoId={medico.membresiaId} onGuardado={() => setEditandoHorario(false)} />
        : (
          <>
            <h2 className="dia-titulo">{fechaLarga(fecha)}</h2>
            <AgendaDia medicoId={medico.membresiaId} fecha={fecha} zona={zona} />
          </>
        ))}
    </section>
  );
}
