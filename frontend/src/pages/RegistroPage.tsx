import { useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api, ApiError, unwrap, type Schemas } from "../api/client";
import { useAuth } from "../auth/AuthContext";

type Solicitud = Schemas["SolicitudRegistro"];

const MIN_PASSWORD = 10;

const VACIA: Solicitud = {
  consultorioNombre: "", nit: "", nombre: "", email: "", password: "", esMedico: true, registroMedico: "",
  especialidad: "", aceptaTerminos: false, codigoPartner: "",
};

const CLAVE_REF = "galeno.ref";
const DIAS_REF = 60;

/** El código del enlace de un partner (?ref=) se recuerda 60 días, por si la persona vuelve después. */
function codigoReferido(): string {
  const deEnlace = new URLSearchParams(window.location.search).get("ref");
  try {
    if (deEnlace) {
      localStorage.setItem(CLAVE_REF, JSON.stringify({ codigo: deEnlace.toUpperCase(), hasta: Date.now() + DIAS_REF * 86_400_000 }));
      return deEnlace.toUpperCase();
    }
    const guardado = JSON.parse(localStorage.getItem(CLAVE_REF) ?? "null") as { codigo: string; hasta: number } | null;
    return guardado && guardado.hasta > Date.now() ? guardado.codigo : "";
  } catch {
    return deEnlace?.toUpperCase() ?? "";
  }
}

/** Registro público de un consultorio nuevo. Nada se crea hasta que la persona abre el enlace de su correo. */
export function RegistroPage() {
  const [form, setForm] = useState<Solicitud>(() => ({ ...VACIA, codigoPartner: codigoReferido() }));
  const codigo = (form.codigoPartner ?? "").trim();
  const partner = useQuery({
    queryKey: ["codigo-partner", codigo.toUpperCase()],
    enabled: codigo.length >= 4,
    retry: false,
    queryFn: async () => unwrap(await api.GET("/api/v1/partners/codigos/{codigo}", { params: { path: { codigo } } })),
  });
  const [confirmacion, setConfirmacion] = useState("");
  const [errorLocal, setErrorLocal] = useState<string | null>(null);
  const set = (campo: keyof Solicitud) => (e: { target: { value: string } }) => setForm({ ...form, [campo]: e.target.value });

  const enviar = useMutation({
    mutationFn: async (body: Solicitud) => unwrap(await api.POST("/api/v1/registro", { body })),
  });
  const errores = enviar.error instanceof ApiError ? enviar.error.errores : {};

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setErrorLocal(null);
    if (form.password.length < MIN_PASSWORD) return setErrorLocal(`La contraseña debe tener al menos ${MIN_PASSWORD} caracteres`);
    if (form.password !== confirmacion) return setErrorLocal("Las contraseñas no coinciden");
    if (!form.aceptaTerminos) return setErrorLocal("Debe aceptar los términos y la política de tratamiento de datos");
    enviar.mutate({
      ...form,
      nit: form.nit || undefined,
      registroMedico: form.esMedico ? form.registroMedico : undefined,
      especialidad: form.esMedico && form.especialidad ? form.especialidad : undefined,
      codigoPartner: codigo || undefined,
    });
  }

  if (enviar.isSuccess) {
    return (
      <div className="login">
        <div className="card login-card stack">
          <h1>Revise su correo</h1>
          <p>
            Enviamos un enlace a <strong>{enviar.data.email}</strong> para activar <strong>{form.consultorioNombre}</strong>.
            Vence en 24 horas.
          </p>
          <p className="muted small">¿No le llegó? Revise la carpeta de spam o vuelva a registrarse en unos minutos.</p>
          {import.meta.env.DEV && <a href="/dev/buzon" className="btn btn-secondary">Abrir el buzón de desarrollo</a>}
          <a href="/" className="btn-link">Volver al inicio de sesión</a>
        </div>
      </div>
    );
  }

  return (
    <div className="login">
      <form className="card login-card ancho stack" onSubmit={onSubmit}>
        <h1>Registre su consultorio</h1>
        <p className="muted">Cree la cuenta de su consultorio en Galeno. Usted quedará como administrador y podrá invitar a su equipo.</p>

        <h3>Consultorio</h3>
        <Campo label="Nombre del consultorio" error={errores.consultorioNombre}>
          <input value={form.consultorioNombre} onChange={set("consultorioNombre")} required maxLength={200} placeholder="Consultorio Dra. Pérez" />
        </Campo>
        <Campo label="NIT (opcional)" error={errores.nit}>
          <input value={form.nit} onChange={set("nit")} maxLength={30} placeholder="900123456-7" />
        </Campo>

        <h3>Su cuenta</h3>
        <Campo label="Nombre completo" error={errores.nombre}>
          <input value={form.nombre} onChange={set("nombre")} required autoComplete="name" maxLength={200} />
        </Campo>
        <Campo label="Correo" error={errores.email}>
          <input type="email" value={form.email} onChange={set("email")} required autoComplete="email" maxLength={190} />
        </Campo>
        <Campo label={`Contraseña (mínimo ${MIN_PASSWORD} caracteres)`} error={errores.password}>
          <input type="password" value={form.password} onChange={set("password")} required autoComplete="new-password" />
        </Campo>
        <label>Confirmar contraseña
          <input type="password" value={confirmacion} onChange={(e) => setConfirmacion(e.target.value)} required autoComplete="new-password" />
        </label>

        <label className="check">
          <input type="checkbox" checked={form.esMedico} onChange={(e) => setForm({ ...form, esMedico: e.target.checked })} />
          Soy médico y voy a atender pacientes
        </label>
        {form.esMedico && (
          <>
            <Campo label="Registro médico (ReTHUS)" error={errores.registroMedico}>
              <input value={form.registroMedico} onChange={set("registroMedico")} required maxLength={50} />
            </Campo>
            <Campo label="Especialidad (opcional)" error={errores.especialidad}>
              <input value={form.especialidad} onChange={set("especialidad")} maxLength={120} placeholder="Medicina general" />
            </Campo>
          </>
        )}

        <Campo label="Código de partner (opcional)" error={errores.codigoPartner}>
          <input value={form.codigoPartner} onChange={(e) => setForm({ ...form, codigoPartner: e.target.value.toUpperCase() })} maxLength={20} placeholder="Si alguien le recomendó Galeno" />
          {partner.data && <small className="badge badge-ok">Recomendado por {partner.data.partner}: {partner.data.descuentoPorcentaje} % de descuento en su suscripción</small>}
          {partner.isError && codigo.length >= 4 && <small className="field-error">Ese código no existe o no está activo</small>}
        </Campo>

        <label className="check">
          <input type="checkbox" checked={form.aceptaTerminos} onChange={(e) => setForm({ ...form, aceptaTerminos: e.target.checked })} />
          <span>
            Acepto los términos de uso y la <a href="/politica-datos" target="_blank" rel="noreferrer">política de tratamiento de datos personales</a>
          </span>
        </label>

        {(errorLocal || enviar.error) && <p className="alert alert-error">{errorLocal ?? enviar.error?.message}</p>}
        <button className="btn" disabled={enviar.isPending}>{enviar.isPending ? "Enviando…" : "Registrar consultorio"}</button>
        <a href="/" className="btn-link center">Ya tengo cuenta: iniciar sesión</a>
      </form>
    </div>
  );
}

