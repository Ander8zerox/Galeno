import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, unwrap, type Schemas } from "../api/client";
import { useModulos } from "../features/rips/modulos";

/** El administrador activa las funciones opcionales del consultorio. Desactivar no borra lo que ya se generó. */
export function ModulosPage() {
  const modulos = useModulos();
  return (
    <section className="stack">
      <div className="page-header"><h1>Módulos</h1></div>
      <p className="muted">
        Funciones opcionales del consultorio. Vienen incluidas en todos los planes: active solo las que use. Las
        desactivadas no aparecen en ninguna pantalla, y desactivar una no borra lo que ya se registró.
      </p>
      {modulos.isLoading && <p className="muted">Cargando…</p>}
      {modulos.error && <p className="alert alert-error">{modulos.error.message}</p>}
      {modulos.data?.map((m) => <ModuloCard key={m.modulo} modulo={m} />)}
    </section>
  );
}

function ModuloCard({ modulo: m }: { modulo: Schemas["ModuloVista"] }) {
  const queryClient = useQueryClient();
  const activar = useMutation({
    mutationFn: async (activo: boolean) => unwrap(await api.PUT("/api/v1/modulos/{modulo}", { params: { path: { modulo: m.modulo! } }, body: { activo } })),
    onSuccess: (lista) => queryClient.setQueryData(["modulos"], lista),
  });
  return (
    <div className="card stack-sm">
      <div className="row between">
        <h3>{m.nombre} {m.activo && <span className="badge badge-ok">Activo</span>}</h3>
        <button className={m.activo ? "btn btn-secondary" : "btn"} disabled={activar.isPending} onClick={() => activar.mutate(!m.activo)}>
          {m.activo ? "Desactivar" : "Activar"}
        </button>
      </div>
      <p className="muted small">{m.descripcion}</p>
      {m.modulo === "RIPS" && m.activo && (
        <p className="small">Siguiente paso: arme la <Link to="/precios">lista de precios por CUPS</Link>. Los médicos verán "Procedimientos realizados" en cada atención.</p>
      )}
      {activar.error && <p className="alert alert-error">{activar.error.message}</p>}
    </div>
  );
}
