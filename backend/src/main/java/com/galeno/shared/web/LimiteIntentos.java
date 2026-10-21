package com.galeno.shared.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Límite de intentos por clave (IP, correo) en una ventana deslizante, en memoria. Basta para una sola
 * instancia; con varias detrás del balanceador, el límite efectivo se multiplica por el número de instancias
 * (aceptable para frenar abusos; para algo estricto haría falta Redis o el WAF).
 */
@Component
public class LimiteIntentos {

    private static final int MAX_CLAVES = 100_000;

    private final Map<String, Deque<Instant>> intentos = new ConcurrentHashMap<>();
    private final Clock clock;

    public LimiteIntentos() {
        this(Clock.systemUTC());
    }

    LimiteIntentos(Clock clock) {
        this.clock = clock;
    }

    /** Registra un intento y dice si está dentro del límite. */
    public boolean permitir(String clave, int maximo, Duration ventana) {
        Instant ahora = clock.instant();
        if (intentos.size() > MAX_CLAVES) {
            intentos.clear(); // protección de memoria: preferible olvidar a crecer sin límite
        }
        Deque<Instant> deque = intentos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(ahora.minus(ventana))) {
                deque.pollFirst();
            }
            if (deque.size() >= maximo) {
                return false;
            }
            deque.addLast(ahora);
            return true;
        }
    }
}
