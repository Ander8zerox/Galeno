package com.galeno.formatos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.galeno.formatos.DefinicionFormato.Campo;
import com.galeno.formatos.DefinicionFormato.Columna;
import com.galeno.formatos.DefinicionFormato.Fila;
import com.galeno.formatos.DefinicionFormato.ItemEscala;
import com.galeno.formatos.DefinicionFormato.OpcionEscala;
import com.galeno.formatos.DefinicionFormato.Seccion;
import com.galeno.formatos.DefinicionFormato.Tipo;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Valida las respuestas contra la definición. El frontend valida lo mismo para mostrar los errores, pero el que decide
 * es el servidor. Al guardar se revisan tipos, opciones y rangos; al firmar, además, los obligatorios.
 */
final class ValidadorRespuestas {

    static final int MAX_TEXTO = 500;
    static final int MAX_TEXTO_LARGO = 20_000;
    static final int MAX_FILAS = 50;

    private static final JsonNodeFactory NODOS = JsonNodeFactory.instance;

    private ValidadorRespuestas() {
    }

    /**
     * Devuelve las respuestas limpias, sin valores vacíos ni campos ocultos por {@code visibleSi}, para que lo que se
     * guarda sea lo que se ve. Responde 400 si algo no corresponde a la definición.
     */
    static ObjectNode normalizar(String formato, DefinicionFormato d, JsonNode respuestas) {
        ObjectNode limpias = NODOS.objectNode();
        if (respuestas == null || respuestas.isNull()) {
            return limpias;
        }
        if (!respuestas.isObject()) {
            throw ApiProblem.badRequest(formato + ": las respuestas deben ser un objeto por sección");
        }
        Map<String, Seccion> secciones = d.seccionesPorClave();
        for (Map.Entry<String, JsonNode> s : respuestas.properties()) {
            Seccion seccion = secciones.get(s.getKey());
            if (seccion == null) {
                throw ApiProblem.badRequest(formato + ": la sección «" + s.getKey() + "» no existe en esta versión");
            }
            ObjectNode valores = normalizarSeccion(formato, seccion, s.getValue());
            if (!valores.isEmpty()) {
                limpias.set(seccion.clave(), valores);
            }
        }
        return limpias;
    }

    /** Campos obligatorios visibles sin diligenciar, como «Sección › Campo». Vacío = se puede firmar. */
    static List<String> faltantes(DefinicionFormato d, JsonNode respuestas) {
        List<String> faltan = new ArrayList<>();
        for (Seccion s : d.secciones()) {
            JsonNode valores = respuestas == null ? null : respuestas.get(s.clave());
            for (Campo c : s.campos()) {
                if (!c.obligatorio() || !visible(c, valores)) {
                    continue;
                }
                JsonNode v = valores == null ? null : valores.get(c.clave());
                boolean completo = v != null && (c.tipo() != Tipo.ESCALA || v.size() == c.items().size());
                if (!completo) {
                    faltan.add(s.titulo() + " › " + c.etiqueta());
                }
            }
        }
        return faltan;
    }

    /** {@code visibleSi}: el otro campo tiene ese valor (o, si es de selección múltiple, lo incluye). */
    static boolean visible(Campo c, JsonNode valoresSeccion) {
        if (c.visibleSi() == null) {
            return true;
        }
        JsonNode otro = valoresSeccion == null ? null : valoresSeccion.get(c.visibleSi().campo());
        if (otro == null) {
            return false;
        }
        JsonNode igual = c.visibleSi().igual();
        if (otro.isArray()) {
            for (JsonNode x : otro) {
                if (x.equals(igual)) {
                    return true;
                }
            }
            return false;
        }
        return otro.equals(igual);
    }

    private static ObjectNode normalizarSeccion(String formato, Seccion s, JsonNode valores) {
        ObjectNode limpios = NODOS.objectNode();
        if (valores == null || valores.isNull()) {
            return limpios;
        }
        if (!valores.isObject()) {
            throw ApiProblem.badRequest(formato + " › " + s.titulo() + ": se esperaba un objeto con los campos");
        }
        Set<String> conocidos = new HashSet<>();
        s.campos().forEach(c -> conocidos.add(c.clave()));
        for (String clave : valores.propertyNames()) {
            if (!conocidos.contains(clave)) {
                throw ApiProblem.badRequest(formato + " › " + s.titulo() + ": el campo «" + clave + "» no existe en esta versión");
            }
        }
        // En el orden de la definición: visibleSi solo mira campos anteriores, ya limpios
        for (Campo c : s.campos()) {
            String donde = formato + " › " + s.titulo() + " › " + c.etiqueta();
            JsonNode v = normalizarValor(donde, c, valores.get(c.clave()));
            if (v != null && visible(c, limpios)) {
                limpios.set(c.clave(), v);
            }
        }
        return limpios;
    }

