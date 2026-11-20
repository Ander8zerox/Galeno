package com.galeno.historia;

import java.util.Optional;

/**
 * El servicio habilitado del consultorio en el REPS (p. ej. 328 medicina general), si lo configuró para el RIPS.
 * Decide qué consulta se sugiere en cada atención. Lo implementa el módulo RIPS, que también da los datos del encuentro
 * por defecto (los del prestador) y el nombre de los códigos de sus tablas de referencia.
 */
public interface ServicioHabilitado {

    Optional<String> codigo();

    /** Modalidad, grupo de servicios y causa externa del prestador, con los que arranca cada atención. */
    default Optional<Encuentro> encuentroPorDefecto() {
        return Optional.empty();
    }

    /** Nombre de un código de una tabla de SISPRO (p. ej. "modalidad_atencion", "01"), para mostrarlo y en el PDF. */
    default Optional<String> nombreReferencia(String tabla, String codigo) {
        return Optional.empty();
    }

    /** El pagador de la atención (bloque 2 del RDA, "entidad responsable"), si el RIPS lo tiene. */
    default Optional<String> entidadResponsable(java.util.UUID atencionId) {
        return Optional.empty();
    }

    record Encuentro(String modalidadAtencion, String grupoServicios, String causaExterna) {
    }
}
