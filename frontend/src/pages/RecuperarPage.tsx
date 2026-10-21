import { useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { api, expectOk, unwrap } from "../api/client";

const MIN_PASSWORD = 10;

/** "¿Olvidó su contraseña?": pide el enlace. La respuesta es la misma exista o no la cuenta. */
export function RecuperarPage() {
  const [email, setEmail] = useState("");
  const enviar = useMutation({
    mutationFn: async () => expectOk(await api.POST("/api/v1/auth/recuperar", { body: { email } })),
  });

  return (
    <div className="login">
      {enviar.isSuccess ? (
        <div className="card login-card stack">
          <h1>Revise su correo</h1>
          <p>Si <strong>{email}</strong> tiene una cuenta en Galeno, le enviamos un enlace para elegir una contraseña nueva. Vence en una hora.</p>
          <p className="muted small">¿No le llegó? Revise la carpeta de spam o pida otro enlace en unos minutos.</p>
          {import.meta.env.DEV && <a href="/dev/buzon" className="btn btn-secondary">Abrir el buzón de desarrollo</a>}
          <a href="/" className="btn-link">Volver al inicio de sesión</a>
        </div>
      ) : (
        <form className="card login-card stack" onSubmit={(e: FormEvent) => { e.preventDefault(); enviar.mutate(); }}>
          <h1>¿Olvidó su contraseña?</h1>
          <p className="muted">Escriba el correo de su cuenta y le enviaremos un enlace para elegir una nueva.</p>
          <label>Correo
            <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          </label>
          {enviar.error && <p className="alert alert-error">{enviar.error.message}</p>}
          <button className="btn" disabled={enviar.isPending}>{enviar.isPending ? "Enviando…" : "Enviar enlace"}</button>
          <a href="/" className="btn-link center">Volver al inicio de sesión</a>
        </form>
      )}
    </div>
  );
}

/** El token llega en el fragmento (#) del enlace del correo: nunca se envía al servidor en la URL. */
export function RestablecerPage() {
  const [token] = useState(() => window.location.hash.slice(1));
  const [password, setPassword] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [errorLocal, setErrorLocal] = useState<string | null>(null);
  const [listo, setListo] = useState(false);

  const info = useQuery({
    queryKey: ["restablecer", token],
    enabled: !!token && !listo,
    retry: false,
    queryFn: async () => unwrap(await api.POST("/api/v1/auth/restablecer/consultar", { body: { token } })),
  });
  const cambiar = useMutation({
    mutationFn: async () => expectOk(await api.POST("/api/v1/auth/restablecer", { body: { token, password } })),
    onSuccess: () => {
      setListo(true);
      history.replaceState(null, "", "/restablecer"); // retira el token de la barra de direcciones
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setErrorLocal(null);
    if (password.length < MIN_PASSWORD) return setErrorLocal(`La contraseña debe tener al menos ${MIN_PASSWORD} caracteres`);
    if (password !== confirmacion) return setErrorLocal("Las contraseñas no coinciden");
    cambiar.mutate();
  }

  if (listo) {
    return (
      <Tarjeta>
        <h1>Contraseña cambiada</h1>
        <p>Ya puede ingresar con su contraseña nueva. Por seguridad, se cerraron las sesiones que tenía abiertas en otros equipos.</p>
        <a href="/" className="btn">Ingresar</a>
      </Tarjeta>
    );
  }
  if (!token) return <Tarjeta><p>El enlace está incompleto.</p></Tarjeta>;
  if (info.isLoading) return <Tarjeta><p>Verificando el enlace…</p></Tarjeta>;
  if (info.error || !info.data) {
    return (
      <Tarjeta>
        <p>{info.error?.message ?? "El enlace no es válido o ya venció."}</p>
        <a href="/recuperar" className="btn btn-secondary">Pedir otro enlace</a>
      </Tarjeta>
    );
  }
  return (
    <div className="login">
      <form className="card login-card stack" onSubmit={onSubmit}>
        <h1>Elija una contraseña nueva</h1>
        <p className="muted">Para la cuenta <strong>{info.data.email}</strong>.</p>
        <label>Contraseña nueva (mínimo {MIN_PASSWORD} caracteres)
          <input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required autoFocus />
        </label>
        <label>Confirmar contraseña
          <input type="password" autoComplete="new-password" value={confirmacion} onChange={(e) => setConfirmacion(e.target.value)} required />
        </label>
        {(errorLocal || cambiar.error) && <p className="alert alert-error">{errorLocal ?? cambiar.error?.message}</p>}
        <button className="btn" disabled={cambiar.isPending}>{cambiar.isPending ? "Guardando…" : "Cambiar contraseña"}</button>
      </form>
    </div>
  );
}

function Tarjeta({ children }: { children: React.ReactNode }) {
  return (
    <div className="login">
      <div className="card login-card stack">{children}</div>
    </div>
  );
}
