package com.galeno.shared.correo;

import com.galeno.shared.SoporteProperties;

/** El pie de todos los correos: a dónde escribir, porque no-responder@ no tiene buzón (SES responde a soporte). */
final class PieCorreo {

    private PieCorreo() {
    }

    static String agregar(String texto, SoporteProperties soporte) {
        if (soporte == null || soporte.correo() == null || soporte.correo().isBlank()) {
            return texto;
        }
        return texto + "\n\n--\n¿Necesita ayuda? Responda este correo o escríbanos a " + soporte.contacto() + ".";
    }
}
