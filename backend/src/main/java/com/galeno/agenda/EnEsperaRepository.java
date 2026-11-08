package com.galeno.agenda;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface EnEsperaRepository extends JpaRepository<EnEspera, UUID> {

    List<EnEspera> findByEstadoOrderByCreadaEn(EnEspera.Estado estado);

    List<EnEspera> findByPacienteIdAndEstado(UUID pacienteId, EnEspera.Estado estado);
}
