package com.galeno.historia;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los medicamentos formulados en cada atención (bloque 7 del RDA), en una línea cada uno. Lo implementa el módulo de
 * fórmulas (historia no depende de él).
 */
public interface MedicamentosDeAtencion {

    Map<UUID, List<String>> lineas(Collection<UUID> atencionIds);
}
