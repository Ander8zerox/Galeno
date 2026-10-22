package com.galeno.shared.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

/**
 * Convierte XHTML + CSS en PDF. Las plantillas las arma cada módulo; aquí solo están el estilo común
 * (carta, márgenes, pie con numeración) y las utilidades de escape.
 */
@Component
public class PdfRenderer {

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-CO"));

    /** Estilos comunes. Las fuentes base del PDF (Helvetica) cubren el español, incluidas tildes y ñ. */
    public static final String ESTILOS = """
            @page { size: letter; margin: 16mm 16mm 20mm 16mm;
                    @bottom-center { content: "Galeno · página " counter(page) " de " counter(pages);
                                     font-family: Helvetica, sans-serif; font-size: 8pt; color: #637083; } }
            body { font-family: Helvetica, sans-serif; font-size: 10pt; color: #1f2933; }
            h1 { font-size: 15pt; margin: 0; color: #0f6e8c; }
            h2 { font-size: 11pt; margin: 14px 0 4px; color: #0f6e8c; border-bottom: 1px solid #dde3ea; padding-bottom: 2px; }
            .muted { color: #637083; }
            .small { font-size: 8.5pt; }
            .encabezado { width: 100%; border-bottom: 2px solid #0f6e8c; padding-bottom: 6px; margin-bottom: 10px; }
            .encabezado td { vertical-align: top; }
            .derecha { text-align: right; }
            .caja { border: 1px solid #dde3ea; border-radius: 4px; padding: 6px 8px; margin-bottom: 8px; }
            table.datos { width: 100%; border-collapse: collapse; }
            table.datos td { padding: 2px 4px; vertical-align: top; }
            table.grilla { width: 100%; border-collapse: collapse; margin-top: 4px; }
            table.grilla th { background: #eef5f8; text-align: left; font-size: 8.5pt; padding: 4px; border: 1px solid #dde3ea; }
            table.grilla td { padding: 4px; border: 1px solid #dde3ea; vertical-align: top; }
            .firma { margin-top: 28px; width: 60%; }
            .firma .linea { border-top: 1px solid #1f2933; padding-top: 4px; }
            .texto { white-space: pre-wrap; }
            h3 { font-size: 10.5pt; margin: 0 0 4px; color: #1f2933; }
            h2, h3 { page-break-after: avoid; }
            tr, .caja, .firma { page-break-inside: avoid; }
            /* Historia completa: atenciones seguidas, separadas por una línea, como un documento corrido (no una por
               hoja); los títulos nunca quedan solos al final de una hoja */
            .atencion { border-top: 2px solid #0f6e8c; margin-top: 14px; padding-top: 8px; }
            .marca-agua { position: fixed; top: 40%; left: 10%; font-size: 60pt; color: #f3d9a4; transform: rotate(-30deg); }
            """;

    public byte[] render(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF", e);
        }
    }

    /** Envuelve el cuerpo en un documento XHTML con los estilos comunes. */
    public static String documento(String titulo, String cuerpo) {
        return """
                <!DOCTYPE html>
                <html lang="es"><head><meta charset="UTF-8" /><title>%s</title><style>%s</style></head>
                <body>%s</body></html>""".formatted(esc(titulo), ESTILOS, cuerpo);
    }

    /** Escapa texto para XHTML; los saltos de línea se conservan con {@code .texto}. */
    public static String esc(Object valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : valor.toString().toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> {
                    // Caracteres de control (salvo salto de línea y tabulación) no son válidos en XML
                    if (c >= 0x20 || c == '\n' || c == '\t') {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String fechaHora(Instant instante, ZoneId zona) {
        return instante == null ? "" : FECHA_HORA.format(instante.atZone(zona));
    }
}
