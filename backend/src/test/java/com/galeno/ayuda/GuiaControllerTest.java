package com.galeno.ayuda;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class GuiaControllerTest {

    @Test
    void entregaElPdfDeLaGuiaComoDescarga() throws Exception {
        var respuesta = new GuiaController().guia();

        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment").contains(GuiaController.NOMBRE);
        assertThat(respuesta.getHeaders().getCacheControl()).isEqualTo("no-store");
        byte[] pdf = respuesta.getBody();
        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
