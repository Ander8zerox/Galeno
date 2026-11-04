package com.galeno.formatos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Campo {@code odontograma}: numeración FDI, cinco superficies por diente y un catálogo fijo de hallazgos.
 *
 * <p>Respuesta: {@code {"denticion": "permanente", "dientes": {"36": {"hallazgos": ["endodoncia"],
 * "superficies": {"O": "caries", "M": "resina"}, "nota": "..."}}}}. Un diente sin nada no se guarda.
 */
final class Odontograma {

    /** Permanentes 11–48 y temporales 51–85, por cuadrante. */
    static final List<Integer> PERMANENTES = dientes(1, 8);
    static final List<Integer> TEMPORALES = dientes(5, 5);

    static final Map<String, List<Integer>> DENTICIONES = Map.of(
            "permanente", PERMANENTES,
            "temporal", TEMPORALES,
            "mixta", Stream.concat(PERMANENTES.stream(), TEMPORALES.stream()).toList());

    /** V vestibular, L lingual o palatina, M mesial, D distal, O oclusal o incisal. */
    static final Map<String, String> SUPERFICIES = orden(
            "V", "vestibular", "L", "lingual o palatina", "M", "mesial", "D", "distal", "O", "oclusal o incisal");

    static final Map<String, String> HALLAZGOS_SUPERFICIE = orden(
            "caries", "caries", "resina", "obturación en resina", "amalgama", "obturación en amalgama",
            "sellante", "sellante");

    static final Map<String, String> HALLAZGOS_DIENTE = orden(
            "ausente", "ausente", "extraccion_indicada", "extracción indicada", "corona", "corona",
            "endodoncia", "endodoncia", "implante", "implante", "protesis", "prótesis", "fractura", "fractura");

    static final int MAX_NOTA = 300;

    private static final JsonNodeFactory NODOS = JsonNodeFactory.instance;

    private Odontograma() {
    }

    private static List<Integer> dientes(int primerCuadrante, int porCuadrante) {
        return IntStream.range(primerCuadrante, primerCuadrante + 4)
                .flatMap(c -> IntStream.rangeClosed(1, porCuadrante).map(n -> c * 10 + n)).boxed().toList();
    }

