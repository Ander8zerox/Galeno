import { useEffect, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Client } from "openapi-fetch";
import QRCode from "qrcode";
import { ShieldCheck } from "lucide-react";
import { expectOk, unwrap } from "../api/client";
import type { paths } from "../api/schema";
import { contactoSoporte } from "../soporte";

/**
 * Verificación en dos pasos (código de una app de autenticación), compartida por las tres entradas: consultorio,
 * plataforma y partners. Cada una tiene su propio cliente de API (con su token), así que se lo pasa a estos
 * componentes. La API decide cuándo es obligatoria; aquí solo se muestra.
 */
type Api = Client<paths>;
export type RequisitoMfa = "VERIFICAR" | "CONFIGURAR";

// ---------------------------------------------------------------- equipos recordados

const CLAVE_EQUIPOS = "galeno.equipos";

/**
 * "Recordar este equipo 30 días": el token va en el cuerpo del login (CloudFront no pasa cookies a la API). Uno por
 * correo, para computadores compartidos en el consultorio. Sin la contraseña no sirve de nada.
 */
export const equiposRecordados = {
  leer(email: string): string | undefined {
    try {
      return (JSON.parse(localStorage.getItem(CLAVE_EQUIPOS) ?? "{}") as Record<string, string>)[email.trim().toLowerCase()];
    } catch {
      return undefined;
    }
  },
  guardar(email: string, token: string | undefined | null) {
    if (!token) return;
    try {
      const todos = JSON.parse(localStorage.getItem(CLAVE_EQUIPOS) ?? "{}") as Record<string, string>;
      todos[email.trim().toLowerCase()] = token;
      localStorage.setItem(CLAVE_EQUIPOS, JSON.stringify(todos));
    } catch {
      /* almacenamiento no disponible: pedirá el código la próxima vez */
    }
  },
};

/** El correo del token pendiente (claim "email"): para guardar el equipo recordado aunque no venga del login. */
export function correoDelToken(token: string): string | undefined {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const json = new TextDecoder().decode(Uint8Array.from(atob(payload), (c) => c.charCodeAt(0)));
    return (JSON.parse(json) as { email?: string }).email;
  } catch {
    return undefined;
  }
}

// ---------------------------------------------------------------- al ingresar

interface VerificacionProps<R extends { codigosRecuperacion?: string[] }> {
  /** Con el token pendiente ya puesto. */
  api: Api;
  requisito: RequisitoMfa;
  /** Manda el código a la entrada que corresponde (/auth/mfa, /plataforma/mfa, /partners/mfa). */
  completar: (codigo: string, recordar: boolean) => Promise<R>;
  /** Con la respuesta final, después de mostrar los códigos de recuperación si los hubo. */
  alTerminar: (respuesta: R) => void;
  onCancelar: () => void;
}

