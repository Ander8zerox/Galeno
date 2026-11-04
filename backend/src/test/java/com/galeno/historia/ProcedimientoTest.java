package com.galeno.historia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

class ProcedimientoTest {

    private static Procedimiento p(String codigo, int cantidad, String valor, String diente, String superficies) {
        return new Procedimiento(codigo, "Descripción", cantidad, valor == null ? null : new BigDecimal(valor), diente,
                superficies);
    }

    @Test
    void normalizaCodigoValorYSuperficies() {
        Procedimiento r = p(" 232102 ", 2, "45000", "36", "om");

        assertThat(r.getCodigoCups()).isEqualTo("232102");
        assertThat(r.getValorUnitario()).isEqualByComparingTo("45000.00");
        assertThat(r.getValorUnitario().scale()).isEqualTo(2);
        // En el orden del odontograma (V L M D O), sin repetidas
        assertThat(r.getSuperficies()).isEqualTo("MO");
        assertThat(p("232102", 1, null, "36", "OOM").getSuperficies()).isEqualTo("MO");
        assertThat(r.esConsulta()).isFalse();
        assertThat(p("890203", 1, null, null, null).esConsulta()).isTrue();
    }

    @Test
    void sinValorODienteEsValido() {
        Procedimiento r = p("890201", 1, null, " ", "");
        assertThat(r.getValorUnitario()).isNull();
        assertThat(r.getDiente()).isNull();
        assertThat(r.getSuperficies()).isNull();
    }

    @Test
    void rechazaLoQueNoTieneSentido() {
        assertProblema(() -> p("23-21", 1, null, null, null), "Código CUPS no válido");
        assertProblema(() -> p("232102", 0, null, null, null), "entre 1 y 99");
        assertProblema(() -> p("232102", 100, null, null, null), "entre 1 y 99");
        assertProblema(() -> p("232102", 1, "-1", null, null), "no puede ser negativo");
        assertProblema(() -> p("232102", 1, null, "19", null), "numeración FDI");
        assertProblema(() -> p("232102", 1, null, "56", null), "numeración FDI");
        assertProblema(() -> p("232102", 1, null, "36", "X"), "Superficies no válidas");
        // Superficies sin diente no dicen nada
        assertProblema(() -> p("232102", 1, null, null, "O"), "con el diente");
    }

    private static void assertProblema(Runnable r, String mensaje) {
        assertThatThrownBy(r::run).isInstanceOf(ApiProblem.class).hasMessageContaining(mensaje);
    }

    @Test
    void laConsultaSugeridaDependeDeLaEspecialidad() {
        assertThat(HistoriaService.sufijoConsulta(null)).isEqualTo("01");
        assertThat(HistoriaService.sufijoConsulta("Medicina general")).isEqualTo("01");
        assertThat(HistoriaService.sufijoConsulta("Odontología")).isEqualTo("03");
        assertThat(HistoriaService.sufijoConsulta("ODONTOLOGIA GENERAL")).isEqualTo("03");
        assertThat(HistoriaService.sufijoConsulta("Medicina interna")).isEqualTo("02");
    }

    @Test
    void firmadaNoSeCambianLosProcedimientos() {
        Atencion a = Atencion.nueva(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Dra. Prueba", null, null);
        a.registrarProcedimientos(List.of(p("890201", 1, "50000", null, null)));
        a.actualizar(new Atencion.Contenido("Control", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, List.of(new Diagnostico("J029", "Faringitis", true, null))));
        a.firmar("RM-1");

        assertThatThrownBy(() -> a.registrarProcedimientos(List.of())).isInstanceOf(ApiProblem.class)
                .hasMessageContaining("ya está firmada");
        assertThat(a.getProcedimientos()).hasSize(1);
    }
}
