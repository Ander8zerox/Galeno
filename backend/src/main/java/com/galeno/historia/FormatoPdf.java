package com.galeno.historia;

import static com.galeno.shared.pdf.PdfRenderer.esc;
import static com.galeno.shared.pdf.PdfRenderer.fechaHora;

import com.galeno.formatos.FormatosService.DocumentoFormato;
import com.galeno.identidad.IdentidadService.ConsultorioResumen;
import com.galeno.pacientes.Paciente;
import com.galeno.shared.pdf.PdfRenderer;

/**
 * Un formato que se entrega como documento propio (incapacidad, remisión, contrarreferencia, consentimiento): el
 * encabezado del consultorio, el paciente, el formato, los diagnósticos si el formato los pide, y la firma del médico.
 */
final class FormatoPdf {

    private FormatoPdf() {
    }

    static String html(Atencion a, Paciente p, ConsultorioResumen consultorio, DocumentoFormato doc) {
        StringBuilder b = new StringBuilder();
        if (!a.firmada()) {
            b.append("<div class=\"marca-agua\">BORRADOR</div>");
        }
        b.append(EncabezadoPdf.html(consultorio, doc.titulo(), "Fecha: " + fechaHora(a.getFechaAtencion(), consultorio.zona())));
        b.append(EncabezadoPdf.paciente(p));
        b.append(doc.html());
        if (doc.diagnosticos() && !a.getDiagnosticos().isEmpty()) {
            b.append("<h2>Diagnósticos</h2><table class=\"grilla\"><tr><th>Código CIE-10</th><th>Descripción</th><th></th></tr>");
            for (Diagnostico d : a.getDiagnosticos()) {
                b.append("<tr><td>").append(esc(d.getCodigoCie10())).append("</td><td>").append(esc(d.getDescripcion()))
                        .append("</td><td>").append(d.isPrincipal() ? "Principal" : "Relacionado").append("</td></tr>");
            }
            b.append("</table>");
        }
        b.append("<div class=\"firma\"><div class=\"linea\"><strong>").append(esc(a.getMedicoNombre())).append("</strong><br />");
        if (a.getRegistroMedico() != null) {
            b.append("Registro médico ").append(esc(a.getRegistroMedico())).append("<br />");
        }
        b.append(a.firmada()
                ? "<span class=\"small muted\">Firmado electrónicamente en Galenum el " + esc(fechaHora(a.getFirmadaEn(), consultorio.zona())) + "</span>"
                : "<span class=\"small muted\">Borrador sin firmar: sin validez</span>");
        b.append("</div></div>");
        b.append("<p class=\"small muted\">Atención ").append(esc(a.getId()))
                .append(". Documento generado por Galenum; el original reposa en la historia clínica.</p>");
        return PdfRenderer.documento(doc.nombre() + " " + p.getNombres() + " " + p.getApellidos(), b.toString());
    }
}