/** Pantalla entre la contraseña y la sesión: pide el código o, si hace falta, guía la configuración. */
export function VerificacionDosPasos<R extends { codigosRecuperacion?: string[] }>(
  { api, requisito, completar, alTerminar, onCancelar }: VerificacionProps<R>,
) {
  const [terminado, setTerminado] = useState<R | null>(null);
  const configurar = requisito === "CONFIGURAR";
  const config = useQuery({
    queryKey: ["mfa-configuracion-ingreso"],
    enabled: configurar,
    staleTime: Infinity,
    gcTime: 0,
    queryFn: async () => unwrap(await api.POST("/api/v1/mfa/configuracion")),
  });

  if (terminado?.codigosRecuperacion?.length) {
    return (
      <div className="login con-foto">
        <div className="card login-card ancho stack">
          <CodigosRecuperacion codigos={terminado.codigosRecuperacion} onListo={() => alTerminar(terminado)} />
        </div>
      </div>
    );
  }

  return (
    <div className="login con-foto">
      <div className={`card login-card stack ${configurar ? "ancho" : ""}`}>
        <h1>{configurar ? "Proteja su cuenta" : "Verificación en dos pasos"}</h1>
        {configurar ? (
          <>
            <p className="muted">
              Su cuenta necesita verificación en dos pasos: además de la contraseña, al ingresar le pediremos un código
              que cambia cada 30 segundos en su celular. Así nadie entra solo con su contraseña.
            </p>
            {config.error && <p className="alert alert-error">{config.error.message}</p>}
            {config.data && <PasosConfiguracion uri={config.data.uri!} secreto={config.data.secreto!} />}
          </>
        ) : (
          <p className="muted">Abra su app de autenticación y escriba el código de 6 dígitos de Galenum.</p>
        )}
        <FormularioCodigo
          etiquetaBoton={configurar ? "Activar e ingresar" : "Verificar"}
          permitirRecuperacion={!configurar}
          permitirRecordar
          deshabilitado={configurar && !config.data}
          enviar={async (codigo, recordar) => {
            const r = await completar(codigo, recordar);
            if (r.codigosRecuperacion?.length) setTerminado(r);
            else alTerminar(r);
          }}
        />
        <button type="button" className="btn-link small center" onClick={onCancelar}>Volver al ingreso</button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- piezas

const PLAY_STORE = "https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2";
const APP_STORE = "https://apps.apple.com/app/google-authenticator/id388497605";

function PasosConfiguracion({ uri, secreto }: { uri: string; secreto: string }) {
  const [qr, setQr] = useState<string | null>(null);
  const [verClave, setVerClave] = useState(false);
  useEffect(() => {
    // Se dibuja aquí mismo: el secreto no sale del navegador hacia ningún servicio de QR
    QRCode.toDataURL(uri, { margin: 1, width: 200, errorCorrectionLevel: "M" }).then(setQr, () => setQr(null));
  }, [uri]);
  return (
    <>
    <div className="app-mfa" role="note">
      <strong>Antes de seguir: descargue Google Authenticator en su celular</strong>
      <span className="small">Es gratis. Le dará un código nuevo cada 30 segundos para entrar a Galenum.</span>
      <div className="row">
        <a className="btn btn-secondary" href={PLAY_STORE} target="_blank" rel="noopener noreferrer">Android (Play Store)</a>
        <a className="btn btn-secondary" href={APP_STORE} target="_blank" rel="noopener noreferrer">iPhone (App Store)</a>
      </div>
      <span className="muted small">Si ya usa Microsoft Authenticator, 1Password o Authy, también sirven.</span>
    </div>
    <ol className="pasos-mfa">
      <li>
        Abra la app de autenticación en su celular (si la acaba de instalar, ábrala y acepte sus pasos iniciales).
      </li>
      <li>
        En la app, agregue una cuenta y escanee este código:
        <div className="qr-mfa">{qr ? <img src={qr} width={200} height={200} alt="Código QR para su app de autenticación" /> : <span className="muted small">Generando el código…</span>}</div>
        {verClave ? (
          <p className="small">Clave para escribir a mano: <code className="clave-mfa">{secreto.replace(/(.{4})/g, "$1 ").trim()}</code></p>
        ) : (
          <button type="button" className="btn-link small" onClick={() => setVerClave(true)}>¿No puede escanear? Escriba la clave a mano</button>
        )}
      </li>
      <li>Escriba abajo el código de 6 dígitos que aparece en la app.</li>
    </ol>
    </>
  );
}

function FormularioCodigo({ etiquetaBoton, permitirRecuperacion, permitirRecordar, deshabilitado, enviar }: {
  etiquetaBoton: string;
  permitirRecuperacion?: boolean;
  permitirRecordar?: boolean;
  deshabilitado?: boolean;
  enviar: (codigo: string, recordar: boolean) => Promise<void>;
}) {
  const [codigo, setCodigo] = useState("");
  const [recuperacion, setRecuperacion] = useState(false);
  const [recordar, setRecordar] = useState(false);
  const mutacion = useMutation({ mutationFn: () => enviar(codigo.trim(), recordar) });
  return (
    <form className="stack" onSubmit={(e: FormEvent) => { e.preventDefault(); mutacion.mutate(); }}>
      {recuperacion ? (
        <label>
          Código de recuperación
          <input value={codigo} onChange={(e) => setCodigo(e.target.value.toUpperCase())} placeholder="XXXX-XXXX"
                 autoComplete="off" maxLength={9} required autoFocus />
        </label>
      ) : (
        <label>
          Código de 6 dígitos
          <input className="codigo-mfa" value={codigo} onChange={(e) => setCodigo(e.target.value.replace(/\D/g, "").slice(0, 6))}
                 inputMode="numeric" autoComplete="one-time-code" pattern="\d{6}" maxLength={6} required autoFocus />
        </label>
      )}
      {permitirRecordar && (
        <label className="check">
          <input type="checkbox" checked={recordar} onChange={(e) => setRecordar(e.target.checked)} />
          Recordar este equipo 30 días (no lo marque en un computador público)
        </label>
      )}
      {mutacion.error && <p className="alert alert-error">{mutacion.error.message}</p>}
      <button className="btn" disabled={mutacion.isPending || deshabilitado}>{mutacion.isPending ? "Verificando…" : etiquetaBoton}</button>
      {permitirRecuperacion && (
        <button type="button" className="btn-link small center" onClick={() => { setRecuperacion(!recuperacion); setCodigo(""); mutacion.reset(); }}>
          {recuperacion ? "Usar el código de la app" : "¿No tiene el celular? Use un código de recuperación"}
        </button>
      )}
    </form>
  );
}

/** Se muestran una sola vez: la API solo guarda su huella. */
function CodigosRecuperacion({ codigos, onListo }: { codigos: string[]; onListo: () => void }) {
  const [guardados, setGuardados] = useState(false);
  const [copiado, setCopiado] = useState(false);
  const texto = `Códigos de recuperación de Galenum\nCada uno sirve una sola vez, si no tiene el celular a mano.\n\n${codigos.join("\n")}\n`;
  function descargar() {
    const url = URL.createObjectURL(new Blob([texto], { type: "text/plain;charset=utf-8" }));
    const a = Object.assign(document.createElement("a"), { href: url, download: "galenum-codigos-recuperacion.txt" });
    a.click();
    URL.revokeObjectURL(url);
  }
  return (
    <>
      <h2 className="row"><ShieldCheck size={22} aria-hidden /> Guarde sus códigos de recuperación</h2>
      <p className="muted">
        Si pierde o cambia el celular, con uno de estos códigos podrá ingresar. Cada uno sirve una sola vez. Guárdelos en
        un lugar seguro (no en el mismo celular): <strong>no podrá volver a verlos</strong>.
      </p>
      <ul className="codigos-mfa">{codigos.map((c) => <li key={c}><code>{c}</code></li>)}</ul>
      <div className="row">
        <button type="button" className="btn btn-secondary" onClick={descargar}>Descargar</button>
        <button type="button" className="btn btn-secondary" onClick={() => {
          navigator.clipboard?.writeText(texto).then(() => setCopiado(true), () => setCopiado(false));
        }}>{copiado ? "Copiados" : "Copiar"}</button>
      </div>
      <label className="check">
        <input type="checkbox" checked={guardados} onChange={(e) => setGuardados(e.target.checked)} />
        Ya guardé mis códigos en un lugar seguro
      </label>
      <button type="button" className="btn" disabled={!guardados} onClick={onListo}>Continuar</button>
    </>
  );
}

// ---------------------------------------------------------------- Seguridad (con la sesión iniciada)

const fecha = new Intl.DateTimeFormat("es-CO", { dateStyle: "long" });

/** Activar, regenerar códigos, olvidar equipos y desactivar (si no es obligatoria). */
export function SeguridadCuenta({ api }: { api: Api }) {
  const queryClient = useQueryClient();
  const estado = useQuery({
    queryKey: ["mfa-estado"],
    queryFn: async () => unwrap(await api.GET("/api/v1/mfa")),
  });
  const [modo, setModo] = useState<null | "activar" | "codigos" | "desactivar">(null);
  const [codigos, setCodigos] = useState<string[] | null>(null);
  const refrescar = () => queryClient.invalidateQueries({ queryKey: ["mfa-estado"] });
  const config = useQuery({
    queryKey: ["mfa-configuracion"],
    enabled: modo === "activar",
    staleTime: Infinity,
    gcTime: 0,
    queryFn: async () => unwrap(await api.POST("/api/v1/mfa/configuracion")),
  });
  const olvidar = useMutation({
    mutationFn: async () => expectOk(await api.DELETE("/api/v1/mfa/equipos")),
    onSuccess: refrescar,
  });

  const e = estado.data;
  if (codigos) {
    return (
      <section className="card stack seguridad">
        <CodigosRecuperacion codigos={codigos} onListo={() => { setCodigos(null); setModo(null); refrescar(); }} />
      </section>
    );
  }
  return (
    <section className="card stack seguridad">
      <h2 className="row"><ShieldCheck size={22} aria-hidden /> Verificación en dos pasos</h2>
      {estado.error && <p className="alert alert-error">{estado.error.message}</p>}
      {e && !e.activo && modo !== "activar" && (
        <>
          <p className="muted">
            Además de la contraseña, al ingresar se pide un código que cambia cada 30 segundos en su celular. Si alguien
            obtiene su contraseña, no podrá entrar sin su celular.
          </p>
          <div><button className="btn" onClick={() => setModo("activar")}>Activar</button></div>
        </>
      )}
      {modo === "activar" && (
        <>
          {config.error && <p className="alert alert-error">{config.error.message}</p>}
          {config.data && <PasosConfiguracion uri={config.data.uri!} secreto={config.data.secreto!} />}
          <FormularioCodigo etiquetaBoton="Activar" deshabilitado={!config.data} enviar={async (codigo) => {
            setCodigos(unwrap(await api.POST("/api/v1/mfa/activacion", { body: { codigo } })).codigos ?? []);
          }} />
          <div><button type="button" className="btn-link small" onClick={() => setModo(null)}>Cancelar</button></div>
        </>
      )}
      {e?.activo && (
        <>
          <p>
            <span className="badge badge-ok">Activa</span>{" "}
            {e.activadaEn && <span className="muted small">desde el {fecha.format(new Date(e.activadaEn))}</span>}
          </p>
          <dl className="datos-mfa">
            <dt>Códigos de recuperación sin usar</dt>
            <dd>{e.codigosRestantes}{(e.codigosRestantes ?? 0) <= 3 && <span className="badge badge-warn">Genere unos nuevos</span>}</dd>
            <dt>Equipos recordados</dt>
            <dd>
              {e.equiposRecordados}
              {(e.equiposRecordados ?? 0) > 0 && (
                <button className="btn-link small" disabled={olvidar.isPending} onClick={() => olvidar.mutate()}>
                  Olvidarlos (pedirán el código otra vez)
                </button>
              )}
            </dd>
          </dl>
          {modo === null && (
            <div className="row">
              <button className="btn btn-secondary" onClick={() => setModo("codigos")}>Generar códigos de recuperación nuevos</button>
              {!e.obligatoria && <button className="btn-link danger" onClick={() => setModo("desactivar")}>Desactivar</button>}
            </div>
          )}
          {e.obligatoria && (
            <p className="muted small">
              Es obligatoria para su cuenta porque administra un consultorio o la plataforma, así que no se puede
              desactivar. Si pierde el celular y los códigos, escríbanos a {contactoSoporte} para restablecerla.
            </p>
          )}
          {modo === "codigos" && (
            <>
              <p className="muted small">Los códigos anteriores dejarán de servir. Confirme con un código de su app.</p>
              <FormularioCodigo etiquetaBoton="Generar códigos" permitirRecuperacion enviar={async (codigo) => {
                setCodigos(unwrap(await api.POST("/api/v1/mfa/codigos-recuperacion", { body: { codigo } })).codigos ?? []);
              }} />
              <div><button type="button" className="btn-link small" onClick={() => setModo(null)}>Cancelar</button></div>
            </>
          )}
          {modo === "desactivar" && (
            <>
              <p className="muted small">Su cuenta quedará protegida solo con la contraseña. Confirme con un código de su app.</p>
              <FormularioCodigo etiquetaBoton="Desactivar" permitirRecuperacion enviar={async (codigo) => {
                expectOk(await api.POST("/api/v1/mfa/desactivacion", { body: { codigo } }));
                setModo(null);
                await refrescar();
              }} />
              <div><button type="button" className="btn-link small" onClick={() => setModo(null)}>Cancelar</button></div>
            </>
          )}
        </>
      )}
    </section>
  );
}
