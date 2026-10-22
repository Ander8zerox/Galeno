package com.galeno.formulacion;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.galeno.pacientes.Paciente;
import com.galeno.pacientes.PacienteService;
import com.galeno.shared.ai.LlmClient;
import com.galeno.shared.ai.LlmClient.LlmRequest;
import com.galeno.shared.ai.LlmClient.LlmResult;
import com.galeno.shared.ai.Prompts;
import com.galeno.shared.ai.jobs.AiJob;
import com.galeno.shared.ai.jobs.AiJobHandler;
import com.galeno.shared.ai.jobs.AiJobService;
import com.galeno.shared.ai.jobs.AiJobType;

@Component
class BorradorFormulaHandler implements AiJobHandler {

    static final String PROMPT = "formulacion-v1";

    private final AiJobService jobs;
    private final PacienteService pacientes;
    private final LlmClient llm;
    private final Prompts prompts;

    BorradorFormulaHandler(AiJobService jobs, PacienteService pacientes, LlmClient llm, Prompts prompts) {
        this.jobs = jobs;
        this.pacientes = pacientes;
        this.llm = llm;
        this.prompts = prompts;
    }

    @Override
    public AiJobType type() {
        return AiJobType.BORRADOR_FORMULA;
    }

    @Override
    public LlmResult<BorradorFormula> handle(AiJob job) {
        Entrada entrada = jobs.readInput(job, Entrada.class);
        Paciente paciente = pacientes.get(entrada.pacienteId());

        String contenido = """
                %s
                Diagnóstico: %s

                Indicación del médico:
                <indicacion>
                %s
                </indicacion>""".formatted(paciente.resumenAnonimizado(),
                entrada.diagnostico() == null || entrada.diagnostico().isBlank() ? "no especificado" : entrada.diagnostico(),
                entrada.texto());

        LlmResult<BorradorFormula> result = llm.generate(new LlmRequest(AiJobType.BORRADOR_FORMULA, prompts.get(PROMPT), contenido), BorradorFormula.class);
        BorradorFormula revisado = result.output()
                .conAdvertencias(ValidadorFormula.revisar(result.output(), paciente.getAlergias()));
        return new LlmResult<>(revisado, result.model(), result.inputTokens(), result.outputTokens());
    }

    /** Lo que se guarda en {@code ai_jobs.entrada}. */
    record Entrada(UUID pacienteId, String texto, String diagnostico) {
    }
}
