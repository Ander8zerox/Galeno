package com.galeno.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ControlRegistroTest {

    @Test
    void losAliasDeGmailSonElMismoBuzon() {
        assertThat(ControlRegistro.buzon("A.Nombre+consultorio@Gmail.com")).isEqualTo("anombre@gmail.com");
        assertThat(ControlRegistro.buzon("anombre@googlemail.com")).isEqualTo("anombre@gmail.com");
    }

    @Test
    void enOtrosDominiosSoloSeQuitaElMas() {
        assertThat(ControlRegistro.buzon("dra.perez+citas@clinica.co")).isEqualTo("dra.perez@clinica.co");
        assertThat(ControlRegistro.buzon("dra.perez@clinica.co")).isEqualTo("dra.perez@clinica.co");
    }

    @Test
    void rechazaCorreosTemporales() {
        assertThat(ControlRegistro.desechable("alguien@mailinator.com")).isTrue();
        assertThat(ControlRegistro.desechable("alguien@YOPMAIL.com")).isTrue();
        assertThat(ControlRegistro.desechable("alguien@gmail.com")).isFalse();
        assertThat(ControlRegistro.desechable("alguien@clinica.co")).isFalse();
    }
}
