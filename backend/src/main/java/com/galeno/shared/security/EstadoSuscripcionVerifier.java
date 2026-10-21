package com.galeno.shared.security;

import java.util.UUID;

/** Lo implementa el módulo de suscripciones; lo usa {@link SuscripcionFilter}. */
public interface EstadoSuscripcionVerifier {

    /** El consultorio está suspendido (por falta de pago o por la plataforma) y solo puede consultar. */
    boolean soloLectura(UUID consultorioId);
}
