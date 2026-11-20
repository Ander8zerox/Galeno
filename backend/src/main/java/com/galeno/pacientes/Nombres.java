package com.galeno.pacientes;

/**
 * Nombres y apellidos de personas: letras (con tildes, ñ y diéresis), espacios, apóstrofo, punto y guion. Sin números
 * ni otros símbolos ("María José", "O'Neill", "Gómez-Pérez", "Ma. Fernanda"). Se toleran espacios al inicio y al final.
 */
public final class Nombres {

    public static final String PATRON = "\\s*\\p{L}[\\p{L} .'-]*";

    /** Igual que {@link #PATRON}, pero acepta vacío o solo espacios (campos opcionales que se borran así). */
    public static final String PATRON_OPCIONAL = "\\s*|" + PATRON;

    public static final String MENSAJE = "Solo letras, espacios, apóstrofo, punto o guion; sin números";

    private Nombres() {
    }
}
