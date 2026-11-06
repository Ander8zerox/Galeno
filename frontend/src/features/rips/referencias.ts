import { useQuery } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";

export type Referencia = Schemas["ReferenciaVista"];

/** Tabla de referencia de SISPRO completa (no cambian durante el día: se guardan en caché una hora). */
/** @param activo falso: no consulta (para un campo que a veces no usa tabla) */
export function useReferencia(tabla: string, activo = true) {
  return useQuery({
    queryKey: ["rips-referencia", tabla],
    enabled: activo,
    queryFn: async () => unwrap(await api.GET("/api/v1/rips/referencias/{tabla}", { params: { path: { tabla } } })),
    staleTime: 60 * 60_000,
  });
}

/** Búsqueda en una tabla larga (municipios, servicios). */
export function useBusquedaReferencia(tabla: string, q: string) {
  return useQuery({
    queryKey: ["rips-referencia", tabla, q],
    enabled: q.trim().length >= 2,
    queryFn: async () => unwrap(await api.GET("/api/v1/rips/referencias/{tabla}", { params: { path: { tabla }, query: { q } } })),
  });
}

/** "MEDELLÍN" → "Medellín"; las tablas de SISPRO vienen en mayúsculas. */
export function nombrePropio(s?: string | null): string {
  if (!s) return "";
  const minusculas = new Set(["de", "del", "la", "las", "los", "el", "y", "e", "en"]);
  return s.toLowerCase().split(/(\s+|-|,)/).map((p, i) =>
    i > 0 && minusculas.has(p) ? p : p.charAt(0).toUpperCase() + p.slice(1)).join("");
}

export const COLOMBIA = "170";

/** Lo que le falta a cada paciente para el RIPS (los de las citas del día), en una sola consulta. */
export function useFaltantesRips(pacienteIds: string[], activo: boolean) {
  const ids = [...new Set(pacienteIds)].sort();
  return useQuery({
    queryKey: ["rips-faltantes", ids],
    enabled: activo && ids.length > 0,
    queryFn: async () => unwrap(await api.GET("/api/v1/rips/pacientes/faltantes", { params: { query: { ids } } })),
    select: (lista) => new Map(lista.map((f) => [f.pacienteId!, f.faltantes ?? []])),
  });
}
