package com.galeno.historia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface DocumentoClinicoRepository extends JpaRepository<DocumentoClinico, UUID> {

    List<DocumentoClinico> findByPacienteIdOrderByCreadoEnDesc(UUID pacienteId);

    long countByPacienteId(UUID pacienteId);

    @Query("select distinct d.pacienteId from DocumentoClinico d where d.pacienteId in :pacienteIds")
    java.util.List<UUID> pacientesConAlguna(java.util.Collection<UUID> pacienteIds);


    boolean existsByAtencionIdAndAnuladoEnIsNull(UUID atencionId);

    List<DocumentoClinico> findByAtencionId(UUID atencionId);
}
