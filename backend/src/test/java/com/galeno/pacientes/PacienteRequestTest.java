package com.galeno.pacientes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class PacienteRequestTest {

    private static final ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void cerrar() {
        FABRICA.close();
    }

    private static PacienteRequest request(String telefono, String email, Boolean sinCorreo) {
        return new PacienteRequest("CC", "1000000001", "Ana", "Prueba", LocalDate.of(1990, 1, 1), "F", telefono, email,
                null, null, null, sinCorreo);
    }

    private static Set<String> camposConError(PacienteRequest r) {
        return FABRICA.getValidator().validate(r).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void conTelefonoYCorreoEsValido() {
        assertThat(camposConError(request("300 123 4567", "ana@ejemplo.test", null))).isEmpty();
        assertThat(camposConError(request("+57 (604) 444-5566", "ana@ejemplo.test", false))).isEmpty();
    }

    @Test
    void elTelefonoEsObligatorioYConNumeros() {
        assertThat(camposConError(request(null, "ana@ejemplo.test", null))).contains("telefono");
        assertThat(camposConError(request("  ", "ana@ejemplo.test", null))).contains("telefono");
        assertThat(camposConError(request("no tiene", "ana@ejemplo.test", null))).contains("telefono");
        assertThat(camposConError(request("12345", "ana@ejemplo.test", null))).contains("telefono");
    }

    @Test
    void sinCorreoHayQueMarcarQueNoTiene() {
        assertThat(camposConError(request("3001234567", null, null))).containsExactly("correoResuelto");
        assertThat(camposConError(request("3001234567", " ", false))).contains("correoResuelto");
        assertThat(camposConError(request("3001234567", null, true))).isEmpty();
    }

    @Test
    void conLaMarcaNoSeGuardaCorreo() {
        assertThat(request("3001234567", "ana@ejemplo.test", true).emailAGuardar()).isNull();
        assertThat(request("3001234567", " ana@ejemplo.test ", false).emailAGuardar()).isEqualTo("ana@ejemplo.test");
    }
}
