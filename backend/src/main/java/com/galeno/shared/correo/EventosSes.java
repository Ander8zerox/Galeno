package com.galeno.shared.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.galeno.shared.correo.CorreoSuprimido.Motivo;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Interpreta un evento de SES y actualiza la lista de supresión. Acepta el mensaje tal como llega a la cola:
 * dentro del sobre de SNS (campo {@code Message}) o crudo (raw message delivery), y tanto el formato de
 * "event publishing" ({@code eventType}) como el de notificaciones de identidad ({@code notificationType}).
 * <ul>
 *   <li>Rebote permanente: la dirección no existe o no acepta correo, se suprime.</li>
 *   <li>Rebote transitorio (buzón lleno, servidor caído): solo se registra en el log; SES ya reintentó.</li>
 *   <li>Queja: el destinatario lo marcó como spam, se suprime.</li>
 * </ul>
 */
@Component
public class EventosSes {

    private static final Logger log = LoggerFactory.getLogger(EventosSes.class);

    private final SupresionCorreos supresion;
    private final JsonMapper json = JsonMapper.builder().build();

    EventosSes(SupresionCorreos supresion) {
        this.supresion = supresion;
    }

    /** Devuelve cuántas direcciones quedaron suprimidas por este evento (0 si no aplica o no se entiende). */
    public int procesar(String mensaje) {
        JsonNode evento;
        try {
            evento = json.readTree(mensaje);
            if (evento.has("Type") && evento.has("Message")) { // sobre de SNS
                evento = json.readTree(evento.get("Message").asString());
            }
        } catch (JacksonException e) {
            log.warn("Evento de SES que no es JSON, se descarta");
            return 0;
        }
        String tipo = texto(evento, "eventType");
        if (tipo.isEmpty()) {
            tipo = texto(evento, "notificationType");
        }
        return switch (tipo) {
            case "Bounce" -> rebote(evento.path("bounce"));
            case "Complaint" -> queja(evento.path("complaint"));
            default -> 0; // Send, Delivery, etc.: no cambian nada
        };
    }

    private int rebote(JsonNode bounce) {
        String tipo = texto(bounce, "bounceType");
        int n = 0;
        for (JsonNode r : bounce.path("bouncedRecipients")) {
            String email = texto(r, "emailAddress");
            String detalle = (tipo + " " + texto(bounce, "bounceSubType") + ": " + texto(r, "diagnosticCode")).trim();
            if ("Permanent".equals(tipo)) {
                supresion.registrar(email, Motivo.REBOTE, detalle);
                n++;
            } else {
                log.info("Rebote transitorio para {} ({}), no se suprime", email, detalle);
            }
        }
        return n;
    }

    private int queja(JsonNode complaint) {
        String tipo = texto(complaint, "complaintFeedbackType");
        int n = 0;
        for (JsonNode r : complaint.path("complainedRecipients")) {
            supresion.registrar(texto(r, "emailAddress"), Motivo.QUEJA, tipo.isEmpty() ? "Queja del destinatario" : "Queja: " + tipo);
            n++;
        }
        return n;
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode v = nodo.path(campo);
        return v.isString() ? v.asString() : "";
    }
}
