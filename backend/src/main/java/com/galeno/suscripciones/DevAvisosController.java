package com.galeno.suscripciones;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo en desarrollo: correr ya el proceso diario de avisos (para probarlo sin esperar a las 8 a. m.). */
@RestController
@Profile("local")
@RequestMapping("/api/v1/dev/suscripciones")
public class DevAvisosController {

    private final AvisosSuscripcion avisos;

    DevAvisosController(AvisosSuscripcion avisos) {
        this.avisos = avisos;
    }

    public record AvisosEnviados(int enviados) {
    }

    @PostMapping("/avisos")
    public AvisosEnviados avisos() {
        return new AvisosEnviados(avisos.enviar(java.time.Instant.now()));
    }
}
