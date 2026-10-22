package com.galeno.shared.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.galeno.shared.ai.jobs.AiJobType;

/**
 * @param provider          {@code fake} (desarrollo, sin costo) o {@code bedrock}
 * @param modelFormula      id del modelo en Bedrock para redactar fórmulas, p. ej. {@code anthropic.claude-haiku-5-5}
 * @param modelDiagnostico  id del modelo para el apoyo diagnóstico (se puede subir a Sonnet si la calidad no alcanza)
 * @param effort            low | medium | high | xhigh | max
 * @param workerConcurrency trabajos de IA que se procesan en paralelo por instancia
 */
@ConfigurationProperties("app.ai")
public record AiProperties(String provider, String modelFormula, String modelDiagnostico, long maxTokens, String effort,
                           int workerConcurrency) {

    /** Cada función usa su propio modelo: se cambia uno sin tocar el otro (y sin tocar código). */
    public String modeloPara(AiJobType tarea) {
        return switch (tarea) {
            case BORRADOR_FORMULA -> modelFormula;
            case APOYO_DIAGNOSTICO -> modelDiagnostico;
        };
    }
}
