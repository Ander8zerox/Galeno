package com.galeno.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.galeno.shared.ai.jobs.AiJobType;

class AiPropertiesTest {

    @Test
    void cadaFuncionUsaSuModelo() {
        var props = new AiProperties("bedrock", "anthropic.claude-haiku-5-5", "anthropic.claude-sonnet-5-5", 8000, "medium", 4);

        assertThat(props.modeloPara(AiJobType.BORRADOR_FORMULA)).isEqualTo("anthropic.claude-haiku-5-5");
        assertThat(props.modeloPara(AiJobType.APOYO_DIAGNOSTICO)).isEqualTo("anthropic.claude-sonnet-5-5");
    }
}
