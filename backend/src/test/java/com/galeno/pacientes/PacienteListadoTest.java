package com.galeno.pacientes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.galeno.pacientes.DatosPersona.Cambio;

class PacienteListadoTest {

    private static final LocalDate NACIMIENTO = LocalDate.of(1985, 3, 15);

    private static Paciente importadoDelRips() {
        return Paciente.importado("CC", "123", NACIMIENTO, "F", UUID.randomUUID());
    }

    @Test
    void completaElNombreDeUnPacienteImportadoSinNombre() {
        Paciente p = importadoDelRips();

        List<Cambio> cambios = p.cambiosDesde(new DatosPersona("Ana", "Ruiz", null, null, "3001234567", null));
        p.aplicar(cambios);

        assertThat(cambios).containsExactly(new Cambio("nombres", "Paciente", "Ana"), new Cambio("apellidos", "CC 123", "Ruiz"),
                new Cambio("datosIncompletos", "true", "false"), new Cambio("telefono", null, "3001234567"));
        assertThat(p.getNombres() + " " + p.getApellidos()).isEqualTo("Ana Ruiz");
        assertThat(p.isDatosIncompletos()).isFalse();
    }

    @Test
    void aUnPacienteCompletoSoloLeLlenaLoVacioYAvisaLasDiferencias() {
        Paciente p = importadoDelRips();
        p.actualizarDemograficos("Ana María", "Ruiz", NACIMIENTO, "F", "3000000000", null);
        DatosPersona listado = new DatosPersona("Ana", "Ruiz Gómez", LocalDate.of(1985, 3, 16), "F", "3111111111", "ana@x.co");

        assertThat(p.cambiosDesde(listado)).containsExactly(new Cambio("email", null, "ana@x.co"));
        assertThat(p.diferenciasCon(listado)).hasSize(3)
                .anySatisfy(d -> assertThat(d).contains("«Ana Ruiz Gómez»").contains("«Ana María Ruiz»"))
                .anySatisfy(d -> assertThat(d).contains("1985-03-16"))
                .anySatisfy(d -> assertThat(d).contains("3111111111"));
    }

    @Test
    void deshacerRestauraSalvoLoQueAlguienEditoDespues() {
        Paciente p = importadoDelRips();
        List<Cambio> cambios = p.cambiosDesde(new DatosPersona("Ana", "Ruiz", null, null, "3001234567", null));
        p.aplicar(cambios);
        // La secretaria corrige el teléfono después de importar
        p.actualizarDemograficos("Ana", "Ruiz", NACIMIENTO, "F", "3009999999", null);

        p.revertir(cambios);

        assertThat(p.getNombres()).isEqualTo("Paciente");
        assertThat(p.getApellidos()).isEqualTo("CC 123");
        assertThat(p.isDatosIncompletos()).isTrue();
        assertThat(p.getTelefono()).isEqualTo("3009999999");
    }
}
