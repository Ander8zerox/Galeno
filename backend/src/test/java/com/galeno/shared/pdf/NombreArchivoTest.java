package com.galeno.shared.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class NombreArchivoTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Test
    void apellidosNombresYFechaSinTildesNiEspacios() {
        assertThat(NombreArchivo.pdf("historia-clinica", "Rojas Medina", "María Fernanda",
                Instant.parse("2026-11-04T15:00:00Z"), BOGOTA))
                .isEqualTo("historia-clinica-rojas-medina-maria-fernanda-2026-11-04.pdf");
        assertThat(NombreArchivo.slug("  Núñez  Peña, María-José (hija) ")).isEqualTo("nunez-pena-maria-jose-hija");
    }

    @Test
    void laFechaEsLaDelConsultorio() {
        // 11 p. m. en Bogotá ya es el día siguiente en UTC
        assertThat(NombreArchivo.pdf("atencion", "Gómez", "Carlos", Instant.parse("2026-11-05T04:00:00Z"), BOGOTA))
                .isEqualTo("atencion-gomez-carlos-2026-11-04.pdf");
    }

    @Test
    void sinNombreUtilizableQuedaElTipoYLaFecha() {
        assertThat(NombreArchivo.pdf("formula", "", "***", Instant.parse("2026-11-04T15:00:00Z"), BOGOTA))
                .isEqualTo("formula-2026-11-04.pdf");
    }

    @Test
    void unNombreMuyLargoSeRecorta() {
        String nombre = NombreArchivo.pdf("atencion", "A".repeat(100), "B", Instant.parse("2026-11-04T15:00:00Z"), BOGOTA);
        assertThat(nombre).hasSize("atencion-".length() + 80 + "-2026-11-04.pdf".length()).doesNotContain("--");
    }
}
