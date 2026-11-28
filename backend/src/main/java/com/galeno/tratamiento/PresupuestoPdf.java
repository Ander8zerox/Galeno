package com.galeno.tratamiento;

import static com.galeno.shared.pdf.PdfRenderer.esc;
import static com.galeno.shared.pdf.PdfRenderer.fechaHora;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.galeno.historia.EncabezadoPdf;
import com.galeno.identidad.IdentidadService.ConsultorioResumen;
import com.galeno.identidad.IdentidadService.Profesional;
import com.galeno.pacientes.Paciente;
import com.galeno.shared.pdf.PdfRenderer;

/**
 * Presupuesto del plan de tratamiento para el paciente: fases, procedimientos con diente y valores, total, vigencia de
 * la cotización y las aceptaciones (con la firma, si la hubo). Lo cancelado no se imprime.
 */
final class PresupuestoPdf {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private PresupuestoPdf() {
    }

    static String html(TratamientosService.Presupuesto pr, Paciente paciente, ConsultorioResumen consultorio,
                       Profesional odontologo) {
        PlanTratamiento plan = pr.plan();
        String numero = plan.getId().toString().substring(0, 8).toUpperCase();
        Instant fecha = plan.getPresentadoEn() != null ? plan.getPresentadoEn() : plan.getActualizadoEn();
        StringBuilder b = new StringBuilder();
        b.append(EncabezadoPdf.html(consultorio, "PRESUPUESTO DE TRATAMIENTO",
                "No. " + numero + " · " + fechaHora(fecha, consultorio.zona())));
        b.append(EncabezadoPdf.paciente(paciente));
        if (plan.getEstado() == PlanTratamiento.Estado.BORRADOR) {
            b.append("<p class=\"small muted\"><strong>Borrador:</strong> este presupuesto aún no se ha presentado al paciente.</p>");
        }

        List<ItemPlan> vigentes = pr.items().stream().filter(i -> i.getEstado() != ItemPlan.Estado.CANCELADO).toList();
        Map<Integer, List<ItemPlan>> porFase = new LinkedHashMap<>();
        vigentes.forEach(i -> porFase.computeIfAbsent(i.getFase(), f -> new java.util.ArrayList<>()).add(i));

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Integer, List<ItemPlan>> fase : porFase.entrySet()) {
            String nombre = fase.getValue().stream().map(ItemPlan::getFaseNombre).filter(Objects::nonNull).findFirst()
                    .map(n -> "Fase " + fase.getKey() + ": " + n).orElse("Fase " + fase.getKey());
            b.append("<h2>").append(esc(nombre)).append("</h2>");
            b.append("<table class=\"grilla\"><tr><th>Procedimiento</th><th>Diente</th><th class=\"derecha\">Cant.</th>"
                    + "<th class=\"derecha\">Valor unitario</th><th class=\"derecha\">Descuento</th><th class=\"derecha\">Total</th></tr>");
            BigDecimal totalFase = BigDecimal.ZERO;
            for (ItemPlan i : fase.getValue()) {
                b.append("<tr><td>").append(esc(i.getDescripcion())).append("<br /><span class=\"small muted\">CUPS ")
                        .append(esc(i.getCodigoCups()));
                if (i.getAceptacion() != null && i.getAceptacion() > 1) {
                    b.append(" · adicional ").append(i.getAceptacion() - 1);
                } else if (i.getAceptacion() == null && !pr.aceptaciones().isEmpty()) {
                    b.append(" · adicional por aceptar");
                }
                if (i.getEstado() == ItemPlan.Estado.REALIZADO) {
                    b.append(" · realizado");
                }
                b.append("</span></td><td>").append(esc(diente(i))).append("</td><td class=\"derecha\">")
                        .append(i.getCantidad()).append("</td><td class=\"derecha\">").append(pesos(i.getValorUnitario()))
                        .append("</td><td class=\"derecha\">")
                        .append(i.getDescuento().signum() == 0 ? "" : pesos(i.getDescuento()))
                        .append("</td><td class=\"derecha\">").append(pesos(i.total())).append("</td></tr>");
                totalFase = totalFase.add(i.total());
            }
            b.append("<tr><td colspan=\"5\" class=\"derecha\"><strong>Total de la fase</strong></td><td class=\"derecha\"><strong>")
                    .append(pesos(totalFase)).append("</strong></td></tr></table>");
            total = total.add(totalFase);
        }
        b.append("<p class=\"derecha\" style=\"font-size: 12pt;\"><strong>Total del tratamiento: ").append(pesos(total))
                .append("</strong></p>");

        if (plan.getPresentadoEn() != null) {
            String hasta = plan.getPresentadoEn().atZone(consultorio.zona()).toLocalDate()
                    .plusDays(plan.getVigenciaDias()).format(FECHA);
            b.append("<p class=\"small\">Cotización válida por ").append(plan.getVigenciaDias())
                    .append(" días, hasta el ").append(hasta)
                    .append(". Los valores pueden cambiar si el tratamiento se modifica durante su ejecución.</p>");
        }
        if (plan.getObservaciones() != null) {
            b.append("<h2>Observaciones</h2><div class=\"texto\">").append(esc(plan.getObservaciones())).append("</div>");
        }

        b.append("<div class=\"firma\"><div class=\"linea\"><strong>").append(esc(odontologo.nombre())).append("</strong><br />");
        if (odontologo.registroMedico() != null) {
            b.append("Registro ").append(esc(odontologo.registroMedico())).append("<br />");
        }
        b.append("<span class=\"small muted\">Odontólogo tratante</span></div></div>");

        if (pr.aceptaciones().isEmpty()) {
            b.append("<div class=\"firma\"><div class=\"linea\">Firma del paciente o su representante<br />"
                    + "<span class=\"small muted\">Acepto el plan de tratamiento y su presupuesto.</span></div></div>");
        }
        for (AceptacionPlan a : pr.aceptaciones()) {
            String que = a.getNumero() == 1 ? "Aceptación del plan" : "Aceptación del adicional " + (a.getNumero() - 1);
            b.append("<div class=\"firma-formato\"><p><strong>").append(esc(que)).append("</strong> · ")
                    .append(pesos(a.getTotal())).append(" · ").append(esc(fechaHora(a.getAceptadoEn(), consultorio.zona())))
                    .append("</p>");
            if (a.getFirma() != null) {
                b.append("<img class=\"firma-imagen\" src=\"").append(esc(a.getFirma())).append("\" alt=\"Firma\" /><br />");
            } else {
                b.append("<p class=\"small\">Aceptación verbal registrada en Galenum.</p>");
            }
            if (a.getNombreFirmante() != null) {
                b.append("<span class=\"small\">").append(esc(a.getNombreFirmante())).append("</span>");
            }
            b.append("</div>");
        }
        b.append("<p class=\"small muted\">Plan ").append(esc(plan.getId())).append("</p>");
        return PdfRenderer.documento("Presupuesto " + numero, b.toString());
    }

    private static String diente(ItemPlan i) {
        if (i.getDiente() == null) {
            return "";
        }
        return i.getSuperficies() == null ? i.getDiente() : i.getDiente() + " " + i.getSuperficies();
    }

    /** "$ 1.250.000" (pesos colombianos; los centavos solo si los hay). */
    static String pesos(BigDecimal valor) {
        DecimalFormat f = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CO")));
        return "$ " + f.format(valor);
    }
}
