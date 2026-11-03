package com.galeno.shared.correo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.galeno.shared.SoporteProperties;

class PieCorreoTest {

    @Test
    void agregaElCorreoDeSoporte() {
        String texto = PieCorreo.agregar("Hola.\n\nGalenum", new SoporteProperties("soporte@galenum.com.co", ""));
        assertThat(texto).startsWith("Hola.\n\nGalenum\n\n--\n")
                .endsWith("escríbanos a soporte@galenum.com.co.");
    }

    @Test
    void conWhatsappLoMuestraLegible() {
        String texto = PieCorreo.agregar("Hola", new SoporteProperties("soporte@galenum.com.co", "573001234567"));
        assertThat(texto).endsWith("soporte@galenum.com.co o por WhatsApp al +57 300 123 4567.");
    }

    @Test
    void sinCorreoDeSoporteNoCambiaElTexto() {
        assertThat(PieCorreo.agregar("Hola", new SoporteProperties("", ""))).isEqualTo("Hola");
    }
}
