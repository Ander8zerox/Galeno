package com.galeno.shared.ai.bedrock;

import com.anthropic.bedrock.backends.BedrockMantleBackend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.StructuredTextBlock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.galeno.shared.ai.AiProperties;
import com.galeno.shared.ai.LlmClient;

/**
 * Claude en Amazon Bedrock mediante el SDK oficial de Anthropic. Las credenciales salen de la cadena
 * estándar de AWS (rol de la instancia EC2/ECS en producción) y la región de {@code AWS_REGION}:
 * no hay llaves de API que guardar.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "bedrock")
public class BedrockLlmClient implements LlmClient {

    private final AnthropicClient client;
    private final AiProperties props;

    public BedrockLlmClient(AiProperties props) {
        this.props = props;
        this.client = AnthropicOkHttpClient.builder()
                .backend(BedrockMantleBackend.fromEnv())
                .build();
    }

    @Override
    public <T> LlmResult<T> generate(LlmRequest request, Class<T> outputType) {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(props.modeloPara(request.tarea()))
                .maxTokens(props.maxTokens())
                .system(request.systemPrompt())
                .outputConfig(StructuredOutputConfig.<T>builder()
                        .format(outputType)
                        .effort(OutputConfig.Effort.of(props.effort()))
                        .build())
                .addUserMessage(request.userContent())
                .build();

        StructuredMessage<T> message = client.messages().create(params);

        StopReason stop = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            throw new LlmException("El modelo rechazó la solicitud por sus políticas de seguridad");
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new LlmException("La respuesta superó el máximo de tokens configurado");
        }
        T output = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new LlmException("El modelo no devolvió contenido"));
        return new LlmResult<>(output, message.model().asString(),
                message.usage().inputTokens(), message.usage().outputTokens());
    }
}
