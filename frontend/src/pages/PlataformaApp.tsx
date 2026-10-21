import { useState, type FormEvent } from "react";
import { QueryClient, QueryClientProvider, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import createClient, { type Middleware } from "openapi-fetch";
import { ApiError, unwrap, type Schemas } from "../api/client";
import type { paths } from "../api/schema";
import { ESTADO_SUSCRIPCION, pesos } from "./SuscripcionPage";
import { ESTADO_PARTNER, TablaComisiones } from "./PartnerApp";

type Pago = Schemas["PagoVista"];
type Consultorio = Schemas["ConsultorioVista"];

/**
 * Consola de los operadores de Galeno (no de los consultorios): revisar pagos y administrar suscripciones.
 * Tiene su propio token, guardado aparte de la sesión del consultorio.
 */
const CLAVE = "galeno.plataforma";
const fecha = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium" });
const fechaHora = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium", timeStyle: "short" });

type Sesion = Schemas["SesionPlataforma"];

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
    if (response.status === 401 && !request.url.endsWith("/plataforma/login")) alSalir();
    return response;
  },
};
const api = createClient<paths>({ baseUrl: "/" });
api.use(auth);
const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

export function PlataformaApp() {
  const [sesion, setSesion] = useState<Sesion | null>(leerSesion);
  alSalir = () => {
    sessionStorage.removeItem(CLAVE);
    token = null;
    queryClient.clear();
    setSesion(null);
  };

  if (!sesion) {
    return <Login onEntrar={(s) => {
      sessionStorage.setItem(CLAVE, JSON.stringify(s));
      token = s.accessToken!;
      setSesion(s);
    }} />;
  }
  return (
    <QueryClientProvider client={queryClient}>
      <Consola sesion={sesion} onSalir={alSalir} />
    </QueryClientProvider>
  );
}

function Login({ onEntrar }: { onEntrar: (s: Sesion) => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const entrar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/plataforma/login", { body: { email, password } })),
    onSuccess: onEntrar,
  });
  return (
    <div className="login">
      <form className="card login-card" onSubmit={(e: FormEvent) => { e.preventDefault(); entrar.mutate(); }}>
        <h1>Galeno · Plataforma</h1>
        <p className="muted">Consola para los operadores de Galeno.</p>
        <label>Correo<input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
        <label>Contraseña<input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
        {entrar.error && <p className="alert alert-error">{entrar.error.message}</p>}
        <button className="btn" disabled={entrar.isPending}>{entrar.isPending ? "Ingresando…" : "Ingresar"}</button>
      </form>
    </div>
  );
}

type Pestana = "pagos" | "consultorios" | "partners" | "configuracion" | "auditoria";

function Consola({ sesion, onSalir }: { sesion: Sesion; onSalir: () => void }) {
  const [pestana, setPestana] = useState<Pestana>("pagos");
  const pendientes = useQuery({
    queryKey: ["plataforma", "pagos", "REPORTADO"],
    queryFn: async () => unwrap(await api.GET("/api/v1/plataforma/pagos", { params: { query: { estado: "REPORTADO" } } })),
    refetchInterval: 60_000,
  });
  return (
    <div className="layout">
      <header className="topbar">
        <span className="brand">Galeno · Plataforma</span>
        <nav className="topnav">
          {([["pagos", "Pagos por revisar"], ["consultorios", "Consultorios"], ["partners", "Partners"], ["configuracion", "Planes y datos de pago"], ["auditoria", "Auditoría"]] as const).map(([p, t]) => (
            <a key={p} href="#" className={pestana === p ? "active" : ""} onClick={(e) => { e.preventDefault(); setPestana(p); }}>
              {t}{p === "pagos" && !!pendientes.data?.length && <> <span className="badge badge-warn">{pendientes.data.length}</span></>}
            </a>
          ))}
        </nav>
        <span className="spacer" />
        <span>{sesion.nombre}</span>
        <button className="btn-link" onClick={onSalir}>Salir</button>
      </header>
      <main className="content">
        {pestana === "pagos" && <PagosPorRevisar pagos={pendientes.data} cargando={pendientes.isLoading} />}
        {pestana === "consultorios" && <Consultorios />}
        {pestana === "partners" && <Partners />}
        {pestana === "configuracion" && <Configuracion />}
        {pestana === "auditoria" && <Auditoria />}
      </main>
    </div>
  );
}

