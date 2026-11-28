package com.galeno.tratamiento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.galeno.tratamiento.SugerenciasOdontograma.Sugerencia;

import tools.jackson.databind.json.JsonMapper;

class SugerenciasOdontogramaTest {

    @Test
    void sugiereResinaPorCariesYExodonciaPorExtraccionIndicada() {
        var respuestas = JsonMapper.builder().build().readTree("""
                {"odontograma": {"dientes": {"denticion": "permanente", "dientes": {
                  "48": {"hallazgos": ["extraccion_indicada"]},
                  "36": {"superficies": {"O": "caries", "M": "caries", "D": "resina"}},
                  "11": {"hallazgos": ["fractura"]},
                  "21": {"hallazgos": ["ausente"], "superficies": {"V": "caries"}},
                  "26": {"superficies": {"O": "resina"}}
                }}}}""");

        assertThat(SugerenciasOdontograma.de(respuestas)).containsExactly(
                new Sugerencia(1, SugerenciasOdontograma.RESINA, "11", null, "Fractura"),
                new Sugerencia(1, SugerenciasOdontograma.RESINA, "36", "MO", "Caries en M, O"),
                new Sugerencia(2, SugerenciasOdontograma.EXODONCIA_PERMANENTE_MULTI, "48", null, "Extracción indicada"));
    }

    @Test
    void exodonciaSegunElDiente() {
        assertThat(SugerenciasOdontograma.exodoncia(41)).isEqualTo(SugerenciasOdontograma.EXODONCIA_PERMANENTE_UNI);
        assertThat(SugerenciasOdontograma.exodoncia(14)).isEqualTo(SugerenciasOdontograma.EXODONCIA_PERMANENTE_MULTI);
        assertThat(SugerenciasOdontograma.exodoncia(34)).isEqualTo(SugerenciasOdontograma.EXODONCIA_PERMANENTE_UNI);
        assertThat(SugerenciasOdontograma.exodoncia(75)).isEqualTo(SugerenciasOdontograma.EXODONCIA_TEMPORAL_MULTI);
        assertThat(SugerenciasOdontograma.exodoncia(52)).isEqualTo(SugerenciasOdontograma.EXODONCIA_TEMPORAL_UNI);
    }

    @Test
    void sinOdontogramaNoHaySugerencias() {
        assertThat(SugerenciasOdontograma.de(null)).isEmpty();
        assertThat(SugerenciasOdontograma.de(JsonMapper.builder().build().readTree("{}"))).isEmpty();
    }
}
