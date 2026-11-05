package com.galeno.historia;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Precio de cada CUPS en el consultorio. Lo implementa el módulo de RIPS: así la historia propone el valor al
 * registrar un procedimiento sin depender de ese módulo.
 */
public interface ListaPrecios {

    /**
     * Los que tienen precio; los demás no aparecen en el mapa.
     *
     * @param contexto la atención (o, si aún no existe, el paciente y la cita): el precio puede depender de su pagador.
     *                 Nulo, la lista general.
     */
    Map<String, BigDecimal> precios(Collection<String> codigosCups, Contexto contexto);

    record Contexto(UUID pacienteId, UUID citaId, UUID atencionId) {
    }
}
