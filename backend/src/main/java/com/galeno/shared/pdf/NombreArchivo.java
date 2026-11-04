package com.galeno.shared.pdf;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Nombre de descarga de los PDF clínicos: {@code historia-clinica-rojas-medina-maria-fernanda-2026-11-04.pdf}.
 *
 * <p>Lleva apellidos, nombres y fecha para distinguir los archivos en la carpeta de descargas. Nunca el número de
 * documento: junto con el nombre facilita la suplantación y no ayuda a distinguirlos (datos sensibles, Ley 1581).
 */
public final class NombreArchivo {

    private static final int MAX_PERSONA = 80;

    private NombreArchivo() {
    }

    /**
     * @param tipo p. ej. {@code historia-clinica}, {@code atencion}, {@code formula}
     * @param fecha la del documento (la atención, la fórmula) o la de generación, en la zona del consultorio
     */
    public static String pdf(String tipo, String apellidos, String nombres, Instant fecha, ZoneId zona) {
        String persona = slug(apellidos + " " + nombres);
        if (persona.length() > MAX_PERSONA) {
            persona = persona.substring(0, MAX_PERSONA).replaceAll("-+$", "");
        }
        LocalDate dia = fecha.atZone(zona).toLocalDate();
        return tipo + (persona.isEmpty() ? "" : "-" + persona) + "-" + dia + ".pdf";
    }

    /** Minúsculas sin tildes ni espacios: {@code "Núñez Peña, María José"} → {@code nunez-pena-maria-jose}. */
    static String slug(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
