package com.galeno.shared.ai.jobs;

import com.galeno.shared.ai.LlmClient.LlmResult;

/** Cada módulo implementa el procesamiento de sus tipos de trabajo de IA. */
public interface AiJobHandler {

    AiJobType type();

    /** Se ejecuta fuera de transacción y con el tenant del trabajo ya fijado. */
    LlmResult<?> handle(AiJob job);
}