async function verComprobante(id: string) {
  const res = await fetch(`/api/v1/plataforma/pagos/${id}/comprobante`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!res.ok) throw new ApiError(res.status, "No se pudo abrir el comprobante");
  const url = URL.createObjectURL(await res.blob());
  window.open(url, "_blank", "noopener");
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function PagosPorRevisar({ pagos, cargando }: { pagos?: Pago[]; cargando: boolean }) {
  return (
    <section className="stack">
      <div className="page-header"><h1>Pagos por revisar</h1></div>
      <p className="muted small">Compare cada pago con el extracto bancario antes de aprobarlo. Al aprobar, la suscripción se extiende y se avisa al consultorio.</p>
      {cargando && <p className="muted">Cargando…</p>}
      {pagos?.length === 0 && <p className="muted">No hay pagos por revisar.</p>}
      {pagos?.map((p) => <PagoPendiente key={p.id} pago={p} />)}
    </section>
  );
}

function PagoPendiente({ pago: p }: { pago: Pago }) {
  const queryClient = useQueryClient();
  const [motivo, setMotivo] = useState("");
  const [rechazando, setRechazando] = useState(false);
  const [errorComprobante, setErrorComprobante] = useState<string | null>(null);
  const listo = () => queryClient.invalidateQueries({ queryKey: ["plataforma"] });
  const aprobar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/plataforma/pagos/{id}/aprobar", { params: { path: { id: p.id! } } })),
    onSuccess: listo,
  });
  const rechazar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/plataforma/pagos/{id}/rechazar", { params: { path: { id: p.id! } }, body: { motivo } })),
    onSuccess: listo,
  });
  const error = aprobar.error ?? rechazar.error;
  return (
    <div className="card stack">
      <div className="row">
        <strong>{p.consultorioNombre}</strong>
        <span className="spacer" />
        <span className="muted small">Reportado {fechaHora.format(new Date(p.reportadoEn!))}</span>
      </div>
      <div className="row">
        <span>Plan <strong>{p.plan === "ANUAL" ? "anual" : "mensual"}</strong></span>
        <span><strong>{pesos(p.monto)}</strong></span>
        <span>Pagado el {fecha.format(new Date(`${p.fechaPago}T12:00:00`))}</span>
        <span>Referencia <strong>{p.referencia}</strong></span>
        {p.tieneComprobante
          ? <button className="btn-link" onClick={() => verComprobante(p.id!).catch((e: Error) => setErrorComprobante(e.message))}>Ver comprobante</button>
          : <span className="muted small">Sin comprobante</span>}
      </div>
      {errorComprobante && <p className="field-error">{errorComprobante}</p>}
      {error && <p className="alert alert-error">{error.message}</p>}
      {rechazando ? (
        <form className="row" onSubmit={(e) => { e.preventDefault(); rechazar.mutate(); }}>
          <input className="grow" placeholder="Motivo (lo verá el consultorio)" value={motivo} onChange={(e) => setMotivo(e.target.value)} required maxLength={300} autoFocus />
          <button className="btn" disabled={rechazar.isPending}>Rechazar</button>
          <button type="button" className="btn-link" onClick={() => setRechazando(false)}>Cancelar</button>
        </form>
      ) : (
        <div className="row">
          <button className="btn" disabled={aprobar.isPending} onClick={() => {
            if (confirm(`¿Aprobar ${pesos(p.monto)} de ${p.consultorioNombre}? Su suscripción se extiende ${p.meses === 1 ? "1 mes" : `${p.meses} meses`}.`)) aprobar.mutate();
          }}>Aprobar</button>
          <button className="btn btn-secondary" onClick={() => setRechazando(true)}>Rechazar…</button>
        </div>
      )}
    </div>
  );
}

function Consultorios() {
  const lista = useQuery({
    queryKey: ["plataforma", "consultorios"],
    queryFn: async () => unwrap(await api.GET("/api/v1/plataforma/consultorios")),
  });
  const [filtro, setFiltro] = useState("");
  const visibles = (lista.data ?? []).filter((c) => !filtro || c.nombre?.toLowerCase().includes(filtro.toLowerCase())
    || c.administradores?.some((a) => a.includes(filtro.toLowerCase())));
  return (
    <section className="stack">
      <div className="page-header"><h1>Consultorios</h1><span className="muted">{lista.data?.length ?? 0}</span></div>
      <input className="search" placeholder="Buscar por nombre o correo…" value={filtro} onChange={(e) => setFiltro(e.target.value)} />
      {lista.error && <p className="alert alert-error">{lista.error.message}</p>}
      {visibles.map((c) => <FilaConsultorio key={c.id} consultorio={c} />)}
    </section>
  );
}

