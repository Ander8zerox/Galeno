import { useState, type FormEvent } from "react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";
import { duracionTexto } from "./AgendarForm";
import { fechaCorta, fechaEn, fechaHoraCorta, hora, hoyEn, lunesDe, primeroDelMes, sumarDias } from "./tiempo";

type Cita = Schemas["CitaVista"];
type Medico = Schemas["MedicoAgenda"];

/** Abrir la vista del día en una fecha, con el formulario de agendar ya abierto si se eligió un cupo. */
export type AbrirDia = (fecha: string, abrir?: { inicio: string; duracion?: number }, medicoId?: string) => void;

const activa = (c: Cita) => c.estado !== "CANCELADA";
const DIAS_CORTOS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];

function useCitasYLibres(medicoId: string, desde: string, hasta: string) {
  const citas = useQuery({
    queryKey: ["citas", medicoId, desde, hasta],
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/medicos/{medicoId}/citas", { params: { path: { medicoId }, query: { desde, hasta } } })),
  });
  const dias = useQuery({
    queryKey: ["disponibilidad", medicoId, "dias", desde, hasta],
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/medicos/{medicoId}/disponibilidad/dias", { params: { path: { medicoId }, query: { desde, hasta } } })),
  });
  return { citas, dias, cargando: citas.isLoading || dias.isLoading, error: citas.error ?? dias.error };
}

/** Las citas de cada día ("2026-11-09" → citas), en la zona del consultorio. */
function porDia(citas: Cita[] | undefined, zona: string): Map<string, Cita[]> {
  const mapa = new Map<string, Cita[]>();
  for (const c of citas ?? []) {
    const f = fechaEn(c.inicio!, zona);
    mapa.set(f, [...(mapa.get(f) ?? []), c]);
  }
  return mapa;
}

// ---------------------------------------------------------------- Semana

