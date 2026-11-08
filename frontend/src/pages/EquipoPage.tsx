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
      <DatosConsultorio />
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

/** Contacto que ven los pacientes en los correos de sus citas, y si se envían. */
function DatosConsultorio() {
  const queryClient = useQueryClient();
  const datos = useQuery({
    queryKey: ["consultorio-datos"],
    queryFn: async () => unwrap(await api.GET("/api/v1/equipo/consultorio")),
  });
  if (datos.isLoading) return <p className="muted">Cargando…</p>;
  if (datos.error) return <p className="alert alert-error">{datos.error.message}</p>;
  return <DatosConsultorioForm key={JSON.stringify(datos.data)} inicial={datos.data!}
    onGuardado={(d) => queryClient.setQueryData(["consultorio-datos"], d)} />;
}

function DatosConsultorioForm({ inicial, onGuardado }: {
  inicial: Schemas["DatosConsultorio"];
  onGuardado: (d: Schemas["DatosConsultorio"]) => void;
}) {
  const [form, setForm] = useState({
    direccion: inicial.direccion ?? "", telefono: inicial.telefono ?? "", correo: inicial.correo ?? "",
    correosPacientes: !!inicial.correosPacientes,
  });
  const [guardado, setGuardado] = useState(false);
  const guardar = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/equipo/consultorio", { body: form })),
    onSuccess: (d) => { setGuardado(true); onGuardado(d); },
  });
  const set = (campo: "direccion" | "telefono" | "correo") => (e: { target: { value: string } }) => {
    setGuardado(false);
    setForm({ ...form, [campo]: e.target.value });
  };

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    guardar.mutate();
  }

  return (
    <form className="card stack" onSubmit={onSubmit}>
      <h3>Datos del consultorio: {inicial.nombre}</h3>
      <p className="muted small">Los ven sus pacientes en los correos de sus citas, para saber a dónde ir y cómo avisar si no pueden asistir.</p>
      <div className="form-grid">
        <label className="wide">Dirección<input value={form.direccion} maxLength={200} onChange={set("direccion")} placeholder="Cra 10 # 20-30, consultorio 201, Bogotá" /></label>
        <label>Teléfono<input value={form.telefono} maxLength={40} onChange={set("telefono")} placeholder="601 555 0000" /></label>
        <label>Correo para respuestas<input type="email" value={form.correo} maxLength={190} onChange={set("correo")} placeholder="citas@suconsultorio.com" /></label>
      </div>
      <label className="check">
        <input type="checkbox" checked={form.correosPacientes}
          onChange={(e) => { setGuardado(false); setForm({ ...form, correosPacientes: e.target.checked }); }} />
        Enviar correos a los pacientes sobre sus citas: al agendarla, si cambia o se cancela, y un recordatorio 24 horas antes
      </label>
      {form.correosPacientes && !form.telefono && !form.correo && (
        <p className="alert">Sin teléfono ni correo, el paciente no sabrá cómo avisar si no puede asistir.</p>
      )}
      {guardar.error && <p className="alert alert-error">{guardar.error.message}</p>}
      {guardado && <p className="alert alert-ok">Datos guardados.</p>}
      <div><button className="btn" disabled={guardar.isPending}>{guardar.isPending ? "Guardando…" : "Guardar"}</button></div>
    </form>
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
  const invitar = useMutation({
    mutationFn: async () =>
      unwrap(await api.POST("/api/v1/equipo/invitaciones", {
        body: { email, roles, registroMedico: roles.includes("MEDICO") && registroMedico ? registroMedico : undefined },
      })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["invitaciones"] });
      setEmail("");
      setRegistroMedico("");
    },
  });
  // Si después se reenvía o se revoca, este enlace deja de servir: no se sigue mostrando
  const pendientes = useQuery({
    queryKey: ["invitaciones"],
    queryFn: async () => unwrap(await api.GET("/api/v1/equipo/invitaciones")),
  });
  const creada = invitar.data && pendientes.data?.some((i) => i.id === invitar.data.id) ? invitar.data : null;

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    invitar.mutate();
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
      {creada && (
        <div className="alert stack">
          <span>
            Le enviamos la invitación a <strong>{creada.email}</strong> (vence el {fecha.format(new Date(creada.expiraEn!))}).
            Si no le llega, revise la carpeta de spam o compártale este enlace:
          </span>
          <EnlaceCopiable key={creada.id} enlace={creada.enlace!} />
        </div>
      )}
    </form>
  );
}

function EnlaceCopiable({ enlace }: { enlace: string }) {
  const [copiado, setCopiado] = useState(false);

  async function copiar() {
    try {
      await navigator.clipboard.writeText(enlace);
      setCopiado(true);
    } catch {
      setCopiado(false);
    }
  }

  return (
    <div className="row">
      <input readOnly value={enlace} className="grow" onFocus={(e) => e.target.select()} />
      <button type="button" className="btn btn-secondary" onClick={copiar}>{copiado ? "Copiado" : "Copiar"}</button>
    </div>
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
  // Crea un enlace nuevo y anula el anterior (el servidor solo guarda el hash del token)
  const reenviar = useMutation({
    mutationFn: async (id: string) =>
      unwrap(await api.POST("/api/v1/equipo/invitaciones/{id}/reenviar", { params: { path: { id } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["invitaciones"] }),
  });

  if (!pendientes.data?.length) return null;
  return (
    <div className="card table-wrap stack">
      <table>
        <thead><tr><th>Invitación pendiente</th><th>Roles</th><th>Vence</th><th /></tr></thead>
        <tbody>
          {pendientes.data.map((i) => (
            <tr key={i.id}>
              <td>{i.email}</td>
              <td>{i.roles?.map((r) => ROL_NOMBRE[r]).join(", ")}</td>
              <td>{fecha.format(new Date(i.expiraEn!))}</td>
              <td>
                <button className="btn-link" disabled={reenviar.isPending} onClick={() => reenviar.mutate(i.id!)}>Reenviar</button>
                {" · "}
                <button className="btn-link danger" onClick={() => revocar.mutate(i.id!)}>Revocar</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {reenviar.data && pendientes.data.some((i) => i.id === reenviar.data.id) && (
        <div className="alert stack">
          <span>
            Le reenviamos la invitación a <strong>{reenviar.data.email}</strong> con un enlace nuevo; el anterior ya no
            sirve. Si prefiere, compártale este:
          </span>
          <EnlaceCopiable key={reenviar.data.id} enlace={reenviar.data.enlace!} />
        </div>
      )}
      {reenviar.error && <p className="alert alert-error">{reenviar.error.message}</p>}
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
