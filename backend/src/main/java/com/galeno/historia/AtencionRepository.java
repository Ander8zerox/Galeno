package com.galeno.historia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AtencionRepository extends JpaRepository<Atencion, UUID> {

    Optional<Atencion> findByCitaId(UUID citaId);

    boolean existsByClaveImportacion(String claveImportacion);

    List<Atencion> findByLoteImportacionId(UUID loteImportacionId);

    long countByPacienteId(UUID pacienteId);

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
}
