import { useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api, unwrap } from "../api/client";
import { ROL_NOMBRE, useAuth } from "../auth/AuthContext";

const MIN_PASSWORD = 10;

/** El token llega en el fragmento (#) del enlace: nunca se envía al servidor en la URL. */
export function InvitacionPage() {
  const [token] = useState(() => window.location.hash.slice(1));
  const { iniciar } = useAuth();
  const navigate = useNavigate();
  const [nombre, setNombre] = useState("");
  const [password, setPassword] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [registroMedico, setRegistroMedico] = useState("");
  const [especialidad, setEspecialidad] = useState("");
  const [errorLocal, setErrorLocal] = useState<string | null>(null);

  const info = useQuery({
    queryKey: ["invitacion", token],
    enabled: !!token,
    retry: false,
    queryFn: async () => unwrap(await api.POST("/api/v1/invitaciones/consultar", { body: { token } })),
  });

  const aceptar = useMutation({
    mutationFn: async () =>
      unwrap(await api.POST("/api/v1/invitaciones/aceptar", {
        body: {
          token,
          nombre: nombre || undefined,
          password,
          registroMedico: registroMedico || undefined,
          especialidad: especialidad || undefined,
        },
      })),
    onSuccess: async (res) => {
      await iniciar(res);
      history.replaceState(null, "", "/pacientes"); // retira el token de la barra de direcciones
      navigate("/pacientes", { replace: true });
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setErrorLocal(null);
    if (!info.data?.cuentaExistente) {
      if (password.length < MIN_PASSWORD) return setErrorLocal(`La contraseña debe tener al menos ${MIN_PASSWORD} caracteres`);
      if (password !== confirmacion) return setErrorLocal("Las contraseñas no coinciden");
    }
    aceptar.mutate();
  }

  if (!token) return <Mensaje texto="El enlace de invitación está incompleto." />;
  if (info.isLoading) return <Mensaje texto="Cargando invitación…" />;
  if (info.error || !info.data) return <Mensaje texto={info.error?.message ?? "La invitación no es válida o ya venció."} />;
  const inv = info.data;

  return (
    <div className="login">
      <form className="card login-card" onSubmit={onSubmit}>
        <h1>Únase a {inv.consultorioNombre}</h1>
        <p className="muted">
          Lo invitaron como <strong>{inv.roles?.map((r) => ROL_NOMBRE[r]).join(" y ")}</strong> con el correo {inv.email}.
        </p>
        {inv.cuentaExistente ? (
          <>
            <p className="alert">Ya tiene una cuenta en Galeno. Confirme con su contraseña actual y el consultorio se agregará a su cuenta.</p>
            <label>Contraseña actual
              <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </label>
          </>
        ) : (
          <>
            <label>Nombre completo
              <input value={nombre} onChange={(e) => setNombre(e.target.value)} required autoComplete="name" />
            </label>
            <label>Contraseña (mínimo {MIN_PASSWORD} caracteres)
              <input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </label>
            <label>Confirmar contraseña
              <input type="password" autoComplete="new-password" value={confirmacion} onChange={(e) => setConfirmacion(e.target.value)} required />
            </label>
          </>
        )}
        {inv.requiereRegistroMedico && (
          <>
            <label>Registro médico (ReTHUS)
              <input value={registroMedico} onChange={(e) => setRegistroMedico(e.target.value)} required />
            </label>
            <label>Especialidad (opcional)
              <input value={especialidad} onChange={(e) => setEspecialidad(e.target.value)} />
            </label>
          </>
        )}
        {(errorLocal || aceptar.error) && <p className="alert alert-error">{errorLocal ?? aceptar.error?.message}</p>}
        <button className="btn" disabled={aceptar.isPending}>{aceptar.isPending ? "Ingresando…" : "Aceptar invitación"}</button>
      </form>
    </div>
  );
}

function Mensaje({ texto }: { texto: string }) {
  return (
    <div className="login">
      <div className="card login-card">
        <h1>Galeno</h1>
        <p>{texto}</p>
        <a href="/">Ir al inicio de sesión</a>
      </div>
    </div>
  );
}
