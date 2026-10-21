package com.galeno.suscripciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.galeno.suscripciones.AvisosSuscripcion.Tipo;

class AvisosSuscripcionTest {

    private static final Instant INICIO = Instant.parse("2026-10-01T13:00:00Z");
    private final Suscripcion prueba = Suscripcion.enPrueba(INICIO); // vence el 31 de octubre

    private static Instant antesDelVencimiento(long horas) {
        return INICIO.plus(Suscripcion.PRUEBA).minus(Duration.ofHours(horas));
    }

    @Test
    void avisaUnaSemanaTresDiasYUnDiaAntes() {
        assertThat(AvisosSuscripcion.tipo(prueba, antesDelVencimiento(8 * 24))).isEmpty();
        assertThat(AvisosSuscripcion.tipo(prueba, antesDelVencimiento(6 * 24))).contains(Tipo.VENCE_7);
        assertThat(AvisosSuscripcion.tipo(prueba, antesDelVencimiento(2 * 24))).contains(Tipo.VENCE_3);
        assertThat(AvisosSuscripcion.tipo(prueba, antesDelVencimiento(5))).contains(Tipo.VENCE_1);
    }

    @Test
    void avisaAlVencerYAlQuedarEnSoloLectura() {
        Instant vence = prueba.vence();
        assertThat(AvisosSuscripcion.tipo(prueba, vence.plus(Duration.ofHours(1)))).contains(Tipo.VENCIDA);
        assertThat(AvisosSuscripcion.tipo(prueba, vence.plus(Suscripcion.GRACIA).plusSeconds(60))).contains(Tipo.SUSPENDIDA);
        assertThat(AvisosSuscripcion.tipo(prueba, vence.plus(Duration.ofDays(60)))).isEmpty();
    }

    @Test
    void laSuspensionManualNoGeneraAvisosAutomaticos() {
        prueba.suspender("Fraude", INICIO);

        assertThat(AvisosSuscripcion.tipo(prueba, antesDelVencimiento(5))).isEmpty();
    }

    @Test
    void reconoceElComprobantePorSuContenido() {
        assertThat(SuscripcionService.tipoComprobante("%PDF-1.7 ...".getBytes())).isEqualTo("application/pdf");
        assertThat(SuscripcionService.tipoComprobante(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).isEqualTo("image/jpeg");
        assertThat(SuscripcionService.pesos(150000)).isEqualTo("$150.000");
    }
}
