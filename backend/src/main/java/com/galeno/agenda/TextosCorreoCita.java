package com.galeno.agenda;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.galeno.identidad.IdentidadService.ContactoConsultorio;
import com.galeno.shared.correo.Correo;

/**
 * Los correos al paciente sobre su cita, en texto plano. Sin datos clínicos (ni motivo ni diagnóstico): solo cuándo,
 * dónde y con quién, y cómo avisar si no puede ir.
 */
final class TextosCorreoCita {

    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("h:mm a", ES);

    private TextosCorreoCita() {
    }

    /**
     * @param nombrePaciente null si no se conoce (importado sin nombre): el saludo queda genérico
     * @param medico         nombre del médico; null si ya no está activo
     */
    static Correo correo(CambioCita.Tipo tipo, ContactoConsultorio consultorio, String email, String nombrePaciente,
                         String medico, Instant inicio, Instant inicioAnterior) {
        ZoneId zona = consultorio.zona();
        ZonedDateTime cuando = inicio.atZone(zona);
        String dia = mayuscula(FECHA_CORTA.format(cuando));
        String hora = hora(cuando);
        String asunto = switch (tipo) {
            case AGENDADA -> "Cita agendada: " + dia + ", " + hora;
            case REPROGRAMADA -> "Su cita cambió: " + dia + ", " + hora;
            case CANCELADA -> "Cita cancelada: " + dia + ", " + hora;
            case RECORDATORIO -> "Recordatorio de su cita: " + dia + ", " + hora;
        };

        StringBuilder t = new StringBuilder();
        t.append(nombrePaciente == null ? "Hola," : "Hola, " + nombrePaciente.strip() + ".").append("\n\n");
        switch (tipo) {
            case AGENDADA -> t.append("Su cita en ").append(consultorio.nombre()).append(" quedó agendada:\n\n");
            case REPROGRAMADA -> t.append("Su cita en ").append(consultorio.nombre()).append(" cambió de fecha u hora. Ahora es:\n\n");
            case CANCELADA -> t.append("Su cita en ").append(consultorio.nombre()).append(" fue cancelada:\n\n");
            case RECORDATORIO -> t.append("Le recordamos su cita en ").append(consultorio.nombre()).append(":\n\n");
        }
        t.append("  Fecha: ").append(FECHA.format(cuando)).append('\n');
        t.append("  Hora: ").append(hora).append('\n');
        if (medico != null) {
            t.append("  Con: ").append(medico).append('\n');
        }
        if (tipo != CambioCita.Tipo.CANCELADA && consultorio.direccion() != null) {
            t.append("  Dirección: ").append(consultorio.direccion()).append('\n');
        }
        if (tipo == CambioCita.Tipo.REPROGRAMADA && inicioAnterior != null) {
            ZonedDateTime antes = inicioAnterior.atZone(zona);
            t.append("  (Antes: ").append(FECHA_CORTA.format(antes)).append(", ").append(hora(antes)).append(")\n");
        }
        t.append('\n');
        String contacto = contacto(consultorio);
        if (tipo == CambioCita.Tipo.CANCELADA) {
            t.append(contacto == null ? "Si desea una nueva cita, comuníquese con el consultorio."
                    : "Si desea una nueva cita, comuníquese con el consultorio: " + contacto + ".");
        } else {
            t.append(contacto == null ? "Si no puede asistir, por favor avísele al consultorio con anticipación."
                    : "Si no puede asistir, por favor avísenos con anticipación: " + contacto + ".");
        }
        t.append("\n\n").append(consultorio.nombre()).append("\n\n");
        t.append("--\nRecibe este correo porque tiene una cita en ").append(consultorio.nombre())
                .append(". Si no desea recibir estos correos, avísele al consultorio.");
        if (consultorio.correo() == null) {
            t.append(" Este correo no recibe respuestas.");
        }
        return Correo.deConsultorio(email, asunto, t.toString(), consultorio.nombre(), consultorio.correo());
    }

    /** "601 555 0000 o citas@consultorio.com", con lo que el consultorio tenga. */
    private static String contacto(ContactoConsultorio c) {
        if (c.telefono() != null && c.correo() != null) {
            return c.telefono() + " o " + c.correo();
        }
        return c.telefono() != null ? c.telefono() : c.correo();
    }

    /** "8:00 a. m." con espacios normales: el formato de Java usa espacios finos que algunos clientes muestran mal. */
    private static String hora(ZonedDateTime t) {
        return HORA.format(t).replace(' ', ' ').replace(' ', ' ');
    }

    private static String mayuscula(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
