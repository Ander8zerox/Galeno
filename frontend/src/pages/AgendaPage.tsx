import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, unwrap } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { AgendaDia } from "../features/agenda/AgendaDia";
import { HorarioEditor } from "../features/agenda/HorarioEditor";
import { fechaCorta, fechaLarga, hoyEn, lunesDe, mesLargo, primeroDelMes, sumarDias } from "../features/agenda/tiempo";
import { AgendaMes, AgendaSemana, AgendaTodos, BuscarEspacio, type AbrirDia } from "../features/agenda/VistasAgenda";

const STORAGE_MEDICO = "galeno.agenda.medico";
const STORAGE_VISTA = "galeno.agenda.vista";
const TODOS = "__todos";

type Vista = "dia" | "semana" | "mes";

function leer(clave: string): string | null {
  try {
    return localStorage.getItem(clave);
  } catch {
    return null;
  }
}

function guardar(clave: string, valor: string) {
  try {
    localStorage.setItem(clave, valor);
  } catch {
    /* preferencia opcional */
  }
}

export function AgendaPage() {
  const { me } = useAuth();
  const zona = me?.zonaHoraria ?? "America/Bogota";
  const [fecha, setFecha] = useState(() => hoyEn(zona));
  const [medicoId, setMedicoId] = useState<string | null>(() => leer(STORAGE_MEDICO));
  // "Todos los médicos" es solo de la vista del día; el médico elegido se conserva para la semana y el mes
  const [todos, setTodos] = useState(false);
  const [vista, setVista] = useState<Vista>(() => {
    const v = leer(STORAGE_VISTA);
    return v === "semana" || v === "mes" ? v : "dia";
  });
  const [editandoHorario, setEditandoHorario] = useState(false);
  const [buscando, setBuscando] = useState(false);
  // Cupo para abrir el formulario de agendar en la vista del día; `n` vuelve a montar la vista para que lo tome
  const [abrir, setAbrir] = useState<{ inicio: string; duracion?: number; n: number } | null>(null);

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
    setEditandoHorario(false);
    if (id === TODOS) {
      setTodos(true);
      setVista("dia");
      return;
    }
    setTodos(false);
    setMedicoId(id);
    guardar(STORAGE_MEDICO, id);
  }

  function elegirVista(v: Vista) {
    setVista(v);
    setEditandoHorario(false);
    if (v !== "dia") setTodos(false);
    guardar(STORAGE_VISTA, v);
  }

  const abrirDia: AbrirDia = (dia, cupo, otroMedico) => {
    if (otroMedico) elegirMedico(otroMedico);
    setTodos(false);
    setFecha(dia);
    setVista("dia");
    setBuscando(false);
    setAbrir(cupo ? { ...cupo, n: (abrir?.n ?? 0) + 1 } : null);
  };

  function mover(paso: number) {
    setAbrir(null);
    if (vista === "dia") setFecha(sumarDias(fecha, paso));
    else if (vista === "semana") setFecha(sumarDias(fecha, 7 * paso));
    else setFecha(primeroDelMes(fecha, paso));
  }

  // Solo se usa el médico recordado si está entre los permitidos: así nunca se piden citas ajenas
  const medico = medicos.data?.find((m) => m.membresiaId === medicoId);
  const varios = (medicos.data?.length ?? 0) > 1;
  const verTodos = todos && varios && vista === "dia";

  if (medicos.isLoading) return <p className="muted">Cargando…</p>;
  if (medicos.error) return <p className="alert alert-error">{medicos.error.message}</p>;
  if (!medicos.data?.length) return <p className="muted">No hay médicos activos en este consultorio.</p>;

  const lunes = lunesDe(fecha);
  const titulo = vista === "dia" ? fechaLarga(fecha)
    : vista === "semana" ? `Semana del ${fechaCorta(lunes)} al ${fechaCorta(sumarDias(lunes, 6))}`
    : mesLargo(fecha);
  const anterior = { dia: "Día anterior", semana: "Semana anterior", mes: "Mes anterior" }[vista];
  const siguiente = { dia: "Día siguiente", semana: "Semana siguiente", mes: "Mes siguiente" }[vista];

  return (
    <section className="stack">
      <div className="page-header">
        <h1>Agenda</h1>
        <div className="row">
          {!editandoHorario && (
            <button className="btn btn-secondary" onClick={() => setBuscando((v) => !v)}>Buscar espacio libre</button>
          )}
          {medico?.puedeEditarHorario && !verTodos && (
            <button className="btn btn-secondary" onClick={() => setEditandoHorario((v) => !v)}>
              {editandoHorario ? "Volver a la agenda" : "Horario de atención"}
            </button>
          )}
        </div>
      </div>

      <div className="card row between barra-agenda">
        {varios ? (
          <label className="row">Médico
            <select value={verTodos ? TODOS : medicoId ?? ""} onChange={(e) => elegirMedico(e.target.value)}>
              {/* Siempre a la vista: al elegirla, la agenda pasa al día (las columnas por médico son de un día) */}
              <option value={TODOS}>Todos los médicos (día)</option>
              {medicos.data.map((m) => <option key={m.membresiaId} value={m.membresiaId}>{m.nombre}</option>)}
            </select>
          </label>
        ) : (
          <strong>{medico?.nombre}</strong>
        )}
        {!editandoHorario && (
          <div className="row">
            <div className="segmentos" role="group" aria-label="Vista">
              {(["dia", "semana", "mes"] as const).map((v) => (
                <button key={v} type="button" className={vista === v ? "activo" : undefined} aria-pressed={vista === v}
                  onClick={() => elegirVista(v)}>
                  {{ dia: "Día", semana: "Semana", mes: "Mes" }[v]}
                </button>
              ))}
            </div>
            <div className="row navegar-fecha">
              <button className="btn btn-secondary" aria-label={anterior} title={anterior} onClick={() => mover(-1)}>‹</button>
              <button className="btn btn-secondary" onClick={() => { setAbrir(null); setFecha(hoyEn(zona)); }}>Hoy</button>
              <button className="btn btn-secondary" aria-label={siguiente} title={siguiente} onClick={() => mover(1)}>›</button>
              <input type="date" value={fecha} onChange={(e) => { if (e.target.value) { setAbrir(null); setFecha(e.target.value); } }} />
            </div>
          </div>
        )}
      </div>

      {buscando && !editandoHorario && (
        <BuscarEspacio medicos={medicos.data} medicoId={verTodos ? null : medicoId} fecha={fecha} zona={zona}
          abrirDia={abrirDia} onCerrar={() => setBuscando(false)} />
      )}

      {editandoHorario && medico?.membresiaId ? (
        <HorarioEditor medicoId={medico.membresiaId} onGuardado={() => setEditandoHorario(false)} />
      ) : (
        <>
          <h2 className="dia-titulo">{titulo}</h2>
          {vista === "dia" && <AvisoFestivo fecha={fecha} />}
          {verTodos && <AgendaTodos medicos={medicos.data} fecha={fecha} zona={zona} abrirDia={abrirDia} />}
          {!verTodos && medico?.membresiaId && (
            <>
              {vista === "dia" && (
                <AgendaDia key={`${medico.membresiaId}-${abrir?.n ?? 0}`} medicoId={medico.membresiaId} fecha={fecha} zona={zona}
                  abrir={abrir ?? undefined} />
              )}
              {vista === "semana" && <AgendaSemana medicoId={medico.membresiaId} fecha={fecha} zona={zona} abrirDia={abrirDia} />}
              {vista === "mes" && <AgendaMes medicoId={medico.membresiaId} fecha={fecha} zona={zona} abrirDia={abrirDia} />}
            </>
          )}
        </>
      )}
    </section>
  );
}

/** Si el día es festivo en Colombia: la agenda no ofrece cupos, salvo al médico que marcó "Atiendo festivos". */
function AvisoFestivo({ fecha }: { fecha: string }) {
  const anio = fecha.slice(0, 4);
  const festivos = useQuery({
    queryKey: ["festivos", anio],
    staleTime: Infinity, // los festivos de un año no cambian
    queryFn: async () =>
      unwrap(await api.GET("/api/v1/agenda/festivos", { params: { query: { desde: `${anio}-01-01`, hasta: `${anio}-12-31` } } })),
  });
  const festivo = festivos.data?.find((f) => f.fecha === fecha);
  if (!festivo) return null;
  return (
    <p className="alert">
      <strong>Festivo: {festivo.nombre}.</strong> No hay cupos este día, salvo si el médico marcó "Atiendo festivos" en su
      horario. Para atender igual, agende con sobrecupo.
    </p>
  );
}
