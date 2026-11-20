package com.galeno.historia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PreconsultaRepository extends JpaRepository<Preconsulta, UUID> {

    Optional<Preconsulta> findByCitaId(UUID citaId);

    List<Preconsulta> findByCitaIdIn(Collection<UUID> citaIds);
}
