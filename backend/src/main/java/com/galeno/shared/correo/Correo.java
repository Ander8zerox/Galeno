package com.galeno.shared.correo;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Un correo saliente en texto plano (los clientes de correo lo muestran bien y no hay HTML que escapar).
 *
 * @param remitenteNombre para correos a pacientes en nombre de un consultorio: el nombre que ve el paciente
 *                        ("Consultorio Sonrisas"). Con él no se agrega el pie de soporte de Galenum (el paciente no es
 *                        cliente de Galenum). null: correo de Galenum a sus usuarios.
 * @param responderA      a dónde van las respuestas; null: al soporte de Galenum (o a nadie, con remitenteNombre)
 */
public record Correo(String para, String asunto, String texto, String remitenteNombre, String responderA) {

    public Correo(String para, String asunto, String texto) {
        this(para, asunto, texto, null, null);
    }

    /** Correo de un consultorio a un paciente. */
    public static Correo deConsultorio(String para, String asunto, String texto, String consultorio, String responderA) {
        return new Correo(para, asunto, texto, consultorio, responderA);
    }

    boolean aPaciente() {
        return remitenteNombre != null;
    }

    /**
     * El remitente con el nombre del consultorio y la dirección de Galenum: {@code "=?UTF-8?B?...?= <no-responder@…>"}.
     * El nombre va codificado (RFC 2047) porque puede tener tildes, comillas o comas.
     */
    String remitente(String remitenteGaleno) {
        if (remitenteNombre == null) {
            return remitenteGaleno;
        }
        int abre = remitenteGaleno.indexOf('<');
        String direccion = abre >= 0 ? remitenteGaleno.substring(abre + 1, remitenteGaleno.indexOf('>', abre)).strip()
                : remitenteGaleno.strip();
        String nombre = Base64.getEncoder().encodeToString(remitenteNombre.getBytes(StandardCharsets.UTF_8));
        return "=?UTF-8?B?" + nombre + "?= <" + direccion + ">";
    }
}
