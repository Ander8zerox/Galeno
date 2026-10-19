package com.galeno.shared.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.galeno.shared.tenancy.TenantFilter;

/**
 * Dos tipos de token:
 * <ul>
 *   <li><b>Selección</b> (10 min): la persona ya se autenticó pero aún no eligió consultorio. Solo sirve
 *   para listar sus consultorios y pedir el token de sesión.</li>
 *   <li><b>Sesión</b>: atado a un consultorio y a una membresía, con los permisos de esa membresía.</li>
 * </ul>
 */
@Service
public class TokenService {

    public static final String CLAIM_AUTHORITIES = "authorities";
    public static final String CLAIM_MEMBRESIA = "membresia_id";
    /** Authority de todo token de sesión; los de selección no la tienen. */
    public static final String MIEMBRO = "MIEMBRO";
    public static final String SELECCION = "SELECCION_CONSULTORIO";

    private static final Duration TTL_SELECCION = Duration.ofMinutes(10);

    private final JwtEncoder encoder;
    private final JwtProperties props;

    public TokenService(JwtEncoder encoder, JwtProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public IssuedToken issueSeleccion(UUID userId, String nombre, String email) {
        return issue(base(userId, nombre, email, TTL_SELECCION).claim(CLAIM_AUTHORITIES, List.of(SELECCION)), TTL_SELECCION);
    }

    public IssuedToken issueSesion(UUID userId, String nombre, String email, UUID tenantId, UUID membresiaId,
                                   Collection<String> roles, Collection<Permiso> permisos) {
        List<String> authorities = new ArrayList<>();
        authorities.add(MIEMBRO);
        roles.forEach(r -> authorities.add("ROLE_" + r));
        permisos.forEach(p -> authorities.add(p.name()));
        JwtClaimsSet.Builder claims = base(userId, nombre, email, props.ttl())
                .claim(TenantFilter.CLAIM, tenantId.toString())
                .claim(CLAIM_MEMBRESIA, membresiaId.toString())
                .claim(CLAIM_AUTHORITIES, authorities);
        return issue(claims, props.ttl());
    }

    private JwtClaimsSet.Builder base(UUID userId, String nombre, String email, Duration ttl) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("name", nombre)
                .claim("email", email);
    }

    private IssuedToken issue(JwtClaimsSet.Builder claims, Duration ttl) {
        JwtClaimsSet built = claims.build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, built)).getTokenValue(), built.getExpiresAt());
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
