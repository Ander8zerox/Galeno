package com.galeno.shared.correo;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Para desarrollo: no envía nada, guarda los últimos correos en memoria (los muestra {@link BuzonController})
 * y deja una línea en el log. Solo existe con {@code app.correo.proveedor=local}.
 */
@Component
@ConditionalOnProperty(name = "app.correo.proveedor", havingValue = "local", matchIfMissing = true)
public class BuzonLocal implements EnvioCorreo {

    private static final Logger log = LoggerFactory.getLogger(BuzonLocal.class);
    private static final int MAX = 50;

    public record Enviado(String para, String asunto, String texto, Instant enviadoEn) {
    }

    private final Deque<Enviado> enviados = new ArrayDeque<>();

    @Override
    public synchronized void enviar(Correo correo) {
        enviados.addFirst(new Enviado(correo.para(), correo.asunto(), correo.texto(), Instant.now()));
        while (enviados.size() > MAX) {
            enviados.removeLast();
        }
        log.info("Correo (buzón local) para {}: {}", correo.para(), correo.asunto());
    }

    public synchronized List<Enviado> recientes() {
        return List.copyOf(enviados);
    }
}
