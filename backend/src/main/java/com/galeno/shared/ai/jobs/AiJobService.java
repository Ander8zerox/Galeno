package com.galeno.shared.ai.jobs;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

@Service
public class AiJobService {

    private final AiJobRepository repository;
    private final JsonMapper mapper;

    public AiJobService(AiJobRepository repository, JsonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public AiJob enqueue(AiJobType tipo, UUID pacienteId, Object entrada, String versionPrompt) {
        String json = mapper.writeValueAsString(entrada);
        return repository.save(AiJob.pendiente(tipo, pacienteId, AuthUser.current().id(), versionPrompt, json));
    }

    @Transactional(readOnly = true)
    public AiJob get(UUID id) {
        // Cada trabajo solo lo ve quien lo pidió: el borrador de un médico no lo lee otro médico del consultorio
        return repository.findById(id)
                .filter(job -> job.getSolicitadoPor().equals(AuthUser.current().id()))
                .orElseThrow(() -> ApiProblem.notFound("Trabajo de IA no encontrado"));
    }

    public <T> T readInput(AiJob job, Class<T> type) {
        return mapper.readValue(job.getEntrada(), type);
    }
}
