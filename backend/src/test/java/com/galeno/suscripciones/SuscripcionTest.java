package com.galeno.suscripciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.galeno.suscripciones.Suscripcion.Estado;

class SuscripcionTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Instant INICIO = Instant.parse("2026-10-01T15:00:00Z");

    private static Instant dia(int n) {
        return INICIO.plus(Duration.ofDays(n));
    }

    @Test
    void pruebaDe30DiasLuegoGraciaDe5YLuegoSoloLectura() {
        Suscripcion s = Suscripcion.enPrueba(INICIO);

        assertThat(s.estado(dia(0))).isEqualTo(Estado.PRUEBA);
        assertThat(s.estado(dia(29))).isEqualTo(Estado.PRUEBA);
        assertThat(s.estado(dia(30))).isEqualTo(Estado.VENCIDA);
        assertThat(s.estado(dia(34))).isEqualTo(Estado.VENCIDA);
        assertThat(s.estado(dia(35))).isEqualTo(Estado.SUSPENDIDA);
    }

    @Test
    void pagarDuranteLaPruebaSumaDesdeQueTermina() {
        Suscripcion s = Suscripcion.enPrueba(INICIO);

        Instant[] periodo = s.extenderPorPago(1, dia(10), BOGOTA);

        assertThat(periodo[0]).isEqualTo(dia(30));
        // 31 de octubre + 1 mes = 30 de noviembre (misma hora local en Bogotá)
        assertThat(periodo[1]).isEqualTo(Instant.parse("2026-11-30T15:00:00Z"));
        assertThat(s.estado(dia(10))).isEqualTo(Estado.ACTIVA);
        assertThat(s.vence()).isEqualTo(periodo[1]);
    }

    @Test
    void pagarYaSuspendidaCuentaDesdeHoyNoDesdeQueVencio() {
        Suscripcion s = Suscripcion.enPrueba(INICIO);
        Instant hoy = dia(60);
        assertThat(s.estado(hoy)).isEqualTo(Estado.SUSPENDIDA);

        Instant[] periodo = s.extenderPorPago(12, hoy, BOGOTA);

        assertThat(periodo[0]).isEqualTo(hoy);
        assertThat(periodo[1]).isEqualTo(hoy.atZone(BOGOTA).plusYears(1).toInstant());
        assertThat(s.estado(hoy)).isEqualTo(Estado.ACTIVA);
    }

    @Test
    void dosPagosSeguidosSeAcumulan() {
        Suscripcion s = Suscripcion.enPrueba(INICIO);
        s.extenderPorPago(1, dia(40), BOGOTA);
        Instant primerVencimiento = s.vence();

        s.extenderPorPago(1, dia(41), BOGOTA);

        assertThat(s.vence()).isEqualTo(primerVencimiento.atZone(BOGOTA).plusMonths(1).toInstant());
    }

    @Test
    void laCortesiaNoConvierteLaPruebaEnPagada() {
        Suscripcion s = Suscripcion.enPrueba(INICIO);

        s.regalarDias(15, dia(5));

        assertThat(s.vence()).isEqualTo(dia(45));
        assertThat(s.estado(dia(40))).isEqualTo(Estado.PRUEBA);
    }

    @Test
    void laSuspensionManualMandaHastaQueSeReactive() {
        Suscripcion s = Suscripcion.enPrueba(INICIO);
        s.extenderPorPago(12, dia(1), BOGOTA);

        s.suspender("Pago devuelto por el banco", dia(2));
        assertThat(s.estado(dia(2))).isEqualTo(Estado.SUSPENDIDA);

        s.reactivar(dia(3));
        assertThat(s.estado(dia(3))).isEqualTo(Estado.ACTIVA);
    }
}
