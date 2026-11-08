import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { api, unwrap } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { fechaHoraCorta, hora } from "../agenda/tiempo";
import { AnularDocumento, BotonArchivo, BotonDescargar, MarcaAnulado, SubirDocumento, TIPO_DOCUMENTO } from "./Documentos";

/** Historia clínica del paciente: atenciones y documentos, del más reciente al más antiguo. */
export function LineaHistoria({ pacienteId }: { pacienteId: string }) {
  const { me, puede } = useAuth();
  const zona = me?.zonaHoraria ?? "America/Bogota";
  const navigate = useNavigate();
  const [subiendo, setSubiendo] = useState(false);
  const [eligiendo, setEligiendo] = useState(false);
  const queryClient = useQueryClient();

  const eventos = useQuery({
    queryKey: ["historia", pacienteId],
    queryFn: async () => unwrap(await api.GET("/api/v1/historia/pacientes/{pacienteId}", { params: { path: { pacienteId } } })),
  });

  // Antes de una atención nueva: si el paciente tiene cita hoy con este médico, se atiende esa cita (si no, quedaría
  // abierta y podría terminar como "No asistió"); si no, se ofrece registrarla en la agenda como cita de ahora
  const citasHoy = useQuery({
    queryKey: ["citas-de-hoy", pacienteId],
    enabled: eligiendo,
    staleTime: 0,
    queryFn: async () => unwrap(await api.GET("/api/v1/historia/pacientes/{pacienteId}/citas-de-hoy", { params: { path: { pacienteId } } })),
  });
  const nueva = useMutation({
    mutationFn: async (op: { citaId?: string; registrarEnAgenda?: boolean }) =>
      unwrap(await api.POST("/api/v1/historia/atenciones", { body: { pacienteId, ...op } })),
    onSuccess: (a) => {
      queryClient.invalidateQueries({ queryKey: ["citas"] });
      queryClient.invalidateQueries({ queryKey: ["disponibilidad"] });
      navigate(`/pacientes/${pacienteId}/atenciones/${a.id}`);
    },
  });
  const citas = citasHoy.data ?? [];

  return (
    <div className="stack">
      <div className="row">
        {puede("HISTORIA_REGISTRAR") && (
          <>
            <button className="btn" disabled={nueva.isPending} onClick={() => setEligiendo((v) => !v)}>Nueva atención</button>
            <button className="btn btn-secondary" onClick={() => setSubiendo((v) => !v)}>{subiendo ? "Cerrar" : "Subir documento"}</button>
          </>
        )}
        <span className="spacer" />
        {/* Copia para el paciente o para un traslado: solo lo firmado, con notas y la lista de documentos */}
        <BotonArchivo ruta={`/api/v1/historia/pacientes/${pacienteId}/pdf`} nombre="historia-clinica.pdf" className="btn btn-secondary">
          Descargar historia completa (PDF)
        </BotonArchivo>
      </div>
      {eligiendo && (
        <div className="card stack-sm">
          {citasHoy.isLoading && <p className="muted">Revisando la agenda de hoy…</p>}
          {citasHoy.error && <p className="alert alert-error">{citasHoy.error.message}</p>}
          {citasHoy.data && (citas.length > 0 ? (
            <>
              <p>
                El paciente tiene cita hoy con usted a las{" "}
                <strong>{citas.map((c) => hora(c.inicio!, zona)).join(" y ")}</strong>. Atiéndala para que la cita quede
                atendida en la agenda.
              </p>
              <div className="row">
                {citas.map((c) => (
                  <button key={c.id} className="btn" disabled={nueva.isPending} onClick={() => nueva.mutate({ citaId: c.id })}>
                    Atender la cita de las {hora(c.inicio!, zona)}
                  </button>
                ))}
                <button className="btn-link" disabled={nueva.isPending} onClick={() => nueva.mutate({ registrarEnAgenda: true })}
                  title="Se registra en la agenda como otra cita de ahora">Es otra consulta</button>
                <button className="btn-link" onClick={() => setEligiendo(false)}>Cancelar</button>
              </div>
            </>
          ) : (
            <>
              <p>
                El paciente no tiene cita hoy con usted. Recomendado: registrarla en su agenda como cita de ahora
                (sobrecupo), para que ese tiempo se vea ocupado.
              </p>
              <div className="row">
                <button className="btn" disabled={nueva.isPending} onClick={() => nueva.mutate({ registrarEnAgenda: true })}>
                  Registrar en la agenda y atender
                </button>
                <button className="btn-link" disabled={nueva.isPending} onClick={() => nueva.mutate({})}
                  title="Por ejemplo, para registrar una consulta de otro día o fuera del consultorio">Atender sin registrarla</button>
                <button className="btn-link" onClick={() => setEligiendo(false)}>Cancelar</button>
              </div>
            </>
          ))}
        </div>
      )}
      {nueva.error && <p className="alert alert-error">{nueva.error.message}</p>}
      {subiendo && <div className="card"><SubirDocumento pacienteId={pacienteId} /></div>}

      {eventos.isLoading && <p className="muted">Cargando…</p>}
      {eventos.error && <p className="alert alert-error">{eventos.error.message}</p>}
      {eventos.data?.length === 0 && <p className="muted">Este paciente aún no tiene atenciones ni documentos.</p>}

      <div className="timeline">
        {eventos.data?.map((e) => e.tipo === "ATENCION" ? (
          <Link key={e.id} to={`/pacientes/${pacienteId}/atenciones/${e.id}`} className={`evento ${e.estado === "BORRADOR" ? "evento-borrador" : ""}`}>
            <span className="evento-fecha">{fechaHoraCorta(e.fecha!, zona)}</span>
            <div className="grow">
              <div className="row">
                <strong>{e.titulo}</strong>
                {e.estado === "BORRADOR" && <span className="badge badge-warn">Borrador</span>}
                {e.estado === "IMPORTADA" && <span className="badge">Importada</span>}
              </div>
              <div className="muted small">{[e.detalle, e.autor].filter(Boolean).join(" · ")}</div>
            </div>
          </Link>
        ) : (
          <div key={e.id} className={`evento evento-documento ${e.anulacion ? "documento-anulado" : ""}`}>
            <span className="evento-fecha">{fechaHoraCorta(e.fecha!, zona)}</span>
            <div className="grow stack-sm">
              <div className="row"><strong>{e.titulo}</strong><span className="badge">{TIPO_DOCUMENTO[e.estado as keyof typeof TIPO_DOCUMENTO] ?? "Documento"}</span></div>
              <div className="muted small">{e.detalle}</div>
              <MarcaAnulado motivo={e.anulacion} />
              {!e.anulacion && puede("HISTORIA_REGISTRAR") && <AnularDocumento id={e.id!} pacienteId={pacienteId} />}
            </div>
            <BotonDescargar id={e.id!} nombre={e.detalle ?? "documento"} />
          </div>
        ))}
      </div>
    </div>
  );
}
