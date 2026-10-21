package com.galeno.shared.security;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.galeno.shared.tenancy.TenantFilter;

/**
 * Usuario autenticado de la petición actual, leído del JWT.
 *
 * @param tenantId     consultorio de la sesión; {@code null} en un token de selección
 * @param membresiaId  membresía de la persona en ese consultorio; {@code null} en un token de selección
 */
public record AuthUser(UUID id, UUID tenantId, UUID membresiaId, String nombre, Set<String> authorities) {

    public static AuthUser current() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth)) {
            throw new IllegalStateException("No hay un usuario autenticado");
        }
        var jwt = auth.getToken();
        return new AuthUser(
                UUID.fromString(jwt.getSubject()),
                uuidOrNull(jwt.getClaimAsString(TenantFilter.CLAIM)),
                uuidOrNull(jwt.getClaimAsString(TokenService.CLAIM_MEMBRESIA)),
                jwt.getClaimAsString("name"),
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
    }

    public boolean puede(Permiso permiso) {
        return authorities.contains(permiso.name());
    }

    /** Rol en el consultorio de la sesión (ADMIN, MEDICO, SECRETARIO). */
    public boolean tieneRol(String rol) {
        return authorities.contains("ROLE_" + rol);
    }

    private static UUID uuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