    /** El valor limpio, o {@code null} si está vacío. */
    private static JsonNode normalizarValor(String donde, Campo c, JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        return switch (c.tipo()) {
            case TEXTO -> texto(donde, v, MAX_TEXTO);
            case TEXTO_LARGO -> texto(donde, v, MAX_TEXTO_LARGO);
            case NUMERO -> numero(donde, v, c.min(), c.max(), c.unidad());
            case FECHA -> fecha(donde, v);
            case SELECCION -> seleccion(donde, v, c.opciones());
            case MULTIPLE -> multiple(donde, v, c.opciones());
            case SI_NO -> siNo(donde, v);
            case TABLA -> tabla(donde, c, v);
            case ESCALA -> escala(donde, c, v);
            case ODONTOGRAMA -> Odontograma.normalizar(donde, v);
        };
    }

    private static JsonNode texto(String donde, JsonNode v, int max) {
        if (!v.isString()) {
            throw ApiProblem.badRequest(donde + ": se esperaba texto");
        }
        String s = v.stringValue().strip();
        if (s.length() > max) {
            throw ApiProblem.badRequest(donde + ": máximo " + max + " caracteres");
        }
        return s.isEmpty() ? null : NODOS.stringNode(s);
    }

    private static JsonNode numero(String donde, JsonNode v, BigDecimal min, BigDecimal max, String unidad) {
        if (!v.isNumber()) {
            throw ApiProblem.badRequest(donde + ": se esperaba un número");
        }
        BigDecimal n = v.decimalValue();
        String u = unidad == null ? "" : " " + unidad;
        if (min != null && n.compareTo(min) < 0 || max != null && n.compareTo(max) > 0) {
            throw ApiProblem.badRequest(donde + ": debe estar entre " + texto(min) + " y " + texto(max) + u);
        }
        return v;
    }

    private static String texto(BigDecimal n) {
        return n == null ? "—" : n.stripTrailingZeros().toPlainString();
    }

    private static JsonNode fecha(String donde, JsonNode v) {
        try {
            if (v.isString()) {
                LocalDate.parse(v.stringValue());
                return v;
            }
        } catch (DateTimeParseException e) {
            // cae al error de abajo
        }
        throw ApiProblem.badRequest(donde + ": se esperaba una fecha AAAA-MM-DD");
    }

    private static JsonNode seleccion(String donde, JsonNode v, List<String> opciones) {
        if (!v.isString() || !opciones.contains(v.stringValue())) {
            throw ApiProblem.badRequest(donde + ": opción no válida");
        }
        return v;
    }

    private static JsonNode multiple(String donde, JsonNode v, List<String> opciones) {
        if (!v.isArray()) {
            throw ApiProblem.badRequest(donde + ": se esperaba una lista de opciones");
        }
        Set<String> elegidas = new LinkedHashSet<>();
        for (JsonNode x : v) {
            if (!x.isString() || !opciones.contains(x.stringValue())) {
                throw ApiProblem.badRequest(donde + ": opción no válida");
            }
            elegidas.add(x.stringValue());
        }
        if (elegidas.isEmpty()) {
            return null;
        }
        // En el orden de la definición, sin repetidas
        ArrayNode orden = NODOS.arrayNode();
        opciones.stream().filter(elegidas::contains).forEach(orden::add);
        return orden;
    }

    private static JsonNode siNo(String donde, JsonNode v) {
        if (!v.isBoolean()) {
            throw ApiProblem.badRequest(donde + ": se esperaba sí o no");
        }
        return v;
    }

