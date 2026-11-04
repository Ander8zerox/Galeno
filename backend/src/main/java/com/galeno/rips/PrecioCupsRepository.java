package com.galeno.rips;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PrecioCupsRepository extends JpaRepository<PrecioCups, UUID> {

    Optional<PrecioCups> findByCodigoCups(String codigoCups);

    List<PrecioCups> findByCodigoCupsIn(Collection<String> codigos);

    List<PrecioCups> findAllByOrderByCodigoCups();
}
