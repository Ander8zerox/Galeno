import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, setToken, setUnauthorizedHandler, unwrap, type Schemas } from "../api/client";
import { correoDelToken, equiposRecordados, type RequisitoMfa } from "../components/DosPasos";

/**
 * Toda la autenticación vive en este módulo. Al pasar a Cognito solo cambia este archivo:
 * el resto de la app usa useAuth() y el cliente de API.
 *
 * Flujo: login → (si usa verificación en dos pasos, o debe configurarla) código → (si la persona tiene varios
 * consultorios) elegir uno → sesión en ese consultorio.
 */
export type Me = Schemas["MeResponse"];
export type ConsultorioOpcion = Schemas["ConsultorioOpcion"];
type SesionResponse = Schemas["SesionResponse"];
export type Permiso = NonNullable<Me["permisos"]>[number];
export type Rol = NonNullable<Me["roles"]>[number];

type Session = { token: string; me: Me; expiresAt: string };

interface AuthState {
  me: Me | null;
  /** Consultorios entre los que debe elegir tras el login (null si no hay selección pendiente). */
  seleccion: ConsultorioOpcion[] | null;
  /** Si no es null, la contraseña ya pasó y falta el código de la verificación en dos pasos (o configurarla). */
  mfa: RequisitoMfa | null;
  login: (email: string, password: string) => Promise<void>;
  /** Manda el código; la respuesta se aplica con {@link terminarMfa} (después de mostrar los códigos de recuperación). */
  completarMfa: (codigo: string, recordar: boolean) => Promise<SesionResponse>;
  terminarMfa: (res: SesionResponse) => Promise<void>;
  elegirConsultorio: (consultorioId: string) => Promise<void>;
  /** Para respuestas de sesión obtenidas fuera del login (p. ej. al aceptar una invitación). */
  iniciar: (res: SesionResponse) => Promise<void>;
  logout: () => void;
  puede: (permiso: Permiso) => boolean;
}

const STORAGE_KEY = "galeno.session";
const AuthContext = createContext<AuthState | null>(null);

function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Session;
    return new Date(session.expiresAt) > new Date() && session.me ? session : null;
  } catch {
    return null;
  }
}

function saveSession(session: Session | null) {
  try {
    if (session) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    /* almacenamiento no disponible: la sesión dura lo que la pestaña */
  }
}

// El token se fija al cargar el módulo, antes de que cualquier componente haga peticiones.
const initialSession = loadSession();
setToken(initialSession?.token ?? null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [me, setMe] = useState<Me | null>(initialSession?.me ?? null);
  const [seleccion, setSeleccion] = useState<ConsultorioOpcion[] | null>(null);
  const [mfa, setMfa] = useState<{ requisito: RequisitoMfa; email?: string } | null>(null);

  const logout = useCallback(() => {
    saveSession(null);
    setToken(null);
    setMe(null);
    setSeleccion(null);
    setMfa(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => setUnauthorizedHandler(logout), [logout]);

  const iniciar = useCallback(async (res: SesionResponse) => {
    if (!res.accessToken || !res.expiresAt) throw new Error("Respuesta de sesión incompleta");
    setToken(res.accessToken);
    if (res.mfa) {
      // Token pendiente: solo sirve para el código. No se guarda en sessionStorage.
      setMe(null);
      setSeleccion(null);
      setMfa({ requisito: res.mfa, email: correoDelToken(res.accessToken) });
      return;
    }
    if (!res.completa) {
      setMe(null);
      setSeleccion(res.consultorios ?? []);
      return;
    }
    const nuevo = unwrap(await api.GET("/api/v1/auth/me"));
    // Al cambiar de consultorio no puede quedar en caché nada del anterior
    queryClient.clear();
    saveSession({ token: res.accessToken, me: nuevo, expiresAt: res.expiresAt });
    setSeleccion(null);
    setMe(nuevo);
  }, [queryClient]);

  const login = useCallback(async (email: string, password: string) => {
    const equipo = equiposRecordados.leer(email);
    await iniciar(unwrap(await api.POST("/api/v1/auth/login", { body: { email, password, equipo } })));
  }, [iniciar]);

  const completarMfa = useCallback(async (codigo: string, recordar: boolean) =>
    unwrap(await api.POST("/api/v1/auth/mfa", { body: { codigo, recordar } })), []);

  const terminarMfa = useCallback(async (res: SesionResponse) => {
    if (mfa?.email) equiposRecordados.guardar(mfa.email, res.equipo);
    setMfa(null);
    await iniciar(res);
  }, [iniciar, mfa]);

  const elegirConsultorio = useCallback(async (consultorioId: string) => {
    await iniciar(unwrap(await api.POST("/api/v1/auth/sesion", { body: { consultorioId } })));
  }, [iniciar]);

  const puede = useCallback((permiso: Permiso) => !!me?.permisos?.includes(permiso), [me]);

  const value = useMemo(
    () => ({ me, seleccion, mfa: mfa?.requisito ?? null, login, completarMfa, terminarMfa, elegirConsultorio, iniciar, logout, puede }),
    [me, seleccion, mfa, login, completarMfa, terminarMfa, elegirConsultorio, iniciar, logout, puede],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth debe usarse dentro de AuthProvider");
  return ctx;
}

export const ROL_NOMBRE: Record<Rol, string> = { ADMIN: "Administrador", MEDICO: "Médico", SECRETARIO: "Secretario" };
export const ROLES: Rol[] = ["ADMIN", "MEDICO", "SECRETARIO"];
