package com.galeno.shared.ai.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import com.galeno.shared.ai.AiProperties;
import com.galeno.shared.ai.LlmClient.LlmResult;
import com.galeno.shared.tenancy.TenantContext;

/**
 * Cola de IA sobre MySQL. Cuando llegue la Capa 3 (SQS), solo cambia cómo se reclaman los trabajos;
 * los handlers de cada módulo quedan iguales.
 */
@Component
public class AiJobWorker {

    private static final Logger log = LoggerFactory.getLogger(AiJobWorker.class);
    private static final Duration STUCK_AFTER = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 3;

    private final AiJobRepository repository;
    private final TransactionTemplate tx;
    private final JsonMapper mapper;
    private final Map<AiJobType, AiJobHandler> handlers = new EnumMap<>(AiJobType.class);
    private final Semaphore slots;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiJobWorker(AiJobRepository repository, TransactionTemplate tx, JsonMapper mapper,
                       List<AiJobHandler> handlers, AiProperties props) {
        this.repository = repository;
        this.tx = tx;
        this.mapper = mapper;
        handlers.forEach(h -> this.handlers.put(h.type(), h));
        this.slots = new Semaphore(Math.max(1, props.workerConcurrency()));
    }

    @Scheduled(fixedDelayString = "${app.ai.worker-poll-ms:1000}")
    public void poll() {
        int free = slots.availablePermits();
        if (free == 0) {
            return;
        }
        List<AiJobRepository.PendingJob> claimed = TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(status -> {
            List<AiJobRepository.PendingJob> jobs = repository.lockPending(free);
            if (!jobs.isEmpty()) {
                repository.markStarted(jobs.stream().map(j -> UUID.fromString(j.getId())).toList(),
                        AiJobStatus.EN_PROCESO, Instant.now());
            }
            return jobs;
        }));
        for (AiJobRepository.PendingJob job : claimed) {
            slots.acquireUninterruptibly();
            executor.submit(() -> {
                try {
                    TenantContext.runAs(UUID.fromString(job.getTenantId()), () -> process(UUID.fromString(job.getId())));
                } finally {
                    slots.release();
                }
            });
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void requeueStuck() {
        int requeued = TenantContext.callAs(TenantContext.SYSTEM, () -> tx.execute(status -> repository.requeueStuck(
                AiJobStatus.PENDIENTE, AiJobStatus.EN_PROCESO, Instant.now().minus(STUCK_AFTER), MAX_ATTEMPTS)));
        if (requeued > 0) {
            log.warn("Se devolvieron {} trabajos de IA colgados a la cola", requeued);
        }
    }

    private void process(UUID id) {
        AiJob job = tx.execute(status -> repository.findById(id).orElseThrow());
        try {
            AiJobHandler handler = handlers.get(job.getTipo());
            if (handler == null) {
                throw new IllegalStateException("No hay handler para " + job.getTipo());
            }
            LlmResult<?> result = handler.handle(job); // fuera de transacción: la llamada al LLM puede tardar
            String json = mapper.writeValueAsString(result.output());
            tx.executeWithoutResult(status -> repository.findById(id).orElseThrow()
                    .completar(json, result.model(), result.inputTokens(), result.outputTokens()));
        } catch (RuntimeException e) {
            log.warn("Falló el trabajo de IA {} ({})", id, job.getTipo(), e);
            tx.executeWithoutResult(status -> repository.findById(id).ifPresent(j -> j.fallar(e.getMessage())));
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
