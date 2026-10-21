package com.galeno.historia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface NotaAclaratoriaRepository extends JpaRepository<NotaAclaratoria, UUID> {

    List<NotaAclaratoria> findByAtencionIdOrderByCreadaEn(UUID atencionId);

    long countByAtencionIdIn(java.util.Collection<UUID> atencionIds);
}
