package com.galeno.rips;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TarifaRepository extends JpaRepository<Tarifa, UUID> {

    List<Tarifa> findByPagadorIdOrderByCodigoCups(UUID pagadorId);

    List<Tarifa> findByPagadorIdAndCodigoCupsIn(UUID pagadorId, Collection<String> codigos);

    Optional<Tarifa> findByPagadorIdAndCodigoCups(UUID pagadorId, String codigoCups);
}
