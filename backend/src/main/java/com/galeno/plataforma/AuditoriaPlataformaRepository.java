package com.galeno.plataforma;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditoriaPlataformaRepository extends JpaRepository<AuditoriaPlataforma, UUID> {

    List<AuditoriaPlataforma> findTop100ByOrderByCreadoEnDesc();
}
