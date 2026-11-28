package com.galeno.tratamiento;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import tools.jackson.databind.JsonNode;

/**
 * Procedimientos que sugiere el último odontograma. Es una propuesta (docs/diseno-plan-tratamiento.md, a validar con un
 * odontólogo): el odontólogo acepta, cambia o quita cada una antes de que entre al plan.
 *
 * <ul>
 *   <li>Caries en una o varias superficies: obturación en resina del diente, con esas superficies.</li>
 *   <li>Fractura: obturación en resina (el odontólogo la cambia por corona si hace falta).</li>
 *   <li>Extracción indicada: exodoncia del diente permanente o temporal, unirradicular o multirradicular.</li>
 * </ul>
 */
final class SugerenciasOdontograma {

    static final String RESINA = "232102";
    static final String EXODONCIA_PERMANENTE_UNI = "230101";
    static final String EXODONCIA_PERMANENTE_MULTI = "230102";
    static final String EXODONCIA_TEMPORAL_UNI = "230201";
    static final String EXODONCIA_TEMPORAL_MULTI = "230202";

    private SugerenciasOdontograma() {
    }

    /** @param motivo por qué se sugiere, p. ej. "Caries en M, O" */
    record Sugerencia(int fase, String codigoCups, String diente, String superficies, String motivo) {
    }

    /** @param respuestas las del formato odonto.odontograma ({@code {"odontograma": {"dientes": {...}}}}) */
    static List<Sugerencia> de(JsonNode respuestas) {
        JsonNode dientes = respuestas == null ? null : respuestas.path("odontograma").get("dientes");
        JsonNode mapa = dientes == null ? null : dientes.get("dientes");
        if (mapa == null || !mapa.isObject()) {
            return List.of();
        }
        // Por número de diente, para que las sugerencias salgan en orden
        Map<Integer, JsonNode> porDiente = new TreeMap<>();
        for (Map.Entry<String, JsonNode> e : mapa.properties()) {
            try {
                porDiente.put(Integer.parseInt(e.getKey()), e.getValue());
            } catch (NumberFormatException ignorar) {
                // un diente que no es número no se sugiere
            }
        }
        List<Sugerencia> operatoria = new ArrayList<>();
        List<Sugerencia> cirugia = new ArrayList<>();
        for (Map.Entry<Integer, JsonNode> e : porDiente.entrySet()) {
            String diente = String.valueOf(e.getKey());
            JsonNode d = e.getValue();
            List<String> hallazgos = new ArrayList<>();
            d.path("hallazgos").forEach(h -> hallazgos.add(h.stringValue()));
            if (hallazgos.contains("ausente") || hallazgos.contains("implante")) {
                continue;
            }
            if (hallazgos.contains("extraccion_indicada")) {
                cirugia.add(new Sugerencia(2, exodoncia(e.getKey()), diente, null, "Extracción indicada"));
                continue;
            }
            StringBuilder caries = new StringBuilder();
            for (String s : List.of("V", "L", "M", "D", "O")) {
                JsonNode h = d.path("superficies").get(s);
                if (h != null && "caries".equals(h.stringValue())) {
                    caries.append(s);
                }
            }
            if (!caries.isEmpty()) {
                operatoria.add(new Sugerencia(1, RESINA, diente, caries.toString(),
                        "Caries en " + String.join(", ", caries.toString().split(""))));
            } else if (hallazgos.contains("fractura")) {
                operatoria.add(new Sugerencia(1, RESINA, diente, null, "Fractura"));
            }
        }
        List<Sugerencia> todas = new ArrayList<>(operatoria);
        todas.addAll(cirugia);
        return todas;
    }

    /**
     * Permanentes: molares y primeros premolares superiores, multirradiculares. Temporales (51–85): los molares
     * (4 y 5 de cada cuadrante), multirradiculares.
     */
    static String exodoncia(int diente) {
        int cuadrante = diente / 10;
        int numero = diente % 10;
        if (cuadrante >= 5) {
            return numero >= 4 ? EXODONCIA_TEMPORAL_MULTI : EXODONCIA_TEMPORAL_UNI;
        }
        boolean multi = numero >= 6 || (numero == 4 && (cuadrante == 1 || cuadrante == 2));
        return multi ? EXODONCIA_PERMANENTE_MULTI : EXODONCIA_PERMANENTE_UNI;
    }
}
