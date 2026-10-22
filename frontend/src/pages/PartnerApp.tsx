import { useState, type FormEvent } from "react";
import { QueryClient, QueryClientProvider, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import createClient, { type Middleware } from "openapi-fetch";
import { expectOk, unwrap, type Schemas } from "../api/client";
import type { paths } from "../api/schema";
import { pesos } from "./SuscripcionPage";
import { Wallet } from "lucide-react";
import { Shell } from "../components/Shell";

/**
 * Portal de los partners (quienes venden Galeno): inscripción, su código, sus referidos y sus comisiones.
 * Tiene su propio token, guardado aparte de la sesión de un consultorio y de la consola de plataforma.
 */
const CLAVE = "galeno.partner";
const MIN_PASSWORD = 10;
const fecha = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium" });

type Sesion = Schemas["SesionPartner"];
type Comision = Schemas["ComisionVista"];

function leerSesion(): Sesion | null {
  try {
    const s = JSON.parse(sessionStorage.getItem(CLAVE) ?? "null") as Sesion | null;
    return s && new Date(s.expiresAt!) > new Date() ? s : null;
  } catch {
    return null;
  }
}

let token: string | null = leerSesion()?.accessToken ?? null;
let alSalir: () => void = () => {};
const auth: Middleware = {
  onRequest({ request }) {
    if (token) request.headers.set("Authorization", `Bearer ${token}`);
    return request;
  },
  onResponse({ response, request }) {
    if (response.status === 401 && !request.url.includes("/partners/login") && !request.url.includes("/inscripcion")) alSalir();
    return response;
  },
};
const api = createClient<paths>({ baseUrl: "/" });
api.use(auth);
const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

export const ESTADO_COMISION: Record<NonNullable<Comision["estado"]>, [string, string]> = {
  RETENIDA: ["Retenida", "badge-info"],
  DISPONIBLE: ["Disponible", "badge-ok"],
  PAGADA: ["Pagada", ""],
  ANULADA: ["Anulada", "badge-danger"],
};

export const ESTADO_PARTNER: Record<NonNullable<Schemas["PartnerVista"]["estado"]>, [string, string]> = {
  PENDIENTE: ["En revisión", "badge-warn"],
  ACTIVO: ["Activo", "badge-ok"],
  SUSPENDIDO: ["Suspendido", "badge-danger"],
  RECHAZADO: ["Rechazado", "badge-danger"],
};

export function PartnerApp() {
  const [sesion, setSesion] = useState<Sesion | null>(leerSesion);
  alSalir = () => {
    sessionStorage.removeItem(CLAVE);
    token = null;
    queryClient.clear();
    setSesion(null);
  };
  const entrar = (s: Sesion) => {
    sessionStorage.setItem(CLAVE, JSON.stringify(s));
    token = s.accessToken!;
    setSesion(s);
  };
  const ruta = window.location.pathname;

  let contenido;
  if (ruta === "/partner/condiciones") contenido = <Condiciones />;
  else if (ruta === "/partner/inscripcion") contenido = <Inscripcion />;
  else if (ruta === "/partner/confirmar") contenido = <Confirmar onEntrar={(s) => { entrar(s); history.replaceState(null, "", "/partner"); }} />;
  else if (!sesion) contenido = <Login onEntrar={entrar} />;
  else contenido = <Panel sesion={sesion} onSalir={alSalir} />;
  return <QueryClientProvider client={queryClient}>{contenido}</QueryClientProvider>;
}

function Login({ onEntrar }: { onEntrar: (s: Sesion) => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const entrar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/partners/login", { body: { email, password } })),
    onSuccess: onEntrar,
  });
  return (
    <div className="login">
      <form className="card login-card stack" onSubmit={(e: FormEvent) => { e.preventDefault(); entrar.mutate(); }}>
        <h1>Partners de Galeno</h1>
        <p className="muted">Recomiende Galeno a consultorios y reciba una comisión mensual mientras sigan usándolo.</p>
        <label>Correo<input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
        <label>Contraseña<input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
        {entrar.error && <p className="alert alert-error">{entrar.error.message}</p>}
        <button className="btn" disabled={entrar.isPending}>{entrar.isPending ? "Ingresando…" : "Ingresar"}</button>
        <a href="/partner/inscripcion" className="btn btn-secondary">Quiero ser partner</a>
        <a href="/recuperar" className="btn-link small center">¿Olvidó su contraseña?</a>
      </form>
    </div>
  );
}

