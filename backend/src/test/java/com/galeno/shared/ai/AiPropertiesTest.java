package com.galeno.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.galeno.shared.ai.jobs.AiJobType;

class AiPropertiesTest {

    @Test
    void cadaFuncionUsaSuModelo() {
        var props = new AiProperties("bedrock", "us.anthropic.claude-haiku-5-5", "us.anthropic.claude-sonnet-5-5", 8000, "medium", 4);

        assertThat(props.modeloPara(AiJobType.BORRADOR_FORMULA)).isEqualTo("us.anthropic.claude-haiku-5-5");
        assertThat(props.modeloPara(AiJobType.APOYO_DIAGNOSTICO)).isEqualTo("us.anthropic.claude-sonnet-5-5");
    }
}
