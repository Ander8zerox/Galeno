package com.galeno.rips;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RipsPrestadorRepository extends JpaRepository<RipsPrestador, UUID> {

    /** Hay uno por consultorio: el filtro de tenant deja solo el del consultorio actual. */
    Optional<RipsPrestador> findFirstBy();
}
