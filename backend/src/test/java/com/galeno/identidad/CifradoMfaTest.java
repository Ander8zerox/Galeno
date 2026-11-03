package com.galeno.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class CifradoMfaTest {

    private final CifradoMfa cifrado = new CifradoMfa("clave-de-prueba-con-mas-de-32-caracteres");

    @Test
    void cifraYDescifra() {
        byte[] secreto = Totp.nuevoSecreto();
        String guardado = cifrado.cifrar(secreto);
        assertThat(cifrado.descifrar(guardado)).isEqualTo(secreto);
        // Cada cifrado lleva su propio IV
        assertThat(cifrado.cifrar(secreto)).isNotEqualTo(guardado);
    }

    @Test
    void unDatoAlteradoOConOtraClaveNoSeDescifra() {
        String guardado = cifrado.cifrar(Totp.nuevoSecreto());
        byte[] bytes = Base64.getDecoder().decode(guardado);
        bytes[bytes.length - 1] ^= 1;
        assertThatThrownBy(() -> cifrado.descifrar(Base64.getEncoder().encodeToString(bytes)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CifradoMfa("otra-clave-de-prueba-con-mas-de-32-caracteres").descifrar(guardado))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void elHmacDependeDeLaClave() {
        assertThat(cifrado.hmac("ABCDEFGH")).isEqualTo(cifrado.hmac("ABCDEFGH")).hasSize(64);
        assertThat(new CifradoMfa("otra-clave-de-prueba-con-mas-de-32-caracteres").hmac("ABCDEFGH"))
                .isNotEqualTo(cifrado.hmac("ABCDEFGH"));
    }

    @Test
    void exigeUnaClaveLarga() {
        assertThatThrownBy(() -> new CifradoMfa("corta")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CifradoMfa("")).isInstanceOf(IllegalStateException.class);
    }
}
