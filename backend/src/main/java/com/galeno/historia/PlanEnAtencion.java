package com.galeno.historia;

import java.util.Collection;
import java.util.UUID;

/**
 * Lo que la historia necesita del plan de tratamiento. Lo implementa el paquete de tratamiento: así la historia enlaza
 * un procedimiento realizado con su ítem del plan sin depender de ese paquete.
 */
public interface PlanEnAtencion {

    /** Si el consultorio usa el plan de tratamiento (y por eso muestra los procedimientos realizados). */
    boolean disponible();

    /**
     * Al guardar la atención: los ítems deben ser del plan vigente y aceptado del paciente, y estar pendientes.
     *
     * @throws com.galeno.shared.web.ApiExceptionHandler.ApiProblem si alguno no lo está
     */
    void validar(UUID pacienteId, Collection<UUID> itemIds);

    /** Al firmar la atención: los ítems quedan realizados en ella. */
    void realizados(UUID pacienteId, UUID atencionId, Collection<UUID> itemIds);
}
