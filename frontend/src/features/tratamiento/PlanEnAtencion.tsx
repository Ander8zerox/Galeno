import { useQuery } from "@tanstack/react-query";
import { api, ApiError, unwrap, type Schemas } from "../../api/client";
import { pesos } from "../rips/modulos";
import type { ProcedimientoForm } from "../rips/ProcedimientosEditor";

type Plan = Schemas["PlanTratamientoVista"];
type Item = Schemas["ItemPlanVista"];

/**
 * El plan de tratamiento del paciente para la atención. {@code disponible}: el consultorio usa el plan (el servidor no
 * respondió 404), y por eso la atención muestra los procedimientos realizados aunque no tenga el módulo de RIPS.
 */
export function usePlanDelPaciente(pacienteId: string) {
  const consulta = useQuery({
    queryKey: ["planes-tratamiento", pacienteId],
    retry: false,
    queryFn: async () => unwrap(await api.GET("/api/v1/tratamientos", { params: { query: { pacienteId } } })),
  });
  const noDisponible = consulta.error instanceof ApiError && consulta.error.status === 404;
  const vigente = consulta.data?.find((p) => p.estado === "ACEPTADO");
  return { disponible: consulta.isSuccess && !noDisponible, aceptado: vigente };
}

/** Valor por unidad que el paciente aceptó (con el descuento repartido), para el procedimiento realizado. */
const valorAceptado = (i: Item) => Math.round(((i.total ?? 0) / (i.cantidad || 1)) * 100) / 100;

export function procedimientoDelItem(i: Item): ProcedimientoForm {
  return { codigoCups: i.codigoCups!, descripcion: i.descripcion, cantidad: i.cantidad ?? 1, valorUnitario: valorAceptado(i),
    diente: i.diente ?? undefined, superficies: i.superficies ?? undefined, itemPlanId: i.id };
}

/**
 * Los procedimientos aceptados y pendientes del plan: "Realizar en esta atención" los agrega a los procedimientos
 * realizados; al firmar la atención quedan realizados en el plan.
 */
export function PlanEnAtencion({ plan, procedimientos, onAgregar }: {
  plan: Plan; procedimientos: ProcedimientoForm[]; onAgregar: (p: ProcedimientoForm) => void;
}) {
  const yaEnAtencion = new Set(procedimientos.map((p) => p.itemPlanId).filter(Boolean));
  const pendientes = (plan.items ?? []).filter((i) => i.aceptacion != null && i.estado === "PENDIENTE" && !yaEnAtencion.has(i.id));
  const enEsta = (plan.items ?? []).filter((i) => yaEnAtencion.has(i.id));
  return (
    <div className="nota stack-sm">
      <div className="row between">
        <strong>Plan de tratamiento</strong>
        <span className="muted small">{plan.totales?.realizados ?? 0} de {plan.totales?.procedimientos ?? 0} realizados · pendiente {pesos(plan.totales?.pendiente)}</span>
      </div>
      {pendientes.map((i) => (
        <div key={i.id} className="row between">
          <span>
            {i.diente && <strong>{i.diente}{i.superficies ? ` ${i.superficies}` : ""} · </strong>}
            {i.descripcion} <span className="muted small">Fase {i.fase} · {pesos(i.total)}</span>
          </span>
          <button type="button" className="btn btn-secondary" onClick={() => onAgregar(procedimientoDelItem(i))}>Realizar en esta atención</button>
        </div>
      ))}
      {!pendientes.length && <span className="muted small">No quedan procedimientos pendientes del plan.</span>}
      {!!enEsta.length && (
        <span className="muted small">
          {enEsta.length === 1 ? "1 procedimiento del plan va en esta atención: queda realizado al firmarla."
            : `${enEsta.length} procedimientos del plan van en esta atención: quedan realizados al firmarla.`}
        </span>
      )}
    </div>
  );
}
