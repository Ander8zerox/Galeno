package com.galeno.shared.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * En producción el origen solo atiende a nuestra distribución de CloudFront: sin el secreto de origen, 403. El
 * chequeo de salud queda abierto porque lo hace la propia instancia (Docker) sin pasar por CloudFront.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrigenCloudFrontFilter extends OncePerRequestFilter {

    private final IpCliente ipCliente;

    public OrigenCloudFrontFilter(IpCliente ipCliente) {
        this.ipCliente = ipCliente;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // La ruta ya normalizada: getRequestURI() es la cruda y "/actuator/health/../api" la pasaría
        String ruta = request.getServletPath();
        return !ipCliente.exigeOrigen() || ruta.equals("/actuator/health") || ruta.startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!ipCliente.vieneDeCloudFront(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN); // sin sendError: /error pasaría por la seguridad (401)
            return;
        }
        chain.doFilter(request, response);
    }
}
