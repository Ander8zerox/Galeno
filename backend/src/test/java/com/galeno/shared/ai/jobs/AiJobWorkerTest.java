package com.galeno.shared.ai.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.galeno.shared.ai.LlmClient.LlmException;
import com.galeno.shared.ai.LlmClient.LlmRechazoException;

class AiJobWorkerTest {

    @Test
    void elMedicoNoVeElErrorDelProveedor() {
        var deBedrock = new RuntimeException(
                "404: {\"type\":\"error\",\"request_id\":\"req_x\",\"error\":{\"message\":\"The model 'anthropic.claude-haiku-5-5' does not exist\"}}");

        String mensaje = AiJobWorker.mensajeParaUsuario(deBedrock);

        assertThat(mensaje).startsWith("El asistente de IA no está disponible").doesNotContain("anthropic", "request_id");
        assertThat(AiJobWorker.mensajeParaUsuario(new LlmException("La respuesta superó el máximo de tokens configurado")))
                .isEqualTo(mensaje);
    }

    @Test
    void unRechazoNoInvitaAReintentar() {
        assertThat(AiJobWorker.mensajeParaUsuario(new LlmRechazoException()))
                .isEqualTo("El asistente de IA no puede responder esta solicitud. Continúe sin él.");
    }
}
