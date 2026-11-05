package com.galeno.rips;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CobroRepository extends JpaRepository<Cobro, UUID> {

    Optional<Cobro> findByCitaId(UUID citaId);

    Optional<Cobro> findByAtencionId(UUID atencionId);

    List<Cobro> findByCitaIdIn(Collection<UUID> citaIds);
}
