package com.galeno.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.galeno.identidad.IdentidadService.ContactoConsultorio;
import com.galeno.shared.correo.Correo;

class TextosCorreoCitaTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final ContactoConsultorio COMPLETO = new ContactoConsultorio("Consultorio Sonrisas", "Cra 10 # 20-30",
            "601 555 0000", "citas@sonrisas.test", true, BOGOTA);
    private static final ContactoConsultorio SIN_CONTACTO = new ContactoConsultorio("Consultorio Sonrisas", null, null,
            null, true, BOGOTA);
    /** Martes 10 de noviembre de 2026, 8:00 a. m. en Bogotá. */
    private static final Instant INICIO = Instant.parse("2026-11-10T13:00:00Z");

    @Test
    void laCitaAgendadaDiceCuandoDondeYConQuien() {
        Correo c = TextosCorreoCita.correo(CambioCita.Tipo.AGENDADA, COMPLETO, "ana@ejemplo.test", "Ana", "Dra. Demo",
                INICIO, null);

        assertThat(c.para()).isEqualTo("ana@ejemplo.test");
        assertThat(c.asunto()).isEqualTo("Cita agendada: Martes 10 de noviembre, 8:00 a. m.");
        assertThat(c.texto()).startsWith("Hola, Ana.\n\nSu cita en Consultorio Sonrisas quedó agendada:")
                .contains("  Fecha: martes 10 de noviembre de 2026\n  Hora: 8:00 a. m.\n  Con: Dra. Demo\n"
                        + "  Dirección: Cra 10 # 20-30\n")
                .contains("Si no puede asistir, por favor avísenos con anticipación: 601 555 0000 o citas@sonrisas.test.")
                .doesNotContain("no recibe respuestas");
        // Lo envía el consultorio y las respuestas le llegan a él
        assertThat(c.remitenteNombre()).isEqualTo("Consultorio Sonrisas");
        assertThat(c.responderA()).isEqualTo("citas@sonrisas.test");
    }

    @Test
    void laReprogramacionMuestraLaHoraAnterior() {
        Correo c = TextosCorreoCita.correo(CambioCita.Tipo.REPROGRAMADA, COMPLETO, "ana@ejemplo.test", "Ana", "Dra. Demo",
                INICIO, Instant.parse("2026-11-10T19:00:00Z"));

        assertThat(c.asunto()).isEqualTo("Su cita cambió: Martes 10 de noviembre, 8:00 a. m.");
        assertThat(c.texto()).contains("cambió de fecha u hora. Ahora es:").contains("(Antes: martes 10 de noviembre, 2:00 p. m.)");
    }

    @Test
    void laCancelacionInvitaAAgendarDeNuevoYNoDaLaDireccion() {
        Correo c = TextosCorreoCita.correo(CambioCita.Tipo.CANCELADA, COMPLETO, "ana@ejemplo.test", "Ana", null, INICIO, null);

        assertThat(c.asunto()).startsWith("Cita cancelada:");
        assertThat(c.texto()).contains("fue cancelada").contains("Si desea una nueva cita, comuníquese con el consultorio: 601")
                .doesNotContain("Dirección").doesNotContain("Con:");
    }

    @Test
    void sinNombreNiContactoElCorreoSigueSiendoUtil() {
        Correo c = TextosCorreoCita.correo(CambioCita.Tipo.RECORDATORIO, SIN_CONTACTO, "x@ejemplo.test", null, "Dra. Demo",
                INICIO, null);

        assertThat(c.asunto()).startsWith("Recordatorio de su cita:");
        assertThat(c.texto()).startsWith("Hola,\n\nLe recordamos su cita")
                .contains("Si no puede asistir, por favor avísele al consultorio con anticipación.")
                .contains("Este correo no recibe respuestas.");
        assertThat(c.responderA()).isNull();
    }
}
