package com.galeno.apoyodiagnostico;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.pacientes.PacienteService;
import com.galeno.shared.ai.jobs.AiJobController.AiJobAccepted;
import com.galeno.shared.ai.jobs.AiJobService;
import com.galeno.shared.ai.jobs.AiJobType;

@RestController
@RequestMapping("/api/v1/apoyo-diagnostico")
public class ApoyoDiagnosticoController {

    private final AiJobService jobs;
    private final PacienteService pacientes;

    ApoyoDiagnosticoController(AiJobService jobs, PacienteService pacientes) {
        this.jobs = jobs;
        this.pacientes = pacientes;
    }

    /** Encola el análisis; el resultado se consulta en {@code GET /api/v1/ai-jobs/{jobId}}. */
    @PostMapping("/sugerencias")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiJobAccepted solicitar(@Valid @RequestBody SolicitudApoyo r) {
        pacientes.get(r.pacienteId());
        var entrada = new ApoyoDiagnosticoHandler.Entrada(r.pacienteId(), r.motivoConsulta(), r.enfermedadActual(),
                r.signosVitales(), r.examenFisico());
        return AiJobAccepted.of(jobs.enqueue(AiJobType.APOYO_DIAGNOSTICO, r.pacienteId(), entrada,
                ApoyoDiagnosticoHandler.PROMPT));
    }

    public record SolicitudApoyo(@NotNull UUID pacienteId,
                                 @NotBlank @Size(max = 1000) String motivoConsulta,
                                 @NotBlank @Size(max = 4000) String enfermedadActual,
                                 @Size(max = 500) String signosVitales,
                                 @Size(max = 4000) String examenFisico) {
    }
}
