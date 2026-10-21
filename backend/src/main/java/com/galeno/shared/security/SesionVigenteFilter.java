package com.galeno.shared.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * El JWT dura horas, pero si un administrador suspende a alguien o le quita un rol, el cambio debe
 * aplicar ya. Este filtro rechaza el token si la membresía ya no está activa o si el token trae
 * permisos que la membresía ya no tiene. La consulta se guarda en memoria 60 segundos por membresía.
 */
public class SesionVigenteFilter extends OncePerRequestFilter {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final Set<String> PERMISOS = Arrays.stream(Permiso.values()).map(Enum::name).collect(Collectors.toSet());

    private final SesionVerifier verifier;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Credenciales> credenciales = new ConcurrentHashMap<>();

    public SesionVigenteFilter(SesionVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Cualquier token (sesión, selección, plataforma) emitido antes del último cambio de contraseña
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && emitidoAntesDelCambio(jwt)) {
            rechazar(response, "Su contraseña cambió. Ingrese de nuevo.");
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && jwt.getToken().hasClaim(TokenService.CLAIM_MEMBRESIA)) {
            UUID membresiaId = UUID.fromString(jwt.getToken().getClaimAsString(TokenService.CLAIM_MEMBRESIA));
            Set<String> enToken = jwt.getAuthorities().stream().map(a -> a.getAuthority())
                    .filter(PERMISOS::contains).collect(Collectors.toSet());
            Set<String> vigentes = vigentes(membresiaId);
            if (vigentes == null || !vigentes.containsAll(enToken)) {
                rechazar(response, "Su acceso en este consultorio cambió. Ingrese de nuevo.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean emitidoAntesDelCambio(JwtAuthenticationToken jwt) {
        Instant emitido = jwt.getToken().getIssuedAt();
        if (emitido == null || jwt.getToken().getSubject() == null) {
            return false;
        }
        UUID usuarioId = UUID.fromString(jwt.getToken().getSubject());
        Credenciales c = credenciales.get(usuarioId);
        if (c == null || c.expira().isBefore(Instant.now())) {
            c = new Credenciales(verifier.credencialesCambiadasEn(usuarioId), Instant.now().plus(CACHE_TTL));
            credenciales.put(usuarioId, c);
        }
        // El iat del JWT va en segundos: un token emitido en el mismo segundo del cambio se acepta
        return c.cambiadasEn() != null && emitido.isBefore(c.cambiadasEn().truncatedTo(ChronoUnit.SECONDS));
    }

    @EventListener
    public void alCambiarCredenciales(CredencialesCambiadas evento) {
        credenciales.remove(evento.usuarioId());
    }

    /** Para que un cambio hecho por el administrador aplique sin esperar a que venza la caché. */
    public void invalidar(UUID membresiaId) {
        cache.remove(membresiaId);
    }

    private Set<String> vigentes(UUID membresiaId) {
        Entry entry = cache.get(membresiaId);
        if (entry == null || entry.expira().isBefore(Instant.now())) {
            Set<Permiso> permisos = verifier.permisosVigentes(membresiaId);
            entry = new Entry(permisos == null ? null : permisos.stream().map(Enum::name).collect(Collectors.toSet()),
                    Instant.now().plus(CACHE_TTL));
            cache.put(membresiaId, entry);
        }
        return entry.permisos();
    }

    private static void rechazar(HttpServletResponse response, String detalle) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"title":"Unauthorized","status":401,"detail":"%s"}""".formatted(detalle));
    }

    private record Entry(Set<String> permisos, Instant expira) {
    }

    private record Credenciales(Instant cambiadasEn, Instant expira) {
    }
}
