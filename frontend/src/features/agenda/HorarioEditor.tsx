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
  const [atiendeFestivos, setAtiendeFestivos] = useState(false);
  // "" = sin tope
  const [maxSobrecupos, setMaxSobrecupos] = useState("");

  useEffect(() => {
    if (!horario.data) return;
    setDuracion(horario.data.duracionMinutos ?? 20);
    setAtiendeFestivos(!!horario.data.atiendeFestivos);
    setMaxSobrecupos(horario.data.maxSobrecuposDia == null ? "" : String(horario.data.maxSobrecuposDia));
    setFranjas((horario.data.bloques ?? []).map((b) => ({ diaSemana: b.diaSemana!, horaInicio: hhmm(b.horaInicio), horaFin: hhmm(b.horaFin) })));
  }, [horario.data]);

  const guardar = useMutation({
    mutationFn: async () =>
      unwrap(await api.PUT("/api/v1/agenda/medicos/{medicoId}/horario", {
        params: { path: { medicoId } },
        body: { duracionMinutos: duracion, bloques: franjas, atiendeFestivos,
          maxSobrecuposDia: maxSobrecupos === "" ? undefined : Number(maxSobrecupos) },
      })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["horario", medicoId] });
      queryClient.invalidateQueries({ queryKey: ["disponibilidad"] });
      onGuardado();
    },
  });

  // Citas que caben cada día con la duración elegida (lo que la agenda ofrece como cupos)
  const citasDelDia = (dia: number) => franjas.filter((f) => f.diaSemana === dia)
    .reduce((n, f) => n + Math.max(0, Math.floor((minutos(f.horaFin) - minutos(f.horaInicio)) / duracion)), 0);
  const totalSemana = DIAS.reduce((n, _, i) => n + citasDelDia(i + 1), 0);
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
              <strong>{nombre}{citasDelDia(dia) > 0 && <span className="muted small"> · {citasDelDia(dia)} citas</span>}</strong>
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
      <p className="small muted">Con estas franjas y citas de {duracion} minutos: {totalSemana} citas por semana. Los días sin franjas no se habilitan.</p>
      <label className="row">Sobrecupos por día, como máximo
        <input type="number" min={0} max={50} value={maxSobrecupos} placeholder="Sin tope" style={{ width: "7rem" }}
          onChange={(e) => setMaxSobrecupos(e.target.value)} />
        <small className="muted">0: no recibe sobrecupos. Las atenciones sin cita no cuentan.</small>
      </label>
      <label className="row">
        <input type="checkbox" checked={atiendeFestivos} onChange={(e) => setAtiendeFestivos(e.target.checked)} />
        Atiendo festivos (si no, los festivos de Colombia no tienen cupos)
      </label>
      {guardar.error && <p className="alert alert-error">{guardar.error.message}</p>}
      <div><button className="btn" disabled={guardar.isPending} onClick={() => guardar.mutate()}>Guardar horario</button></div>
    </div>
  );
}

/** "08:30" → 510 */
function minutos(hhmm: string): number {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + (m || 0);
}
