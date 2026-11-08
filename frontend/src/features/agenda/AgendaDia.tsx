import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { api, unwrap, type Schemas } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { CobroForm, faltaAutorizacion, resumenCobro } from "../rips/CobroForm";
import { DatosRipsPaciente } from "../rips/DatosRipsPaciente";
import { useModuloActivo } from "../rips/modulos";
import { useCobrosCitas, type Cobro } from "../rips/pagadores";
import { useFaltantesRips, useReferencia } from "../rips/referencias";
import { AgendarForm } from "./AgendarForm";
import { AnotarEspera, EspacioLiberado } from "./ListaEspera";
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
/** @param abrir abre el formulario de agendar en ese cupo (desde la semana, "Todos los médicos" o la búsqueda) */
export function AgendaDia({ medicoId, fecha, zona, abrir }: {
  medicoId: string;
  fecha: string;
  zona: string;
  abrir?: { inicio: string; duracion?: number };
}) {
  const [agendando, setAgendandoEstado] = useState<{ inicio?: string; duracion?: number } | null>(abrir ?? null);
  // Espacio libre para el que se buscan sugerencias (al cancelar una cita o desde "¿A quién llamar?")
  const [hueco, setHuecoEstado] = useState<{ inicio: string; titulo: string } | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const setAgendando = (a: typeof agendando) => { setAgendandoEstado(a); if (a) { setHuecoEstado(null); setAviso(null); } };
  const setHueco = (h: typeof hueco) => { setHuecoEstado(h); setAviso(null); if (h) setAgendandoEstado(null); };
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
  // "¿A quién llamar?" solo en el primer cupo de cada tramo libre: las sugerencias cubren el tramo completo
  const finesLibres = new Set((libres.data ?? []).map((l) => Date.parse(l.fin!)));
  // Con RIPS activo, se avisa en la cita lo que le falta al paciente para que la secretaria lo complete al recibirlo
  const { puede } = useAuth();
  const modulo = useModuloActivo("RIPS");
  const rips = modulo && puede("PACIENTE_GESTIONAR");
  const abiertas = (citas.data ?? []).filter(abierta);
  const faltantes = useFaltantesRips(abiertas.map((c) => c.pacienteId!), rips);
  // Y el pagador y el cobro de cada una, que la secretaria registra al recibir al paciente
  const cobros = useCobrosCitas(abiertas.map((c) => c.id!), modulo && puede("AGENDA_GESTIONAR"));

  return (
    <div className="stack">
      <div className="row between">
        <span className="muted">{activas} cita(s) · {libres.data?.length ?? 0} cupo(s) libre(s)</span>
        <button className="btn btn-secondary" onClick={() => setAgendando({})}>Sobrecupo / otra hora</button>
      </div>

      {agendando && (
        <AgendarForm medicoId={medicoId} fecha={fecha} zona={zona} inicio={agendando.inicio} cupos={libres.data} duracionInicial={agendando.duracion} onCerrar={() => setAgendando(null)} />
      )}

      {aviso && <p className="alert alert-ok">{aviso}</p>}
      {hueco && (
        <EspacioLiberado key={hueco.inicio} medicoId={medicoId} inicio={hueco.inicio} zona={zona} titulo={hueco.titulo}
          onListo={(t) => { setHuecoEstado(null); setAviso(t); }} onCerrar={() => setHueco(null)} />
      )}

      {(citas.error || libres.error) && <p className="alert alert-error">{citas.error?.message ?? libres.error?.message}</p>}
      {(citas.isLoading || libres.isLoading) && <p className="muted">Cargando…</p>}
      {!citas.isLoading && !libres.isLoading && filas.length === 0 && (
        <p className="card muted center">No hay cupos libres este día (fuera del horario del médico, ya pasaron o están ocupados). Puede agendar un sobrecupo si es necesario.</p>
      )}

      <div className="timeline">
        {filas.map((f) => f.tipo === "libre" ? (
          <div key={`l-${f.inicio}`} className="slot-par">
            <button className="slot" onClick={() => setAgendando({ inicio: f.inicio })}>
              <span className="slot-hora">{hora(f.inicio, zona)}</span>
              <span className="muted">Disponible</span>
              <span className="slot-accion">Agendar</span>
            </button>
            {!finesLibres.has(Date.parse(f.inicio)) && <button className="btn-link" title="Lista de espera y citas de más tarde que caben aquí"
              onClick={() => setHueco({ inicio: f.inicio, titulo: `Espacio de las ${hora(f.inicio, zona)}` })}>¿A quién llamar?</button>}
          </div>
        ) : (
          <CitaFila key={f.cita.id} cita={f.cita} zona={zona} faltanRips={faltantes.data?.get(f.cita.pacienteId!)}
            cobro={cobros.data?.get(f.cita.id!)}
            onCancelada={(c) => {
              if (Date.parse(c.inicio!) > Date.now()) {
                setHueco({ inicio: c.inicio!, titulo: `Se canceló la cita de ${c.pacienteNombre}: ¿a quién le damos el espacio?` });
              }
            }} />
        ))}
      </div>
    </div>
  );
}