type Accion = "cortesia" | "suspender" | "pago" | "partner" | null;

function FilaConsultorio({ consultorio: c }: { consultorio: Consultorio }) {
  const queryClient = useQueryClient();
  const [accion, setAccion] = useState<Accion>(null);
  const [dias, setDias] = useState("15");
  const [motivo, setMotivo] = useState("");
  const [plan, setPlan] = useState("MENSUAL");
  const [monto, setMonto] = useState("");
  const [fechaPago, setFechaPago] = useState(new Date().toLocaleDateString("en-CA"));
  const [referencia, setReferencia] = useState("");
  const [codigoPartner, setCodigoPartner] = useState("");
  const s = c.suscripcion!;
  const id = c.id!;
  const hecho = () => {
    setAccion(null);
    setMotivo("");
    queryClient.invalidateQueries({ queryKey: ["plataforma"] });
  };
  const ejecutar = useMutation({
    mutationFn: async () => {
      const path = { params: { path: { id } } };
      if (accion === "cortesia") return unwrap(await api.POST("/api/v1/plataforma/consultorios/{id}/cortesia", { ...path, body: { dias: Number(dias), motivo } }));
      if (accion === "suspender") return unwrap(await api.POST("/api/v1/plataforma/consultorios/{id}/suspender", { ...path, body: { motivo } }));
      if (accion === "partner") return unwrap(await api.PUT("/api/v1/plataforma/consultorios/{id}/partner", { ...path, body: { codigo: codigoPartner } }));
      return unwrap(await api.POST("/api/v1/plataforma/consultorios/{id}/pagos", { ...path, body: { plan, monto: Number(monto), fechaPago, referencia } }));
    },
    onSuccess: hecho,
  });
  const reactivar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/plataforma/consultorios/{id}/reactivar", { params: { path: { id } } })),
    onSuccess: hecho,
  });
  const [texto, clase] = ESTADO_SUSCRIPCION[s.estado!];
  return (
    <div className="card stack">
      <div className="row">
        <strong>{c.nombre}</strong>
        {c.nit && <span className="muted small">NIT {c.nit}</span>}
        <span className={`badge ${clase}`}>{texto}</span>
        {s.pagoEnRevision && <span className="badge badge-warn">Pago por revisar</span>}
        <span className="spacer" />
        <span className="small">{s.estado === "SUSPENDIDA" ? "En solo lectura" : `Vence el ${fecha.format(new Date(s.venceEn!))}`}</span>
      </div>
      <div className="muted small">
        Registrado el {fecha.format(new Date(c.creadoEn!))} · {c.administradores?.join(", ") || "sin administradores activos"}
        {s.motivoSuspension && ` · Suspendido: ${s.motivoSuspension}`}
        {!!s.descuentoPorcentaje && ` · Referido (${s.descuentoPorcentaje} % de descuento)`}
      </div>
      {accion ? (
        <form className="row" onSubmit={(e) => { e.preventDefault(); ejecutar.mutate(); }}>
          {accion === "partner" && <label>Código del partner<input value={codigoPartner} onChange={(e) => setCodigoPartner(e.target.value.toUpperCase())} required maxLength={20} /></label>}
          {accion === "cortesia" && <label>Días<input type="number" min={1} max={366} value={dias} onChange={(e) => setDias(e.target.value)} required style={{ width: "6rem" }} /></label>}
          {accion === "pago" && (
            <>
              <label>Plan<select value={plan} onChange={(e) => setPlan(e.target.value)}><option value="MENSUAL">Mensual</option><option value="ANUAL">Anual</option></select></label>
              <label>Valor<input type="number" min={1} value={monto} onChange={(e) => setMonto(e.target.value)} required style={{ width: "9rem" }} /></label>
              <label>Fecha<input type="date" value={fechaPago} onChange={(e) => setFechaPago(e.target.value)} required /></label>
              <label>Referencia<input value={referencia} onChange={(e) => setReferencia(e.target.value)} required maxLength={100} /></label>
            </>
          )}
          {(accion === "cortesia" || accion === "suspender") && <label className="grow">Motivo<input value={motivo} onChange={(e) => setMotivo(e.target.value)} required maxLength={300} /></label>}
          <button className="btn" disabled={ejecutar.isPending}>
            {accion === "cortesia" ? "Regalar días" : accion === "suspender" ? "Suspender" : accion === "partner" ? "Asignar" : "Registrar pago"}
          </button>
          <button type="button" className="btn-link" onClick={() => setAccion(null)}>Cancelar</button>
        </form>
      ) : (
        <div className="row">
          <button className="btn btn-secondary" onClick={() => setAccion("pago")}>Registrar pago recibido</button>
          <button className="btn btn-secondary" onClick={() => setAccion("cortesia")}>Regalar días</button>
          <button className="btn btn-secondary" onClick={() => setAccion("partner")}>Asignar partner</button>
          {s.estado === "SUSPENDIDA" && s.motivoSuspension
            ? <button className="btn btn-secondary" disabled={reactivar.isPending} onClick={() => reactivar.mutate()}>Reactivar</button>
            : <button className="btn-link danger" onClick={() => setAccion("suspender")}>Suspender…</button>}
        </div>
      )}
      {(ejecutar.error || reactivar.error) && <p className="alert alert-error">{(ejecutar.error ?? reactivar.error)!.message}</p>}
    </div>
  );
}