    private static Map<String, String> orden(String... claveValor) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            m.put(claveValor[i], claveValor[i + 1]);
        }
        return m;
    }

    /** El valor limpio, o {@code null} si no tiene dentición. Responde 400 si algo no es del catálogo. */
    static JsonNode normalizar(String donde, JsonNode v) {
        if (!v.isObject()) {
            throw ApiProblem.badRequest(donde + ": se esperaba un odontograma");
        }
        for (String clave : v.propertyNames()) {
            if (!clave.equals("denticion") && !clave.equals("dientes")) {
                throw ApiProblem.badRequest(donde + ": propiedad desconocida «" + clave + "»");
            }
        }
        JsonNode denticion = v.get("denticion");
        if (denticion == null || denticion.isNull()) {
            return null;
        }
        List<Integer> permitidos = denticion.isString() ? DENTICIONES.get(denticion.stringValue()) : null;
        if (permitidos == null) {
            throw ApiProblem.badRequest(donde + ": dentición no válida");
        }
        ObjectNode limpio = NODOS.objectNode();
        limpio.put("denticion", denticion.stringValue());
        ObjectNode dientes = NODOS.objectNode();
        JsonNode recibidos = v.get("dientes");
        if (recibidos != null && !recibidos.isNull()) {
            if (!recibidos.isObject()) {
                throw ApiProblem.badRequest(donde + ": se esperaban los dientes por número");
            }
            // En orden FDI, para que la respuesta no dependa del orden en que se marcaron
            Map<Integer, ObjectNode> ordenados = new java.util.TreeMap<>();
            for (Map.Entry<String, JsonNode> d : recibidos.properties()) {
                Integer numero = d.getKey().matches("\\d{2}") ? Integer.valueOf(d.getKey()) : null;
                if (numero == null || !permitidos.contains(numero)) {
                    throw ApiProblem.badRequest(donde + ": el diente " + d.getKey() + " no está en la dentición "
                            + denticion.stringValue());
                }
                ObjectNode diente = diente(donde + " › diente " + numero, d.getValue());
                if (diente != null) {
                    ordenados.put(numero, diente);
                }
            }
            ordenados.forEach((n, d) -> dientes.set(String.valueOf(n), d));
        }
        limpio.set("dientes", dientes);
        return limpio;
    }

    private static ObjectNode diente(String donde, JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isObject()) {
            throw ApiProblem.badRequest(donde + ": se esperaba un objeto");
        }
        for (String clave : v.propertyNames()) {
            if (!Set.of("hallazgos", "superficies", "nota").contains(clave)) {
                throw ApiProblem.badRequest(donde + ": propiedad desconocida «" + clave + "»");
            }
        }
        ObjectNode limpio = NODOS.objectNode();
        Set<String> hallazgos = new TreeSet<>(java.util.Comparator.comparing(h -> List.copyOf(HALLAZGOS_DIENTE.keySet()).indexOf(h)));
        JsonNode h = v.get("hallazgos");
        if (h != null && !h.isNull()) {
            if (!h.isArray()) {
                throw ApiProblem.badRequest(donde + ": se esperaba una lista de hallazgos");
            }
            for (JsonNode x : h) {
                if (!x.isString() || !HALLAZGOS_DIENTE.containsKey(x.stringValue())) {
                    throw ApiProblem.badRequest(donde + ": hallazgo no válido");
                }
                hallazgos.add(x.stringValue());
            }
        }
        ObjectNode superficies = NODOS.objectNode();
        JsonNode s = v.get("superficies");
        if (s != null && !s.isNull()) {
            if (!s.isObject()) {
                throw ApiProblem.badRequest(donde + ": se esperaban las superficies");
            }
            for (String sup : SUPERFICIES.keySet()) {
                JsonNode valor = s.get(sup);
                if (valor != null && !valor.isNull()) {
                    if (!valor.isString() || !HALLAZGOS_SUPERFICIE.containsKey(valor.stringValue())) {
                        throw ApiProblem.badRequest(donde + " › " + SUPERFICIES.get(sup) + ": hallazgo no válido");
                    }
                    superficies.put(sup, valor.stringValue());
                }
            }
            for (String sup : s.propertyNames()) {
                if (!SUPERFICIES.containsKey(sup)) {
                    throw ApiProblem.badRequest(donde + ": superficie no válida «" + sup + "»");
                }
            }
        }
        if (hallazgos.contains("ausente") && (hallazgos.size() > 1 || !superficies.isEmpty())) {
            throw ApiProblem.badRequest(donde + ": un diente ausente no tiene otros hallazgos");
        }
        if (!hallazgos.isEmpty()) {
            ArrayNode lista = NODOS.arrayNode();
            hallazgos.forEach(lista::add);
            limpio.set("hallazgos", lista);
        }
        if (!superficies.isEmpty()) {
            limpio.set("superficies", superficies);
        }
        JsonNode nota = v.get("nota");
        if (nota != null && !nota.isNull()) {
            if (!nota.isString() || nota.stringValue().strip().length() > MAX_NOTA) {
                throw ApiProblem.badRequest(donde + ": la nota debe ser texto de máximo " + MAX_NOTA + " caracteres");
            }
            if (!nota.stringValue().isBlank()) {
                limpio.put("nota", nota.stringValue().strip());
            }
        }
        return limpio.isEmpty() ? null : limpio;
    }

    /** Un renglón por diente con hallazgos: «36: endodoncia; caries oclusal o incisal, resina mesial». */
    static List<String> hallazgos(JsonNode v) {
        List<String> lineas = new ArrayList<>();
        JsonNode dientes = v.get("dientes");
        if (dientes == null) {
            return lineas;
        }
        for (Map.Entry<String, JsonNode> d : dientes.properties()) {
            List<String> partes = new ArrayList<>();
            JsonNode h = d.getValue().get("hallazgos");
            if (h != null) {
                h.forEach(x -> partes.add(HALLAZGOS_DIENTE.get(x.stringValue())));
            }
            JsonNode s = d.getValue().get("superficies");
            if (s != null) {
                // Agrupa por hallazgo: «caries oclusal y mesial»
                Map<String, List<String>> porHallazgo = new LinkedHashMap<>();
                s.properties().forEach(e -> porHallazgo.computeIfAbsent(e.getValue().stringValue(), k -> new ArrayList<>())
                        .add(SUPERFICIES.get(e.getKey())));
                porHallazgo.forEach((hallazgo, sups) -> partes.add(HALLAZGOS_SUPERFICIE.get(hallazgo) + " "
                        + String.join(" y ", sups)));
            }
            JsonNode nota = d.getValue().get("nota");
            String texto = String.join("; ", partes);
            if (nota != null) {
                texto = texto.isEmpty() ? nota.stringValue() : texto + " (" + nota.stringValue() + ")";
            }
            lineas.add(d.getKey() + ": " + texto);
        }
        return lineas;
    }

    static String nombreDenticion(String d) {
        return switch (d) {
            case "temporal" -> "Temporal";
            case "mixta" -> "Mixta";
            default -> "Permanente";
        };
    }

    /** Los dientes que se dibujan, por filas: superior derecha→izquierda del paciente, luego inferior. */
    static List<List<Integer>> filas(String denticion) {
        List<List<Integer>> filas = new ArrayList<>();
        if (!denticion.equals("temporal")) {
            filas.add(fila(1, 2, 8));
            filas.add(fila(4, 3, 8));
        }
        if (!denticion.equals("permanente")) {
            filas.add(fila(5, 6, 5));
            filas.add(fila(8, 7, 5));
        }
        // Mixta: temporales en medio, como se ven en boca
        if (denticion.equals("mixta")) {
            return List.of(filas.get(0), filas.get(2), filas.get(3), filas.get(1));
        }
        return filas;
    }

    /** Cuadrante derecho del paciente (a la izquierda de quien mira) de afuera hacia la línea media, y el izquierdo al revés. */
    private static List<Integer> fila(int derecho, int izquierdo, int porCuadrante) {
        List<Integer> f = new ArrayList<>();
        for (int n = porCuadrante; n >= 1; n--) {
            f.add(derecho * 10 + n);
        }
        for (int n = 1; n <= porCuadrante; n++) {
            f.add(izquierdo * 10 + n);
        }
        return f;
    }
}
