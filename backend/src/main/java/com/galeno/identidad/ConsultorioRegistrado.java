package com.galeno.identidad;

import java.util.UUID;

/** Se publica cuando un consultorio nuevo queda activo (registro web confirmado). */
public record ConsultorioRegistrado(UUID consultorioId) {
}
