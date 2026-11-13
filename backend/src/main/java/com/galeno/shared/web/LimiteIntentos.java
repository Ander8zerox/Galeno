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
 *
 * <p>El bean es el de los intentos sensibles (registro, login, recuperación). Los filtros de frecuencia y los topes
 * diarios usan instancias propias, para que una avalancha de peticiones no vacíe estos contadores al proteger la
 * memoria.
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
        protegerMemoria();
        Deque<Instant> deque = intentos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (deque) {
            purgar(deque, ahora.minus(ventana));
            if (deque.size() >= maximo) {
                return false;
            }
            deque.addLast(ahora);
            return true;
        }
    }

    /** Si la clave ya llegó al máximo en la ventana, sin registrar un intento (p. ej. antes de comparar la contraseña). */
    public boolean agotado(String clave, int maximo, Duration ventana) {
        Deque<Instant> deque = intentos.get(clave);
        if (deque == null) {
            return false;
        }
        synchronized (deque) {
            purgar(deque, clock.instant().minus(ventana));
            return deque.size() >= maximo;
        }
    }

    /** Registra un intento sin consultar el límite (p. ej. solo los fallidos, junto con {@link #agotado}). */
    public void registrar(String clave) {
        protegerMemoria();
        Deque<Instant> deque = intentos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(clock.instant());
        }
    }

    /** Segundos hasta que se libere un intento de la clave (al menos 1), para el encabezado Retry-After. */
    public long segundosParaReintentar(String clave, Duration ventana) {
        Deque<Instant> deque = intentos.get(clave);
        if (deque == null) {
            return 1;
        }
        synchronized (deque) {
            Instant primero = deque.peekFirst();
            return primero == null ? 1 : Math.max(1, Duration.between(clock.instant(), primero.plus(ventana)).toSeconds() + 1);
        }
    }

    private void protegerMemoria() {
        if (intentos.size() > MAX_CLAVES) {
            intentos.clear(); // preferible olvidar a crecer sin límite
        }
    }

    private static void purgar(Deque<Instant> deque, Instant desde) {
        while (!deque.isEmpty() && deque.peekFirst().isBefore(desde)) {
            deque.pollFirst();
        }
    }
}
