package com.galeno.shared.correo;

/**
 * Envío de correos transaccionales (verificación de registro, invitaciones, recuperación de contraseña).
 * En local los guarda un buzón de desarrollo ({@link BuzonLocal}); en AWS los enviará SES.
 */
public interface EnvioCorreo {

    void enviar(Correo correo);
}
