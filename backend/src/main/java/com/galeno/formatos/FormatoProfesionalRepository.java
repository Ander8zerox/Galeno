package com.galeno.formatos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FormatoProfesionalRepository extends JpaRepository<FormatoProfesional, UUID> {

    List<FormatoProfesional> findByUsuarioId(UUID usuarioId);

    Optional<FormatoProfesional> findByUsuarioIdAndFormatoId(UUID usuarioId, UUID formatoId);
}
