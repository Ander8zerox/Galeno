package com.galeno.identidad;

import java.util.Optional;

/**
 * Valida el código de partner que trae quien registra un consultorio. Lo implementa el módulo de partners
 * (identidad no depende de él).
 */
public interface CodigosReferido {

    /** Nombre del partner si el código es de un partner activo. */
    Optional<String> partnerActivo(String codigo);
}
