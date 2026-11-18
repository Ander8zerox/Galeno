package com.galeno.ayuda;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La guía de uso en PDF. Solo para quien inició sesión: no está en la web pública (la landing ofrece el folleto).
 * Va dentro del jar para que la versión de la guía siempre corresponda a la de la aplicación.
 */
@RestController
@RequestMapping("/api/v1/ayuda")
public class GuiaController {

    static final String NOMBRE = "guia-de-uso-galenum.pdf";

    private final byte[] guia;

    public GuiaController() throws IOException {
        try (InputStream in = new ClassPathResource("ayuda/" + NOMBRE).getInputStream()) {
            this.guia = in.readAllBytes();
        }
    }

    @GetMapping(value = "/guia", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> guia() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore()) // detrás de la sesión: que CloudFront no la guarde
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(NOMBRE, StandardCharsets.UTF_8).build().toString())
                .body(guia);
    }
}
