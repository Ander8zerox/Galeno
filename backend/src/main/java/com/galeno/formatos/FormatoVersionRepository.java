package com.galeno.formatos;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FormatoVersionRepository extends JpaRepository<FormatoVersion, UUID> {

    Optional<FormatoVersion> findByFormatoIdAndVersion(UUID formatoId, int version);

    /** La última publicada: la que se usa al agregar el formato a una atención nueva. */
    Optional<FormatoVersion> findFirstByFormatoIdOrderByVersionDesc(UUID formatoId);
}
