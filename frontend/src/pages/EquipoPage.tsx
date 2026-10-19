import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, expectOk, unwrap, type Schemas } from "../api/client";
import { ROL_NOMBRE, ROLES, useAuth, type Rol } from "../auth/AuthContext";

type Miembro = Schemas["Miembro"];

const fecha = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium" });

export function EquipoPage() {
  const equipo = useQuery({
    queryKey: ["equipo"],
    queryFn: async () => unwrap(await api.GET("/api/v1/equipo")),
  });
  const medicos = (equipo.data ?? []).filter((m) => m.roles?.includes("MEDICO") && m.estado === "ACTIVA");

  return (
    <section className="stack">
      <div className="page-header"><h1>Equipo del consultorio</h1></div>
      <Invitar />
      <Pendientes />
      <div className="stack">
        <h3>Miembros</h3>
        {equipo.isLoading && <p className="muted">Cargando…</p>}
        {equipo.error && <p className="alert alert-error">{equipo.error.message}</p>}
        {equipo.data?.map((m) => <MiembroCard key={m.membresiaId} miembro={m} medicos={medicos} />)}
      </div>
    </section>
  );
}

function RolesCheckboxes({ value, onChange }: { value: Rol[]; onChange: (roles: Rol[]) => void }) {
  return (
    <div className="row">
      {ROLES.map((r) => (
        <label key={r} className="check">
          <input type="checkbox" checked={value.includes(r)}
            onChange={(e) => onChange(e.target.checked ? [...value, r] : value.filter((x) => x !== r))} />
          {ROL_NOMBRE[r]}
        </label>
      ))}
    </div>
  );
}

function Invitar() {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const [roles, setRoles] = useState<Rol[]>(["MEDICO"]);
  const [registroMedico, setRegistroMedico] = useState("");
  const [copiado, setCopiado] = useState(false);
  const invitar = useMutation({
    mutationFn: async () =>
      unwrap(await api.POST("/api/v1/equipo/invitaciones", {
        body: { email, roles, registroMedico: roles.includes("MEDICO") && registroMedico ? registroMedico : undefined },
      })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["invitaciones"] });
      setEmail("");
      setRegistroMedico("");
      setCopiado(false);
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    invitar.mutate();
  }

  async function copiar(enlace: string) {
    try {
      await navigator.clipboard.writeText(enlace);
      setCopiado(true);
    } catch {
      setCopiado(false);
    }
  }

  return (
    <form className="card stack" onSubmit={onSubmit}>
      <h3>Invitar a alguien</h3>
      <div className="form-grid">
        <label>Correo<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
        {roles.includes("MEDICO") && (
          <label>Registro médico (opcional: lo puede ingresar al aceptar)
            <input value={registroMedico} onChange={(e) => setRegistroMedico(e.target.value)} />
          </label>
        )}
      </div>
      <RolesCheckboxes value={roles} onChange={setRoles} />
      {invitar.error && <p className="alert alert-error">{invitar.error.message}</p>}
      <div><button className="btn" disabled={!roles.length || invitar.isPending}>Crear invitación</button></div>
      {invitar.data && (
        <div className="alert stack">
          <span>Envíe este enlace a <strong>{invitar.data.email}</strong> (vence el {fecha.format(new Date(invitar.data.expiraEn!))}):</span>
          <div className="row">
            <input readOnly value={invitar.data.enlace} className="grow" onFocus={(e) => e.target.select()} />
            <button type="button" className="btn btn-secondary" onClick={() => copiar(invitar.data.enlace!)}>{copiado ? "Copiado" : "Copiar"}</button>
          </div>
        </div>
      )}
    </form>
  );
}

