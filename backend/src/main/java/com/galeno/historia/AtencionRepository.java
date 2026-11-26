package com.galeno.historia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AtencionRepository extends JpaRepository<Atencion, UUID> {

    Optional<Atencion> findByCitaId(UUID citaId);

    boolean existsByClaveImportacion(String claveImportacion);

    @Query("select a.claveImportacion from Atencion a where a.claveImportacion in :claves")
    List<String> clavesImportadas(java.util.Collection<String> claves);

    List<Atencion> findByLoteImportacionId(UUID loteImportacionId);

    long countByPacienteId(UUID pacienteId);

    @Query("select distinct a.pacienteId from Atencion a where a.pacienteId in :pacienteIds")
    java.util.List<UUID> pacientesConAlguna(java.util.Collection<UUID> pacienteIds);

    @Query("select a.id from Atencion a where a.loteImportacionId = :loteId")
    List<UUID> idsDelLote(UUID loteId);

    /*
     * Borrado en bloque de lo que creó una importación: primero las colecciones de cada atención y luego las atenciones.
     * Si algo más las referencia (notas, fórmulas, documentos, lotes de RIPS), la llave foránea lo impide.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE d FROM atencion_diagnosticos d JOIN atenciones a ON a.id = d.atencion_id "
            + "WHERE a.lote_importacion_id = :loteId AND a.tenant_id = :tenantId", nativeQuery = true)
    int borrarDiagnosticosDelLote(UUID loteId, UUID tenantId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE d FROM atencion_procedimientos d JOIN atenciones a ON a.id = d.atencion_id "
            + "WHERE a.lote_importacion_id = :loteId AND a.tenant_id = :tenantId", nativeQuery = true)
    int borrarProcedimientosDelLote(UUID loteId, UUID tenantId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE d FROM atencion_ordenes d JOIN atenciones a ON a.id = d.atencion_id "
            + "WHERE a.lote_importacion_id = :loteId AND a.tenant_id = :tenantId", nativeQuery = true)
    int borrarOrdenesDelLote(UUID loteId, UUID tenantId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE d FROM atencion_factores_riesgo d JOIN atenciones a ON a.id = d.atencion_id "
            + "WHERE a.lote_importacion_id = :loteId AND a.tenant_id = :tenantId", nativeQuery = true)
    int borrarFactoresDelLote(UUID loteId, UUID tenantId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE FROM atenciones WHERE lote_importacion_id = :loteId AND tenant_id = :tenantId", nativeQuery = true)
    int borrarDelLote(UUID loteId, UUID tenantId);

    /** Las firmadas del paciente y los borradores propios de quien consulta. */
    @Query("""
            select a from Atencion a
            where a.pacienteId = :pacienteId
              and (a.estado = com.galeno.historia.Atencion.Estado.FIRMADA or a.medicoUsuarioId = :usuarioId)
            order by a.fechaAtencion desc
            """)
    List<Atencion> visibles(UUID pacienteId, UUID usuarioId);

    /** Para la historia completa impresa: solo lo firmado (o importado), de la más antigua a la más reciente. */
    @Query("""
            select a from Atencion a
            where a.pacienteId = :pacienteId and a.estado = com.galeno.historia.Atencion.Estado.FIRMADA
            order by a.fechaAtencion
            """)
    List<Atencion> firmadas(UUID pacienteId);

    /** Para el RIPS: las firmadas en Galenum (no las importadas) entre esas fechas. */
    @Query("""
            select a from Atencion a
            where a.estado = com.galeno.historia.Atencion.Estado.FIRMADA
              and a.origen = com.galeno.historia.Atencion.Origen.GALENO
              and a.fechaAtencion >= :desde and a.fechaAtencion < :hasta
            order by a.fechaAtencion
            """)
    List<Atencion> firmadasEntre(java.time.Instant desde, java.time.Instant hasta);
}
