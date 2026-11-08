package com.galeno.shared.correo;

/**
 * Envío de correos transaccionales (verificación de registro, invitaciones, recuperación de contraseña).
 * En local los guarda un buzón de desarrollo ({@link BuzonLocal}); en AWS los enviará SES.
 */
public interface EnvioCorreo {

    void enviar(Correo correo);

    /**
     * Quitar la dirección de la lista de supresión del proveedor (SES tiene la suya, a nivel de cuenta, además de la
     * de Galenum). @return si estaba en esa lista
     */
    default boolean quitarSupresion(String email) {
        return false;
    }
}