function Inscripcion() {
  const [f, setF] = useState({
    email: "", password: "", confirmacion: "", nombreCuenta: "", tipoPersona: "NATURAL" as "NATURAL" | "JURIDICA", nombre: "",
    tipoDocumento: "CC", numeroDocumento: "", telefono: "", banco: "", tipoCuenta: "AHORROS", numeroCuenta: "", aceptaTerminos: false,
  });
  const [errorLocal, setErrorLocal] = useState<string | null>(null);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const enviar = useMutation({
    mutationFn: async () => {
      const { confirmacion: _c, ...body } = f;
      expectOk(await api.POST("/api/v1/partners/inscripcion", {
        body: { ...body, nombre: f.tipoPersona === "NATURAL" ? (f.nombre || f.nombreCuenta) : f.nombre, banco: f.banco || undefined, numeroCuenta: f.numeroCuenta || undefined },
      }));
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setErrorLocal(null);
    if (f.password.length < MIN_PASSWORD) return setErrorLocal(`La contraseña debe tener al menos ${MIN_PASSWORD} caracteres`);
    if (f.password !== f.confirmacion) return setErrorLocal("Las contraseñas no coinciden");
    if (!f.aceptaTerminos) return setErrorLocal("Debe aceptar las condiciones del programa");
    enviar.mutate();
  }

  if (enviar.isSuccess) {
    return (
      <div className="login">
        <div className="card login-card stack">
          <h1>Revise su correo</h1>
          <p>Enviamos un enlace a <strong>{f.email}</strong> para confirmar su inscripción. Después la revisaremos y le avisaremos cuando quede aprobada.</p>
          {import.meta.env.DEV && <a href="/dev/buzon" className="btn btn-secondary">Abrir el buzón de desarrollo</a>}
        </div>
      </div>
    );
  }
  return (
    <div className="login">
      <form className="card login-card ancho stack" onSubmit={onSubmit}>
        <h1>Inscríbase como partner</h1>
        <p className="muted">Recomiende Galeno con su código: el consultorio recibe un descuento y usted una comisión por cada pago mientras siga usándolo.</p>
        <h3>Su cuenta</h3>
        <label>Su nombre<input value={f.nombreCuenta} onChange={set("nombreCuenta")} required maxLength={200} autoComplete="name" /></label>
        <label>Correo<input type="email" value={f.email} onChange={set("email")} required maxLength={190} autoComplete="email" /></label>
        <label>Contraseña (mínimo {MIN_PASSWORD} caracteres)<input type="password" value={f.password} onChange={set("password")} required autoComplete="new-password" /></label>
        <label>Confirmar contraseña<input type="password" value={f.confirmacion} onChange={set("confirmacion")} required autoComplete="new-password" /></label>
        <h3>A quién le pagamos</h3>
        <label>Persona
          <select value={f.tipoPersona} onChange={(e) => setF({ ...f, tipoPersona: e.target.value as "NATURAL" | "JURIDICA", tipoDocumento: e.target.value === "JURIDICA" ? "NIT" : "CC" })}>
            <option value="NATURAL">Natural</option><option value="JURIDICA">Jurídica (empresa)</option>
          </select>
        </label>
        {f.tipoPersona === "JURIDICA" && <label>Razón social<input value={f.nombre} onChange={set("nombre")} required maxLength={200} /></label>}
        <div className="row">
          <label>Documento
            <select value={f.tipoDocumento} onChange={set("tipoDocumento")}>
              {(f.tipoPersona === "JURIDICA" ? ["NIT"] : ["CC", "CE", "PA", "PT"]).map((t) => <option key={t}>{t}</option>)}
            </select>
          </label>
          <label className="grow">Número<input value={f.numeroDocumento} onChange={set("numeroDocumento")} required maxLength={30} /></label>
        </div>
        <label>Teléfono<input value={f.telefono} onChange={set("telefono")} required maxLength={30} /></label>
        <div className="row">
          <label className="grow">Banco (opcional)<input value={f.banco} onChange={set("banco")} maxLength={80} /></label>
          <label>Cuenta<select value={f.tipoCuenta} onChange={set("tipoCuenta")}><option value="AHORROS">Ahorros</option><option value="CORRIENTE">Corriente</option></select></label>
        </div>
        <label>Número de cuenta (opcional; la necesitamos para pagarle)<input value={f.numeroCuenta} onChange={set("numeroCuenta")} maxLength={40} /></label>
        <label className="check">
          <input type="checkbox" checked={f.aceptaTerminos} onChange={(e) => setF({ ...f, aceptaTerminos: e.target.checked })} />
          <span>Acepto las <a href="/partner/condiciones" target="_blank" rel="noreferrer">condiciones del programa de partners</a></span>
        </label>
        {(errorLocal || enviar.error) && <p className="alert alert-error">{errorLocal ?? enviar.error?.message}</p>}
        <button className="btn" disabled={enviar.isPending}>{enviar.isPending ? "Enviando…" : "Inscribirme"}</button>
        <a href="/partner" className="btn-link center">Ya soy partner: ingresar</a>
      </form>
    </div>
  );
}

/** El token llega en el fragmento (#) del enlace del correo: nunca se envía al servidor en la URL. */
function Confirmar({ onEntrar }: { onEntrar: (s: Sesion) => void }) {
  const [tokenEnlace] = useState(() => window.location.hash.slice(1));
  const [password, setPassword] = useState("");
  const [listo, setListo] = useState(false);
  const info = useQuery({
    queryKey: ["inscripcion", tokenEnlace],
    enabled: !!tokenEnlace && !listo,
    queryFn: async () => unwrap(await api.POST("/api/v1/partners/inscripcion/consultar", { body: { token: tokenEnlace } })),
  });
  const confirmar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/partners/inscripcion/confirmar", { body: { token: tokenEnlace, password: password || undefined } })),
    onSuccess: (s) => { setListo(true); onEntrar(s); window.location.assign("/partner"); },
  });
  if (!tokenEnlace) return <Mensaje texto="El enlace está incompleto." />;
  if (info.isLoading) return <Mensaje texto="Verificando el enlace…" />;
  if (info.error || !info.data) return <Mensaje texto={info.error?.message ?? "El enlace no es válido o ya venció."} />;
  return (
    <div className="login">
      <form className="card login-card stack" onSubmit={(e: FormEvent) => { e.preventDefault(); confirmar.mutate(); }}>
        <h1>Confirme su inscripción</h1>
        <p className="muted">{info.data.nombre} · {info.data.email}</p>
        {info.data.cuentaExistente && (
          <>
            <p className="alert">Ya tiene una cuenta en Galeno con este correo. Confirme con su contraseña actual.</p>
            <label>Contraseña actual<input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
          </>
        )}
        {confirmar.error && <p className="alert alert-error">{confirmar.error.message}</p>}
        <button className="btn" disabled={confirmar.isPending}>{confirmar.isPending ? "Confirmando…" : "Confirmar inscripción"}</button>
      </form>
    </div>
  );
}

