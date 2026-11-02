package com.galeno.identidad;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Códigos de 6 dígitos que cambian cada 30 segundos (TOTP, RFC 6238, con HMAC-SHA1): los que generan Google
 * Authenticator, Microsoft Authenticator, 1Password y similares. El secreto viaja a la app como Base32 dentro de un
 * código QR ({@link #uri}).
 */
final class Totp {

    static final int PERIODO_SEGUNDOS = 30;
    /** Un intervalo antes y uno después: tolera relojes de celular desfasados hasta 30 s. */
    private static final int TOLERANCIA = 1;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** 160 bits, el tamaño que recomienda el RFC 4226 para HMAC-SHA1. */
    static byte[] nuevoSecreto() {
        byte[] secreto = new byte[20];
        RANDOM.nextBytes(secreto);
        return secreto;
    }

    static long paso(Instant instante) {
        return Math.floorDiv(instante.getEpochSecond(), PERIODO_SEGUNDOS);
    }

    static String codigo(byte[] secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secreto, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return "%06d".formatted(binario % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @return el paso del código si es válido y posterior a {@code ultimoPaso} (un código ya usado no vuelve a
     * servir), o -1
     */
    static long verificar(byte[] secreto, String codigo, Instant ahora, long ultimoPaso) {
        if (codigo == null || !codigo.matches("\\d{6}")) {
            return -1;
        }
        long actual = paso(ahora);
        for (long p = actual - TOLERANCIA; p <= actual + TOLERANCIA; p++) {
            if (p > ultimoPaso && igual(codigo(secreto, p), codigo)) {
                return p;
            }
        }
        return -1;
    }

    /** Lo que lleva el QR: la app lo muestra como "Galenum (correo)". */
    static String uri(String emisor, String cuenta, byte[] secreto) {
        String etiqueta = URLEncoder.encode(emisor + ":" + cuenta, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=%d".formatted(
                etiqueta, base32(secreto), URLEncoder.encode(emisor, StandardCharsets.UTF_8), PERIODO_SEGUNDOS);
    }

    /** RFC 4648 sin relleno, como lo esperan las apps. */
    static String base32(byte[] datos) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return sb.toString();
    }

    /** Comparación en tiempo constante. */
    private static boolean igual(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
