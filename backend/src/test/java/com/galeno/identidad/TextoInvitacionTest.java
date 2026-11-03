package com.galeno.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TextoInvitacionTest {

    @Test
    void diceQuienInvitaAQueConsultorioYConQueRoles() {
        String texto = EquipoService.textoInvitacion("Ana Pérez", "Consultorio Sonrisas",
                List.of(Rol.ADMIN, Rol.MEDICO), "https://galenum.com.co/invitacion#abc", "10 de noviembre");
        assertThat(texto)
                .contains("Ana Pérez le invitó a unirse al equipo de Consultorio Sonrisas en Galenum como administrador y médico.")
                .contains("https://galenum.com.co/invitacion#abc")
                .contains("vence el 10 de noviembre");
    }
}
