import { useEffect, useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, unwrap, type Schemas } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { PacienteForm } from "../pacientes/PacienteForm";
import { duracionTexto } from "./AgendarForm";
import { fechaCorta, fechaHoraCorta, hora, hoyEn } from "./tiempo";

type EnEspera = Schemas["EnEsperaVista"];
type Medico = Schemas["MedicoAgenda"];
type Franja = NonNullable<EnEspera["franja"]>;
type Cita = Schemas["CitaVista"];

const FRANJAS: Record<Franja, string> = { CUALQUIERA: "Cualquier hora", MANANA: "En la mañana", TARDE: "En la tarde" };
const DURACIONES = [10, 15, 20, 30, 40, 60, 90, 120, 180, 240];
const CUALQUIERA = "";

export function useListaEspera() {
  return useQuery({
    queryKey: ["lista-espera"],
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/lista-espera")),
  });
}

/** Tras agendar o mover una cita desde aquí, se refresca la agenda. Las sugerencias no: el panel se cierra y, al abrirlo, se piden de nuevo. */
function useRefrescarAgenda() {
  const queryClient = useQueryClient();
  return () => {
    for (const k of ["citas", "disponibilidad", "lista-espera", "espacios"]) {
      queryClient.invalidateQueries({ queryKey: [k] });
    }
  };
}

function Detalle({ e, zona }: { e: EnEspera; zona: string }) {
  return (
    <span className="muted small">
      {e.medicoNombre ?? "Cualquier médico"} · {duracionTexto(e.duracionMinutos!)} · {FRANJAS[e.franja!]}
      {e.antesDe && ` · antes del ${fechaCorta(e.antesDe)}`}
      {e.citaInicio && ` · quiere adelantar su cita del ${fechaHoraCorta(e.citaInicio, zona)}`}
      {e.pacienteTelefono && ` · ${e.pacienteTelefono}`}
      {e.nota && ` · ${e.nota}`}
    </span>
  );
}

// ---------------------------------------------------------------- Lista

export function ListaEspera({ medicos, zona, onCerrar }: { medicos: Medico[]; zona: string; onCerrar: () => void }) {
  const lista = useListaEspera();
  const refrescar = useRefrescarAgenda();
  const [anotando, setAnotando] = useState(false);
  const [retirando, setRetirando] = useState<string | null>(null);
  const retirar = useMutation({
    mutationFn: async (id: string) =>
      unwrap(await api.POST("/api/v1/agenda/lista-espera/{id}/retirar", { params: { path: { id } } })),
    onSuccess: () => { setRetirando(null); refrescar(); },
  });

  return (
    <div className="card stack agendar">
      <div className="row between">
        <h3>Lista de espera</h3>
        <div className="row">
          {!anotando && <button className="btn btn-secondary" onClick={() => setAnotando(true)}>Anotar paciente</button>}
          <button type="button" className="btn-link" onClick={onCerrar}>Cerrar</button>
        </div>
      </div>
      <p className="muted small">
        Pacientes que quieren una cita antes o que no consiguieron cupo. Cuando se cancele una cita, la agenda le sugiere
        a quién llamar; nada se agenda ni se mueve sin que usted lo confirme.
      </p>
      {anotando && <AnotarEspera medicos={medicos} zona={zona} onCerrar={() => setAnotando(false)} />}
      {lista.error && <p className="alert alert-error">{lista.error.message}</p>}
      {lista.isLoading && <p className="muted">Cargando…</p>}
      {lista.data?.length === 0 && !anotando && <p className="muted">No hay pacientes en espera.</p>}
      <div className="resultados">
        {lista.data?.map((e) => (
          <div key={e.id} className="espacio-libre">
            <span className="stack-sm">
              <span className="row">
                <Link to={`/pacientes/${e.pacienteId}`}><strong>{e.pacienteNombre}</strong></Link>
                {!e.vigente && <span className="badge badge-warn" title="Ya pasó la fecha o la cita a adelantar ya no está abierta">Vencida</span>}
                <span className="muted small">desde el {fechaHoraCorta(e.creadaEn!, zona)}</span>
              </span>
              <Detalle e={e} zona={zona} />
            </span>
            {retirando === e.id ? (
              <span className="row confirmar">
                <span className="small">¿Sacar de la lista?</span>
                <button className="btn" disabled={retirar.isPending} onClick={() => retirar.mutate(e.id!)}>Sí, sacar</button>
                <button className="btn-link" onClick={() => setRetirando(null)}>Volver</button>
              </span>
            ) : (
              <button className="btn-link danger" onClick={() => setRetirando(e.id!)}>Sacar de la lista</button>
            )}
          </div>
        ))}
      </div>
      {retirar.error && <p className="alert alert-error">{retirar.error.message}</p>}
    </div>
  );
}

