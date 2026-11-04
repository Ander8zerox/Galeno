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
            // Una sección con el mismo nombre del formato (p. ej. "Odontograma") no repite el título
            if (!s.titulo().equalsIgnoreCase(nombre)) {
                b.append("<h3 class=\"seccion-formato\">").append(esc(s.titulo())).append("</h3>");
            }
            // En el orden de la definición: los campos sencillos seguidos van en una misma grilla
            List<String> filas = new ArrayList<>();
            for (Campo c : s.campos()) {
                JsonNode v = valores.get(c.clave());
                if (v == null) {
                    continue;
                }
                switch (c.tipo()) {
                    case TABLA, ODONTOGRAMA -> {
                        cerrarGrilla(b, filas);
                        b.append(c.tipo() == DefinicionFormato.Tipo.TABLA ? tabla(c, v) : odontograma(c, v));
                    }
                    case ESCALA -> filas.add(fila(c.etiqueta(), escala(c, v)));
                    default -> filas.add(fila(c.etiqueta(), esc(valor(c.tipo(), v, c.unidad()))));
                }
            }
            cerrarGrilla(b, filas);
        }
        return b.isEmpty() ? "" : "<h2>" + esc(nombre) + "</h2>" + b;
    }

    private static void cerrarGrilla(StringBuilder b, List<String> filas) {
        if (!filas.isEmpty()) {
            b.append("<table class=\"grilla\">").append(String.join("", filas)).append("</table>");
            filas.clear();
        }
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

    /** Colores de los hallazgos por superficie: rojo lo que hay que tratar, azul lo tratado (como en la pantalla). */
    private static final java.util.Map<String, String> COLORES = java.util.Map.of(
            "caries", "#d64545", "resina", "#2f6fd6", "amalgama", "#5b6577", "sellante", "#2a9d8f");

    private static final java.util.Map<String, String> SIGLAS = java.util.Map.of(
            "ausente", "AU", "extraccion_indicada", "EI", "corona", "CO", "endodoncia", "EN", "implante", "IM",
            "protesis", "PR", "fractura", "FR");

    /** Cuadrícula de dientes (sin SVG: openhtmltopdf no lo dibuja sin Batik) y la lista de hallazgos, que es la que vale. */
    private static String odontograma(Campo c, JsonNode v) {
        String denticion = v.get("denticion").stringValue();
        JsonNode dientes = v.get("dientes");
        StringBuilder b = new StringBuilder("<p class=\"small\"><strong>").append(esc(c.etiqueta())).append("</strong> · dentición ")
                .append(esc(Odontograma.nombreDenticion(denticion).toLowerCase(java.util.Locale.ROOT))).append("</p>");
        b.append("<table class=\"odontograma\">");
        for (List<Integer> fila : Odontograma.filas(denticion)) {
            b.append("<tr>");
            for (int i = 0; i < fila.size(); i++) {
                if (i == fila.size() / 2) {
                    b.append("<td class=\"linea-media\"></td>");
                }
                b.append("<td>").append(diente(fila.get(i), dientes.get(String.valueOf(fila.get(i))))).append("</td>");
            }
            b.append("</tr>");
        }
        b.append("</table><p class=\"small muted\">Rojo: caries · Azul: resina · Gris: amalgama · Verde: sellante · ")
                .append("AU ausente · EI extracción indicada · CO corona · EN endodoncia · IM implante · PR prótesis · FR fractura</p>");
        List<String> hallazgos = Odontograma.hallazgos(v);
        b.append(hallazgos.isEmpty() ? "<p class=\"small\">Sin hallazgos.</p>"
                : "<ul class=\"small\"><li>" + hallazgos.stream().map(com.galeno.shared.pdf.PdfRenderer::esc)
                        .collect(java.util.stream.Collectors.joining("</li><li>")) + "</li></ul>");
        return b.toString();
    }

    private static String diente(int numero, JsonNode d) {
        int cuadrante = numero / 10;
        boolean superior = cuadrante == 1 || cuadrante == 2 || cuadrante == 5 || cuadrante == 6;
        // A la izquierda de quien mira está el lado derecho del paciente: allí la mesial queda hacia la línea media
        boolean derechoPaciente = cuadrante == 1 || cuadrante == 4 || cuadrante == 5 || cuadrante == 8;
        JsonNode sup = d == null ? null : d.get("superficies");
        java.util.function.Function<String, String> celda = s -> {
            String color = sup != null && sup.has(s) ? COLORES.get(sup.get(s).stringValue()) : null;
            return "<td" + (color != null ? " style=\"background: " + color + "\"" : "") + "></td>";
        };
        List<String> siglas = new ArrayList<>();
        if (d != null && d.has("hallazgos")) {
            d.get("hallazgos").forEach(h -> siglas.add(SIGLAS.get(h.stringValue())));
        }
        boolean ausente = siglas.contains("AU");
        String arriba = superior ? "V" : "L", abajo = superior ? "L" : "V";
        String izq = derechoPaciente ? "D" : "M", der = derechoPaciente ? "M" : "D";
        return "<div class=\"numero\">" + numero + "</div><table class=\"diente" + (ausente ? " ausente" : "") + "\">"
                + "<tr><td></td>" + celda.apply(arriba) + "<td></td></tr>"
                + "<tr>" + celda.apply(izq) + celda.apply("O") + celda.apply(der) + "</tr>"
                + "<tr><td></td>" + celda.apply(abajo) + "<td></td></tr></table>"
                + "<div class=\"siglas\">" + esc(String.join(" ", siglas)) + "</div>";
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
