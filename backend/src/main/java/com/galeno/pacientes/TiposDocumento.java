package com.galeno.pacientes;

import java.util.Set;

/** Tipos de documento de identificación de la tabla de referencia de MinSalud (los que usa RIPS). */
public final class TiposDocumento {

    /** Para validaciones con {@code @Pattern}. */
    public static final String PATRON = "CC|TI|CE|PA|RC|PT|MS|AS|CN|NV|SC|PE|CD|DE|SI";

    public static final Set<String> VALIDOS = Set.of(PATRON.split("\\|"));

    private TiposDocumento() {
    }

    /** La cédula y la tarjeta de identidad son solo números: CC de 3 a 10 dígitos, TI de 10 u 11. */
    public static String errorNumero(String tipo, String numero) {
        String n = numero == null ? "" : numero.strip();
        return switch (tipo == null ? "" : tipo) {
            case "CC" -> n.matches("\\d{3,10}") ? null : "La cédula lleva solo números, de 3 a 10 dígitos";
            case "TI" -> n.matches("\\d{10,11}") ? null : "La tarjeta de identidad lleva solo números, 10 u 11 dígitos";
            default -> n.matches("[A-Za-z0-9-]{1,20}") ? null : "Solo letras, números y guiones, máximo 20";
        };
    }

    /**
     * La regla RVC007 del RIPS: RC hasta 7 años, TI de 7 a 17, CC desde 18, con un año de tolerancia (quien acaba de
     * cumplir 18 suele tener todavía la TI). Los demás documentos no se revisan.
     */
    public static boolean acordeConEdad(String tipo, int edad) {
        return switch (tipo == null ? "" : tipo) {
            case "RC" -> edad < 8;
            case "TI" -> edad >= 6 && edad <= 18;
            case "CC" -> edad >= 17;
            default -> true;
        };
    }
}
