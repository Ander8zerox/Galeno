package com.galeno.rips;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RipsProfesionalRepository extends JpaRepository<RipsProfesional, UUID> {

    Optional<RipsProfesional> findByUsuarioId(UUID usuarioId);
}
