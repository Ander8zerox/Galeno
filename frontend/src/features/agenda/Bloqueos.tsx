import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { aIso, fechaEn, fechaHoraCorta, hora, hoyEn, sumarDias } from "./tiempo";
import type { AbrirDia } from "./VistasAgenda";

type Bloqueo = Schemas["BloqueoVista"];
type Medico = Schemas["MedicoAgenda"];
type Cita = Schemas["CitaVista"];

const CONSULTORIO = "";
const MOTIVOS = ["Vacaciones", "Capacitación", "Congreso", "Incapacidad", "Cierre del consultorio"];

/** "Todo el día" si el bloqueo empieza y termina a medianoche (hora del consultorio). */
export function rangoBloqueo(b: Bloqueo, zona: string): string {
  const medianoche = (iso: string) => hora(iso, zona) === "12:00 a. m." || hora(iso, zona) === "00:00";
  const desde = fechaEn(b.inicio!, zona);
  const hastaDia = fechaEn(new Date(Date.parse(b.fin!) - 1).toISOString(), zona);
  if (medianoche(b.inicio!) && medianoche(b.fin!)) {
    return desde === hastaDia ? `${fechaCortaDia(desde)}, todo el día` : `Del ${fechaCortaDia(desde)} al ${fechaCortaDia(hastaDia)}`;
  }
  return desde === hastaDia
    ? `${fechaCortaDia(desde)}, de ${hora(b.inicio!, zona)} a ${hora(b.fin!, zona)}`
    : `Del ${fechaHoraCorta(b.inicio!, zona)} al ${fechaHoraCorta(b.fin!, zona)}`;
}

function fechaCortaDia(fecha: string): string {
  return new Intl.DateTimeFormat("es-CO", { weekday: "short", day: "numeric", month: "short", timeZone: "UTC" })
    .format(new Date(`${fecha}T12:00:00Z`));
}

/** Los bloqueos que tocan un rango de fechas (los del consultorio y los de los médicos que ve quien consulta). */
export function useBloqueos(desde: string, hasta: string) {
  return useQuery({
    queryKey: ["bloqueos", desde, hasta],
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/bloqueos", { params: { query: { desde, hasta } } })),
  });
}

/** Vacaciones, capacitaciones y cierres: lista de los próximos y formulario para bloquear. */
export function Bloqueos({ medicos, medicoId, zona, abrirDia, onCerrar }: {
  medicos: Medico[];
  medicoId: string | null;
  zona: string;
  abrirDia: AbrirDia;
  onCerrar: () => void;
}) {
  const hoy = hoyEn(zona);
  const queryClient = useQueryClient();
  const esAdmin = !!useAuth().me?.roles?.includes("ADMIN");
  const lista = useBloqueos(hoy, sumarDias(hoy, 365));
  const [creando, setCreando] = useState(false);
  const [afectadas, setAfectadas] = useState<{ motivo: string; citas: Cita[] } | null>(null);
  const [quitando, setQuitando] = useState<string | null>(null);

  const refrescar = () => {
    for (const k of ["bloqueos", "disponibilidad", "espacios", "sugerencias"]) queryClient.invalidateQueries({ queryKey: [k] });
  };
  const quitar = useMutation({
    mutationFn: async (id: string) => unwrap(await api.DELETE("/api/v1/agenda/bloqueos/{id}", { params: { path: { id } } })),
    onSuccess: () => { setQuitando(null); refrescar(); },
  });
  const nombre = (b: Bloqueo) => b.medicoId ? b.medicoNombre ?? "Médico" : "Todo el consultorio";

  return (
    <div className="card stack agendar">
      <div className="row between">
        <h3>Bloqueos de agenda</h3>
        <div className="row">
          {!creando && <button className="btn btn-secondary" onClick={() => { setCreando(true); setAfectadas(null); }}>Bloquear agenda</button>}
          <button type="button" className="btn-link" onClick={onCerrar}>Cerrar</button>
        </div>
      </div>
      <p className="muted small">
        Vacaciones, capacitaciones o cierres. En un bloqueo no se ofrecen cupos ni espacios; solo se puede agendar con
        sobrecupo. Las citas que ya estaban no se cancelan: Galenum se las muestra para que las mueva o cancele.
      </p>

      {creando && (
        <NuevoBloqueo medicos={medicos} medicoId={medicoId} zona={zona} esAdmin={esAdmin} onCerrar={() => setCreando(false)}
          onCreado={(r) => {
            setCreando(false);
            refrescar();
            if (r.citasAfectadas?.length) setAfectadas({ motivo: r.bloqueo!.motivo!, citas: r.citasAfectadas });
          }} />
      )}

      {afectadas && (
        <div className="alert alert-warn stack-sm">
          <strong>
            Hay {afectadas.citas.length} cita{afectadas.citas.length === 1 ? "" : "s"} dentro del bloqueo "{afectadas.motivo}".
            Llame a cada paciente y muévala o cancélela.
          </strong>
          <div className="resultados">
            {afectadas.citas.map((c) => (
              <div key={c.id} className="espacio-libre">
                <span>
                  <strong>{fechaHoraCorta(c.inicio!, zona)}</strong> · {c.pacienteNombre}
                  {c.pacienteTelefono && ` · ${c.pacienteTelefono}`}
                  {medicos.length > 1 && <span className="muted"> · {c.medicoNombre}</span>}
                </span>
                <button className="btn btn-secondary" onClick={() => abrirDia(fechaEn(c.inicio!, zona), undefined, c.medicoId)}>Ver el día</button>
              </div>
            ))}
          </div>
        </div>
      )}

      {lista.isLoading && <p className="muted">Cargando…</p>}
      {lista.error && <p className="alert alert-error">{lista.error.message}</p>}
      {lista.data?.length === 0 && !creando && <p className="muted">No hay bloqueos próximos.</p>}
      <div className="resultados">
        {lista.data?.map((b) => {
          const puedeQuitar = !!b.medicoId || esAdmin;
          return (
            <div key={b.id} className="espacio-libre">
              <span className="stack-sm">
                <span><strong>{b.motivo}</strong> · {nombre(b)}</span>
                <span className="muted small">{rangoBloqueo(b, zona)}</span>
              </span>
              {puedeQuitar && (quitando === b.id ? (
                <span className="row confirmar">
                  <span className="small">¿Quitar el bloqueo?</span>
                  <button className="btn" disabled={quitar.isPending} onClick={() => quitar.mutate(b.id!)}>Sí, quitar</button>
                  <button className="btn-link" onClick={() => setQuitando(null)}>Volver</button>
                </span>
              ) : (
                <button className="btn-link danger" onClick={() => setQuitando(b.id!)}>Quitar</button>
              ))}
            </div>
          );
        })}
      </div>
      {quitar.error && <p className="alert alert-error">{quitar.error.message}</p>}
    </div>
  );
}

