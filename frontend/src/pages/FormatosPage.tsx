import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../api/client";

type FormatoCatalogo = Schemas["FormatoCatalogo"];

/** El administrador elige qué formatos del catálogo de Galenum usa el consultorio. */
export function FormatosPage() {
  const catalogo = useQuery({
    queryKey: ["formatos-consultorio"],
    queryFn: async () => unwrap(await api.GET("/api/v1/formatos/consultorio")),
  });

  return (
    <section className="stack">
      <div className="page-header"><h1>Formatos clínicos</h1></div>
      <p className="muted">
        Los formatos se suman a la atención (motivo, examen, diagnósticos y plan siguen igual). Active solo los que use
        el consultorio: los demás no aparecen. Cada profesional elige en la atención cuáles se agregan solos a las suyas.
        Desactivar un formato no borra lo que ya se diligenció.
      </p>
      {catalogo.isLoading && <p className="muted">Cargando…</p>}
      {catalogo.error && <p className="alert alert-error">{catalogo.error.message}</p>}
      {catalogo.data?.map((f) => <FormatoCard key={f.formatoId} formato={f} />)}
    </section>
  );
}

function FormatoCard({ formato: f }: { formato: FormatoCatalogo }) {
  const queryClient = useQueryClient();
  const activar = useMutation({
    mutationFn: async (activo: boolean) =>
      unwrap(await api.PUT("/api/v1/formatos/consultorio/{formatoId}", { params: { path: { formatoId: f.formatoId! } }, body: { activo } })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["formatos-consultorio"] });
      queryClient.invalidateQueries({ queryKey: ["formatos-disponibles"] });
    },
  });
  return (
    <div className="card stack-sm">
      <div className="row between">
        <h3>{f.nombre} <span className="badge">{f.especialidad}</span> {f.activo && <span className="badge badge-ok">Activo</span>}</h3>
        <button className={f.activo ? "btn btn-secondary" : "btn"} disabled={activar.isPending} onClick={() => activar.mutate(!f.activo)}>
          {f.activo ? "Desactivar" : "Activar"}
        </button>
      </div>
      {f.descripcion && <p className="muted small">{f.descripcion}</p>}
      <p className="small">Secciones: {f.secciones?.join(" · ")} <span className="muted">(versión {f.version})</span></p>
      {activar.error && <p className="alert alert-error">{activar.error.message}</p>}
    </div>
  );
}
