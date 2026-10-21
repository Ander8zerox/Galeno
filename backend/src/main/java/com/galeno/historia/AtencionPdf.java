package com.galeno.historia;

import static com.galeno.shared.pdf.PdfRenderer.esc;
import static com.galeno.shared.pdf.PdfRenderer.fechaHora;

import java.util.ArrayList;
import java.util.List;

import com.galeno.identidad.IdentidadService.ConsultorioResumen;
import com.galeno.pacientes.Paciente;
import com.galeno.shared.pdf.PdfRenderer;

/** Plantilla XHTML de una atención de la historia clínica. */
final class AtencionPdf {

    private AtencionPdf() {
    }

    static String html(Atencion a, Paciente p, ConsultorioResumen consultorio, List<NotaAclaratoria> notas) {
        StringBuilder b = new StringBuilder();
        if (!a.firmada()) {
            b.append("<div class=\"marca-agua\">BORRADOR</div>");
        }
        b.append(EncabezadoPdf.html(consultorio, "HISTORIA CLÍNICA · ATENCIÓN",
                "Fecha: " + fechaHora(a.getFechaAtencion(), consultorio.zona())));
        b.append(EncabezadoPdf.paciente(p));

        seccion(b, "Motivo de consulta", a.getMotivoConsulta());
        seccion(b, "Enfermedad actual", a.getEnfermedadActual());
        seccion(b, "Antecedentes", a.getAntecedentes());
        seccion(b, "Revisión por sistemas", a.getRevisionSistemas());

        List<String> signos = new ArrayList<>();
        if (a.getTaSistolica() != null && a.getTaDiastolica() != null) {
            signos.add("TA " + a.getTaSistolica() + "/" + a.getTaDiastolica() + " mmHg");
        }
        agregar(signos, "FC", a.getFrecuenciaCardiaca(), "lpm");
        agregar(signos, "FR", a.getFrecuenciaRespiratoria(), "rpm");
        agregar(signos, "T", a.getTemperatura(), "°C");
        agregar(signos, "SatO2", a.getSaturacionO2(), "%");
        agregar(signos, "Peso", a.getPesoKg(), "kg");
        agregar(signos, "Talla", a.getTallaCm(), "cm");
        if (a.getPesoKg() != null && a.getTallaCm() != null && a.getTallaCm().signum() > 0) {
            double metros = a.getTallaCm().doubleValue() / 100;
            signos.add("IMC %.1f".formatted(a.getPesoKg().doubleValue() / (metros * metros)));
        }
        if (!signos.isEmpty()) {
            seccion(b, "Signos vitales", String.join(" · ", signos));
        }
        seccion(b, "Examen físico", a.getExamenFisico());

        if (!a.getDiagnosticos().isEmpty()) {
            b.append("<h2>Diagnósticos</h2><table class=\"grilla\"><tr><th>Código</th><th>Descripción</th><th>Tipo</th><th></th></tr>");
            for (Diagnostico d : a.getDiagnosticos()) {
                b.append("<tr><td>").append(esc(d.getCodigoCie10())).append("</td><td>").append(esc(d.getDescripcion()))
                        .append("</td><td>").append(esc(tipo(d.getTipo()))).append("</td><td>")
                        .append(d.isPrincipal() ? "Principal" : "Relacionado").append("</td></tr>");
            }
            b.append("</table>");
        }
        seccion(b, "Análisis", a.getAnalisis());
        seccion(b, "Plan", a.getPlan());

        b.append("<div class=\"firma\"><div class=\"linea\"><strong>").append(esc(a.getMedicoNombre())).append("</strong><br />");
        if (a.getRegistroMedico() != null) {
            b.append("Registro médico ").append(esc(a.getRegistroMedico())).append("<br />");
        }
        b.append(a.firmada()
                ? "<span class=\"small muted\">Firmada electrónicamente en Galeno el " + esc(fechaHora(a.getFirmadaEn(), consultorio.zona())) + "</span>"
                : "<span class=\"small muted\">Borrador sin firmar: sin validez como registro clínico</span>");
        b.append("</div></div>");

        if (!notas.isEmpty()) {
            b.append("<h2>Notas aclaratorias</h2>");
            for (NotaAclaratoria n : notas) {
                b.append("<div class=\"caja\"><span class=\"small muted\">").append(esc(n.getAutorNombre())).append(" · ")
                        .append(esc(fechaHora(n.getCreadaEn(), consultorio.zona()))).append("</span><div class=\"texto\">")
                        .append(esc(n.getTexto())).append("</div></div>");
            }
        }
        b.append("<p class=\"small muted\">Atención ").append(esc(a.getId())).append(" · origen ").append(esc(a.getOrigen()))
                .append(". Documento generado por Galeno; la historia clínica original reposa en el sistema.</p>");
        return PdfRenderer.documento("Atención " + p.getNombres() + " " + p.getApellidos(), b.toString());
    }

    private static void seccion(StringBuilder b, String titulo, String texto) {
        if (texto != null && !texto.isBlank()) {
            b.append("<h2>").append(esc(titulo)).append("</h2><div class=\"texto\">").append(esc(texto.strip())).append("</div>");
        }
    }

    private static void agregar(List<String> signos, String etiqueta, Object valor, String unidad) {
        if (valor != null) {
            signos.add(etiqueta + " " + valor + " " + unidad);
        }
    }

    private static String tipo(Diagnostico.Tipo t) {
        return switch (t) {
            case IMPRESION_DIAGNOSTICA -> "Impresión diagnóstica";
            case CONFIRMADO_NUEVO -> "Confirmado nuevo";
            case CONFIRMADO_REPETIDO -> "Confirmado repetido";
        };
    }
}
