import { useQuery } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";

export type Modulo = NonNullable<Schemas["ModuloVista"]["modulo"]>;

/**
 * Módulos opcionales del consultorio. Sin activar, sus pantallas y campos no se muestran.
 *
 * @param conSesion falso antes de entrar a un consultorio (sin sesión la consulta respondería 401)
 */
export function useModulos(conSesion = true) {
  return useQuery({
    queryKey: ["modulos"],
    enabled: conSesion,
    queryFn: async () => unwrap(await api.GET("/api/v1/modulos")),
    staleTime: 60_000,
  });
}

export function useModuloActivo(modulo: Modulo, conSesion = true): boolean {
  return !!useModulos(conSesion).data?.some((m) => m.modulo === modulo && m.activo);
}

const PESOS = new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 });
export const pesos = (v?: number | null) => (v === undefined || v === null ? "—" : PESOS.format(v));
