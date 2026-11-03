package com.galeno.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cómo contacta un cliente a Galenum: va en los mensajes que dicen "escríbanos", en el pie y la respuesta de los correos.
 * El frontend tiene los mismos valores en {@code frontend/src/soporte.ts}.
 *
 * @param correo   p. ej. {@code soporte@galenum.com.co} (se reenvía al buzón del equipo, ver docs/guia-operacion.md)
 * @param whatsapp número en formato internacional sin signos, p. ej. {@code 573001234567}; vacío, no se muestra
 */
@ConfigurationProperties("app.soporte")
public record SoporteProperties(String correo, String whatsapp) {

    public boolean tieneWhatsapp() {
        return whatsapp != null && !whatsapp.isBlank();
    }

    /** Para los mensajes de error y los correos: "soporte@… o por WhatsApp al +57 300 123 4567". */
    public String contacto() {
        return tieneWhatsapp() ? correo + " o por WhatsApp al " + whatsappLegible() : correo;
    }

    /** {@code 573001234567} → {@code +57 300 123 4567} (los demás países, con el + y sin espacios). */
    String whatsappLegible() {
        String n = whatsapp.strip();
        if (n.startsWith("57") && n.length() == 12) {
            return "+57 " + n.substring(2, 5) + " " + n.substring(5, 8) + " " + n.substring(8);
        }
        return "+" + n;
    }
}