function Partners() {
  const lista = useQuery({ queryKey: ["plataforma", "partners"], queryFn: async () => unwrap(await api.GET("/api/v1/plataforma/partners")) });
  const [abierto, setAbierto] = useState<string | null>(null);
  const pendientes = (lista.data ?? []).filter((p) => p.estado === "PENDIENTE").length;
  return (
    <section className="stack">
      <div className="page-header"><h1>Partners</h1>{!!pendientes && <span className="badge badge-warn">{pendientes} por aprobar</span>}</div>
      {lista.error && <p className="alert alert-error">{lista.error.message}</p>}
      {lista.data?.length === 0 && <p className="muted">Todavía no hay partners inscritos.</p>}
      {lista.data?.map((p) => (
        <div key={p.id} className="card stack">
          <div className="row">
            <strong>{p.nombre}</strong>
            <span className={`badge ${ESTADO_PARTNER[p.estado!][1]}`}>{ESTADO_PARTNER[p.estado!][0]}</span>
            {p.codigo && <span className="badge">{p.codigo}</span>}
            <span className="spacer" />
            <span className="small">{p.referidos} referidos · disponible {pesos(p.saldo?.disponible)} · retenido {pesos(p.saldo?.retenido)}</span>
          </div>
          <div className="muted small">{p.tipoDocumento} {p.numeroDocumento} · {p.email} · {p.telefono} · inscrito el {fecha.format(new Date(p.creadoEn!))}{p.motivo && ` · ${p.motivo}`}</div>
          <AccionesPartner partner={p} />
          <button className="btn-link small" style={{ alignSelf: "flex-start" }} onClick={() => setAbierto(abierto === p.id ? null : p.id!)}>
            {abierto === p.id ? "Ocultar detalle" : "Ver referidos, comisiones y pagos"}
          </button>
          {abierto === p.id && <DetallePartner id={p.id!} />}
        </div>
      ))}
    </section>
  );
}

