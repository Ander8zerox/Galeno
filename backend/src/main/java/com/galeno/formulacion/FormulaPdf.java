package com.galeno.formulacion;

import static com.galeno.shared.pdf.PdfRenderer.esc;
import static com.galeno.shared.pdf.PdfRenderer.fechaHora;

import com.galeno.historia.EncabezadoPdf;
import com.galeno.identidad.IdentidadService.ConsultorioResumen;
import com.galeno.identidad.IdentidadService.Profesional;
import com.galeno.pacientes.Paciente;
import com.galeno.shared.pdf.PdfRenderer;

/**
 * Fórmula médica impresa. Incluye lo que exige la prescripción en Colombia: prestador, paciente y documento,
 * fecha, medicamento por denominación común con concentración y forma farmacéutica, vía, dosis, frecuencia,
 * duración, cantidad total, indicaciones, y nombre y registro del prescriptor.
 */
final class FormulaPdf {

    private FormulaPdf() {
    }

    static String html(Formula f, Paciente p, ConsultorioResumen consultorio, Profesional medico, String diagnostico) {
        StringBuilder b = new StringBuilder();
        String numero = f.getId().toString().substring(0, 8).toUpperCase();
        b.append(EncabezadoPdf.html(consultorio, "FÓRMULA MÉDICA",
                "No. " + numero + " · " + fechaHora(f.getFirmadaEn(), consultorio.zona())));
        b.append(EncabezadoPdf.paciente(p));
        if (diagnostico != null) {
            b.append("<p><span class=\"muted\">Diagnóstico:</span> ").append(esc(diagnostico)).append("</p>");
        }

        b.append("<table class=\"grilla\"><tr><th>#</th><th>Medicamento</th><th>Vía</th><th>Dosis y frecuencia</th>"
                + "<th>Duración</th><th>Cantidad</th></tr>");
        int n = 1;
        for (FormulaItem i : f.getItems()) {
            String medicamento = String.join(" ", noVacios(i.getMedicamento(), i.getConcentracion(), i.getFormaFarmaceutica()));
            b.append("<tr><td>").append(n++).append("</td><td><strong>").append(esc(medicamento)).append("</strong>");
            if (i.getIndicaciones() != null && !i.getIndicaciones().isBlank()) {
                b.append("<br /><span class=\"small\">").append(esc(i.getIndicaciones())).append("</span>");
            }
            b.append("</td><td>").append(esc(i.getVia())).append("</td><td>").append(esc(i.getDosis())).append(" ")
                    .append(esc(i.getFrecuencia())).append("</td><td>").append(esc(i.getDuracion())).append("</td><td>")
                    .append(esc(i.getCantidad())).append("</td></tr>");
        }
        b.append("</table>");

        if (f.getObservaciones() != null && !f.getObservaciones().isBlank()) {
            b.append("<h2>Observaciones</h2><div class=\"texto\">").append(esc(f.getObservaciones())).append("</div>");
        }

        b.append("<div class=\"firma\"><div class=\"linea\"><strong>").append(esc(medico.nombre())).append("</strong><br />");
        String registro = f.getRegistroMedico() != null ? f.getRegistroMedico() : medico.registroMedico();
        if (registro != null) {
            b.append("Registro médico ").append(esc(registro)).append("<br />");
        }
        if (medico.especialidad() != null) {
            b.append(esc(medico.especialidad())).append("<br />");
        }
        b.append("<span class=\"small muted\">Firmada electrónicamente en Galeno el ")
                .append(esc(fechaHora(f.getFirmadaEn(), consultorio.zona()))).append("</span></div></div>");
        b.append("<p class=\"small muted\">Fórmula ").append(esc(f.getId())).append("</p>");
        return PdfRenderer.documento("Fórmula " + numero, b.toString());
    }

    private static java.util.List<String> noVacios(String... partes) {
        return java.util.Arrays.stream(partes).filter(s -> s != null && !s.isBlank()).map(String::strip).toList();
    }
}
