package com.galeno.formulacion;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FormulaRepository extends JpaRepository<Formula, UUID> {

    List<Formula> findByPacienteIdOrderByFirmadaEnDesc(UUID pacienteId);

    long countByPacienteId(UUID pacienteId);

    @Query("select distinct f.pacienteId from Formula f where f.pacienteId in :pacienteIds")
    java.util.List<UUID> pacientesConAlguna(java.util.Collection<UUID> pacienteIds);


    long countByAtencionIdIn(java.util.Collection<UUID> atencionIds);

    List<Formula> findByAtencionIdIn(java.util.Collection<UUID> atencionIds);
}