    /** Lista de filas; con filas fijas, cada una dice cuál es en {@code "fila"}. Las filas vacías no se guardan. */
    private static JsonNode tabla(String donde, Campo c, JsonNode v) {
        if (!v.isArray()) {
            throw ApiProblem.badRequest(donde + ": se esperaba una lista de filas");
        }
        if (v.size() > MAX_FILAS) {
            throw ApiProblem.badRequest(donde + ": máximo " + MAX_FILAS + " filas");
        }
        Set<String> filasFijas = new HashSet<>();
        if (c.filas() != null) {
            c.filas().stream().map(Fila::clave).forEach(filasFijas::add);
        }
        Set<String> vistas = new HashSet<>();
        ArrayNode limpias = NODOS.arrayNode();
        for (JsonNode fila : v) {
            if (!fila.isObject()) {
                throw ApiProblem.badRequest(donde + ": cada fila debe ser un objeto");
            }
            ObjectNode limpia = NODOS.objectNode();
            for (String clave : fila.propertyNames()) {
                if (!clave.equals("fila") && c.columnas().stream().noneMatch(col -> col.clave().equals(clave))) {
                    throw ApiProblem.badRequest(donde + ": la columna «" + clave + "» no existe");
                }
            }
            for (Columna col : c.columnas()) {
                Campo comoCampo = new Campo(col.clave(), col.tipo(), col.etiqueta(), null, false, col.opciones(), null,
                        null, null, null, null, null, null, null, false);
                JsonNode valor = normalizarValor(donde + " › " + col.etiqueta(), comoCampo, fila.get(col.clave()));
                if (valor != null) {
                    limpia.set(col.clave(), valor);
                }
            }
            if (c.filas() != null) {
                JsonNode cual = fila.get("fila");
                if (cual == null || !cual.isString() || !filasFijas.contains(cual.stringValue()) || !vistas.add(cual.stringValue())) {
                    throw ApiProblem.badRequest(donde + ": fila no válida o repetida");
                }
                if (!limpia.isEmpty()) {
                    ObjectNode conFila = NODOS.objectNode();
                    conFila.put("fila", cual.stringValue());
                    conFila.setAll(limpia);
                    limpias.add(conFila);
                }
            } else if (!limpia.isEmpty()) {
                limpias.add(limpia);
            }
        }
        return limpias.isEmpty() ? null : limpias;
    }

    /** Objeto ítem → puntaje elegido. Se pueden guardar ítems sueltos; la escala completa solo se exige al firmar. */
    private static JsonNode escala(String donde, Campo c, JsonNode v) {
        if (!v.isObject()) {
            throw ApiProblem.badRequest(donde + ": se esperaba el puntaje de cada ítem");
        }
        ObjectNode limpia = NODOS.objectNode();
        for (Map.Entry<String, JsonNode> e : v.properties()) {
            ItemEscala item = c.items().stream().filter(i -> i.clave().equals(e.getKey())).findFirst()
                    .orElseThrow(() -> ApiProblem.badRequest(donde + ": el ítem «" + e.getKey() + "» no existe"));
            if (e.getValue().isNull()) {
                continue;
            }
            if (!e.getValue().isInt() || item.opciones().stream().noneMatch(o -> o.puntaje() == e.getValue().intValue())) {
                throw ApiProblem.badRequest(donde + " › " + item.etiqueta() + ": puntaje no válido");
            }
            limpia.set(item.clave(), e.getValue());
        }
        return limpia.isEmpty() ? null : limpia;
    }

    /** Total de la escala y su interpretación; {@code null} si falta algún ítem. */
    static Total total(Campo c, JsonNode v) {
        if (v == null || v.size() != c.items().size()) {
            return null;
        }
        int suma = 0;
        for (ItemEscala i : c.items()) {
            suma += v.get(i.clave()).intValue();
        }
        int t = suma;
        String interpretacion = c.rangos() == null ? null : c.rangos().stream()
                .filter(r -> t >= r.min() && t <= r.max()).map(DefinicionFormato.Rango::interpretacion).findFirst().orElse(null);
        return new Total(t, c.puntajeMaximo(), interpretacion);
    }

    /** Etiqueta de la opción elegida en un ítem de escala. */
    static String opcion(ItemEscala item, int puntaje) {
        return item.opciones().stream().filter(o -> o.puntaje() == puntaje).map(OpcionEscala::etiqueta).findFirst().orElse("");
    }

    record Total(int puntaje, int maximo, String interpretacion) {
    }
}