/** El token llega en el fragmento (#) del enlace del correo: nunca se envía al servidor en la URL. */
export function ConfirmarRegistroPage() {
  const [token] = useState(() => window.location.hash.slice(1));
  const { iniciar } = useAuth();
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  // Al iniciar sesión se refrescan las consultas: sin esto se volvería a consultar un enlace ya usado
  const [activado, setActivado] = useState(false);

  const info = useQuery({
    queryKey: ["registro", token],
    enabled: !!token && !activado,
    retry: false,
    queryFn: async () => unwrap(await api.POST("/api/v1/registro/consultar", { body: { token } })),
  });

  const confirmar = useMutation({
    mutationFn: async () =>
      unwrap(await api.POST("/api/v1/registro/confirmar", { body: { token, password: password || undefined } })),
    onSuccess: async (res) => {
      setActivado(true);
      await iniciar(res);
      history.replaceState(null, "", "/equipo"); // retira el token de la barra de direcciones
      navigate("/equipo", { replace: true });
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    confirmar.mutate();
  }

  if (!token) return <Mensaje texto="El enlace de activación está incompleto." />;
  if (info.isLoading) return <Mensaje texto="Verificando el enlace…" />;
  if (info.error || !info.data) return <Mensaje texto={info.error?.message ?? "El enlace no es válido o ya venció."} enlace />;
  const r = info.data;

  return (
    <div className="login">
      <form className="card login-card stack" onSubmit={onSubmit}>
        <h1>Active {r.consultorioNombre}</h1>
        <p className="muted">Su correo <strong>{r.email}</strong> quedó verificado.</p>
        {r.cuentaExistente && (
          <>
            <p className="alert">
              Ya tiene una cuenta en Galeno con este correo. Confirme con su contraseña actual y el consultorio nuevo
              se agregará a su cuenta (su contraseña no cambia).
            </p>
            <label>Contraseña actual
              <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </label>
          </>
        )}
        {confirmar.error && <p className="alert alert-error">{confirmar.error.message}</p>}
        <button className="btn" disabled={confirmar.isPending}>{confirmar.isPending ? "Activando…" : "Activar consultorio"}</button>
      </form>
    </div>
  );
}

/**
 * Borrador de la política: falta revisarla con un abogado antes de abrir el registro al público
 * (Ley 1581 de 2012, Decreto 1377 de 2013 y Res. 1995 de 1999 sobre la historia clínica).
 */
export function PoliticaDatosPage() {
  return (
    <div className="login">
      <div className="card muy-ancho stack">
        <h1>Política de tratamiento de datos personales</h1>
        <p className="alert">Borrador en revisión legal · versión 2026-10.</p>
        <p>
          Galeno es una herramienta para que consultorios médicos registren a sus pacientes, su agenda y su historia
          clínica. Cada consultorio es el <strong>responsable</strong> del tratamiento de los datos de sus pacientes;
          Galeno actúa como <strong>encargado</strong> y solo los trata para prestar el servicio.
        </p>
        <h3>Qué datos tratamos</h3>
        <p>
          De las personas que usan Galeno: nombre, correo, contraseña (cifrada) y, para médicos, el registro profesional.
          De los pacientes: los datos de identificación, contacto y la información clínica que el consultorio registra,
          que son datos sensibles.
        </p>
        <h3>Para qué</h3>
        <p>
          Prestar el servicio (agenda, historia clínica, fórmulas, reportes), mantener la seguridad y la auditoría de
          accesos, y cumplir las obligaciones legales de conservación de la historia clínica. Las funciones de
          inteligencia artificial reciben solo el contexto clínico necesario, sin nombre ni documento del paciente.
        </p>
        <h3>Dónde y cómo</h3>
        <p>
          Los datos se almacenan cifrados en servidores de Amazon Web Services. El acceso está limitado por consultorio
          y por rol, y cada consulta a una historia queda registrada.
        </p>
        <h3>Derechos</h3>
        <p>
          Los titulares pueden conocer, actualizar, rectificar y solicitar la supresión de sus datos (cuando la ley lo
          permita) y revocar la autorización, a través de su consultorio o escribiendo a Galeno.
        </p>
        <a href="/registro" className="btn-link">Volver al registro</a>
      </div>
    </div>
  );
}

/** Solo en desarrollo: los correos que la API "envió" (para abrir el enlace de activación sin servidor de correo). */
export function BuzonDevPage() {
  const buzon = useQuery({
    queryKey: ["buzon"],
    refetchInterval: 3000,
    queryFn: async () => unwrap(await api.GET("/api/v1/dev/buzon")),
  });
  return (
    <div className="login">
      <div className="card muy-ancho stack">
        <h1>Buzón de desarrollo</h1>
        <p className="muted small">Los correos no salen del servidor local: aquí se ven los últimos 50.</p>
        {buzon.error && <p className="alert alert-error">{buzon.error.message}</p>}
        {buzon.data?.length === 0 && <p className="muted">No hay correos.</p>}
        {buzon.data?.map((c, i) => (
          <div key={i} className="card stack">
            <div className="row"><strong>{c.asunto}</strong><span className="spacer" /><span className="muted small">{c.para}</span></div>
            <pre className="texto-libre">
              {c.texto?.split(/(https?:\/\/\S+)/).map((parte, j) =>
                /^https?:\/\//.test(parte) ? <a key={j} href={parte}>{parte}</a> : parte)}
            </pre>
          </div>
        ))}
        <a href="/" className="btn-link">Ir al inicio de sesión</a>
      </div>
    </div>
  );
}

function Mensaje({ texto, enlace }: { texto: string; enlace?: boolean }) {
  return (
    <div className="login">
      <div className="card login-card stack">
        <h1>Galeno</h1>
        <p>{texto}</p>
        {enlace && <a href="/registro" className="btn btn-secondary">Registrarme de nuevo</a>}
      </div>
    </div>
  );
}

function Campo({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <label>
      {label}
      {children}
      {error && <small className="field-error">{error}</small>}
    </label>
  );
}
