package com.galeno.formatos;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FormatoConsultorioRepository extends JpaRepository<FormatoConsultorio, UUID> {

    Optional<FormatoConsultorio> findByFormatoId(UUID formatoId);

    boolean existsByFormatoId(UUID formatoId);
}
