package com.galeno.historia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AtencionRepository extends JpaRepository<Atencion, UUID> {

    Optional<Atencion> findByCitaId(UUID citaId);

    /** Las firmadas del paciente y los borradores propios de quien consulta. */
    @Query("""
            select a from Atencion a
            where a.pacienteId = :pacienteId
              and (a.estado = com.galeno.historia.Atencion.Estado.FIRMADA or a.medicoUsuarioId = :usuarioId)
            order by a.fechaAtencion desc
            """)
    List<Atencion> visibles(UUID pacienteId, UUID usuarioId);
}