function Panel({ sesion, onSalir }: { sesion: Sesion; onSalir: () => void }) {
  const panel = useQuery({ queryKey: ["partner", "yo"], queryFn: async () => unwrap(await api.GET("/api/v1/partners/yo")) });
  const [copiado, setCopiado] = useState(false);
  if (panel.isLoading) return <Mensaje texto="Cargando…" />;
  if (panel.error || !panel.data) return <Mensaje texto={panel.error?.message ?? "No se pudo cargar su panel."} />;
  const d = panel.data;
  const p = d.partner!;
  const s = p.saldo!;
  const [texto, clase] = ESTADO_PARTNER[p.estado!];

  return (
    <Shell marca="Partners" items={[{ etiqueta: "Mi panel", icono: Wallet, activo: true }]} usuario={sesion.nombre ?? ""} onSalir={onSalir}>
      <div className="stack">
        <div className="page-header between"><h1>{p.nombre}</h1><span className={`badge ${clase}`}>{texto}</span></div>

        {p.estado === "PENDIENTE" && <p className="alert">Estamos revisando su inscripción. Le avisaremos por correo cuando quede aprobada, con su código.</p>}
        {(p.estado === "SUSPENDIDO" || p.estado === "RECHAZADO") && <p className="alert alert-error">{texto}{p.motivo ? `: ${p.motivo}` : ""}. Escríbanos si cree que es un error.</p>}

        {p.codigo && (
          <div className="card stack">
            <h3>Su código</h3>
            <div className="row">
              <strong className="cifra-grande">{p.codigo}</strong>
              <input readOnly value={p.enlace} className="grow" onFocus={(e) => e.target.select()} />
              <button className="btn btn-secondary" onClick={() => navigator.clipboard.writeText(p.enlace!).then(() => setCopiado(true))}>{copiado ? "Copiado" : "Copiar enlace"}</button>
            </div>
            <p className="muted small">
              El consultorio que se registre con su enlace o código recibe {d.descuentoPorcentaje} % de descuento, y usted el {d.comisionPorcentaje} % de cada
              pago que haga mientras siga usando Galeno. Las comisiones se pagan 30 días después de cada pago, cuando su disponible llegue a {pesos(d.minimoLiquidacion)}.
            </p>
          </div>
        )}

        <div className="row">
          <Cifra valor={pesos(s.disponible)} texto="disponible" />
          <Cifra valor={pesos(s.retenido)} texto="retenido (30 días)" />
          <Cifra valor={pesos(s.pagado)} texto="pagado" />
          <Cifra valor={String(p.referidos ?? 0)} texto="consultorios referidos" />
        </div>

        <div className="card table-wrap">
          <h3>Consultorios referidos</h3>
          <table>
            <thead><tr><th>Consultorio</th><th>Desde</th><th>Suscripción</th></tr></thead>
            <tbody>
              {d.referidos?.map((r) => (
                <tr key={r.consultorioId}><td>{r.consultorio}</td><td>{fecha.format(new Date(r.desde!))}</td><td>{ESTADO_REFERIDO[r.estadoSuscripcion!] ?? r.estadoSuscripcion}</td></tr>
              ))}
            </tbody>
          </table>
          {!d.referidos?.length && <p className="muted">Todavía no tiene consultorios referidos. Comparta su enlace.</p>}
        </div>

        <TablaComisiones comisiones={d.comisiones ?? []} />

        {!!d.liquidaciones?.length && (
          <div className="card table-wrap">
            <h3>Pagos recibidos</h3>
            <table>
              <thead><tr><th>Fecha</th><th>Valor</th><th>Comisiones</th><th>Referencia</th></tr></thead>
              <tbody>
                {d.liquidaciones.map((l) => (
                  <tr key={l.id}><td>{fecha.format(new Date(`${l.fechaPago}T12:00:00`))}</td><td>{pesos(l.total)}</td><td>{l.comisiones}</td><td>{l.referencia}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <DatosPago partner={p} />
      </div>
    </Shell>
  );
}

const ESTADO_REFERIDO: Record<string, string> = { PRUEBA: "En prueba gratis", ACTIVA: "Pagando", VENCIDA: "Vencida", SUSPENDIDA: "Suspendida" };

export function TablaComisiones({ comisiones, onAnular }: { comisiones: Comision[]; onAnular?: (c: Comision) => void }) {
  return (
    <div className="card table-wrap">
      <h3>Comisiones</h3>
      <table>
        <thead><tr><th>Fecha</th><th>Consultorio</th><th>Pago</th><th>Comisión</th><th>Estado</th>{onAnular && <th />}</tr></thead>
        <tbody>
          {comisiones.map((c) => {
            const [t, cl] = ESTADO_COMISION[c.estado!];
            return (
              <tr key={c.id}>
                <td>{fecha.format(new Date(c.creadaEn!))}</td>
                <td>{c.consultorio}</td>
                <td>{pesos(c.base)}</td>
                <td><strong>{pesos(c.valor)}</strong> <span className="muted small">({c.porcentaje} %)</span></td>
                <td>
                  <span className={`badge ${cl}`}>{t}</span>
                  {c.estado === "RETENIDA" && <div className="muted small">desde {fecha.format(new Date(c.disponibleDesde!))}</div>}
                  {c.motivoAnulacion && <div className="muted small">{c.motivoAnulacion}</div>}
                </td>
                {onAnular && <td>{(c.estado === "RETENIDA" || c.estado === "DISPONIBLE") && <button className="btn-link danger" onClick={() => onAnular(c)}>Anular…</button>}</td>}
              </tr>
            );
          })}
        </tbody>
      </table>
      {!comisiones.length && <p className="muted">Todavía no hay comisiones: se generan cuando un referido paga su suscripción.</p>}
    </div>
  );
}

function DatosPago({ partner: p }: { partner: Schemas["PartnerVista"] }) {
  const queryClient = useQueryClient();
  const [banco, setBanco] = useState(p.banco ?? "");
  const [tipoCuenta, setTipoCuenta] = useState(p.tipoCuenta ?? "AHORROS");
  const [numeroCuenta, setNumeroCuenta] = useState(p.numeroCuenta ?? "");
  const [telefono, setTelefono] = useState(p.telefono ?? "");
  const guardar = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/partners/yo/datos-pago", { body: { banco, tipoCuenta, numeroCuenta, telefono } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["partner"] }),
  });
  return (
    <form className="card form-grid" onSubmit={(e: FormEvent) => { e.preventDefault(); guardar.mutate(); }}>
      <h3 className="wide">Datos para pagarle</h3>
      <p className="muted small wide">A nombre de {p.nombre} ({p.tipoDocumento} {p.numeroDocumento}). Para cada pago le pediremos su cuenta de cobro o factura.</p>
      <label>Banco<input value={banco} onChange={(e) => setBanco(e.target.value)} maxLength={80} /></label>
      <label>Tipo de cuenta<select value={tipoCuenta} onChange={(e) => setTipoCuenta(e.target.value)}><option value="AHORROS">Ahorros</option><option value="CORRIENTE">Corriente</option></select></label>
      <label>Número de cuenta<input value={numeroCuenta} onChange={(e) => setNumeroCuenta(e.target.value)} maxLength={40} /></label>
      <label>Teléfono<input value={telefono} onChange={(e) => setTelefono(e.target.value)} required maxLength={30} /></label>
      {guardar.error && <p className="alert alert-error wide">{guardar.error.message}</p>}
      {guardar.isSuccess && <p className="alert wide">Datos guardados.</p>}
      <div className="wide"><button className="btn btn-secondary" disabled={guardar.isPending}>Guardar</button></div>
    </form>
  );
}

/**
 * Borrador de las condiciones: revisarlas con un abogado y un contador (contrato de prestación de servicios,
 * retención en la fuente sobre comisiones, cuenta de cobro o factura) antes de abrir el programa.
 */
function Condiciones() {
  return (
    <div className="login">
      <div className="card muy-ancho stack">
        <h1>Condiciones del programa de partners</h1>
        <p className="alert">Borrador en revisión legal · versión 2026-10.</p>
        <p>El partner recomienda Galeno a consultorios con su código o enlace. Un consultorio queda asociado al partner cuando se registra con ese código; cada consultorio tiene un solo partner, que no cambia salvo decisión de Galeno.</p>
        <p>El partner recibe una comisión sobre cada pago de suscripción aprobado de sus consultorios mientras sigan pagando y el partner esté activo. La prueba gratis no genera comisión. No hay comisión por consultorios propios (cuando el partner es su administrador o tiene su mismo NIT).</p>
        <p>Cada comisión queda retenida 30 días después del pago y luego disponible. Galeno paga las disponibles cuando suman el mínimo, por transferencia a la cuenta registrada, contra la cuenta de cobro o factura del partner y con las retenciones de ley.</p>
        <p>El partner es independiente: no es empleado ni representante de Galeno, no puede ofrecer condiciones distintas a las publicadas y no recibe datos de los consultorios ni de sus pacientes, salvo el nombre del consultorio y el estado de su suscripción. Galeno puede suspender al partner ante prácticas engañosas; las comisiones de pagos reversados se anulan.</p>
        <a href="/partner/inscripcion" className="btn-link">Volver a la inscripción</a>
      </div>
    </div>
  );
}

function Cifra({ valor, texto }: { valor: string; texto: string }) {
  return <div className="card cifra"><strong>{valor}</strong><div className="muted small">{texto}</div></div>;
}

function Mensaje({ texto }: { texto: string }) {
  return <div className="login"><div className="card login-card"><h1>Galeno · Partners</h1><p>{texto}</p></div></div>;
}