function AccionesPartner({ partner: p }: { partner: Schemas["PartnerVista"] }) {
  const queryClient = useQueryClient();
  const [accion, setAccion] = useState<"rechazar" | "suspender" | null>(null);
  const [motivo, setMotivo] = useState("");
  const hecho = () => { setAccion(null); setMotivo(""); queryClient.invalidateQueries({ queryKey: ["plataforma"] }); };
  const aprobar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/plataforma/partners/{id}/aprobar", { params: { path: { id: p.id! } } })),
    onSuccess: hecho,
  });
  const cambiar = useMutation({
    mutationFn: async () => accion === "rechazar"
      ? unwrap(await api.POST("/api/v1/plataforma/partners/{id}/rechazar", { params: { path: { id: p.id! } }, body: { motivo } }))
      : unwrap(await api.POST("/api/v1/plataforma/partners/{id}/suspender", { params: { path: { id: p.id! } }, body: { motivo } })),
    onSuccess: hecho,
  });
  const error = aprobar.error ?? cambiar.error;
  return (
    <>
      {accion ? (
        <form className="row" onSubmit={(e) => { e.preventDefault(); cambiar.mutate(); }}>
          <input className="grow" placeholder="Motivo (lo verá el partner)" value={motivo} onChange={(e) => setMotivo(e.target.value)} required maxLength={300} autoFocus />
          <button className="btn" disabled={cambiar.isPending}>{accion === "rechazar" ? "Rechazar" : "Suspender"}</button>
          <button type="button" className="btn-link" onClick={() => setAccion(null)}>Cancelar</button>
        </form>
      ) : (
        <div className="row">
          {p.estado === "PENDIENTE" && <><button className="btn" disabled={aprobar.isPending} onClick={() => aprobar.mutate()}>Aprobar</button>
            <button className="btn btn-secondary" onClick={() => setAccion("rechazar")}>Rechazar…</button></>}
          {p.estado === "SUSPENDIDO" && <button className="btn btn-secondary" disabled={aprobar.isPending} onClick={() => aprobar.mutate()}>Reactivar</button>}
          {p.estado === "ACTIVO" && <button className="btn-link danger" onClick={() => setAccion("suspender")}>Suspender…</button>}
        </div>
      )}
      {error && <p className="alert alert-error">{error.message}</p>}
    </>
  );
}

function DetallePartner({ id }: { id: string }) {
  const queryClient = useQueryClient();
  const detalle = useQuery({ queryKey: ["plataforma", "partner", id], queryFn: async () => unwrap(await api.GET("/api/v1/plataforma/partners/{id}", { params: { path: { id } } })) });
  const [referencia, setReferencia] = useState("");
  const [fechaPago, setFechaPago] = useState(new Date().toLocaleDateString("en-CA"));
  const liquidar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/plataforma/partners/{id}/liquidaciones", { params: { path: { id } }, body: { referencia, fechaPago } })),
    onSuccess: () => { setReferencia(""); queryClient.invalidateQueries({ queryKey: ["plataforma"] }); },
  });
  const anular = useMutation({
    mutationFn: async ({ comision, motivo }: { comision: string; motivo: string }) =>
      unwrap(await api.POST("/api/v1/plataforma/comisiones/{id}/anular", { params: { path: { id: comision } }, body: { motivo } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plataforma"] }),
  });
  if (!detalle.data) return <p className="muted">Cargando…</p>;
  const d = detalle.data;
  const p = d.partner!;
  return (
    <div className="stack">
      <div className="muted small">Cuenta: {p.banco ?? "—"} {p.tipoCuenta?.toLowerCase() ?? ""} {p.numeroCuenta ?? "sin registrar"}</div>
      {d.puedeLiquidar ? (
        <form className="row" onSubmit={(e) => { e.preventDefault(); liquidar.mutate(); }}>
          <span>Disponible <strong>{pesos(p.saldo?.disponible)}</strong>: transfiera y registre el pago</span>
          <input placeholder="Referencia de la transferencia" value={referencia} onChange={(e) => setReferencia(e.target.value)} required maxLength={100} />
          <input type="date" value={fechaPago} onChange={(e) => setFechaPago(e.target.value)} required />
          <button className="btn" disabled={liquidar.isPending}>Registrar pago al partner</button>
        </form>
      ) : <p className="muted small">El disponible ({pesos(p.saldo?.disponible)}) aún no llega al mínimo de {pesos(d.minimoLiquidacion)}.</p>}
      {(liquidar.error || anular.error) && <p className="alert alert-error">{(liquidar.error ?? anular.error)!.message}</p>}
      <div className="table-wrap">
        <table>
          <thead><tr><th>Referido</th><th>Desde</th><th>Origen</th><th>Suscripción</th></tr></thead>
          <tbody>{d.referidos?.map((r) => <tr key={r.consultorioId}><td>{r.consultorio}</td><td>{fecha.format(new Date(r.desde!))}</td><td>{r.origen === "REGISTRO" ? "Con su código" : "Asignado"}</td><td>{r.estadoSuscripcion}</td></tr>)}</tbody>
        </table>
      </div>
      <TablaComisiones comisiones={d.comisiones ?? []} onAnular={(c) => {
        const motivo = prompt("Motivo de la anulación (lo verá el partner)");
        if (motivo) anular.mutate({ comision: c.id!, motivo });
      }} />
    </div>
  );
}

function Configuracion() {
  const queryClient = useQueryClient();
  const planes = useQuery({ queryKey: ["plataforma", "planes"], queryFn: async () => unwrap(await api.GET("/api/v1/plataforma/planes")) });
  const datos = useQuery({ queryKey: ["plataforma", "datos-pago"], queryFn: async () => unwrap(await api.GET("/api/v1/plataforma/datos-pago")) });
  const [texto, setTexto] = useState<string | null>(null);
  const guardarDatos = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/plataforma/datos-pago", { body: { texto: texto ?? "" } })),
    onSuccess: () => { setTexto(null); queryClient.invalidateQueries({ queryKey: ["plataforma", "datos-pago"] }); },
  });
  return (
    <section className="stack">
      <div className="page-header"><h1>Planes y datos de pago</h1></div>
      <div className="card stack">
        <h3>Planes</h3>
        {planes.data?.map((p) => <EditarPlan key={p.codigo} plan={p} />)}
      </div>
      <div className="card stack">
        <h3>Datos para pagar</h3>
        <p className="muted small">Los ven los administradores de los consultorios en Suscripción: banco, tipo y número de cuenta, titular, NIT.</p>
        <textarea rows={5} value={texto ?? datos.data?.texto ?? ""} onChange={(e) => setTexto(e.target.value)} maxLength={2000} />
        {guardarDatos.error && <p className="alert alert-error">{guardarDatos.error.message}</p>}
        <div><button className="btn" disabled={texto === null || guardarDatos.isPending} onClick={() => guardarDatos.mutate()}>Guardar</button></div>
      </div>
    </section>
  );
}

