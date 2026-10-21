package com.galeno.partners;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

interface ComisionPartnerRepository extends JpaRepository<ComisionPartner, UUID> {

    boolean existsByPagoId(UUID pagoId);

    List<ComisionPartner> findByPartnerIdOrderByCreadaEnDesc(UUID partnerId);

    List<ComisionPartner> findAllByOrderByCreadaEnDesc();

    /** Para liquidar: con bloqueo, dos liquidaciones simultáneas no pagan la misma comisión. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ComisionPartner c where c.partnerId = :partnerId and c.liquidacionId is null and c.anuladaEn is null")
    List<ComisionPartner> pendientesParaLiquidar(UUID partnerId);
}
