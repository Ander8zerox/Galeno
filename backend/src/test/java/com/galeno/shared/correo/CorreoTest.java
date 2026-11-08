package com.galeno.shared.correo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class CorreoTest {

    @Test
    void unCorreoDeGalenumUsaElRemitenteConfigurado() {
        assertThat(new Correo("a@b.test", "x", "y").remitente("Galenum <no-responder@galenum.com.co>"))
                .isEqualTo("Galenum <no-responder@galenum.com.co>");
    }

    @Test
    void unCorreoDeConsultorioLlevaSuNombreCodificadoYLaDireccionDeGalenum() {
        Correo c = Correo.deConsultorio("a@b.test", "x", "y", "Clínica \"Sur\", sede 2", null);

        String nombre = Base64.getEncoder().encodeToString("Clínica \"Sur\", sede 2".getBytes(StandardCharsets.UTF_8));
        assertThat(c.remitente("Galenum <no-responder@galenum.com.co>"))
                .isEqualTo("=?UTF-8?B?" + nombre + "?= <no-responder@galenum.com.co>");
        assertThat(c.remitente("no-responder@galenum.com.co")).endsWith(" <no-responder@galenum.com.co>");
    }
}
