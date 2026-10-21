package com.galeno.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LimiteIntentosTest {

    /** Reloj que la prueba puede adelantar. */
    private static final class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-10-21T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    @Test
    void permiteHastaElMaximoYLiberaCuandoPasaLaVentana() {
        Reloj reloj = new Reloj();
        LimiteIntentos limite = new LimiteIntentos(reloj);
        Duration hora = Duration.ofHours(1);

        assertThat(limite.permitir("ip:1", 2, hora)).isTrue();
        assertThat(limite.permitir("ip:1", 2, hora)).isTrue();
        assertThat(limite.permitir("ip:1", 2, hora)).isFalse();
        assertThat(limite.permitir("ip:2", 2, hora)).isTrue();

        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(61));
        assertThat(limite.permitir("ip:1", 2, hora)).isTrue();
    }
}
