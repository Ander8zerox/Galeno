package com.galeno.shared.ai.fake;

import com.galeno.shared.ai.LlmClient.LlmRequest;

/** Respuesta simulada para un tipo de salida, usada solo con {@code app.ai.provider=fake}. */
public interface FakeLlmResponse<T> {

    Class<T> type();

    T respond(LlmRequest request);
}
