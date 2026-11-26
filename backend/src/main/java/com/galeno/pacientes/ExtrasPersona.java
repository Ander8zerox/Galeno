package com.galeno.pacientes;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Lo que la plantilla de importación de Galenum trae del paciente además de {@link DatosPersona}: alergias,
 * antecedentes y responsable. Un campo en {@code null} no cambia nada; solo se llenan los vacíos.
 */
public record ExtrasPersona(String alergias, String antecedentes, String responsableNombre, String responsableParentesco,
                            String responsableTelefono) {

    private static final Set<String> NIEGA = Set.of("niega", "no", "ninguna", "ninguno", "no refiere", "niega alergias",
            "no conocidas", "no tiene", "nada");

    public boolean vacio() {
        return alergias == null && antecedentes == null && responsableNombre == null;
    }

    /** «Niega», «Ninguna», «No refiere»…: el paciente respondió que no tiene alergias. */
    static boolean niegaAlergias(String texto) {
        String n = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", "").strip().replaceAll("\\s+", " ");
        return NIEGA.contains(n);
    }
}
