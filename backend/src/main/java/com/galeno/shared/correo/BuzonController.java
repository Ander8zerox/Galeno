package com.galeno.shared.correo;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.shared.correo.BuzonLocal.Enviado;

/** Solo en desarrollo: los correos que "se enviaron", para abrir el enlace de verificación sin un servidor de correo. */
@RestController
@RequestMapping("/api/v1/dev/buzon")
@ConditionalOnProperty(name = "app.correo.proveedor", havingValue = "local", matchIfMissing = true)
public class BuzonController {

    private final BuzonLocal buzon;
    private final EventosSes eventos;

    BuzonController(BuzonLocal buzon, EventosSes eventos) {
        this.buzon = buzon;
        this.eventos = eventos;
    }

    public record EventoProcesado(int suprimidos) {
    }

    /** Simular un rebote o una queja con el mismo JSON que SES deja en la cola (ver EventosSesTest). */
    @PostMapping("/evento-ses")
    public EventoProcesado eventoSes(@RequestBody String evento) {
        return new EventoProcesado(eventos.procesar(evento));
    }

    @GetMapping
    public List<Enviado> recientes() {
        return buzon.recientes();
    }
}