function Pendientes() {
  const queryClient = useQueryClient();
  const pendientes = useQuery({
    queryKey: ["invitaciones"],
    queryFn: async () => unwrap(await api.GET("/api/v1/equipo/invitaciones")),
  });
  const revocar = useMutation({
    mutationFn: async (id: string) =>
      expectOk(await api.DELETE("/api/v1/equipo/invitaciones/{id}", { params: { path: { id } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["invitaciones"] }),
  });

  if (!pendientes.data?.length) return null;
  return (
    <div className="card table-wrap">
      <table>
        <thead><tr><th>Invitación pendiente</th><th>Roles</th><th>Vence</th><th /></tr></thead>
        <tbody>
          {pendientes.data.map((i) => (
            <tr key={i.id}>
              <td>{i.email}</td>
              <td>{i.roles?.map((r) => ROL_NOMBRE[r]).join(", ")}</td>
              <td>{fecha.format(new Date(i.expiraEn!))}</td>
              <td><button className="btn-link danger" onClick={() => revocar.mutate(i.id!)}>Revocar</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function MiembroCard({ miembro, medicos }: { miembro: Miembro; medicos: Miembro[] }) {
  const { me } = useAuth();
  const queryClient = useQueryClient();
  const [roles, setRoles] = useState<Rol[]>(miembro.roles ?? []);
  const [registroMedico, setRegistroMedico] = useState("");
  const [asignados, setAsignados] = useState<string[]>(miembro.medicosAsignados ?? []);
  const soyYo = miembro.membresiaId === me?.membresiaId;
  const activo = miembro.estado === "ACTIVA";
  const rolesCambiaron = roles.slice().sort().join() !== (miembro.roles ?? []).slice().sort().join();
  const asignacionCambio = asignados.slice().sort().join() !== (miembro.medicosAsignados ?? []).slice().sort().join();
  const pideRegistro = roles.includes("MEDICO") && !miembro.registroMedico;

  const refrescar = () => queryClient.invalidateQueries({ queryKey: ["equipo"] });
  const actualizar = useMutation({
    mutationFn: async (estado: "ACTIVA" | "SUSPENDIDA") =>
      expectOk(await api.PUT("/api/v1/equipo/{membresiaId}", {
        params: { path: { membresiaId: miembro.membresiaId! } },
        body: { roles, estado, registroMedico: pideRegistro ? registroMedico : undefined },
      })),
    onSuccess: refrescar,
  });
  const asignar = useMutation({
    mutationFn: async () =>
      expectOk(await api.PUT("/api/v1/equipo/{membresiaId}/medicos", {
        params: { path: { membresiaId: miembro.membresiaId! } },
        body: { medicos: asignados },
      })),
    onSuccess: refrescar,
  });
  const error = actualizar.error ?? asignar.error;

  return (
    <div className={`card stack ${activo ? "" : "suspendido"}`}>
      <div className="row between">
        <div>
          <strong>{miembro.nombre}</strong> {soyYo && <span className="badge">Usted</span>}
          {!activo && <span className="badge badge-danger">Suspendido</span>}
          <div className="muted small">{miembro.email}{miembro.registroMedico && ` · Registro ${miembro.registroMedico}`}</div>
        </div>
        {!soyYo && (
          <button className="btn-link danger" onClick={() => actualizar.mutate(activo ? "SUSPENDIDA" : "ACTIVA")}>
            {activo ? "Suspender" : "Reactivar"}
          </button>
        )}
      </div>
      <RolesCheckboxes value={roles} onChange={setRoles} />
      {pideRegistro && rolesCambiaron && (
        <label>Registro médico<input value={registroMedico} onChange={(e) => setRegistroMedico(e.target.value)} /></label>
      )}
      {rolesCambiaron && (
        <div><button className="btn" disabled={!roles.length || actualizar.isPending} onClick={() => actualizar.mutate(activo ? "ACTIVA" : "SUSPENDIDA")}>Guardar roles</button></div>
      )}
      {miembro.roles?.includes("SECRETARIO") && (
        <div className="stack">
          <span className="small muted">Médicos que atiende (agenda)</span>
          <div className="row">
            {medicos.map((m) => (
              <label key={m.membresiaId} className="check">
                <input type="checkbox" checked={asignados.includes(m.membresiaId!)}
                  onChange={(e) => setAsignados(e.target.checked ? [...asignados, m.membresiaId!] : asignados.filter((x) => x !== m.membresiaId))} />
                {m.nombre}
              </label>
            ))}
            {!medicos.length && <span className="muted small">No hay médicos activos.</span>}
          </div>
          {asignacionCambio && <div><button className="btn btn-secondary" disabled={asignar.isPending} onClick={() => asignar.mutate()}>Guardar asignación</button></div>}
        </div>
      )}
      {error && <p className="alert alert-error">{error.message}</p>}
    </div>
  );
}
