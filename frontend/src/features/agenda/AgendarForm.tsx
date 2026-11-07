import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, unwrap, type Schemas } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { PacienteForm } from "../pacientes/PacienteForm";
import { aIso, hora } from "./tiempo";

interface Cupo {
  inicio?: string;
  fin?: string;
}

interface Props {
  medicoId: string;
  fecha: string;
  zona: string;
  /** Cupo elegido; sin él, se agenda un sobrecupo a la hora que se indique. */
  inicio?: string;
  /** Cupos libres del día: con ellos se ofrecen citas más largas que un cupo, si los siguientes están libres. */
  cupos?: Cupo[];
  /** Duración ya elegida (por ejemplo, la de "Buscar espacio libre"). */
  duracionInicial?: number;
  onCerrar: () => void;
}

/** La API acepta citas de hasta 4 horas. */
const MAX_MINUTOS = 240;
const DURACIONES_SOBRECUPO = [10, 15, 20, 30, 40, 60, 90, 120, 180, 240];

type PacienteElegido = { id: string; nombre: string; incompleto: boolean };

export function AgendarForm({ medicoId, fecha, zona, inicio, cupos, duracionInicial, onCerrar }: Props) {
  const queryClient = useQueryClient();
  const { puede } = useAuth();
  const sobrecupo = !inicio;
  const [q, setQ] = useState("");
  const [paciente, setPaciente] = useState<PacienteElegido | null>(null);
  const [registrando, setRegistrando] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [horaManual, setHoraManual] = useState("07:00");
  const [duracionSobrecupo, setDuracionSobrecupo] = useState(20);
  const seguidos = inicio ? duracionesPosibles(inicio, cupos ?? []) : [];
  // La duración de la búsqueda puede no ser un múltiplo del cupo (30 minutos con cupos de 20): se ofrece igual, porque
  // la búsqueda ya comprobó que cabe
  const posibles = duracionInicial && seguidos.length && !seguidos.includes(duracionInicial)
    ? [...seguidos, duracionInicial].sort((a, b) => a - b) : seguidos;
  const [duracionCupo, setDuracionCupo] = useState<number | null>(duracionInicial ?? null);
  const cupo = seguidos[0];
  const duracion = sobrecupo ? duracionSobrecupo : duracionCupo ?? cupo;

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
          // En un cupo normal la duración solo se envía si es más larga que el cupo del médico
          duracionMinutos: sobrecupo || (duracion && duracion !== cupo) ? duracion : undefined,
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

  const titulo = sobrecupo ? "Agendar sobrecupo" : `Agendar a las ${hora(inicio!, zona)}`;

  // Un <form> no puede ir dentro de otro: mientras se registra el paciente, su formulario reemplaza al de la cita
  if (registrando) {
    return (
      <div className="card stack agendar">
        <div className="row between">
          <h3>{titulo}: paciente nuevo</h3>
          <button type="button" className="btn-link" onClick={onCerrar}>Cerrar</button>
        </div>
        <PacienteForm inicial={inicialDesde(q)} onCancelar={() => setRegistrando(false)}
          onGuardado={(p) => {
            setPaciente({ id: p.id!, nombre: `${p.nombres} ${p.apellidos}`, incompleto: !!p.datosIncompletos });
            setRegistrando(false);
          }} />
      </div>
    );
  }

  return (
    <form className="card stack agendar" onSubmit={onSubmit}>
      <div className="row between">
        <h3>{titulo}</h3>
        <button type="button" className="btn-link" onClick={onCerrar}>Cerrar</button>
      </div>
      {sobrecupo && (
        <div className="form-grid">
          <label>Hora<input type="time" value={horaManual} onChange={(e) => setHoraManual(e.target.value)} required /></label>
          <label>Duración
            <select value={duracionSobrecupo} onChange={(e) => setDuracionSobrecupo(Number(e.target.value))}>
              {DURACIONES_SOBRECUPO.map((d) => <option key={d} value={d}>{duracionTexto(d)}</option>)}
            </select>
          </label>
        </div>
      )}
      {!sobrecupo && posibles.length > 1 && (
        <label>Duración
          <select value={duracion} onChange={(e) => setDuracionCupo(Number(e.target.value))}>
            {posibles.map((d) => (
              <option key={d} value={d}>{duracionTexto(d)} · hasta las {hora(sumarMinutos(inicio!, d), zona)}</option>
            ))}
          </select>
          <small className="muted">
            Para un tratamiento largo, la cita ocupa varios cupos seguidos. Llega hasta donde haya otra cita o termine
            la franja del horario; para pasar de ahí, use sobrecupo.
          </small>
        </label>
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
          {busqueda.data?.items?.length === 0 && <small className="muted">No hay pacientes con «{q.trim()}».</small>}
        </label>
      )}
      {!paciente && puede("PACIENTE_GESTIONAR") && (
        <div>
          <button type="button" className="btn btn-secondary" onClick={() => setRegistrando(true)}>Registrar paciente nuevo</button>
        </div>
      )}
      <label>Motivo (opcional)<input value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Control, primera vez…" /></label>
      {agendar.error && <p className="alert alert-error">{agendar.error.message}</p>}
      <div><button className="btn" disabled={!paciente || agendar.isPending}>{agendar.isPending ? "Agendando…" : "Agendar cita"}</button></div>
    </form>
  );
}

/**
 * Duraciones que caben desde el cupo elegido: el cupo y los siguientes mientras estén libres y pegados (sin otra cita
 * ni el fin de la franja en medio), hasta 4 horas. El primer valor es la duración del cupo del médico.
 */
export function duracionesPosibles(inicio: string, cupos: Cupo[]): number[] {
  const orden = cupos.filter((c) => c.inicio && c.fin)
    .map((c) => ({ inicio: Date.parse(c.inicio!), fin: Date.parse(c.fin!) }))
    .sort((a, b) => a.inicio - b.inicio);
  const desde = Date.parse(inicio);
  let i = orden.findIndex((c) => c.inicio === desde);
  if (i < 0) return [];
  const paso = (orden[i].fin - orden[i].inicio) / 60000;
  const duraciones = [paso];
  let fin = orden[i].fin;
  for (i++; i < orden.length && orden[i].inicio === fin; i++) {
    const total = (orden[i].fin - desde) / 60000;
    if (total > MAX_MINUTOS) break;
    duraciones.push(total);
    fin = orden[i].fin;
  }
  return duraciones;
}

export function duracionTexto(minutos: number): string {
  const h = Math.floor(minutos / 60);
  const m = minutos % 60;
  if (!h) return `${m} min`;
  return m ? `${h} h ${m} min` : `${h} h`;
}

function sumarMinutos(iso: string, minutos: number): string {
  return new Date(Date.parse(iso) + minutos * 60000).toISOString();
}

/**
 * Lo que se buscó prellena el alta: un número va al documento; un texto, a nombres y apellidos (con 3 palabras, un
 * nombre y dos apellidos, como se suele buscar en Colombia). La secretaria lo corrige si no es así.
 */
function inicialDesde(q: string): Partial<Schemas["PacienteRequest"]> {
  const t = q.trim();
  if (!t) return {};
  if (/^\d+$/.test(t)) return { numeroDocumento: t };
  const palabras = t.split(/\s+/);
  const mitad = Math.max(1, Math.floor(palabras.length / 2));
  return { nombres: palabras.slice(0, mitad).join(" "), apellidos: palabras.slice(mitad).join(" ") };
}
