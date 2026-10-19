package com.galeno.formulacion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.galeno.formulacion.BorradorFormula.ItemPropuesto;

/**
 * Reglas deterministas que se aplican siempre sobre la propuesta de la IA: la IA propone,
 * estas reglas verifican y el médico firma. Aquí irán también el catálogo CUM/INVIMA y los rangos de dosis.
 */
final class ValidadorFormula {

    private ValidadorFormula() {
    }

    static List<String> revisar(BorradorFormula borrador, String alergias) {
        List<String> alertas = new ArrayList<>();
        List<String> alergenos = alergenos(alergias);
        for (ItemPropuesto item : borrador.items() == null ? List.<ItemPropuesto>of() : borrador.items()) {
            String medicamento = normalizar(item.medicamento());
            if (medicamento.isBlank()) {
                alertas.add("Hay un ítem sin medicamento identificado.");
                continue;
            }
            alergenos.stream()
                    .filter(a -> medicamento.contains(a) || a.contains(medicamento))
                    .findFirst()
                    .ifPresent(a -> alertas.add("ALERGIA: el paciente registra alergia a '" + a + "' y se propone "
                            + item.medicamento() + "."));
            if (vacio(item.dosis()) || vacio(item.frecuencia()) || vacio(item.duracion())) {
                alertas.add(item.medicamento() + ": falta dosis, frecuencia o duración.");
            }
        }
        return alertas;
    }

    private static List<String> alergenos(String alergias) {
        if (alergias == null || alergias.isBlank()) {
            return List.of();
        }
        return Arrays.stream(alergias.split("[,;\\n]"))
                .map(ValidadorFormula::normalizar)
                .filter(s -> s.length() >= 3)
                .toList();
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).strip();
    }
}