const abierta = (c: Cita) => c.estado === "PROGRAMADA" || c.estado === "CONFIRMADA";

function CitaFila({ cita, zona, faltanRips, cobro, onCancelada }: {
  cita: Cita; zona: string; faltanRips?: string[]; cobro?: Cobro;
  /** Para ofrecer el espacio que queda libre */
  onCancelada: (cita: Cita) => void;
}) {
  const { me, puede } = useAuth();
  const queryClient = useQueryClient();
  const [cancelando, setCancelando] = useState(false);
  const [marcandoInasistencia, setMarcandoInasistencia] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [completandoRips, setCompletandoRips] = useState(false);
  const [registrandoCobro, setRegistrandoCobro] = useState(false);
  const [anotandoEspera, setAnotandoEspera] = useState(false);
  const conceptos = useReferencia("concepto_recaudo");
  const diferida = useAccionDiferida();
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
    onSuccess: (c, body) => {
      queryClient.invalidateQueries({ queryKey: ["citas"] });
      queryClient.invalidateQueries({ queryKey: ["disponibilidad"] });
      queryClient.invalidateQueries({ queryKey: ["lista-espera"] });
      setCancelando(false);
      setMarcandoInasistencia(false);
      if (body.estado === "CANCELADA") onCancelada(c);
    },
  });
  const cuando = `${cita.pacienteNombre} a las ${hora(cita.inicio!, zona)}`;

  return (
    <div className={`cita cita-${cita.estado!.toLowerCase()}`}>
      <span className="slot-hora">{hora(cita.inicio!, zona)}</span>
      <div className="grow">
        <div className="row">
          <Link to={`/pacientes/${cita.pacienteId}`}><strong>{cita.pacienteNombre}</strong></Link>
          <span className={`badge ${ESTADO_CLASE[cita.estado!]}`}>{ESTADO_NOMBRE[cita.estado!]}</span>
          {cita.sobrecupo && <span className="badge badge-warn">Sobrecupo</span>}
          {abierta(cita) && !!faltanRips?.length && (
            <button className="badge badge-warn badge-boton" title={`Falta: ${faltanRips.join(", ").toLowerCase()}`}
              onClick={() => setCompletandoRips((v) => !v)}>RIPS: faltan {faltanRips.length}</button>
          )}
          {abierta(cita) && faltaAutorizacion(cobro) && (
            <button className="badge badge-warn badge-boton" onClick={() => setRegistrandoCobro(true)}>Falta autorización</button>
          )}
        </div>
        <div className="muted small">
          {hora(cita.inicio!, zona)}–{hora(cita.fin!, zona)} · {cita.pacienteDocumento}
          {cita.pacienteTelefono && ` · ${cita.pacienteTelefono}`}
          {cita.motivo && ` · ${cita.motivo}`}
          {cita.motivoCancelacion && ` · Cancelada: ${cita.motivoCancelacion}`}
        </div>
        {abierta(cita) && cobro?.pagadorId && !registrandoCobro && (
          <div className="small">{resumenCobro(cobro, conceptos.data)}{!cobro.guardado && <span className="muted"> (pagador del paciente, sin registrar)</span>}</div>
        )}
        {registrandoCobro && cobro && abierta(cita) && (
          <CobroForm donde="cita" id={cita.id!} cobro={cobro} onGuardado={() => setRegistrandoCobro(false)} onCancelar={() => setRegistrandoCobro(false)} />
        )}
        {completandoRips && abierta(cita) && <DatosRipsPaciente pacienteId={cita.pacienteId!} editar />}
        {anotandoEspera && abierta(cita) && (
          <AnotarEspera medicos={[]} zona={zona} inicial={{ paciente: { id: cita.pacienteId!, nombre: cita.pacienteNombre ?? "" }, cita }}
            onCerrar={() => setAnotandoEspera(false)} />
        )}
        {cancelando && (
          <div className="stack-sm confirmar">
            <span className="small">¿Cancelar la cita de {cuando}?</span>
            <div className="row">
              <input className="grow" placeholder="Motivo de la cancelación" value={motivo} onChange={(e) => setMotivo(e.target.value)} autoFocus />
              <button className="btn" disabled={!motivo.trim() || cambiar.isPending} onClick={() => cambiar.mutate({ estado: "CANCELADA", motivoCancelacion: motivo })}>Cancelar cita</button>
              <button className="btn-link" onClick={() => setCancelando(false)}>Volver</button>
            </div>
          </div>
        )}
        {marcandoInasistencia && (
          <div className="row confirmar">
            <span className="small grow">¿Marcar que no asistió {cuando}?</span>
            <button className="btn" disabled={cambiar.isPending} onClick={() => cambiar.mutate({ estado: "NO_ASISTIO" })}>Sí, no asistió</button>
            <button className="btn-link" onClick={() => setMarcandoInasistencia(false)}>Volver</button>
          </div>
        )}
        {diferida.pendiente && (
          <div className="row confirmar">
            <span className="small grow">{diferida.pendiente}…</span>
            <button className="btn-link" onClick={diferida.deshacer}>Deshacer</button>
          </div>
        )}
        {(cambiar.error || atender.error) && <p className="alert alert-error">{(cambiar.error ?? atender.error)?.message}</p>}
      </div>
      {abierta(cita) && !cancelando && !marcandoInasistencia && !diferida.pendiente && (
        <div className="row acciones">
          {cita.estado === "PROGRAMADA" && <button className="btn-link" onClick={() => diferida.programar("Confirmando la cita", () => cambiar.mutate({ estado: "CONFIRMADA" }))}>Confirmar</button>}
          {cobro && !registrandoCobro && <button className="btn-link" onClick={() => setRegistrandoCobro(true)}>Cobro</button>}
          {puede("HISTORIA_REGISTRAR") && esMiCita && <button className="btn" disabled={atender.isPending} onClick={() => diferida.programar("Abriendo la atención", () => atender.mutate())}>Atender</button>}
          {Date.parse(cita.inicio!) > Date.now() && !anotandoEspera && (
            <button className="btn-link" title="Anotarlo en la lista de espera para adelantar esta cita si se libera un espacio antes"
              onClick={() => setAnotandoEspera(true)}>Quiere antes</button>
          )}
          <button className="btn-link" onClick={() => setMarcandoInasistencia(true)}>No asistió</button>
          <button className="btn-link danger" onClick={() => setCancelando(true)}>Cancelar</button>
        </div>
      )}
    </div>
  );
}

/**
 * "Deshacer" sin revertir nada en el servidor: la acción se ejecuta unos segundos después del clic, salvo que se
 * deshaga. Para pasos normales del día (confirmar, atender), donde un diálogo de confirmación acostumbraría a decir
 * "sí" sin leer.
 */
function useAccionDiferida(segundos = 4) {
  const [pendiente, setPendiente] = useState<string | null>(null);
  const temporizador = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => {
    if (temporizador.current) clearTimeout(temporizador.current); // al salir de la pantalla no se ejecuta nada
  }, []);

  function programar(texto: string, accion: () => void) {
    setPendiente(texto);
    temporizador.current = setTimeout(() => {
      temporizador.current = null;
      setPendiente(null);
      accion();
    }, segundos * 1000);
  }

  function deshacer() {
    if (temporizador.current) clearTimeout(temporizador.current);
    temporizador.current = null;
    setPendiente(null);
  }

  return { pendiente, programar, deshacer };
}
