import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { api, unwrap } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { fechaHoraCorta } from "../agenda/tiempo";
import { BotonDescargar, SubirDocumento, TIPO_DOCUMENTO } from "./Documentos";

/** Historia clínica del paciente: atenciones y documentos, del más reciente al más antiguo. */
export function LineaHistoria({ pacienteId }: { pacienteId: string }) {
  const { me, puede } = useAuth();
  const zona = me?.zonaHoraria ?? "America/Bogota";
  const navigate = useNavigate();
  const [subiendo, setSubiendo] = useState(false);

  const eventos = useQuery({
    queryKey: ["historia", pacienteId],
    queryFn: async () => unwrap(await api.GET("/api/v1/historia/pacientes/{pacienteId}", { params: { path: { pacienteId } } })),
  });

  const nueva = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/historia/atenciones", { body: { pacienteId } })),
    onSuccess: (a) => navigate(`/pacientes/${pacienteId}/atenciones/${a.id}`),
  });

  return (
    <div className="stack">
      {puede("HISTORIA_REGISTRAR") && (
        <div className="row">
          <button className="btn" disabled={nueva.isPending} onClick={() => nueva.mutate()}>Nueva atención</button>
          <button className="btn btn-secondary" onClick={() => setSubiendo((v) => !v)}>{subiendo ? "Cerrar" : "Subir documento"}</button>
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
          <div key={e.id} className="evento evento-documento">
            <span className="evento-fecha">{fechaHoraCorta(e.fecha!, zona)}</span>
            <div className="grow">
              <div className="row"><strong>{e.titulo}</strong><span className="badge">{TIPO_DOCUMENTO[e.estado as keyof typeof TIPO_DOCUMENTO] ?? "Documento"}</span></div>
              <div className="muted small">{e.detalle}</div>
            </div>
            <BotonDescargar id={e.id!} nombre={e.detalle ?? "documento"} />
          </div>
        ))}
      </div>
    </div>
  );
}
