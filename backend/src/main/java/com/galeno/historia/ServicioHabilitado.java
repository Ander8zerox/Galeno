package com.galeno.historia;

import java.util.Optional;

/**
 * El servicio habilitado del consultorio en el REPS (p. ej. 328 medicina general), si lo configuró para el RIPS.
 * Decide qué consulta se sugiere en cada atención. Lo implementa el módulo RIPS.
 */
public interface ServicioHabilitado {

    Optional<String> codigo();
}
