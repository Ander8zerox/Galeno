package com.galeno.shared.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

/** La respuesta 429 de los filtros de frecuencia, en el mismo formato problem+json que el resto de la API. */
final class RespuestaLimite {

    private RespuestaLimite() {
    }

    static void escribir(HttpServletResponse response, long segundos, String detalle) throws IOException {
        response.setStatus(429); // sin sendError: /error pasaría por la seguridad (401)
        response.setHeader("Retry-After", Long.toString(segundos));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"" + detalle + "\"}");
    }

    /** "1 minuto", "3 minutos": para el mensaje al usuario. */
    static String minutos(long segundos) {
        long m = Math.max(1, (segundos + 59) / 60);
        return m == 1 ? "1 minuto" : m + " minutos";
    }
}
