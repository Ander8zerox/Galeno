package com.galeno.tratamiento;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.formatos.FormatosService;
import com.galeno.historia.PlanEnAtencion;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * El plan de tratamiento visto desde la atención: un procedimiento realizado puede ser un ítem del plan, y al firmar la
 * atención ese ítem queda realizado. No depende de la historia (la historia depende de esta interfaz).
 */
@Component
class ItemsEnAtencion implements PlanEnAtencion {

    private final PlanRepository planes;
    private final ItemPlanRepository items;
    private final FormatosService formatos;

    ItemsEnAtencion(PlanRepository planes, ItemPlanRepository items, FormatosService formatos) {
        this.planes = planes;
        this.items = items;
        this.formatos = formatos;
    }

    @Override
    public boolean disponible() {
        return formatos.activoEnConsultorio(TratamientosService.FORMATO_REQUERIDO);
    }

    @Override
    @Transactional(readOnly = true)
    public void validar(UUID pacienteId, Collection<UUID> itemIds) {
        pendientes(pacienteId, itemIds);
    }

    @Override
    @Transactional
    public void realizados(UUID pacienteId, UUID atencionId, Collection<UUID> itemIds) {
        List<ItemPlan> lista = pendientes(pacienteId, itemIds);
        lista.forEach(i -> i.realizar(atencionId));
        items.flush();
        for (UUID planId : new HashSet<>(lista.stream().map(ItemPlan::getPlanId).toList())) {
            PlanTratamiento p = planes.findById(planId).orElseThrow();
            p.tocar();
            boolean quedaAlgo = items.findByPlanIdOrderByFaseAscOrdenAsc(planId).stream()
                    .anyMatch(i -> !i.aceptado() || i.getEstado() == ItemPlan.Estado.PENDIENTE);
            if (!quedaAlgo) {
                p.cerrar(PlanTratamiento.Estado.TERMINADO, null);
            }
        }
    }

    /** Del plan aceptado y vigente del paciente, aceptados y pendientes; si no, un error que dice cuál y por qué. */
    private List<ItemPlan> pendientes(UUID pacienteId, Collection<UUID> itemIds) {
        List<ItemPlan> lista = items.findAllById(itemIds);
        if (lista.size() != new HashSet<>(itemIds).size()) {
            throw ApiProblem.badRequest("Un procedimiento del plan de tratamiento ya no existe: recargue la atención");
        }
        for (ItemPlan i : lista) {
            PlanTratamiento p = planes.findById(i.getPlanId()).orElseThrow();
            String nombre = i.getCodigoCups() + (i.getDiente() == null ? "" : " (diente " + i.getDiente() + ")");
            if (!p.getPacienteId().equals(pacienteId)) {
                throw ApiProblem.badRequest("El procedimiento " + nombre + " es del plan de otro paciente");
            }
            if (p.getEstado() != PlanTratamiento.Estado.ACEPTADO || !i.aceptado()) {
                throw ApiProblem.conflict("El procedimiento " + nombre + " no está aceptado por el paciente en un plan vigente");
            }
            if (i.getEstado() != ItemPlan.Estado.PENDIENTE) {
                throw ApiProblem.conflict("El procedimiento " + nombre + " del plan ya está "
                        + (i.getEstado() == ItemPlan.Estado.REALIZADO ? "realizado en otra atención" : "cancelado")
                        + ": quítelo de esta atención");
            }
        }
        return lista;
    }
}
