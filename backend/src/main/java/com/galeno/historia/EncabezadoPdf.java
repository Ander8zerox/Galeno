package com.galeno.historia;

import static com.galeno.shared.pdf.PdfRenderer.esc;

import com.galeno.identidad.IdentidadService.ConsultorioResumen;
import com.galeno.pacientes.Paciente;

/** Bloques comunes de los documentos clínicos: encabezado del consultorio y datos del paciente. */
public final class EncabezadoPdf {

    private EncabezadoPdf() {
    }

    public static String html(ConsultorioResumen c, String titulo, String detalle) {
        return "<table class=\"encabezado\"><tr><td><h1>" + esc(c.nombre()) + "</h1>"
                + (c.nit() == null ? "" : "<span class=\"muted\">NIT " + esc(c.nit()) + "</span>")
                + "</td><td class=\"derecha\"><strong>" + esc(titulo) + "</strong><br /><span class=\"muted\">"
                + esc(detalle) + "</span></td></tr></table>";
    }

    public static String paciente(Paciente p) {
        String sexo = switch (p.getSexo()) {
            case "F" -> "Femenino";
            case "M" -> "Masculino";
            default -> "Indeterminado";
        };
        return "<div class=\"caja\"><table class=\"datos\"><tr>"
                + "<td><span class=\"muted small\">Paciente</span><br /><strong>" + esc(p.getNombres() + " " + p.getApellidos()) + "</strong></td>"
                + "<td><span class=\"muted small\">Documento</span><br />" + esc(p.getTipoDocumento() + " " + p.getNumeroDocumento()) + "</td>"
                + "<td><span class=\"muted small\">Edad</span><br />" + p.edad() + " años</td>"
                + "<td><span class=\"muted small\">Sexo</span><br />" + sexo + "</td>"
                + "</tr></table></div>";
    }
}
