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

    /**
     * Lo que la atención no guarda pero el RDA pide: la entidad responsable (cobro del RIPS), los nombres de los códigos
     * del encuentro y los medicamentos formulados en la atención.
     */
    record Extras(String entidadResponsable, String modalidad, String grupoServicios, String causa,
                  List<String> medicamentos) {
        static final Extras NINGUNO = new Extras(null, null, null, null, List.of());
    }

    /** @param formatosHtml los formatos diligenciados, ya en XHTML (vacío si no tiene) */
    static String html(Atencion a, Paciente p, ConsultorioResumen consultorio, List<NotaAclaratoria> notas,
                       String formatosHtml, Extras extras) {
        StringBuilder b = new StringBuilder();
        if (!a.firmada()) {
            b.append("<div class=\"marca-agua\">BORRADOR</div>");
        }
        b.append(EncabezadoPdf.html(consultorio, "HISTORIA CLÍNICA · ATENCIÓN",
                "Fecha: " + fechaHora(a.getFechaAtencion(), consultorio.zona())));
        b.append(EncabezadoPdf.paciente(p));
        cuerpo(b, a, consultorio, notas, formatosHtml, extras);
        b.append("<p class=\"small muted\">Atención ").append(esc(a.getId())).append(" · origen ").append(esc(a.getOrigen()))
                .append(". Documento generado por Galenum; la historia clínica original reposa en el sistema.</p>");
        return PdfRenderer.documento("Atención " + p.getNombres() + " " + p.getApellidos(), b.toString());
    }

    /**
     * Todo lo de la atención salvo el encabezado (bloque 1, prestador) y los datos del paciente (bloque 3), en el orden
     * de los bloques del Resumen Digital de Atención (RDA). También lo usa {@link HistoriaPdf}.
     */
    static void cuerpo(StringBuilder b, Atencion a, ConsultorioResumen consultorio, List<NotaAclaratoria> notas,
                       String formatosHtml, Extras extras) {
        Extras ex = extras == null ? Extras.NINGUNO : extras;
        // 2. Entidad responsable del pago
        seccion(b, "Entidad responsable", ex.entidadResponsable());

        // 4. Datos del encuentro
        List<String> encuentro = new ArrayList<>();
        encuentro.add("Inicio: " + fechaHora(a.getFechaAtencion(), consultorio.zona())
                + (a.getFinalizadaEn() != null ? " · Fin: " + fechaHora(a.getFinalizadaEn(), consultorio.zona()) : ""));
        agregarTexto(encuentro, "Modalidad", ex.modalidad() != null ? ex.modalidad() : a.getModalidadAtencion());
        agregarTexto(encuentro, "Grupo de servicios", ex.grupoServicios() != null ? ex.grupoServicios() : a.getGrupoServicios());
        agregarTexto(encuentro, "Causa", ex.causa() != null ? ex.causa() : a.getCausaExterna());
        agregarTexto(encuentro, "Entorno", a.getEntorno());
        if (a.getAcompananteNombre() != null) {
            encuentro.add("Acompañante: " + a.getAcompananteNombre()
                    + (a.getAcompananteParentesco() != null ? " (" + a.getAcompananteParentesco() + ")" : "")
                    + (a.getAcompananteNumeroDocumento() != null
                    ? " · " + (a.getAcompananteTipoDocumento() != null ? a.getAcompananteTipoDocumento() + " " : "")
                    + a.getAcompananteNumeroDocumento() : ""));
        }
        seccion(b, "Datos del encuentro", String.join("\n", encuentro));

        // Lo que pasó en la consulta (sustenta los diagnósticos)
        seccion(b, "Motivo de consulta", a.getMotivoConsulta());
        seccion(b, "Enfermedad actual", a.getEnfermedadActual());
        // 5. Antecedentes
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
        // Los formatos van con el examen, antes del diagnóstico: son hallazgos que lo sustentan
        b.append(formatosHtml);

        // 6. Diagnósticos
        if (!a.getDiagnosticos().isEmpty()) {
            b.append("<h2>Diagnósticos (CIE-10)</h2><table class=\"grilla\"><tr><th>Código</th><th>Descripción</th><th>Tipo</th><th></th></tr>");
            for (Diagnostico d : a.getDiagnosticos()) {
                b.append("<tr><td>").append(esc(d.getCodigoCie10())).append("</td><td>").append(esc(d.getDescripcion()))
                        .append("</td><td>").append(esc(tipo(d.getTipo()))).append("</td><td>")
                        .append(d.isPrincipal() ? "Principal" : "Relacionado").append("</td></tr>");
            }
            b.append("</table>");
        }
        seccion(b, "Análisis", a.getAnalisis());
        seccion(b, "Plan", a.getPlan());

        // 7. Medicamentos formulados en la atención
        if (!ex.medicamentos().isEmpty()) {
            b.append("<h2>Medicamentos</h2><ul>");
            ex.medicamentos().forEach(m -> b.append("<li>").append(esc(m)).append("</li>"));
            b.append("</ul>");
        }
        // 8. Procedimientos y sus resultados
        if (!a.getProcedimientos().isEmpty()) {
            // Sin valores: la historia clínica es registro clínico, no de cobro
            b.append("<h2>Procedimientos realizados</h2><table class=\"grilla\"><tr><th>CUPS</th><th>Descripción</th><th>Cant.</th><th>Diente</th><th>Resultado</th></tr>");
            for (Procedimiento p : a.getProcedimientos()) {
                b.append("<tr><td>").append(esc(p.getCodigoCups())).append("</td><td>").append(esc(p.getDescripcion()))
                        .append("</td><td>").append(p.getCantidad()).append("</td><td>")
                        .append(p.getDiente() == null ? "" : esc(p.getDiente() + (p.getSuperficies() == null ? "" : " " + p.getSuperficies())))
                        .append("</td><td>").append(p.getResultado() == null ? "" : esc(p.getResultado()))
                        .append("</td></tr>");
            }
            b.append("</table>");
        }
        // 9. Órdenes médicas
        if (!a.getOrdenes().isEmpty()) {
            b.append("<h2>Órdenes médicas</h2><table class=\"grilla\"><tr><th>Tipo</th><th>CUPS</th><th>Descripción</th><th>Cant.</th><th>Indicaciones</th></tr>");
            for (OrdenMedica o : a.getOrdenes()) {
                b.append("<tr><td>").append(esc(tipoOrden(o.getTipo())))
                        .append(o.getPrioridad() == OrdenMedica.Prioridad.URGENTE ? " (urgente)" : "").append("</td><td>")
                        .append(o.getCodigoCups() == null ? "" : esc(o.getCodigoCups())).append("</td><td>")
                        .append(esc(o.getDescripcion())).append("</td><td>").append(o.getCantidad()).append("</td><td>")
                        .append(o.getIndicaciones() == null ? "" : esc(o.getIndicaciones())).append("</td></tr>");
            }
            b.append("</table>");
        }
        // 10. Factores de riesgo
        if (!a.getFactoresRiesgo().isEmpty()) {
            seccion(b, "Factores de riesgo", String.join("\n", a.getFactoresRiesgo().stream().map(FactorRiesgo::texto).toList()));
        }
        // 11. Incapacidad (el certificado va en los formatos)
        if (a.isIncapacidad()) {
            seccion(b, "Incapacidad", "Se expidió incapacidad (ver el certificado en los formatos de esta atención)");
        }

        // 12. Profesional tratante
        b.append("<div class=\"firma\"><div class=\"linea\"><strong>").append(esc(a.getMedicoNombre())).append("</strong><br />");
        if (a.getRegistroMedico() != null) {
            b.append("Registro médico ").append(esc(a.getRegistroMedico())).append("<br />");
        }
        if (a.getOrigen() == Atencion.Origen.IMPORTADA) {
            b.append("<span class=\"small muted\">Registro importado de otro sistema el ")
                    .append(esc(fechaHora(a.getFirmadaEn(), consultorio.zona())))
                    .append(". No corresponde a una firma en Galenum.</span>");
        } else {
            b.append(a.firmada()
                    ? "<span class=\"small muted\">Firmada electrónicamente en Galenum el " + esc(fechaHora(a.getFirmadaEn(), consultorio.zona())) + "</span>"
                    : "<span class=\"small muted\">Borrador sin firmar: sin validez como registro clínico</span>");
        }
        b.append("</div></div>");

        if (!notas.isEmpty()) {
            b.append("<h2>Notas aclaratorias</h2>");
            for (NotaAclaratoria n : notas) {
                b.append("<div class=\"caja\"><span class=\"small muted\">").append(esc(n.getAutorNombre())).append(" · ")
                        .append(esc(fechaHora(n.getCreadaEn(), consultorio.zona()))).append("</span><div class=\"texto\">")
                        .append(esc(n.getTexto())).append("</div></div>");
            }
        }
    }

    static String tipoOrden(OrdenMedica.Tipo t) {
        return switch (t) {
            case LABORATORIO -> "Laboratorio";
            case IMAGEN -> "Imagen diagnóstica";
            case PROCEDIMIENTO -> "Procedimiento";
            case INTERCONSULTA -> "Interconsulta";
        };
    }

    private static void agregarTexto(List<String> lineas, String etiqueta, String valor) {
        if (valor != null && !valor.isBlank()) {
            lineas.add(etiqueta + ": " + valor);
        }
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

    static String tipo(Diagnostico.Tipo t) {
        return switch (t) {
            case IMPRESION_DIAGNOSTICA -> "Impresión diagnóstica";
            case CONFIRMADO_NUEVO -> "Confirmado nuevo";
            case CONFIRMADO_REPETIDO -> "Confirmado repetido";
        };
    }
}
