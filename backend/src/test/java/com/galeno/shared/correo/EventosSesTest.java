package com.galeno.shared.correo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.galeno.shared.correo.CorreoSuprimido.Motivo;

import tools.jackson.databind.json.JsonMapper;

class EventosSesTest {

    record Registro(String email, Motivo motivo, String detalle) {
    }

    /** Guarda lo que se suprimiría, sin base de datos. */
    static class SupresionEnMemoria extends SupresionCorreos {
        final List<Registro> registros = new ArrayList<>();

        SupresionEnMemoria() {
            super(null, null);
        }

        @Override
        public void registrar(String email, Motivo motivo, String detalle) {
            registros.add(new Registro(email, motivo, detalle));
        }
    }

    private final SupresionEnMemoria supresion = new SupresionEnMemoria();
    private final EventosSes eventos = new EventosSes(supresion);

    private static final String REBOTE_PERMANENTE = """
            {"eventType":"Bounce","bounce":{"bounceType":"Permanent","bounceSubType":"General",
             "bouncedRecipients":[{"emailAddress":"no.existe@ejemplo.com","diagnosticCode":"smtp; 550 5.1.1 user unknown"}]},
             "mail":{"messageId":"abc"}}""";

    @Test
    void reboteDentroDelSobreDeSnsSuprime() {
        // SNS entrega el evento de SES como texto dentro del campo Message
        String sobre = JsonMapper.builder().build().writeValueAsString(Map.of(
                "Type", "Notification", "TopicArn", "arn:aws:sns:us-east-1:1:galeno-correo-eventos",
                "Message", REBOTE_PERMANENTE));

        assertThat(eventos.procesar(sobre)).isEqualTo(1);
        assertThat(supresion.registros).singleElement().satisfies(r -> {
            assertThat(r.email()).isEqualTo("no.existe@ejemplo.com");
            assertThat(r.motivo()).isEqualTo(Motivo.REBOTE);
            assertThat(r.detalle()).contains("550 5.1.1");
        });
    }

    @Test
    void reboteCrudoTambienSeEntiende() {
        assertThat(eventos.procesar(REBOTE_PERMANENTE)).isEqualTo(1);
    }

    @Test
    void reboteTransitorioNoSuprime() {
        String evento = """
                {"notificationType":"Bounce","bounce":{"bounceType":"Transient","bounceSubType":"MailboxFull",
                 "bouncedRecipients":[{"emailAddress":"lleno@ejemplo.com"}]}}""";

        assertThat(eventos.procesar(evento)).isZero();
        assertThat(supresion.registros).isEmpty();
    }

    @Test
    void quejaSuprime() {
        String evento = """
                {"eventType":"Complaint","complaint":{"complaintFeedbackType":"abuse",
                 "complainedRecipients":[{"emailAddress":"molesto@ejemplo.com"}]}}""";

        assertThat(eventos.procesar(evento)).isEqualTo(1);
        assertThat(supresion.registros).singleElement()
                .isEqualTo(new Registro("molesto@ejemplo.com", Motivo.QUEJA, "Queja: abuse"));
    }

    @Test
    void entregasYBasuraSeIgnoran() {
        assertThat(eventos.procesar("""
                {"eventType":"Delivery","delivery":{"recipients":["ok@ejemplo.com"]}}""")).isZero();
        assertThat(eventos.procesar("no es json")).isZero();
        assertThat(supresion.registros).isEmpty();
    }
}
