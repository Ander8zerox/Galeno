package com.galeno.rips;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RipsPacienteRepository extends JpaRepository<RipsPaciente, UUID> {
}
