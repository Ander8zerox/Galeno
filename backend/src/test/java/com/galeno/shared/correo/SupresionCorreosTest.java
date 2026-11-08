package com.galeno.shared.correo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.galeno.shared.correo.CorreoSuprimido.Motivo;

class SupresionCorreosTest {

    private static String explicar(Motivo motivo, String detalle) {
        return SupresionCorreos.explicar(new CorreoSuprimido("x@ejemplo.test", motivo, detalle));
    }

    @Test
    void traduceElMotivoTecnicoDelRebote() {
        assertThat(explicar(Motivo.REBOTE, "Permanent General: smtp; 550 5.1.1 user unknown")).isEqualTo("La dirección no existe.");
        assertThat(explicar(Motivo.REBOTE, "Permanent NoEmail: ")).isEqualTo("La dirección no existe.");
        assertThat(explicar(Motivo.REBOTE, "Permanent OnAccountSuppressionList: ")).isEqualTo("La dirección está bloqueada por un rebote anterior.");
        assertThat(explicar(Motivo.REBOTE, "Permanent General: 552 5.2.2 mailbox full")).isEqualTo("El buzón del destinatario está lleno.");
        assertThat(explicar(Motivo.REBOTE, null)).isEqualTo("El servidor de correo del destinatario la rechazó.");
        assertThat(explicar(Motivo.QUEJA, "Queja: abuse")).isEqualTo("El destinatario marcó un correo anterior como no deseado.");
    }
}
