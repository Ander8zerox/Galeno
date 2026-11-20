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
                null, null, null, sinCorreo, null, null, null, null, null);
    }

    private static PacienteRequest menor(String responsableNombre, String responsableTelefono) {
        return new PacienteRequest("TI", "1000000001", "Sofía", "Prueba", LocalDate.now().minusYears(15), "F",
                "3001234567", null, null, null, null, true, responsableNombre, "Madre", "CC", "52000111", responsableTelefono);
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
        assertThat(camposConError(request("12", "ana@ejemplo.test", null))).contains("telefono");
        // Un fijo de 7 dígitos ya no existe (desde 2021 son de 10 y empiezan por 60), ni un número de 10 que no sea 3 o 60
        assertThat(camposConError(request("4445566", "ana@ejemplo.test", null))).contains("telefono");
        assertThat(camposConError(request("5001234567", "ana@ejemplo.test", null))).contains("telefono");
        assertThat(camposConError(request("30012345678", "ana@ejemplo.test", null))).contains("telefono");
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

    @Test
    void unMenorNecesitaResponsable() {
        assertThat(camposConError(menor(null, null))).contains("responsableCompleto");
        assertThat(camposConError(menor("Laura Prueba", "12"))).contains("responsableTelefono");
        assertThat(camposConError(menor("Laura Prueba", "310 555 0000"))).isEmpty();
        // Un adulto no lo necesita
        assertThat(camposConError(request("3001234567", "ana@ejemplo.test", null))).doesNotContain("responsableCompleto");
    }

    @Test
    void normalizaElTelefono() {
        assertThat(Telefonos.normalizar("+57 (604) 444-5566")).isEqualTo("6044445566");
        assertThat(Telefonos.normalizar("300 123 4567")).isEqualTo("3001234567");
        assertThat(Telefonos.normalizar("573001234567")).isEqualTo("3001234567");
        assertThat(Telefonos.normalizar("12")).isNull();
    }

    @Test
    void elNumeroDeLaCedulaYLaTiSonSoloNumeros() {
        assertThat(TiposDocumento.errorNumero("CC", "ABC12")).isNotNull();
        assertThat(TiposDocumento.errorNumero("CC", "12")).isNotNull();
        assertThat(TiposDocumento.errorNumero("CC", "12345678901")).isNotNull();
        assertThat(TiposDocumento.errorNumero("CC", "1020304050")).isNull();
        assertThat(TiposDocumento.errorNumero("TI", "1020304050")).isNull();
        assertThat(TiposDocumento.errorNumero("TI", "10203")).isNotNull();
        assertThat(TiposDocumento.errorNumero("PA", "AB123456")).isNull();
    }

    @Test
    void elTipoDeDocumentoCuadraConLaEdad() {
        assertThat(TiposDocumento.acordeConEdad("CC", 5)).isFalse();
        assertThat(TiposDocumento.acordeConEdad("TI", 40)).isFalse();
        assertThat(TiposDocumento.acordeConEdad("TI", 15)).isTrue();
        assertThat(TiposDocumento.acordeConEdad("RC", 5)).isTrue();
        assertThat(TiposDocumento.acordeConEdad("CC", 40)).isTrue();
    }
}
