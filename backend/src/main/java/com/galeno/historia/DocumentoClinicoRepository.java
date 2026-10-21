package com.galeno.historia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentoClinicoRepository extends JpaRepository<DocumentoClinico, UUID> {

    List<DocumentoClinico> findByPacienteIdOrderByCreadoEnDesc(UUID pacienteId);

    long countByPacienteId(UUID pacienteId);
}
