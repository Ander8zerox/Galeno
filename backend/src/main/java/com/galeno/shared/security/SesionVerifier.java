package com.galeno.shared.security;

import java.util.Set;
import java.util.UUID;

/** Lo implementa el módulo de identidad; lo usa {@link SesionVigenteFilter}. */
public interface SesionVerifier {

    /** Permisos actuales de la membresía, o {@code null} si está suspendida o ya no existe. */
    Set<Permiso> permisosVigentes(UUID membresiaId);
}
