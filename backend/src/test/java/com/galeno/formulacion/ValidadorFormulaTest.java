package com.galeno.formulacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.galeno.formulacion.BorradorFormula.ItemPropuesto;

class ValidadorFormulaTest {

    private static ItemPropuesto item(String medicamento, String dosis) {
        return new ItemPropuesto(medicamento, "500 mg", "tableta", "oral", dosis, "cada 8 horas", "7 días", "21", null);
    }

    @Test
    void alertaCuandoElMedicamentoCoincideConUnaAlergia() {
        var borrador = new BorradorFormula(List.of(item("Amoxicilina", "1 tableta")), List.of());

        List<String> alertas = ValidadorFormula.revisar(borrador, "Penicilina; amoxicilina");

        assertThat(alertas).singleElement().asString().startsWith("ALERGIA").contains("Amoxicilina");
    }

    @Test
    void ignoraTildesYMayusculas() {
        var borrador = new BorradorFormula(List.of(item("Ácido Acetilsalicílico", "1 tableta")), List.of());

        assertThat(ValidadorFormula.revisar(borrador, "acido acetilsalicilico")).hasSize(1);
    }

    @Test
    void alertaCuandoFaltaLaDosis() {
        var borrador = new BorradorFormula(List.of(item("Loratadina", "")), List.of());

        assertThat(ValidadorFormula.revisar(borrador, null)).containsExactly("Loratadina: falta dosis, frecuencia o duración.");
    }

    @Test
    void sinHallazgosNoHayAlertas() {
        var borrador = new BorradorFormula(List.of(item("Loratadina", "1 tableta")), List.of());

        assertThat(ValidadorFormula.revisar(borrador, "polen")).isEmpty();
    }
}
