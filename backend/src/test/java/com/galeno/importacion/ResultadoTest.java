package com.galeno.importacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import com.galeno.importacion.ImportacionService.Resultado;

class ResultadoTest {

    @Test
    void leeElResultadoDeLotesGuardadosAntesDeLosListados() {
        String deLaVersionAnterior = """
                {"pacientesCreados":2,"pacientesActualizados":0,"atencionesCreadas":3,"atencionesYaImportadas":0,
                 "atencionesEliminadas":0,"pacientesEliminados":0,"pacientesConservados":0}""";

        Resultado r = JsonMapper.builder().build().readValue(deLaVersionAnterior, Resultado.class);

        assertThat(r.pacientesCreados()).isEqualTo(2);
        assertThat(r.pacientesRestaurados()).isZero();
    }
}
