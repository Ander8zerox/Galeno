import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "../../api/client";
import { DIAS, hhmm } from "./tiempo";

type Franja = { diaSemana: number; horaInicio: string; horaFin: string };

export function HorarioEditor({ medicoId, onGuardado }: { medicoId: string; onGuardado: () => void }) {
  const queryClient = useQueryClient();
  const horario = useQuery({
    queryKey: ["horario", medicoId],
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/medicos/{medicoId}/horario", { params: { path: { medicoId } } })),
  });
  const [duracion, setDuracion] = useState(20);
  const [franjas, setFranjas] = useState<Franja[]>([]);

  useEffect(() => {
    if (!horario.data) return;
    setDuracion(horario.data.duracionMinutos ?? 20);
    setFranjas((horario.data.bloques ?? []).map((b) => ({ diaSemana: b.diaSemana!, horaInicio: hhmm(b.horaInicio), horaFin: hhmm(b.horaFin) })));
  }, [horario.data]);

  const guardar = useMutation({
    mutationFn: async () =>
      unwrap(await api.PUT("/api/v1/agenda/medicos/{medicoId}/horario", {
        params: { path: { medicoId } },
        body: { duracionMinutos: duracion, bloques: franjas },
      })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["horario", medicoId] });
      queryClient.invalidateQueries({ queryKey: ["disponibilidad"] });
      onGuardado();
    },
  });

  const actualizar = (idx: number, cambio: Partial<Franja>) => setFranjas(franjas.map((f, i) => (i === idx ? { ...f, ...cambio } : f)));

  if (horario.isLoading) return <p className="muted">Cargando…</p>;
  if (horario.error) return <p className="alert alert-error">{horario.error.message}</p>;

  return (
    <div className="card stack">
      <p className="muted small">Los cambios aplican a los cupos futuros. Las citas ya agendadas no se mueven.</p>
      <label className="row">Duración de cada cita
        <select value={duracion} onChange={(e) => setDuracion(Number(e.target.value))}>
          {[10, 15, 20, 30, 40, 45, 60].map((d) => <option key={d} value={d}>{d} minutos</option>)}
        </select>
      </label>
      <div className="horario">
        {DIAS.map((nombre, i) => {
          const dia = i + 1;
          return (
            <div key={dia} className="horario-dia">
              <strong>{nombre}</strong>
              <div className="stack">
                {franjas.map((f, idx) => f.diaSemana !== dia ? null : (
                  <div key={idx} className="row">
                    <input type="time" value={f.horaInicio} onChange={(e) => actualizar(idx, { horaInicio: e.target.value })} aria-label={`${nombre} desde`} />
                    <span>a</span>
                    <input type="time" value={f.horaFin} onChange={(e) => actualizar(idx, { horaFin: e.target.value })} aria-label={`${nombre} hasta`} />
                    <button className="btn-link danger" onClick={() => setFranjas(franjas.filter((_, j) => j !== idx))}>Quitar</button>
                  </div>
                ))}
                {!franjas.some((f) => f.diaSemana === dia) && <span className="muted small">No atiende</span>}
                <div><button className="btn-link" onClick={() => setFranjas([...franjas, { diaSemana: dia, horaInicio: "08:00", horaFin: "12:00" }])}>+ Agregar franja</button></div>
              </div>
            </div>
          );
        })}
      </div>
      {guardar.error && <p className="alert alert-error">{guardar.error.message}</p>}
      <div><button className="btn" disabled={guardar.isPending} onClick={() => guardar.mutate()}>Guardar horario</button></div>
    </div>
  );
}
