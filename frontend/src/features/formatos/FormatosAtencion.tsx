import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, unwrap, type Schemas } from "../../api/client";
import { FormatoEditor } from "./FormatoEditor";
import { vacio, type Definicion, type Respuestas } from "./motor";

type Atencion = Schemas["AtencionVista"];
export type Diligenciado = Schemas["FormatoDiligenciado"];

/**
 * Formatos del borrador. Si el consultorio no tiene formatos activos (y la atención no trae ninguno), no se dibuja
 * nada: la atención se ve como siempre.
 */
export function FormatosAtencion({ atencionId, pacienteId, formatos, respuestas, onRespuestas, onCambio }: {
  atencionId: string;
  pacienteId: string;
  formatos: Diligenciado[];
  respuestas: Record<string, Respuestas>;
  onRespuestas: (id: string, r: Respuestas) => void;
  /** Tras agregar o quitar uno: la atención como quedó (con su versión nueva). */
  onCambio: (a: Atencion) => void;
}) {
  const queryClient = useQueryClient();
  const [elegido, setElegido] = useState("");
  const [quitando, setQuitando] = useState<string | null>(null);
  const disponibles = useQuery({
    queryKey: ["formatos-disponibles"],
    queryFn: async () => unwrap(await api.GET("/api/v1/formatos/disponibles")),
  });
  const agregar = useMutation({
    mutationFn: async (formatoId: string) =>
      unwrap(await api.POST("/api/v1/historia/atenciones/{id}/formatos", { params: { path: { id: atencionId } }, body: { formatoId } })),
    onSuccess: (a) => { setElegido(""); onCambio(a); },
  });
  const quitar = useMutation({
    mutationFn: async (formatoAtencionId: string) =>
      unwrap(await api.DELETE("/api/v1/historia/atenciones/{id}/formatos/{formatoAtencionId}", { params: { path: { id: atencionId, formatoAtencionId } } })),
    onSuccess: (a) => { setQuitando(null); onCambio(a); },
  });
  const porDefecto = useMutation({
    mutationFn: async ({ formatoId, valor }: { formatoId: string; valor: boolean }) =>
      unwrap(await api.PUT("/api/v1/formatos/disponibles/{formatoId}/por-defecto", { params: { path: { formatoId } }, body: { porDefecto: valor } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["formatos-disponibles"] }),
  });

  const lista = disponibles.data ?? [];
  if (!lista.length && !formatos.length) return null;
  const restantes = lista.filter((d) => !formatos.some((f) => f.formatoId === d.formatoId));
  const error = agregar.error ?? quitar.error ?? porDefecto.error;

  return (
    <>
      {formatos.map((f) => {
        const disp = lista.find((d) => d.formatoId === f.formatoId);
        const r = respuestas[f.id!] ?? {};
        return (
          <div key={f.id} className="card stack">
            <div className="row between">
              <h3>{f.nombre} <span className="muted small">v{f.version}</span></h3>
              <div className="row">
                {disp && (
                  <label className="check small" title="Se agrega solo a sus atenciones nuevas">
                    <input type="checkbox" checked={!!disp.porDefecto} disabled={porDefecto.isPending}
                      onChange={(e) => porDefecto.mutate({ formatoId: f.formatoId!, valor: e.target.checked })} />
                    Agregar siempre en mis atenciones
                  </label>
                )}
                {quitando === f.id ? (
                  <span className="row confirmar small">
                    ¿Quitarlo y borrar lo diligenciado?
                    <button className="btn-link danger" disabled={quitar.isPending} onClick={() => quitar.mutate(f.id!)}>Sí, quitar</button>
                    <button className="btn-link" onClick={() => setQuitando(null)}>No</button>
                  </span>
                ) : (
                  <button className="btn-link danger" onClick={() => (vacio(r) ? quitar.mutate(f.id!) : setQuitando(f.id!))}>Quitar</button>
                )}
              </div>
            </div>
            {f.arrastradoDe && (
              <p className="muted small">Parte del registrado en la <Link to={`/pacientes/${pacienteId}/atenciones/${f.arrastradoDe}`} target="_blank">última atención firmada</Link>: actualice lo que cambió.</p>
            )}
            <FormatoEditor definicion={f.definicion as unknown as Definicion} value={r} onChange={(nuevas) => onRespuestas(f.id!, nuevas)} />
          </div>
        );
      })}
      {!!restantes.length && (
        <div className="card row">
          <select aria-label="Formato" value={elegido} onChange={(e) => setElegido(e.target.value)}>
            <option value="">Agregar un formato…</option>
            {restantes.map((d) => <option key={d.formatoId} value={d.formatoId}>{d.nombre}</option>)}
          </select>
          <button className="btn btn-secondary" disabled={!elegido || agregar.isPending} onClick={() => agregar.mutate(elegido)}>Agregar</button>
        </div>
      )}
      {error && <p className="alert alert-error">{error.message}</p>}
    </>
  );
}
