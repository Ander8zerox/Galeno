package com.galeno.historia;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Precio de cada CUPS en el consultorio. Lo implementa el módulo de RIPS: así la historia propone el valor al
 * registrar un procedimiento sin depender de ese módulo.
 */
public interface ListaPrecios {

    /** Los que tienen precio; los demás no aparecen en el mapa. */
    Map<String, BigDecimal> precios(Collection<String> codigosCups);
}
