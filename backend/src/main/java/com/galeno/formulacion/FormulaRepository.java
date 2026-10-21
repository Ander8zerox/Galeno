package com.galeno.formulacion;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FormulaRepository extends JpaRepository<Formula, UUID> {

    List<Formula> findByPacienteIdOrderByFirmadaEnDesc(UUID pacienteId);

    long countByPacienteId(UUID pacienteId);

    long countByAtencionIdIn(java.util.Collection<UUID> atencionIds);
}