function EditarPlan({ plan }: { plan: Schemas["PlanVista"] }) {
  const queryClient = useQueryClient();
  const [precio, setPrecio] = useState(String(plan.precio ?? 0));
  const [activo, setActivo] = useState(!!plan.activo);
  const guardar = useMutation({
    mutationFn: async () => unwrap(await api.PUT("/api/v1/plataforma/planes/{codigo}", {
      params: { path: { codigo: plan.codigo! } }, body: { nombre: plan.nombre!, precio: Number(precio), activo },
    })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plataforma", "planes"] }),
  });
  const cambio = Number(precio) !== plan.precio || activo !== plan.activo;
  return (
    <form className="row" onSubmit={(e) => { e.preventDefault(); guardar.mutate(); }}>
      <strong style={{ minWidth: "6rem" }}>{plan.nombre}</strong>
      <label>Precio (COP)<input type="number" min={0} value={precio} onChange={(e) => setPrecio(e.target.value)} style={{ width: "10rem" }} /></label>
      <span className="muted small">{pesos(Number(precio))} por {plan.meses === 1 ? "mes" : `${plan.meses} meses`}</span>
      <label className="check"><input type="checkbox" checked={activo} onChange={(e) => setActivo(e.target.checked)} />Disponible</label>
      <button className="btn btn-secondary" disabled={!cambio || guardar.isPending}>Guardar</button>
      {guardar.error && <span className="field-error">{guardar.error.message}</span>}
    </form>
  );
}

const ACCION: Record<string, string> = {
  INGRESO: "Ingresó", APROBAR_PAGO: "Aprobó un pago", RECHAZAR_PAGO: "Rechazó un pago", REGISTRAR_PAGO: "Registró un pago",
  VER_COMPROBANTE: "Vio un comprobante", REGALAR_DIAS: "Regaló días", SUSPENDER: "Suspendió", REACTIVAR: "Reactivó",
  CAMBIAR_PLAN: "Cambió un plan", CAMBIAR_DATOS_PAGO: "Cambió los datos de pago",
};

function Auditoria() {
  const eventos = useQuery({ queryKey: ["plataforma", "auditoria"], queryFn: async () => unwrap(await api.GET("/api/v1/plataforma/auditoria")) });
  return (
    <section className="stack">
      <div className="page-header"><h1>Auditoría</h1><span className="muted">últimos 100</span></div>
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Fecha</th><th>Acción</th><th>Consultorio</th><th>Detalle</th></tr></thead>
          <tbody>
            {eventos.data?.map((e, i) => (
              <tr key={i}>
                <td className="small">{fechaHora.format(new Date(e.fecha!))}</td>
                <td>{ACCION[e.accion!] ?? e.accion}</td>
                <td>{e.consultorio ?? "—"}</td>
                <td className="small muted">{e.detalle}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
