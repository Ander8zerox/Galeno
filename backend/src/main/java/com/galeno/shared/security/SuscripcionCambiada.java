package com.galeno.shared.security;

import java.util.UUID;

/** La suscripción de un consultorio cambió (pago aprobado, suspensión, reactivación, cortesía). */
public record SuscripcionCambiada(UUID consultorioId) {
}
