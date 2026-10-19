package com.galeno.shared.ai.fake;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.galeno.shared.ai.LlmClient;

/**
 * Proveedor de desarrollo: respuestas fijas y sin costo, para trabajar el flujo completo
 * sin una cuenta de AWS. Cada módulo aporta sus respuestas de ejemplo con {@link FakeLlmResponse}.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "fake", matchIfMissing = true)
public class FakeLlmClient implements LlmClient {

    private final List<FakeLlmResponse<?>> responses;

    public FakeLlmClient(List<FakeLlmResponse<?>> responses) {
        this.responses = responses;
    }

    @Override
    public <T> LlmResult<T> generate(LlmRequest request, Class<T> outputType) {
        FakeLlmResponse<?> response = responses.stream()
                .filter(r -> r.type().equals(outputType))
                .findFirst()
                .orElseThrow(() -> new LlmException("No hay respuesta simulada para " + outputType.getSimpleName()));
        simulateLatency();
        return new LlmResult<>(outputType.cast(response.respond(request)), "fake", 0, 0);
    }

    private static void simulateLatency() {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
