package com.galeno.shared.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.galeno.shared.web.LimiteIntentos;

/**
 * Límite de peticiones por persona con sesión (cualquier token: consultorio, plataforma o partner), contra un bot
 * que entra con una cuenta válida (robada o de prueba) y lanza miles de consultas o registros. Los topes son
 * holgados para una persona real; las escrituras (crear, cambiar, borrar) tienen uno más bajo.
 *
 * <p>Las peticiones sin sesión las cubre {@link com.galeno.shared.web.LimiteFrecuenciaFilter}, por IP.
 */
public class LimiteUsuarioFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LimiteUsuarioFilter.class);
    private static final Duration MINUTO = Duration.ofMinutes(1);
    private static final Set<String> LECTURA = Set.of("GET", "HEAD", "OPTIONS");

    /** Instancia propia, como en el filtro por IP. */
    private final LimiteIntentos contadores;
    private final LimiteIntentos avisos = new LimiteIntentos();
    private final int porMinuto;
    private final int escriturasPorMinuto;

    public LimiteUsuarioFilter(int porMinuto, int escriturasPorMinuto) {
        this(new LimiteIntentos(), porMinuto, escriturasPorMinuto);
    }

    LimiteUsuarioFilter(LimiteIntentos contadores, int porMinuto, int escriturasPorMinuto) {
        this.contadores = contadores;
        this.porMinuto = porMinuto;
        this.escriturasPorMinuto = escriturasPorMinuto;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            chain.doFilter(request, response);
            return;
        }
        String usuario = auth.getName();
        if (!contadores.permitir("usuario:" + usuario, porMinuto, MINUTO)
                || escritura(request)
                && !contadores.permitir("usuario-escritura:" + usuario, escriturasPorMinuto, MINUTO)) {
            if (avisos.permitir(usuario, 1, MINUTO)) {
                log.warn("Límite de frecuencia por usuario alcanzado: {} {} {}", usuario, request.getMethod(),
                        request.getServletPath());
            }
            response.setStatus(429); // sin sendError: /error pasaría por la seguridad (401)
            response.setHeader("Retry-After", "60");
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"title":"Too Many Requests","status":429,"detail":"Demasiadas solicitudes seguidas. Espere un minuto e intente de nuevo."}""");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Crear, cambiar o borrar datos. Los pasos de entrada (código de dos pasos, elegir consultorio, configurar la app)
     * no cuentan: ya tienen su límite por IP y no deben gastar el cupo de quien apenas entra.
     */
    private static boolean escritura(HttpServletRequest request) {
        String ruta = request.getServletPath();
        return !LECTURA.contains(request.getMethod()) && !ruta.startsWith("/api/v1/auth/") && !ruta.startsWith("/api/v1/mfa")
                && !ruta.endsWith("/mfa");
    }
}
