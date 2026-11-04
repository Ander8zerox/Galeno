package com.galeno.formatos;

import static com.galeno.shared.pdf.PdfRenderer.esc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.galeno.formatos.DefinicionFormato.Campo;
import com.galeno.formatos.DefinicionFormato.Columna;
import com.galeno.formatos.DefinicionFormato.Fila;
import com.galeno.formatos.DefinicionFormato.ItemEscala;
import com.galeno.formatos.DefinicionFormato.Seccion;
import com.galeno.formatos.DefinicionFormato.Tipo;

import tools.jackson.databind.JsonNode;

/** Un formato diligenciado en el PDF de la atención: solo las secciones y campos con datos. */
final class PresentacionFormato {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private PresentacionFormato() {
    }

    /** XHTML del formato; vacío si no tiene nada diligenciado. */
    static String html(String nombre, DefinicionFormato d, JsonNode respuestas) {
        StringBuilder b = new StringBuilder();
        for (Seccion s : d.secciones()) {
            JsonNode valores = respuestas == null ? null : respuestas.get(s.clave());
            if (valores == null || valores.isEmpty()) {
                continue;
            }
            List<String> filas = new ArrayList<>();
            StringBuilder aparte = new StringBuilder();
            for (Campo c : s.campos()) {
                JsonNode v = valores.get(c.clave());
                if (v == null) {
                    continue;
                }
                switch (c.tipo()) {
                    case TABLA -> aparte.append(tabla(c, v));
                    case ESCALA -> filas.add(fila(c.etiqueta(), escala(c, v)));
                    default -> filas.add(fila(c.etiqueta(), esc(valor(c.tipo(), v, c.unidad()))));
                }
            }
            b.append("<h3 class=\"seccion-formato\">").append(esc(s.titulo())).append("</h3>");
            if (!filas.isEmpty()) {
                b.append("<table class=\"grilla\">").append(String.join("", filas)).append("</table>");
            }
            b.append(aparte);
        }
        return b.isEmpty() ? "" : "<h2>" + esc(nombre) + "</h2>" + b;
    }

    private static String fila(String etiqueta, String valorHtml) {
        return "<tr><th style=\"width: 35%\">" + esc(etiqueta) + "</th><td class=\"texto\">" + valorHtml + "</td></tr>";
    }

    private static String tabla(Campo c, JsonNode filas) {
        StringBuilder b = new StringBuilder("<p class=\"small\"><strong>").append(esc(c.etiqueta())).append("</strong></p>")
                .append("<table class=\"grilla\"><tr>");
        if (c.filas() != null) {
            b.append("<th></th>");
        }
        c.columnas().forEach(col -> b.append("<th>").append(esc(col.etiqueta())).append("</th>"));
        b.append("</tr>");
        for (JsonNode f : filas) {
            b.append("<tr>");
            if (c.filas() != null) {
                String cual = f.get("fila").stringValue();
                String etiqueta = c.filas().stream().filter(x -> x.clave().equals(cual)).map(Fila::etiqueta).findFirst().orElse(cual);
                b.append("<th>").append(esc(etiqueta)).append("</th>");
            }
            for (Columna col : c.columnas()) {
                JsonNode v = f.get(col.clave());
                b.append("<td class=\"texto\">").append(v == null ? "" : esc(valor(col.tipo(), v, null))).append("</td>");
            }
            b.append("</tr>");
        }
        return b.append("</table>").toString();
    }

    private static String escala(Campo c, JsonNode v) {
        StringBuilder b = new StringBuilder();
        ValidadorRespuestas.Total total = ValidadorRespuestas.total(c, v);
        if (total != null) {
            b.append("<strong>").append(total.puntaje()).append(" / ").append(total.maximo()).append("</strong>");
            if (total.interpretacion() != null) {
                b.append(" · ").append(esc(total.interpretacion()));
            }
        } else {
            b.append("<span class=\"muted\">Incompleta</span>");
        }
        if (c.items().size() > 1) {
            for (ItemEscala i : c.items()) {
                JsonNode p = v.get(i.clave());
                if (p != null) {
                    b.append("<br />").append(esc(i.etiqueta())).append(": ")
                            .append(esc(ValidadorRespuestas.opcion(i, p.intValue()))).append(" (").append(p.intValue()).append(")");
                }
            }
        }
        return b.toString();
    }

    /** Valor de un campo sencillo como texto legible. */
    static String valor(Tipo tipo, JsonNode v, String unidad) {
        return switch (tipo) {
            case SI_NO -> v.booleanValue() ? "Sí" : "No";
            case FECHA -> LocalDate.parse(v.stringValue()).format(FECHA);
            case MULTIPLE -> {
                List<String> elegidas = new ArrayList<>();
                v.forEach(x -> elegidas.add(x.stringValue()));
                yield String.join(", ", elegidas);
            }
            case NUMERO -> {
                BigDecimal n = v.decimalValue().stripTrailingZeros();
                yield n.toPlainString() + (unidad == null ? "" : " " + unidad);
            }
            default -> v.isString() ? v.stringValue() : v.toString();
        };
    }
}