export function AgendaSemana({ medicoId, fecha, zona, abrirDia }: { medicoId: string; fecha: string; zona: string; abrirDia: AbrirDia }) {
  const desde = lunesDe(fecha);
  const hasta = sumarDias(desde, 6);
  const { citas, dias, cargando, error } = useCitasYLibres(medicoId, desde, hasta);
  const citasDia = porDia(citas.data?.filter(activa), zona);
  const hoy = hoyEn(zona);

  if (error) return <p className="alert alert-error">{error.message}</p>;
  if (cargando) return <p className="muted">Cargando…</p>;
  return (
    <div className="semana">
      {(dias.data ?? []).map((d) => {
        const lista = citasDia.get(d.fecha!) ?? [];
        const libres = d.libres ?? [];
        return (
          <div key={d.fecha} className={`semana-dia${d.fecha === hoy ? " hoy" : ""}`}>
            <button type="button" className="semana-titulo" onClick={() => abrirDia(d.fecha!)} title="Ver el día">
              <strong>{fechaCorta(d.fecha!)}</strong>
              <span className="muted small">{libres.length} libre(s)</span>
            </button>
            {d.festivo && <span className="badge badge-warn">Festivo</span>}
            {lista.map((c) => (
              <button type="button" key={c.id} className={`mini-cita cita-${c.estado!.toLowerCase()}`} onClick={() => abrirDia(d.fecha!)}>
                <span className="slot-hora">{hora(c.inicio!, zona)}–{hora(c.fin!, zona)}</span>
                <span className="mini-nombre">{c.pacienteNombre}</span>
              </button>
            ))}
            {libres.length > 0 && (
              <div className="chips">
                {libres.map((l) => (
                  <button type="button" key={l.inicio} className="chip" title="Agendar" onClick={() => abrirDia(d.fecha!, { inicio: l.inicio! })}>
                    {hora(l.inicio!, zona)}
                  </button>
                ))}
              </div>
            )}
            {!lista.length && !libres.length && !d.festivo && <span className="muted small">Sin cupos</span>}
          </div>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------- Mes

export function AgendaMes({ medicoId, fecha, zona, abrirDia }: { medicoId: string; fecha: string; zona: string; abrirDia: AbrirDia }) {
  const primero = primeroDelMes(fecha);
  const mes = primero.slice(0, 7);
  const desde = lunesDe(primero);
  // Hasta 6 semanas: las que tocan el mes (el máximo que acepta la API)
  const ultimo = sumarDias(primeroDelMes(fecha, 1), -1);
  const hasta = sumarDias(lunesDe(ultimo), 6);
  const { citas, dias, cargando, error } = useCitasYLibres(medicoId, desde, hasta);
  const citasDia = porDia(citas.data?.filter(activa), zona);
  const hoy = hoyEn(zona);

  if (error) return <p className="alert alert-error">{error.message}</p>;
  if (cargando) return <p className="muted">Cargando…</p>;
  return (
    <div className="mes">
      {DIAS_CORTOS.map((d) => <div key={d} className="mes-cabecera">{d}</div>)}
      {(dias.data ?? []).map((d) => {
        const libres = d.libres?.length ?? 0;
        const n = citasDia.get(d.fecha!)?.length ?? 0;
        const otroMes = !d.fecha!.startsWith(mes);
        return (
          <button type="button" key={d.fecha} onClick={() => abrirDia(d.fecha!)}
            className={`mes-dia${otroMes ? " otro-mes" : ""}${d.fecha === hoy ? " hoy" : ""}${libres ? " con-libres" : ""}`}>
            <span className="mes-numero">{Number(d.fecha!.slice(8))}</span>
            {d.festivo && <span className="mes-festivo" title={d.festivo}>Festivo</span>}
            {libres > 0 && <span className="mes-libres" title={`${libres} cupos libres`}>{libres}<span className="mes-texto"> libre{libres === 1 ? "" : "s"}</span></span>}
            {n > 0 && <span className="muted small" title={`${n} citas`}>{n}<span className="mes-texto"> cita{n === 1 ? "" : "s"}</span></span>}
          </button>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------- Todos los médicos (un día)

export function AgendaTodos({ medicos, fecha, zona, abrirDia }: { medicos: Medico[]; fecha: string; zona: string; abrirDia: AbrirDia }) {
  const consultas = useQueries({
    queries: medicos.flatMap((m) => {
      const medicoId = m.membresiaId!;
      return [
        {
          queryKey: ["citas", medicoId, fecha],
          queryFn: async () => unwrap(await api.GET("/api/v1/agenda/medicos/{medicoId}/citas", { params: { path: { medicoId }, query: { desde: fecha, hasta: fecha } } })),
        },
        {
          queryKey: ["disponibilidad", medicoId, fecha],
          queryFn: async () => unwrap(await api.GET("/api/v1/agenda/medicos/{medicoId}/disponibilidad", { params: { path: { medicoId }, query: { fecha } } })),
        },
      ];
    }),
  });
  const error = consultas.find((c) => c.error)?.error;
  if (error) return <p className="alert alert-error">{error.message}</p>;

  return (
    <div className="todos">
      {medicos.map((m, i) => {
        const citas = (consultas[i * 2].data as Cita[] | undefined)?.filter(activa) ?? [];
        const libres = (consultas[i * 2 + 1].data as Schemas["Intervalo"][] | undefined) ?? [];
        const cargando = consultas[i * 2].isLoading || consultas[i * 2 + 1].isLoading;
        const medicoId = m.membresiaId!;
        return (
          <div key={medicoId} className="semana-dia">
            <button type="button" className="semana-titulo" onClick={() => abrirDia(fecha, undefined, medicoId)} title="Ver su agenda del día">
              <strong>{m.nombre}</strong>
              <span className="muted small">{cargando ? "…" : `${citas.length} cita(s) · ${libres.length} libre(s)`}</span>
            </button>
            {citas.map((c) => (
              <button type="button" key={c.id} className={`mini-cita cita-${c.estado!.toLowerCase()}`} onClick={() => abrirDia(fecha, undefined, medicoId)}>
                <span className="slot-hora">{hora(c.inicio!, zona)}–{hora(c.fin!, zona)}</span>
                <span className="mini-nombre">{c.pacienteNombre}</span>
              </button>
            ))}
            {libres.length > 0 && (
              <div className="chips">
                {libres.map((l) => (
                  <button type="button" key={l.inicio} className="chip" title={`Agendar con ${m.nombre}`}
                    onClick={() => abrirDia(fecha, { inicio: l.inicio! }, medicoId)}>
                    {hora(l.inicio!, zona)}
                  </button>
                ))}
              </div>
            )}
            {!cargando && !citas.length && !libres.length && <span className="muted small">Sin cupos este día</span>}
          </div>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------- Buscar el próximo espacio libre

const DURACIONES = [20, 30, 40, 60, 90, 120, 150, 180, 240];
const CUALQUIERA = "";

export function BuscarEspacio({ medicos, medicoId, fecha, zona, abrirDia, onCerrar }: {
  medicos: Medico[];
  /** El médico que se está viendo, o null si se ven todos. */
  medicoId: string | null;
  fecha: string;
  zona: string;
  abrirDia: AbrirDia;
  onCerrar: () => void;
}) {
  const hoy = hoyEn(zona);
  const [minutos, setMinutos] = useState(60);
  const [medico, setMedico] = useState(medicos.length > 1 && !medicoId ? CUALQUIERA : medicoId ?? medicos[0]?.membresiaId ?? CUALQUIERA);
  const [desde, setDesde] = useState(fecha < hoy ? hoy : fecha);
  const [buscar, setBuscar] = useState<{ minutos: number; medico: string; desde: string } | null>(null);

  const espacios = useQuery({
    queryKey: ["espacios", buscar],
    enabled: !!buscar,
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/espacios", {
      params: { query: { minutos: buscar!.minutos, medicoId: buscar!.medico || undefined, desde: buscar!.desde } },
    })),
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBuscar({ minutos, medico, desde });
  }

  return (
    <div className="card stack agendar">
      <div className="row between">
        <h3>Buscar espacio libre</h3>
        <button type="button" className="btn-link" onClick={onCerrar}>Cerrar</button>
      </div>
      <form className="form-grid" onSubmit={onSubmit}>
        <label>Duración
          <select value={minutos} onChange={(e) => setMinutos(Number(e.target.value))}>
            {DURACIONES.map((d) => <option key={d} value={d}>{duracionTexto(d)}</option>)}
          </select>
        </label>
        {medicos.length > 1 && (
          <label>Médico
            <select value={medico} onChange={(e) => setMedico(e.target.value)}>
              <option value={CUALQUIERA}>Cualquier médico</option>
              {medicos.map((m) => <option key={m.membresiaId} value={m.membresiaId}>{m.nombre}</option>)}
            </select>
          </label>
        )}
        <label>Desde<input type="date" value={desde} min={hoy} onChange={(e) => e.target.value && setDesde(e.target.value)} /></label>
        <div className="wide"><button className="btn">Buscar</button></div>
      </form>
      {espacios.error && <p className="alert alert-error">{espacios.error.message}</p>}
      {espacios.isFetching && <p className="muted">Buscando…</p>}
      {espacios.data && !espacios.isFetching && (
        espacios.data.length === 0 ? (
          <p className="muted">No hay {duracionTexto(buscar!.minutos)} seguidos libres en los próximos 60 días. Pruebe una duración menor u otro médico.</p>
        ) : (
          <div className="resultados">
            {espacios.data.map((e) => (
              <div key={`${e.medicoId}-${e.inicio}`} className="espacio-libre">
                <span>
                  <strong>{fechaHoraCorta(e.inicio!, zona)}</strong>–{hora(e.fin!, zona)}
                  {(medicos.length > 1) && <span className="muted"> · {e.medicoNombre}</span>}
                </span>
                <button type="button" className="btn btn-secondary"
                  onClick={() => abrirDia(fechaEn(e.inicio!, zona), { inicio: e.inicio!, duracion: buscar!.minutos }, e.medicoId!)}>
                  Agendar
                </button>
              </div>
            ))}
          </div>
        )
      )}
    </div>
  );
}
