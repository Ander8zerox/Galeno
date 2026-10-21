package com.galeno.importacion;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface LoteImportacionRepository extends JpaRepository<LoteImportacion, UUID> {

    Page<LoteImportacion> findAllByOrderByCreadoEnDesc(Pageable pageable);

    /** Confirmar y deshacer toman el lote con bloqueo: dos clics seguidos no lo procesan dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoteImportacion l where l.id = :id")
    Optional<LoteImportacion> paraProcesar(UUID id);
}
