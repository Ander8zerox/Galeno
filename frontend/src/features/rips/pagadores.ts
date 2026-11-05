import { useQuery } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../../api/client";

export type Pagador = Schemas["PagadorVista"];
export type TipoPagador = NonNullable<Pagador["tipo"]>;
export type Cobro = Schemas["CobroVista"];

export const TIPO_PAGADOR: Record<TipoPagador, string> = {
  EPS: "EPS", PREPAGADA: "Prepagada o plan voluntario", ARL: "ARL", SOAT: "SOAT",
  ENTIDAD_TERRITORIAL: "Secretaría de salud o ADRES", REGIMEN_ESPECIAL: "Régimen especial", OTRO: "Otro", PARTICULAR: "Particular",
};

/** Pagadores del consultorio (el particular siempre está). Los usan la cita, la ficha y la atención. */
export function usePagadores(activo = true) {
  return useQuery({
    queryKey: ["rips-pagadores"],
    enabled: activo,
    queryFn: async () => unwrap(await api.GET("/api/v1/rips/pagadores")),
    staleTime: 60_000,
  });
}

/** El cobro de cada cita del día (guardado o el que se propone), en una sola consulta. */
export function useCobrosCitas(citaIds: string[], activo: boolean) {
  const ids = [...new Set(citaIds)].sort();
  return useQuery({
    queryKey: ["rips-cobros-citas", ids],
    enabled: activo && ids.length > 0,
    queryFn: async () => unwrap(await api.GET("/api/v1/rips/cobros/citas", { params: { query: { ids } } })),
    select: (lista) => new Map(lista.map((c) => [c.citaId!, c.cobro!])),
  });
}

/**
 * El tipo de usuario del RIPS que corresponde al pagador: EPS contributiva → 01 (cotizante), subsidiada → 04,
 * particular → 12, ARL → 09, SOAT → 10, prepagada → 11. Es una sugerencia: un beneficiario es 02, por ejemplo.
 */
export function tipoUsuarioSugerido(p?: Pagador): string | undefined {
  switch (p?.tipo) {
    case "PARTICULAR": return "12";
    case "ARL": return "09";
    case "SOAT": return "10";
    case "PREPAGADA": return "11";
    case "EPS": return p.regimen === "S" ? "04" : p.regimen === "C" ? "01" : undefined;
    default: return undefined;
  }
}

/** El tipo de pagador según el "tipo" de la entidad en SISPRO ("7. EPS REGIMEN CONTRIBUTIVO" → EPS). */
export function tipoDeEntidad(tipoSispro?: string): TipoPagador {
  const n = (tipoSispro ?? "").split(".")[0].trim();
  if (["3", "4", "5", "7"].includes(n)) return "EPS";
  if (n === "8") return "PREPAGADA";
  if (n === "6") return "ARL";
  if (n === "10") return "SOAT";
  if (n === "9") return "REGIMEN_ESPECIAL";
  if (n === "1" || n === "2") return "ENTIDAD_TERRITORIAL";
  return "OTRO";
}

/** Sin cobro (no aplica), copago, cuota moderadora, pagos compartidos. El 04 (anticipo) no va en el RIPS. */
export const NO_APLICA = "05";
