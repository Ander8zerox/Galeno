package com.galeno.apoyodiagnostico;

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
class ApoyoDiagnosticoHandler implements AiJobHandler {

    static final String PROMPT = "apoyo-diagnostico-v1";

    private final AiJobService jobs;
    private final PacienteService pacientes;
    private final LlmClient llm;
    private final Prompts prompts;

    ApoyoDiagnosticoHandler(AiJobService jobs, PacienteService pacientes, LlmClient llm, Prompts prompts) {
        this.jobs = jobs;
        this.pacientes = pacientes;
        this.llm = llm;
        this.prompts = prompts;
    }

    @Override
    public AiJobType type() {
        return AiJobType.APOYO_DIAGNOSTICO;
    }

    @Override
    public LlmResult<SugerenciasDiagnosticas> handle(AiJob job) {
        Entrada e = jobs.readInput(job, Entrada.class);
        Paciente paciente = pacientes.get(e.pacienteId());
        String contenido = """
                %s

                <caso>
                Motivo de consulta: %s
                Enfermedad actual: %s
                Signos vitales: %s
                Examen físico: %s
                </caso>""".formatted(paciente.resumenAnonimizado(), e.motivoConsulta(), e.enfermedadActual(),
                orNone(e.signosVitales()), orNone(e.examenFisico()));
        return llm.generate(new LlmRequest(AiJobType.APOYO_DIAGNOSTICO, prompts.get(PROMPT), contenido), SugerenciasDiagnosticas.class);
    }

    private static String orNone(String s) {
        return s == null || s.isBlank() ? "no registrado" : s;
    }

    record Entrada(UUID pacienteId, String motivoConsulta, String enfermedadActual, String signosVitales,
                   String examenFisico) {
    }
}
