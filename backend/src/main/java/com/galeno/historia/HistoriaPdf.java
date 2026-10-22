package com.galeno.historia;

import static com.galeno.shared.pdf.PdfRenderer.esc;
import static com.galeno.shared.pdf.PdfRenderer.fechaHora;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.galeno.identidad.IdentidadService.ConsultorioResumen;
import com.galeno.pacientes.Paciente;
import com.galeno.shared.pdf.PdfRenderer;

/**
 * Historia clínica completa de un paciente en un solo documento: datos del paciente, índice de atenciones y cada
 * atención firmada (o importada) en una página propia, de la más antigua a la más reciente. Es la copia que se
 * entrega cuando el paciente la solicita o se traslada (Res. 1995 de 1999).
 */
final class HistoriaPdf {

    record AtencionImpresa(Atencion atencion, List<NotaAclaratoria> notas) {
    }

    private HistoriaPdf() {
    }

    static String html(Paciente p, ConsultorioResumen consultorio, List<AtencionImpresa> atenciones,
                       List<DocumentoClinico> documentos, String generadoPor) {
        StringBuilder b = new StringBuilder();
        b.append(EncabezadoPdf.html(consultorio, "HISTORIA CLÍNICA COMPLETA",
                "Generada: " + fechaHora(Instant.now(), consultorio.zona())));
        b.append(EncabezadoPdf.paciente(p));

        if (p.getAlergias() != null && !p.getAlergias().isBlank()) {
            b.append("<h2>Alergias</h2><div class=\"texto\">").append(esc(p.getAlergias().strip())).append("</div>");
        }
        if (p.getAntecedentes() != null && !p.getAntecedentes().isBlank()) {
            b.append("<h2>Antecedentes</h2><div class=\"texto\">").append(esc(p.getAntecedentes().strip())).append("</div>");
        }

        b.append("<h2>Atenciones (").append(atenciones.size()).append(")</h2>");
        if (atenciones.isEmpty()) {
            b.append("<p class=\"muted\">El paciente no tiene atenciones firmadas.</p>");
        } else {
            b.append("<table class=\"grilla\"><tr><th>#</th><th>Fecha</th><th>Profesional</th><th>Diagnóstico principal</th></tr>");
            int i = 1;
            for (AtencionImpresa ai : atenciones) {
                Atencion a = ai.atencion();
                String dx = a.getDiagnosticos().stream().filter(Diagnostico::isPrincipal).findFirst()
                        .map(d -> d.getCodigoCie10() + " " + d.getDescripcion()).orElse("—");
                b.append("<tr><td>").append(i++).append("</td><td style=\"white-space: nowrap\">").append(esc(fechaHora(a.getFechaAtencion(), consultorio.zona())))
                        .append("</td><td>").append(esc(a.getMedicoNombre())).append("</td><td>").append(esc(dx)).append("</td></tr>");
            }
            b.append("</table>");
        }

        if (!documentos.isEmpty()) {
            b.append("<h2>Documentos adjuntos (").append(documentos.size()).append(")</h2>")
                    .append("<p class=\"small muted\">Los archivos no se incluyen en este PDF; reposan en la historia del paciente en Galeno.</p>")
                    .append("<table class=\"grilla\"><tr><th>Fecha</th><th>Tipo</th><th>Descripción</th><th>Archivo</th></tr>");
            for (DocumentoClinico d : documentos) {
                b.append("<tr><td>").append(esc(fechaHora(d.getCreadoEn(), consultorio.zona()))).append("</td><td>")
                        .append(esc(tipoDocumento(d.getTipo()))).append("</td><td>")
                        .append(esc(d.getDescripcion() == null ? "" : d.getDescripcion())).append("</td><td>")
                        .append(esc(d.getNombreArchivo())).append("</td></tr>");
            }
            b.append("</table>");
        }

        // Seguidas, como un documento corrido: no una por hoja (ver .atencion en PdfRenderer)
        if (!atenciones.isEmpty()) {
            b.append("<h2>Detalle de las atenciones</h2>");
        }
        int i = 1;
        for (AtencionImpresa ai : atenciones) {
            Atencion a = ai.atencion();
            b.append("<div class=\"atencion\"><h3>Atención ").append(i++).append(" de ").append(atenciones.size())
                    .append(" · ").append(esc(fechaHora(a.getFechaAtencion(), consultorio.zona()))).append("</h3>");
            AtencionPdf.cuerpo(b, a, consultorio, ai.notas());
            b.append("</div>");
        }

        b.append("<p class=\"small muted\">Copia generada por ").append(esc(generadoPor)).append(" en Galeno. Incluye solo atenciones ")
                .append("firmadas (o importadas de otro sistema); los borradores no son registro clínico. La historia clínica ")
                .append("original reposa en el sistema.</p>");
        return PdfRenderer.documento("Historia clínica " + p.getNombres() + " " + p.getApellidos(), b.toString());
    }

    private static String tipoDocumento(DocumentoClinico.Tipo t) {
        String s = t.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
