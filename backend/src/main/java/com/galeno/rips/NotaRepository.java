package com.galeno.rips;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface NotaRepository extends JpaRepository<Nota, UUID> {

    List<Nota> findByLoteIdOrderByCreadoEn(UUID loteId);

    /** Notas generadas (no anuladas) del lote: mientras las haya, el lote no se anula. */
    @Query("select count(n) > 0 from Nota n where n.loteId = :loteId and n.estado = com.galeno.rips.Nota.Estado.GENERADA")
    boolean hayVigentes(UUID loteId);
}