function NuevoBloqueo({ medicos, medicoId, zona, esAdmin, onCreado, onCerrar }: {
  medicos: Medico[];
  medicoId: string | null;
  zona: string;
  esAdmin: boolean;
  onCreado: (r: Schemas["BloqueoCreado"]) => void;
  onCerrar: () => void;
}) {
  const hoy = hoyEn(zona);
  const [para, setPara] = useState(medicoId ?? medicos[0]?.membresiaId ?? CONSULTORIO);
  const [desde, setDesde] = useState(hoy);
  const [hasta, setHasta] = useState(hoy);
  const [todoElDia, setTodoElDia] = useState(true);
  const [horaDesde, setHoraDesde] = useState("08:00");
  const [horaHasta, setHoraHasta] = useState("12:00");
  const [motivo, setMotivo] = useState("Vacaciones");

  const crear = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/agenda/bloqueos", {
      body: {
        medicoId: para || undefined,
        // Todo el día: desde la medianoche del primer día hasta la del día siguiente al último
        inicio: aIso(desde, todoElDia ? "00:00" : horaDesde, zona),
        fin: aIso(todoElDia ? sumarDias(hasta, 1) : hasta, todoElDia ? "00:00" : horaHasta, zona),
        motivo,
      },
    })),
    onSuccess: onCreado,
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    crear.mutate();
  }

  return (
    <form className="card stack" onSubmit={onSubmit}>
      <div className="form-grid">
        <label>Agenda de
          <select value={para} onChange={(e) => setPara(e.target.value)}>
            {medicos.map((m) => <option key={m.membresiaId} value={m.membresiaId}>{m.nombre}</option>)}
            {esAdmin && <option value={CONSULTORIO}>Todo el consultorio</option>}
          </select>
        </label>
        <label>Motivo
          <input list="motivos-bloqueo" value={motivo} maxLength={200} onChange={(e) => setMotivo(e.target.value)} required />
          <datalist id="motivos-bloqueo">{MOTIVOS.map((m) => <option key={m} value={m} />)}</datalist>
        </label>
        <label>Desde<input type="date" value={desde} min={hoy} required
          onChange={(e) => { setDesde(e.target.value); if (e.target.value > hasta) setHasta(e.target.value); }} /></label>
        <label>Hasta<input type="date" value={hasta} min={desde} required onChange={(e) => setHasta(e.target.value)} /></label>
        <label className="check wide">
          <input type="checkbox" checked={todoElDia} onChange={(e) => setTodoElDia(e.target.checked)} /> Todo el día
        </label>
        {!todoElDia && (
          <>
            <label>Desde la hora<input type="time" value={horaDesde} required onChange={(e) => setHoraDesde(e.target.value)} /></label>
            <label>Hasta la hora<input type="time" value={horaHasta} required onChange={(e) => setHoraHasta(e.target.value)} /></label>
          </>
        )}
      </div>
      {crear.error && <p className="alert alert-error">{crear.error.message}</p>}
      <div className="row">
        <button className="btn" disabled={crear.isPending}>{crear.isPending ? "Bloqueando…" : "Bloquear"}</button>
        <button type="button" className="btn-link" onClick={onCerrar}>Cancelar</button>
      </div>
    </form>
  );
}

/** Aviso en la vista del día: lo que está bloqueado ese día en la agenda del médico. */
export function AvisoBloqueos({ medicoId, fecha, zona }: { medicoId: string; fecha: string; zona: string }) {
  const bloqueos = useBloqueos(fecha, fecha);
  const delMedico = (bloqueos.data ?? []).filter((b) => !b.medicoId || b.medicoId === medicoId);
  if (!delMedico.length) return null;
  return (
    <>
      {delMedico.map((b) => (
        <p key={b.id} className="alert alert-warn">
          <strong>Agenda bloqueada: {b.motivo}</strong> ({rangoBloqueo(b, zona)}{b.medicoId ? "" : ", todo el consultorio"}).
          {" "}No se ofrecen cupos; para atender igual, agende con sobrecupo.
        </p>
      ))}
    </>
  );
}
