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
