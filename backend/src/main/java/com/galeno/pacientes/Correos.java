package com.galeno.pacientes;

/**
 * Formato del correo de un paciente. {@code @Email} de Jakarta acepta direcciones como "juan@gmail" (sin dominio de
 * nivel superior), que rebotan; aquí se exige usuario, dominio y una extensión de al menos 2 letras.
 */
public final class Correos {

    /** usuario@dominio.ext, con subdominios opcionales (correo@mail.empresa.com.co). */
    public static final String PATRON = "[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}";

    /** Igual que {@link #PATRON}, pero acepta vacío (el paciente sin correo). */
    public static final String PATRON_OPCIONAL = "(" + PATRON + ")?";

    public static final String MENSAJE = "Correo no válido: debe ser como nombre@dominio.com";

    private Correos() {
    }

    public static boolean valido(String correo) {
        return correo != null && correo.strip().matches(PATRON);
    }
}
