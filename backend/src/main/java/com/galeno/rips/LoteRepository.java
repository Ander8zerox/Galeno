package com.galeno.rips;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface LoteRepository extends JpaRepository<Lote, UUID> {

    List<Lote> findAllByOrderByCreadoEnDesc();

    /** El último número de envío de un RIPS sin factura del consultorio (0 si no hay). */
    @Query("select coalesce(max(l.numeroEnvio), 0) from Lote l")
    int ultimoEnvio();

    /** De esas atenciones, las que ya están en un lote que no está anulado. */
    @Query("""
            select la.atencionId from Lote l join l.atenciones la
            where l.estado <> com.galeno.rips.Lote.Estado.ANULADO and la.atencionId in :atencionIds
            """)
    List<UUID> enLotesVivos(Collection<UUID> atencionIds);

    @Query("""
            select count(l) > 0 from Lote l join l.atenciones la
            where l.estado = com.galeno.rips.Lote.Estado.GENERADO and la.atencionId = :atencionId
            """)
    boolean enLoteGenerado(UUID atencionId);
}
