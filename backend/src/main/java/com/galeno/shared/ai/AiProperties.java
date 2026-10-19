package com.galeno.shared.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider          {@code fake} (desarrollo, sin costo) o {@code bedrock}
 * @param model             id del modelo en Bedrock, p. ej. {@code anthropic.claude-opus-5-5}
 * @param effort            low | medium | high | xhigh | max
 * @param workerConcurrency trabajos de IA que se procesan en paralelo por instancia
 */
@ConfigurationProperties("app.ai")
public record AiProperties(String provider, String model, long maxTokens, String effort, int workerConcurrency) {
}
