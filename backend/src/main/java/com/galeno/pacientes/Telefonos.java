package com.galeno.pacientes;

/** Teléfonos de Colombia, que desde 2021 tienen 10 dígitos: celulares que empiezan por 3 y fijos por 60. */
public final class Telefonos {

    /** Para {@code @Pattern}: 10 dígitos (celular 3…, fijo 60…), con +57, espacios, guiones o paréntesis opcionales. */
    public static final String PATRON = "(\\+?57)?[ ().-]*(3([ ().-]*\\d){9}|6[ ().-]*0([ ().-]*\\d){8})[ ().-]*";

    public static final String MENSAJE = "Escriba un celular de 10 dígitos que empiece por 3, o un fijo de 10 que empiece por 60";

    private Telefonos() {
    }

    /** Los 10 dígitos sin espacios, guiones, paréntesis ni el +57; null si no es un teléfono colombiano válido. */
    public static String normalizar(String telefono) {
        if (telefono == null) {
            return null;
        }
        String d = telefono.replaceAll("[\\s()\\-.]", "");
        if (d.startsWith("+57")) {
            d = d.substring(3);
        } else if (d.startsWith("57") && d.length() == 12) {
            d = d.substring(2);
        }
        return d.matches("3\\d{9}|60\\d{8}") ? d : null;
    }
}
