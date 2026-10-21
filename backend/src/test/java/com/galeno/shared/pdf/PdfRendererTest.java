package com.galeno.shared.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfRendererTest {

    private final PdfRenderer renderer = new PdfRenderer();

    private static String texto(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void generaUnPdfConTildesYEñes() throws IOException {
        byte[] pdf = renderer.render(PdfRenderer.documento("Prueba",
                "<h1>Fórmula médica</h1><p>Niño de 5 años · acetaminofén 150 mg/5 mL</p>"));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(texto(pdf)).contains("Fórmula médica").contains("Niño de 5 años").contains("acetaminofén");
    }

    @Test
    void elTextoDelUsuarioSeEscapa() throws IOException {
        String malicioso = "<script>alert('x')</script> & \"comillas\"";
        byte[] pdf = renderer.render(PdfRenderer.documento("Prueba", "<p>" + PdfRenderer.esc(malicioso) + "</p>"));

        assertThat(texto(pdf)).contains("<script>alert('x')</script> & \"comillas\"");
        assertThat(PdfRenderer.esc("a\u0007b")).isEqualTo("ab"); // caracteres de control fuera
    }
}
