import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";
import { duracionTexto } from "./AgendarForm";

type Tipo = Schemas["TipoCitaVista"];
type Clase = NonNullable<Tipo["clase"]>;

export const CLASE_NOMBRE: Record<Clase, string> = { PRIMERA_VEZ: "Primera vez", CONTROL: "Control", OTRA: "Otra" };
const DURACIONES = [10, 15, 20, 30, 40, 45, 60, 90, 120];

/** Tipos de cita del consultorio (el servidor crea Primera vez y Control la primera vez que se piden). */
export function useTiposCita() {
  return useQuery({
    queryKey: ["tipos-cita"],
    queryFn: async () => unwrap(await api.GET("/api/v1/agenda/tipos-cita")),
    staleTime: 5 * 60_000,
  });
}

/** Administración de los tipos de cita (solo el administrador). */
export function TiposCita({ onCerrar }: { onCerrar: () => void }) {
  const tipos = useTiposCita();
  const [editando, setEditando] = useState<Tipo | "nuevo" | null>(null);
  return (
    <div className="card stack">
      <div className="row between">
        <h3>Tipos de cita</h3>
        <button className="btn-link" onClick={onCerrar}>Cerrar</button>
      </div>
      <p className="small muted">
        Cada tipo tiene su duración. La clase decide el CUPS de consulta que se sugiere en la atención (8902xx primera vez,
        8903xx control) y la finalidad del RIPS; si fija un CUPS, se usa ese.
      </p>
      {tipos.error && <p className="alert alert-error">{tipos.error.message}</p>}
      <table className="tabla">
        <thead><tr><th>Nombre</th><th>Duración</th><th>Clase</th><th>CUPS</th><th></th></tr></thead>
        <tbody>
          {(tipos.data ?? []).map((t) => (
            <tr key={t.id} className={t.activo ? undefined : "muted"}>
              <td>{t.nombre}{!t.activo && " (desactivado)"}</td>
              <td>{duracionTexto(t.duracionMinutos!)}</td>
              <td>{CLASE_NOMBRE[t.clase!]}</td>
              <td>{t.cups ?? <span className="muted">Según la clase</span>}</td>
              <td><button className="btn-link" onClick={() => setEditando(t)}>Editar</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      {editando
        ? <TipoForm key={editando === "nuevo" ? "nuevo" : editando.id} tipo={editando === "nuevo" ? undefined : editando} onListo={() => setEditando(null)} />
        : <div><button className="btn btn-secondary" onClick={() => setEditando("nuevo")}>Nuevo tipo de cita</button></div>}
    </div>
  );
}

function TipoForm({ tipo, onListo }: { tipo?: Tipo; onListo: () => void }) {
  const queryClient = useQueryClient();
  const [nombre, setNombre] = useState(tipo?.nombre ?? "");
  const [duracion, setDuracion] = useState(tipo?.duracionMinutos ?? 20);
  const [clase, setClase] = useState<Clase>(tipo?.clase ?? "CONTROL");
  const [cups, setCups] = useState(tipo?.cups ?? "");
  const [activo, setActivo] = useState(tipo?.activo ?? true);
  const guardar = useMutation({
    mutationFn: async () => {
      const body = { nombre: nombre.trim(), duracionMinutos: duracion, clase, cups: cups.trim() || undefined, activo };
      return tipo
        ? unwrap(await api.PUT("/api/v1/agenda/tipos-cita/{id}", { params: { path: { id: tipo.id! } }, body }))
        : unwrap(await api.POST("/api/v1/agenda/tipos-cita", { body }));
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tipos-cita"] });
      onListo();
    },
  });
  function onSubmit(e: FormEvent) {
    e.preventDefault();
    guardar.mutate();
  }
  return (
    <form className="form-grid" onSubmit={onSubmit}>
      <label>Nombre<input value={nombre} onChange={(e) => setNombre(e.target.value)} maxLength={80} required placeholder="Control posoperatorio" /></label>
      <label>Duración
        <select value={duracion} onChange={(e) => setDuracion(Number(e.target.value))}>
          {[...new Set([...DURACIONES, duracion])].sort((a, b) => a - b).map((d) => <option key={d} value={d}>{duracionTexto(d)}</option>)}
        </select>
      </label>
      <label>Clase
        <select value={clase} onChange={(e) => setClase(e.target.value as Clase)}>
          {(Object.keys(CLASE_NOMBRE) as Clase[]).map((c) => <option key={c} value={c}>{CLASE_NOMBRE[c]}</option>)}
        </select>
      </label>
      <label>CUPS (opcional)
        <input value={cups} onChange={(e) => setCups(e.target.value)} inputMode="numeric" pattern="[0-9]{6}" maxLength={6}
          title="6 dígitos" placeholder="Según la clase" />
      </label>
      {tipo && <label className="check"><input type="checkbox" checked={activo} onChange={(e) => setActivo(e.target.checked)} /> Activo (se ofrece al agendar)</label>}
      {guardar.error && <p className="alert alert-error wide">{guardar.error.message}</p>}
      <div className="wide row">
        <button className="btn" disabled={!nombre.trim() || guardar.isPending}>{tipo ? "Guardar" : "Crear tipo"}</button>
        <button type="button" className="btn-link" onClick={onListo}>Cancelar</button>
      </div>
    </form>
  );
}
