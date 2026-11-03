package com.galeno.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class TotpTest {

    /** Secreto de los vectores de prueba del RFC 6238 (apéndice B) para SHA1. */
    private static final byte[] SECRETO_RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void coincideConLosVectoresDelRfc6238() {
        // El RFC da 8 dígitos; las apps usan 6, que son los últimos 6
        assertThat(codigoEn(59)).isEqualTo("287082");
        assertThat(codigoEn(1111111109)).isEqualTo("081804");
        assertThat(codigoEn(1111111111)).isEqualTo("050471");
        assertThat(codigoEn(1234567890)).isEqualTo("005924");
        assertThat(codigoEn(2000000000)).isEqualTo("279037");
        assertThat(codigoEn(20000000000L)).isEqualTo("353130");
    }

    @Test
    void aceptaElCodigoActualYUnoDeDesfase() {
        Instant ahora = Instant.ofEpochSecond(1234567890);
        long paso = Totp.paso(ahora);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso), ahora, 0)).isEqualTo(paso);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso - 1), ahora, 0)).isEqualTo(paso - 1);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso + 1), ahora, 0)).isEqualTo(paso + 1);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso - 2), ahora, 0)).isEqualTo(-1);
    }

    @Test
    void unCodigoYaUsadoNoVuelveASirvir() {
        Instant ahora = Instant.ofEpochSecond(1234567890);
        long paso = Totp.paso(ahora);
        String codigo = Totp.codigo(SECRETO_RFC, paso);
        assertThat(Totp.verificar(SECRETO_RFC, codigo, ahora, paso)).isEqualTo(-1);
        // Ni uno anterior al último aceptado
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso - 1), ahora, paso)).isEqualTo(-1);
    }

    @Test
    void rechazaFormatosQueNoSonSeisDigitos() {
        Instant ahora = Instant.ofEpochSecond(59);
        assertThat(Totp.verificar(SECRETO_RFC, null, ahora, 0)).isEqualTo(-1);
        assertThat(Totp.verificar(SECRETO_RFC, "28708", ahora, 0)).isEqualTo(-1);
        assertThat(Totp.verificar(SECRETO_RFC, "94287082", ahora, 0)).isEqualTo(-1);
        assertThat(Totp.verificar(SECRETO_RFC, "28708a", ahora, 0)).isEqualTo(-1);
    }

    @Test
    void base32ComoElRfc4648SinRelleno() {
        assertThat(Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(Totp.base32("f".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MY");
    }

    @Test
    void laUriQueLeenLasApps() {
        String uri = Totp.uri("Galenum", "medico@demo.local", "foobar".getBytes(StandardCharsets.US_ASCII));
        assertThat(uri).isEqualTo("otpauth://totp/Galenum%3Amedico%40demo.local?secret=MZXW6YTBOI&issuer=Galenum"
                + "&algorithm=SHA1&digits=6&period=30");
    }

    private static String codigoEn(long segundos) {
        return Totp.codigo(SECRETO_RFC, Totp.paso(Instant.ofEpochSecond(segundos)));
    }
}
