package com.galeno.shared.ai.jobs;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AiJobRepository extends JpaRepository<AiJob, UUID> {

    /**
     * Toma trabajos pendientes con bloqueo de fila. {@code SKIP LOCKED} permite varias instancias
     * de la API (ECS con 2+ tareas) sin procesar dos veces el mismo trabajo.
     */
    @Query(value = """
            select bin_to_uuid(id) as id, bin_to_uuid(tenant_id) as tenantId
            from ai_jobs
            where estado = 'PENDIENTE'
            order by creado_en
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<PendingJob> lockPending(int limit);

    @Modifying
    @Query("update AiJob j set j.estado = :estado, j.iniciadoEn = :now, j.intentos = j.intentos + 1 where j.id in :ids")
    int markStarted(Collection<UUID> ids, AiJobStatus estado, Instant now);

    /** Devuelve a la cola los trabajos que quedaron colgados (p. ej. la instancia se reinició). */
    @Modifying
    @Query("""
            update AiJob j set j.estado = :pendiente
            where j.estado = :enProceso and j.iniciadoEn < :before and j.intentos < :maxIntentos
            """)
    int requeueStuck(AiJobStatus pendiente, AiJobStatus enProceso, Instant before, int maxIntentos);

    interface PendingJob {
        String getId();

        String getTenantId();
    }
}
