package com.galeno.pacientes;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AlergiaPacienteRepository extends JpaRepository<AlergiaPaciente, UUID> {

    List<AlergiaPaciente> findByPacienteIdOrderByCreadoEn(UUID pacienteId);
}

interface AntecedentePacienteRepository extends JpaRepository<AntecedentePaciente, UUID> {

    List<AntecedentePaciente> findByPacienteIdOrderByCreadoEn(UUID pacienteId);
}