// ---------------------------------------------------------------- Anotar

type Paciente = { id: string; nombre: string };

/**
 * Anotar a un paciente. Si ya tiene una cita abierta, puede esperar para adelantarla: entonces el médico y la duración
 * son los de esa cita.
 * @param inicial desde una cita de la agenda ("Quiere antes"): el paciente y la cita ya elegidos
 */
export function AnotarEspera({ medicos, zona, inicial, onCerrar }: {
  medicos: Medico[];
  zona: string;
  inicial?: { paciente: Paciente; cita: Cita };
  onCerrar: () => void;
}) {
  const { puede } = useAuth();
  const refrescar = useRefrescarAgenda();
  const hoy = hoyEn(zona);
  const [q, setQ] = useState("");
  const [paciente, setPaciente] = useState<Paciente | null>(inicial?.paciente ?? null);
  const [registrando, setRegistrando] = useState(false);
  const [citaId, setCitaId] = useState<string>(inicial?.cita.id ?? "");
  const [medico, setMedico] = useState(medicos.length === 1 ? medicos[0].membresiaId! : CUALQUIERA);
  const [duracion, setDuracion] = useState(20);
  const [franja, setFranja] = useState<Franja>("CUALQUIERA");
  const [antesDe, setAntesDe] = useState("");
  const [motivo, setMotivo] = useState("");
  const [nota, setNota] = useState("");

  const busqueda = useQuery({
    queryKey: ["pacientes", q, "espera"],
    enabled: q.trim().length >= 2 && !paciente,
    queryFn: async () => unwrap(await api.GET("/api/v1/pacientes", { params: { query: { q, size: 8 } } })),
  });
  // Las citas abiertas del paciente: puede querer adelantar una en vez de pedir otra
  const proximas = useQuery({
    queryKey: ["proximas", paciente?.id],
    enabled: !!paciente && !inicial,
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/pacientes/{pacienteId}/proximas", { params: { path: { pacienteId: paciente!.id } } })),
  });
  const abiertas = inicial ? [inicial.cita]
    : (proximas.data ?? []).filter((c) => (c.estado === "PROGRAMADA" || c.estado === "CONFIRMADA") && Date.parse(c.inicio!) > Date.now());

  const anotar = useMutation({
    mutationFn: async () =>
      unwrap(await api.POST("/api/v1/agenda/lista-espera", {
        body: {
          pacienteId: paciente!.id,
          citaId: citaId || undefined,
          medicoId: citaId ? undefined : medico || undefined,
          duracionMinutos: citaId ? undefined : duracion,
          franja,
          antesDe: antesDe || undefined,
          motivo: citaId ? undefined : motivo || undefined,
          nota: nota || undefined,
        },
      })),
    onSuccess: () => { refrescar(); onCerrar(); },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (paciente) anotar.mutate();
  }

  if (registrando) {
    return (
      <div className="card stack">
        <h3>Lista de espera: paciente nuevo</h3>
        <PacienteForm inicial={/^\d+$/.test(q.trim()) ? { numeroDocumento: q.trim() } : {}} onCancelar={() => setRegistrando(false)}
          onGuardado={(p) => { setPaciente({ id: p.id!, nombre: `${p.nombres} ${p.apellidos}` }); setRegistrando(false); }} />
      </div>
    );
  }

  return (
    <form className="card stack" onSubmit={onSubmit}>
      <div className="row between">
        <h3>Anotar en la lista de espera</h3>
        <button type="button" className="btn-link" onClick={onCerrar}>Cerrar</button>
      </div>
      {paciente ? (
        <div className="row">
          <span>Paciente: <strong>{paciente.nombre}</strong></span>
          {!inicial && <button type="button" className="btn-link" onClick={() => { setPaciente(null); setCitaId(""); }}>Cambiar</button>}
        </div>
      ) : (
        <label>Paciente (nombre o documento)
          <input value={q} onChange={(e) => setQ(e.target.value)} autoFocus placeholder="Escriba al menos 2 letras…" />
          {!!busqueda.data?.items?.length && (
            <div className="resultados">
              {busqueda.data.items.map((p) => (
                <button type="button" key={p.id} className="opcion" onClick={() => setPaciente({ id: p.id!, nombre: `${p.nombres} ${p.apellidos}` })}>
                  <strong>{p.nombres} {p.apellidos}</strong>
                  <span className="muted small">{p.tipoDocumento} {p.numeroDocumento}{p.telefono && ` · ${p.telefono}`}</span>
                </button>
              ))}
            </div>
          )}
          {busqueda.data?.items?.length === 0 && <small className="muted">No hay pacientes con «{q.trim()}».</small>}
        </label>
      )}
      {!paciente && puede("PACIENTE_GESTIONAR") && (
        <div><button type="button" className="btn btn-secondary" onClick={() => setRegistrando(true)}>Registrar paciente nuevo</button></div>
      )}
      {paciente && (
        <div className="form-grid">
          {abiertas.length > 0 && (
            <label className="wide">¿Qué necesita?
              <select value={citaId} onChange={(e) => setCitaId(e.target.value)} disabled={!!inicial}>
                {!inicial && <option value="">Una cita nueva</option>}
                {abiertas.map((c) => (
                  <option key={c.id} value={c.id}>
                    Adelantar su cita del {fechaHoraCorta(c.inicio!, zona)} ({c.medicoNombre}, {duracionTexto((Date.parse(c.fin!) - Date.parse(c.inicio!)) / 60000)})
                  </option>
                ))}
              </select>
              {citaId && <small className="muted">Se moverá esa misma cita, con su médico y su duración.</small>}
            </label>
          )}
          {!citaId && medicos.length > 1 && (
            <label>Médico
              <select value={medico} onChange={(e) => setMedico(e.target.value)}>
                <option value={CUALQUIERA}>Cualquier médico</option>
                {medicos.map((m) => <option key={m.membresiaId} value={m.membresiaId}>{m.nombre}</option>)}
              </select>
            </label>
          )}
          {!citaId && (
            <label>Duración
              <select value={duracion} onChange={(e) => setDuracion(Number(e.target.value))}>
                {DURACIONES.map((d) => <option key={d} value={d}>{duracionTexto(d)}</option>)}
              </select>
            </label>
          )}
          <label>Puede
            <select value={franja} onChange={(e) => setFranja(e.target.value as Franja)}>
              {(Object.keys(FRANJAS) as Franja[]).map((f) => <option key={f} value={f}>{FRANJAS[f]}</option>)}
            </select>
          </label>
          <label>Antes de (opcional)<input type="date" value={antesDe} min={hoy} onChange={(e) => setAntesDe(e.target.value)} /></label>
          {!citaId && <label className="wide">Motivo de la cita (opcional)<input value={motivo} maxLength={300} onChange={(e) => setMotivo(e.target.value)} placeholder="Control, primera vez…" /></label>}
          <label className="wide">Nota (opcional)<input value={nota} maxLength={300} onChange={(e) => setNota(e.target.value)} placeholder="Solo después de las 10, llamar al celular…" /></label>
        </div>
      )}
      {anotar.error && <p className="alert alert-error">{anotar.error.message}</p>}
      <div><button className="btn" disabled={!paciente || anotar.isPending}>{anotar.isPending ? "Anotando…" : "Anotar"}</button></div>
    </form>
  );
}

// ---------------------------------------------------------------- Espacio liberado

type Pendiente = { tipo: "espera"; e: EnEspera } | { tipo: "adelantar"; c: Cita };

/**
 * A quién llamar para ocupar el espacio libre que empieza en {@code inicio}. La secretaria llama y, si el paciente
 * acepta, lo asigna aquí; si nadie acepta, lo deja libre.
 */
export function EspacioLiberado({ medicoId, inicio, zona, titulo, onListo, onCerrar }: {
  medicoId: string;
  inicio: string;
  zona: string;
  titulo: string;
  onListo: (aviso: string) => void;
  onCerrar: () => void;
}) {
  const refrescar = useRefrescarAgenda();
  const [pendiente, setPendiente] = useState<Pendiente | null>(null);
  // Aparece arriba de la agenda: si se canceló una cita de más abajo, se trae a la vista
  const caja = useRef<HTMLDivElement>(null);
  useEffect(() => { caja.current?.scrollIntoView?.({ block: "nearest", behavior: "smooth" }); }, []);
  const sug = useQuery({
    queryKey: ["sugerencias", medicoId, inicio],
    retry: false,
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/medicos/{medicoId}/sugerencias", { params: { path: { medicoId }, query: { inicio } } })),
  });
  const a = hora(inicio, zona);

  const asignar = useMutation({
    mutationFn: async (p: Pendiente) => p.tipo === "espera"
      ? unwrap(await api.POST("/api/v1/agenda/lista-espera/{id}/asignar", { params: { path: { id: p.e.id! } }, body: { medicoId, inicio } }))
      : unwrap(await api.PUT("/api/v1/agenda/citas/{citaId}/horario", { params: { path: { citaId: p.c.id! } }, body: { inicio } })),
    onSuccess: (cita) => {
      refrescar();
      onListo(`Listo: ${cita.pacienteNombre} quedó a las ${a}. La cita queda por confirmar.`);
    },
  });

  const libres = sug.data ? (Date.parse(sug.data.fin!) - Date.parse(sug.data.inicio!)) / 60000 : 0;
  const vacio = sug.data && !sug.data.espera?.length && !sug.data.adelantar?.length;

  function Confirmar({ p, texto }: { p: Pendiente; texto: string }) {
    return (
      <span className="row confirmar">
        <span className="small">¿El paciente aceptó? {texto}</span>
        <button className="btn" disabled={asignar.isPending} onClick={() => asignar.mutate(p)}>Sí, asignar</button>
        <button className="btn-link" onClick={() => setPendiente(null)}>Volver</button>
      </span>
    );
  }
  const esta = (p: Pendiente) => pendiente && (pendiente.tipo === "espera" && p.tipo === "espera" ? pendiente.e.id === p.e.id
    : pendiente.tipo === "adelantar" && p.tipo === "adelantar" && pendiente.c.id === p.c.id);

  return (
    <div className="card stack agendar" ref={caja}>
      <div className="row between">
        <h3>{titulo}</h3>
        <button type="button" className="btn-link" onClick={onCerrar}>Cerrar</button>
      </div>
      {sug.isLoading && <p className="muted">Buscando a quién ofrecerlo…</p>}
      {sug.error && <p className="alert alert-error">{sug.error.message}</p>}
      {sug.data && (
        <>
          <p className="small">
            Libre de <strong>{a}</strong> a <strong>{hora(sug.data.fin!, zona)}</strong> ({duracionTexto(libres)}). Llame al
            paciente y, si acepta, asígnele el espacio.
          </p>
          {vacio && <p className="muted">Nadie en la lista de espera puede tomar este espacio y no hay citas más tarde ese día que quepan.</p>}
          {!!sug.data.espera?.length && (
            <div className="stack-sm">
              <strong>Lista de espera</strong>
              <div className="resultados">
                {sug.data.espera.map((e) => {
                  const p: Pendiente = { tipo: "espera", e };
                  return (
                    <div key={e.id} className="espacio-libre">
                      <span className="stack-sm"><strong>{e.pacienteNombre}</strong><Detalle e={e} zona={zona} /></span>
                      {esta(p) ? <Confirmar p={p} texto={e.citaId ? `Su cita pasa a las ${a}.` : `Se le agenda a las ${a}.`} />
                        : <button className="btn btn-secondary" onClick={() => setPendiente(p)}>Asignar</button>}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
          {!!sug.data.adelantar?.length && (
            <div className="stack-sm">
              <strong>Adelantar citas de más tarde</strong>
              <span className="muted small">La última del día va primero: al moverla, el espacio libre queda al final del día.</span>
              <div className="resultados">
                {sug.data.adelantar.map((c) => {
                  const p: Pendiente = { tipo: "adelantar", c };
                  return (
                    <div key={c.id} className="espacio-libre">
                      <span className="stack-sm">
                        <strong>{c.pacienteNombre}</strong>
                        <span className="muted small">
                          Ahora está a las {hora(c.inicio!, zona)} · {duracionTexto((Date.parse(c.fin!) - Date.parse(c.inicio!)) / 60000)}
                          {c.pacienteTelefono && ` · ${c.pacienteTelefono}`}
                        </span>
                      </span>
                      {esta(p) ? <Confirmar p={p} texto={`Su cita pasa de las ${hora(c.inicio!, zona)} a las ${a}.`} />
                        : <button className="btn btn-secondary" onClick={() => setPendiente(p)}>Mover a las {a}</button>}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
          {asignar.error && <p className="alert alert-error">{asignar.error.message}</p>}
          <div><button className="btn btn-secondary" onClick={onCerrar}>Dejar libre</button></div>
        </>
      )}
    </div>
  );
}
