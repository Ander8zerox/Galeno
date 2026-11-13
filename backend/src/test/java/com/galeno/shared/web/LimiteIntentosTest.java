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

    @Test
    void soloCuentanLosRegistradosYSeVencenSolos() {
        Reloj reloj = new Reloj();
        LimiteIntentos limite = new LimiteIntentos(reloj);
        Duration quince = Duration.ofMinutes(15);

        assertThat(limite.agotado("correo:a", 2, quince)).isFalse(); // consultar no registra
        assertThat(limite.agotado("correo:a", 2, quince)).isFalse();
        limite.registrar("correo:a");
        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(5));
        limite.registrar("correo:a");
        assertThat(limite.agotado("correo:a", 2, quince)).isTrue();
        // El primero se vence a los 15 minutos de registrado: faltan 10
        assertThat(limite.segundosParaReintentar("correo:a", quince)).isBetween(600L, 601L);

        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(11));
        assertThat(limite.agotado("correo:a", 2, quince)).isFalse();
    }
}
