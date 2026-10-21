import createClient, { type Middleware } from "openapi-fetch";
import type { components, paths } from "./schema";

/** Tipos generados desde contracts/openapi.json (npm run gen:api). */
export type Schemas = components["schemas"];

/** Error de la API en formato problem+json (RFC 9457). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly errores: Record<string, string> = {},
  ) {
    super(message);
  }
}

let token: string | null = null;
let onUnauthorized: () => void = () => {};

export function setToken(newToken: string | null) {
  token = newToken;
}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

const auth: Middleware = {
  onRequest({ request }) {
    if (token) request.headers.set("Authorization", `Bearer ${token}`);
    return request;
  },
  onResponse({ response, request }) {
    if (response.status === 401 && !request.url.endsWith("/auth/login")) onUnauthorized();
    return response;
  },
};

export const api = createClient<paths>({ baseUrl: "/" });
api.use(auth);

async function problema(res: Response): Promise<ApiError> {
  const p = (await res.json().catch(() => ({}))) as { detail?: string; errores?: Record<string, string> };
  return new ApiError(res.status, p.detail ?? "Error inesperado", p.errores);
}

/** Sube un archivo (multipart). openapi-fetch no maneja bien FormData, así que va con fetch directo. */
export async function subirArchivo<T>(ruta: string, datos: FormData): Promise<T> {
  const res = await fetch(ruta, { method: "POST", body: datos, headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (res.status === 401) onUnauthorized();
  if (!res.ok) throw await problema(res);
  return (await res.json()) as T;
}

/**
 * Descarga con el token (un enlace normal no lo enviaría) y la guarda con el nombre dado. Devuelve una URL local del
 * archivo, válida 5 minutos, para abrirlo también en una pestaña (algunos navegadores guardan sin avisar).
 */
export async function descargarArchivo(ruta: string, nombre: string): Promise<string> {
  const res = await fetch(ruta, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (res.status === 401) onUnauthorized();
  if (!res.ok) throw await problema(res);
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = nombre;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 5 * 60_000);
  return url;
}

/** Para respuestas sin cuerpo (204): lanza ApiError si la petición falló. */
export function expectOk(result: { error?: unknown; response: Response }): void {
  if (!result.response.ok) {
    const problem = (result.error ?? {}) as { detail?: string; errores?: Record<string, string> };
    throw new ApiError(result.response.status, problem.detail ?? "Error inesperado", problem.errores);
  }
}

/** Devuelve `data` o lanza ApiError con el detalle del problema. */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.error !== undefined || result.data === undefined) {
    const problem = (result.error ?? {}) as { detail?: string; errores?: Record<string, string> };
    throw new ApiError(result.response.status, problem.detail ?? "Error inesperado", problem.errores);
  }
  return result.data;
}
