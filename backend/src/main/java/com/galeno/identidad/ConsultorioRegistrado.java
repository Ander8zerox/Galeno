package com.galeno.identidad;

import java.util.UUID;

/**
 * Se publica cuando un consultorio nuevo queda activo (registro web confirmado).
 *
 * @param codigoPartner el código de referido que trajo el registro, o {@code null}
 */
public record ConsultorioRegistrado(UUID consultorioId, UUID administradorId, String codigoPartner, String nit) {

    public ConsultorioRegistrado(UUID consultorioId) {
        this(consultorioId, null, null, null);
    }
}
