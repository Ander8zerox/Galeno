package com.galeno.shared.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.event.EventListener;
import org.springframework.web.filter.OncePerRequestFilter;

import com.galeno.shared.tenancy.TenantContext;

/**
 * Consultorio suspendido = solo lectura: deja pasar las consultas (GET), las descargas y lo necesario para
 * ponerse al día (sesión y suscripción); rechaza con 402 todo lo que registra algo nuevo.
 *
 * <p>No se bloquea el acceso completo: el consultorio debe poder consultar y entregar sus historias clínicas
 * aunque no haya pagado (Res. 1995 de 1999).
 */
public class SuscripcionFilter extends OncePerRequestFilter {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final Set<String> LECTURA = Set.of("GET", "HEAD", "OPTIONS");
    private static final String[] PERMITIDAS = {"/api/v1/auth/", "/api/v1/suscripcion"};

    private final EstadoSuscripcionVerifier verifier;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public SuscripcionFilter(EstadoSuscripcionVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID tenant = TenantContext.get();
        if (tenant != null && !TenantContext.SYSTEM.equals(tenant) && !LECTURA.contains(request.getMethod())
                && !permitida(request.getRequestURI()) && soloLectura(tenant)) {
            response.setStatus(402);
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"title":"Payment Required","status":402,"detail":"La suscripción del consultorio está suspendida: puede consultar y descargar, pero no registrar información nueva. Póngase al día en Suscripción."}""");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Al aprobar un pago o reactivar, para que aplique sin esperar a que venza la caché. */
    @EventListener
    public void invalidar(SuscripcionCambiada evento) {
        cache.remove(evento.consultorioId());
    }

    private static boolean permitida(String uri) {
        for (String p : PERMITIDAS) {
            if (uri.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private boolean soloLectura(UUID tenant) {
        Entry e = cache.get(tenant);
        if (e == null || e.expira().isBefore(Instant.now())) {
            e = new Entry(verifier.soloLectura(tenant), Instant.now().plus(CACHE_TTL));
            cache.put(tenant, e);
        }
        return e.soloLectura();
    }

    private record Entry(boolean soloLectura, Instant expira) {
    }
}
