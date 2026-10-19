package com.galeno.shared.ai.jobs;

import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** El front consulta aquí (polling) el estado de un trabajo de IA hasta que termine. */
@RestController
@RequestMapping("/api/v1/ai-jobs")
public class AiJobController {

    private final AiJobService service;
    private final JsonMapper mapper;

    public AiJobController(AiJobService service, JsonMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/{id}")
    public AiJobResponse get(@PathVariable UUID id) {
        AiJob job = service.get(id);
        JsonNode resultado = job.getResultado() == null ? null : mapper.readTree(job.getResultado());
        return new AiJobResponse(job.getId(), job.getTipo(), job.getEstado(), resultado, job.getError(),
                job.getCreadoEn(), job.getFinalizadoEn());
    }

    public record AiJobResponse(UUID id, AiJobType tipo, AiJobStatus estado, JsonNode resultado, String error,
                                Instant creadoEn, Instant finalizadoEn) {
    }

    /** Respuesta 202 al encolar un trabajo. */
    public record AiJobAccepted(UUID jobId, AiJobStatus estado) {

        public static AiJobAccepted of(AiJob job) {
            return new AiJobAccepted(job.getId(), job.getEstado());
        }
    }
}
