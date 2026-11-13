package com.galeno.shared.web;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite de peticiones por IP, antes de la seguridad: frena a un bot sin sesión antes de que gaste base de datos o
 * bcrypt. Dos niveles:
 * <ul>
 *   <li><b>General:</b> todas las rutas de la API, holgado para un consultorio con varias personas detrás de la misma
 *       IP (la del router).</li>
 *   <li><b>Rutas públicas sensibles:</b> login, código de dos pasos, recuperación, invitaciones, registro y validación
 *       de códigos de partner. Además de sus límites propios por correo.</li>
 * </ul>
 * Va justo después de {@link OrigenCloudFrontFilter}, así la IP ya es la del navegador (ver {@link IpCliente}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LimiteFrecuenciaFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LimiteFrecuenciaFilter.class);

    static final Duration MINUTO = Duration.ofMinutes(1);
    static final Duration VENTANA_SENSIBLES = Duration.ofMinutes(15);

    static final Set<String> SENSIBLES = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/mfa", "/api/v1/auth/recuperar",
            "/api/v1/auth/restablecer", "/api/v1/auth/restablecer/consultar",
            "/api/v1/plataforma/login", "/api/v1/plataforma/mfa",
            "/api/v1/partners/login", "/api/v1/partners/mfa", "/api/v1/partners/inscripcion",
            "/api/v1/partners/inscripcion/consultar", "/api/v1/partners/inscripcion/confirmar",
            "/api/v1/invitaciones/consultar", "/api/v1/invitaciones/aceptar",
            "/api/v1/registro", "/api/v1/registro/consultar", "/api/v1/registro/confirmar");
    private static final String CODIGOS_PARTNER = "/api/v1/partners/codigos/";

    private final IpCliente ipCliente;
    /** Instancia propia: una avalancha aquí no vacía los contadores de registro y login. */
    private final LimiteIntentos contadores;
    private final LimiteIntentos avisos = new LimiteIntentos();
    private final int porMinuto;
    private final int sensibles;

    @Autowired
    public LimiteFrecuenciaFilter(IpCliente ipCliente,
                                  @Value("${app.limites.ip-por-minuto:1200}") int porMinuto,
                                  @Value("${app.limites.sensibles-por-ip-15-min:30}") int sensibles) {
        this(ipCliente, new LimiteIntentos(), porMinuto, sensibles);
    }

    LimiteFrecuenciaFilter(IpCliente ipCliente, LimiteIntentos contadores, int porMinuto, int sensibles) {
        this.ipCliente = ipCliente;
        this.contadores = contadores;
        this.porMinuto = porMinuto;
        this.sensibles = sensibles;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/"); // el chequeo de salud y la documentación quedan fuera
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = ipCliente.de(request);
        String general = "ip:" + ip;
        if (!contadores.permitir(general, porMinuto, MINUTO)) {
            rechazar(response, general, MINUTO, ip, "general");
            return;
        }
        if (sensible(request)) {
            String clave = "ip-sensible:" + ip;
            if (!contadores.permitir(clave, sensibles, VENTANA_SENSIBLES)) {
                rechazar(response, clave, VENTANA_SENSIBLES, ip, request.getServletPath());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    static boolean sensible(HttpServletRequest request) {
        String ruta = request.getServletPath();
        return "POST".equals(request.getMethod()) && SENSIBLES.contains(ruta)
                || "GET".equals(request.getMethod()) && ruta.startsWith(CODIGOS_PARTNER);
    }

    private void rechazar(HttpServletResponse response, String clave, Duration ventana, String ip, String que)
            throws IOException {
        long segundos = contadores.segundosParaReintentar(clave, ventana);
        // Un aviso por minuto y clave: un bot insistente no debe inflar los logs (ni su costo en CloudWatch)
        if (avisos.permitir(clave, 1, MINUTO)) log.warn("Límite de frecuencia por IP alcanzado ({}): {}", que, ip);
        RespuestaLimite.escribir(response, segundos,
                "Demasiadas solicitudes desde su conexión. Espere " + RespuestaLimite.minutos(segundos) + " e intente de nuevo.");
    }
}
