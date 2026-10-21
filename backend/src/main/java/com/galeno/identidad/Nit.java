package com.galeno.identidad;

/**
 * NIT colombiano con su dígito de verificación (algoritmo de la DIAN, módulo 11). Se acepta con o sin
 * puntos y con o sin el dígito; si viene, debe ser el correcto. Se guarda como "900123456-7".
 */
final class Nit {

    private static final int[] PESOS = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};

    private Nit() {
    }

    /** @throws IllegalArgumentException con un mensaje para la persona si no es un NIT válido */
    static String normalizar(String nit) {
        String limpio = nit.replaceAll("[\\s.]", "");
        String numero = limpio;
        Integer dv = null;
        int guion = limpio.indexOf('-');
        if (guion >= 0) {
            numero = limpio.substring(0, guion);
            String d = limpio.substring(guion + 1);
            if (!d.matches("\\d")) {
                throw new IllegalArgumentException("El dígito de verificación del NIT debe ser un solo número");
            }
            dv = Integer.parseInt(d);
        }
        if (!numero.matches("\\d{6,15}")) {
            throw new IllegalArgumentException("El NIT debe tener entre 6 y 15 dígitos");
        }
        int esperado = digitoVerificacion(numero);
        if (dv != null && dv != esperado) {
            throw new IllegalArgumentException("El dígito de verificación del NIT no corresponde (debería ser " + esperado + ")");
        }
        return numero + "-" + esperado;
    }

    static int digitoVerificacion(String numero) {
        int suma = 0;
        for (int i = 0; i < numero.length(); i++) {
            int digito = numero.charAt(numero.length() - 1 - i) - '0';
            suma += digito * PESOS[i];
        }
        int residuo = suma % 11;
        return residuo > 1 ? 11 - residuo : residuo;
    }
}
