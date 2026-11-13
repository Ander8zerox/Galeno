package com.galeno.shared.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.galeno.shared.tenancy.TenantContext;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

class TopesDiariosTest {

    private final TopesDiarios topes = new TopesDiarios(2, 1);

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    void cadaConsultorioTieneSuPropioTope() {
        TenantContext.set(UUID.randomUUID());
        topes.cita();
        topes.cita();
        assertThatThrownBy(topes::cita).isInstanceOf(ApiProblem.class).hasMessageContaining("tope de 2 citas");

        TenantContext.set(UUID.randomUUID());
        topes.cita(); // otro consultorio
        topes.paciente();
        assertThatThrownBy(topes::paciente).hasMessageContaining("tope de 1 pacientes");
    }

    @Test
    void losProcesosDelSistemaNoCuentan() {
        assertThatCode(() -> {
            TenantContext.set(TenantContext.SYSTEM);
            for (int i = 0; i < 5; i++) {
                topes.paciente();
            }
            TenantContext.clear();
            topes.paciente();
            topes.paciente();
        }).doesNotThrowAnyException();
    }
}
