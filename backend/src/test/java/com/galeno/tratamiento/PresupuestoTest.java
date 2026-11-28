package com.galeno.tratamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.galeno.identidad.IdentidadService.ConsultorioResumen;
import com.galeno.identidad.IdentidadService.Profesional;
import com.galeno.pacientes.Paciente;

class PresupuestoTest {

    private static final ConsultorioResumen CONSULTORIO =
            new ConsultorioResumen(UUID.randomUUID(), "Odontología Prueba", "900123456", ZoneId.of("America/Bogota"));

    private static ItemPlan item(int fase, String nombreFase, String cups, String desc, String diente, String sup,
                                 int cantidad, String valor, String descuento) {
        return new ItemPlan(UUID.randomUUID(), new ItemPlan.Datos(fase, nombreFase, 1, cups, desc, diente, sup, cantidad,
                new BigDecimal(valor), new BigDecimal(descuento)));
    }

    @Test
    void totalesSeparanLoAceptadoLoRealizadoYLoPorAceptar() {
        ItemPlan resina = item(1, "Operatoria", "232101", "Resina", "36", "MO", 2, "80000", "10000");
        ItemPlan exodoncia = item(1, null, "237301", "Exodoncia", "48", null, 1, "120000", "0");
        ItemPlan corona = item(2, null, "237101", "Corona", "11", null, 1, "900000", "0");
        resina.aceptar(1);
        exodoncia.aceptar(1);
        exodoncia.cancelar("El paciente no quiso");

        TratamientosService.TotalesPlan t = TratamientosService.TotalesPlan.de(List.of(resina, exodoncia, corona));

        assertThat(resina.total()).isEqualByComparingTo("150000");
        assertThat(t.aceptado()).isEqualByComparingTo("150000");
        assertThat(t.pendiente()).isEqualByComparingTo("150000");
        assertThat(t.cancelado()).isEqualByComparingTo("120000");
        assertThat(t.porAceptar()).isEqualByComparingTo("900000");
        assertThat(t.procedimientos()).isEqualTo(2);
    }

    @Test
    void elPresupuestoMuestraFasesValoresYLoQueFaltaFirmar() {
        PlanTratamiento plan = new PlanTratamiento(UUID.randomUUID(), UUID.randomUUID(), "Control cada 6 meses", 30);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        plan.presentar();
        ItemPlan resina = item(1, "Operatoria", "232101", "Obturación en resina", "36", "MO", 2, "80000", "10000");
        ItemPlan cancelado = item(1, null, "237301", "Exodoncia", "48", null, 1, "120000", "0");
        cancelado.cancelar("No");
        ItemPlan corona = item(2, "Rehabilitación", "237101", "Corona", "11", null, 1, "1250000.50", "0");
        Paciente paciente = mock(Paciente.class);
        when(paciente.getNombres()).thenReturn("Ana");
        when(paciente.getApellidos()).thenReturn("Pérez");
        when(paciente.getTipoDocumento()).thenReturn("CC");
        when(paciente.getNumeroDocumento()).thenReturn("123");
        when(paciente.getSexo()).thenReturn("F");

        String html = PresupuestoPdf.html(new TratamientosService.Presupuesto(plan, List.of(resina, cancelado, corona), List.of()),
                paciente, CONSULTORIO, new Profesional("Dra. Odonto", "RM-1", "Odontología"));

        assertThat(html).contains("PRESUPUESTO DE TRATAMIENTO", "Fase 1: Operatoria", "Fase 2: Rehabilitación",
                "36 MO", "$ 150.000", "$ 1.250.000,5", "Total del tratamiento: $ 1.400.000,5", "válida por 30 días",
                "Firma del paciente", "Control cada 6 meses", "Dra. Odonto")
                .doesNotContain("Exodoncia");
    }

    @Test
    void conAceptacionMuestraLaFirmaYMarcaLosAdicionales() {
        PlanTratamiento plan = new PlanTratamiento(UUID.randomUUID(), UUID.randomUUID(), null, 15);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        plan.presentar();
        ItemPlan resina = item(1, null, "232101", "Resina", "36", "O", 1, "80000", "0");
        resina.aceptar(1);
        ItemPlan adicional = item(1, null, "997102", "Profilaxis", null, null, 1, "60000", "0");
        adicional.aceptar(2);
        String firma = "data:image/png;base64,iVBORw0KGgo=";
        List<AceptacionPlan> aceptaciones = List.of(
                new AceptacionPlan(plan.getId(), 1, UUID.randomUUID(), firma, "Ana Pérez", new BigDecimal("80000")),
                new AceptacionPlan(plan.getId(), 2, UUID.randomUUID(), null, null, new BigDecimal("60000")));
        Paciente paciente = mock(Paciente.class);
        when(paciente.getSexo()).thenReturn("F");

        String html = PresupuestoPdf.html(new TratamientosService.Presupuesto(plan, List.of(resina, adicional), aceptaciones),
                paciente, CONSULTORIO, new Profesional("Dr. X", null, null));

        assertThat(html).contains("adicional 1", "Aceptación del plan", "Aceptación del adicional 1", firma, "Ana Pérez",
                "Aceptación verbal").doesNotContain("Firma del paciente o su representante");
    }

    @Test
    void laFirmaDebeSerUnPng() {
        assertThat(TratamientosService.firma(" ")).isNull();
        assertThat(TratamientosService.firma("data:image/png;base64,iVBORw0KGgo=")).isNotNull();
        assertThatThrownBy(() -> TratamientosService.firma("data:image/svg+xml;base64,PHN2Zz4=")).hasMessageContaining("PNG");
        assertThatThrownBy(() -> TratamientosService.firma("data:image/png;base64,<script>")).hasMessageContaining("PNG");
    }

    @Test
    void pesosColombianos() {
        assertThat(PresupuestoPdf.pesos(new BigDecimal("1250000.00"))).isEqualTo("$ 1.250.000");
        assertThat(PresupuestoPdf.pesos(new BigDecimal("0"))).isEqualTo("$ 0");
    }
}
