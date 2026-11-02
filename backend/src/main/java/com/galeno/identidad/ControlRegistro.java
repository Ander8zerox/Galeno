package com.galeno.identidad;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filtros del registro de consultorios contra el abuso de la prueba gratis: correos desechables y alias del mismo
 * buzón. No impide que alguien vea Galeno (puede pagar un mes); evita que encadene pruebas sin costo.
 */
final class ControlRegistro {

    /** Dominios de correo temporal, uno por línea; las líneas con # son comentarios. */
    private static final Set<String> DESECHABLES = cargar("/registro/correos-desechables.txt");

    private ControlRegistro() {
    }

    static boolean desechable(String email) {
        String dominio = email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        return DESECHABLES.contains(dominio);
    }

    /**
     * El buzón real detrás del correo, para contar pruebas: sin lo que va después de "+" y, en Gmail, sin puntos
     * ({@code n.ombre+x@gmail.com} y {@code nombre@gmail.com} llegan al mismo buzón). Solo para contar: la cuenta se
     * crea con el correo tal como se escribió.
     */
    static String buzon(String email) {
        String e = email.strip().toLowerCase(Locale.ROOT);
        int arroba = e.lastIndexOf('@');
        String local = e.substring(0, arroba);
        String dominio = e.substring(arroba + 1);
        int mas = local.indexOf('+');
        if (mas > 0) {
            local = local.substring(0, mas);
        }
        if (dominio.equals("gmail.com") || dominio.equals("googlemail.com")) {
            local = local.replace(".", "");
            dominio = "gmail.com";
        }
        return local + "@" + dominio;
    }

    private static Set<String> cargar(String recurso) {
        try (InputStream in = ControlRegistro.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("Falta el recurso " + recurso);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(l -> l.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
