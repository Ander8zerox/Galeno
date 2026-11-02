package com.galeno.identidad;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra los secretos TOTP (AES-256-GCM) y firma los códigos de recuperación (HMAC-SHA256) con la clave de
 * {@code app.mfa.clave} (APP_MFA_CLAVE, en SSM). Así, una copia de la base de datos sola no sirve para generar
 * códigos ni para probar códigos de recuperación por fuerza bruta.
 *
 * <p>Ojo: cambiar la clave deja inservibles los secretos guardados (todos tendrían que configurar la app de nuevo).
 */
@Component
class CifradoMfa {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec claveCifrado;
    private final SecretKeySpec claveHmac;

    CifradoMfa(@Value("${app.mfa.clave}") String clave) {
        if (clave == null || clave.length() < 32) {
            throw new IllegalStateException("app.mfa.clave debe tener al menos 32 caracteres");
        }
        // Dos claves derivadas de la misma: una para cifrar y otra para el HMAC
        this.claveCifrado = new SecretKeySpec(sha256("cifrado:" + clave), "AES");
        this.claveHmac = new SecretKeySpec(sha256("hmac:" + clave), "HmacSHA256");
    }

    String cifrar(byte[] datos) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, claveCifrado, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(datos);
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    byte[] descifrar(String texto) {
        try {
            byte[] todo = Base64.getDecoder().decode(texto);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, claveCifrado, new GCMParameterSpec(TAG_BITS, todo, 0, IV_BYTES));
            return cipher.doFinal(todo, IV_BYTES, todo.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el secreto de verificación (¿cambió APP_MFA_CLAVE?)", e);
        }
    }

    String hmac(String valor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(claveHmac);
            return HexFormat.of().formatHex(mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(String valor) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
