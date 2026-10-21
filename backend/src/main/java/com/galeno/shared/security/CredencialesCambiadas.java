package com.galeno.shared.security;

import java.util.UUID;

/** La contraseña de una cuenta cambió: sus tokens anteriores dejan de servir. */
public record CredencialesCambiadas(UUID usuarioId) {
}
