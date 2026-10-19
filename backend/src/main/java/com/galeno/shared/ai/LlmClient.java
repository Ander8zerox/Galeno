package com.galeno.shared.ai;

/**
 * Puerto hacia el modelo de lenguaje. Los módulos de negocio solo conocen esta interfaz:
 * cambiar Bedrock por otro proveedor (o por un microservicio) no toca la lógica clínica.
 */
public interface LlmClient {

    /** Genera una respuesta estructurada que cumple el esquema JSON derivado de {@code outputType}. */
    <T> LlmResult<T> generate(LlmRequest request, Class<T> outputType);

    record LlmRequest(String systemPrompt, String userContent) {
    }

    record LlmResult<T>(T output, String model, long inputTokens, long outputTokens) {
    }

    /** El modelo no completó la respuesta (rechazo de seguridad, límite de tokens, etc.). */
    class LlmException extends RuntimeException {

        public LlmException(String message) {
            super(message);
        }
    }
}
