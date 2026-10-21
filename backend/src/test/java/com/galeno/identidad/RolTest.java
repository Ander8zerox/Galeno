package com.galeno.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.galeno.shared.security.Permiso;

class RolTest {

    @Test
    void elSecretarioNoVeDatosClinicosNiUsaLaIa() {
        assertThat(Rol.permisosDe(List.of(Rol.SECRETARIO)))
                .containsExactlyInAnyOrder(Permiso.PACIENTE_GESTIONAR, Permiso.AGENDA_GESTIONAR)
                .doesNotContain(Permiso.HISTORIA_VER, Permiso.FORMULA_FIRMAR, Permiso.IA_DIAGNOSTICO);
    }

    @Test
    void elAdministradorSinRolMedicoNoFirmaNiVeLaHistoria() {
        assertThat(Rol.permisosDe(List.of(Rol.ADMIN)))
                .contains(Permiso.EQUIPO_GESTIONAR)
                .doesNotContain(Permiso.HISTORIA_VER, Permiso.FORMULA_FIRMAR);
    }

    @Test
    void losRolesSeSuman() {
        assertThat(Rol.permisosDe(List.of(Rol.ADMIN, Rol.MEDICO)))
                .contains(Permiso.EQUIPO_GESTIONAR, Permiso.FORMULA_FIRMAR, Permiso.HISTORIA_VER);
    }
}
