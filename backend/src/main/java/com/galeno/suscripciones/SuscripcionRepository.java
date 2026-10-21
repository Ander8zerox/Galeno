package com.galeno.suscripciones;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SuscripcionRepository extends JpaRepository<Suscripcion, UUID> {

    Optional<Suscripcion> findByTenantId(UUID tenantId);

    /** Aprobar un pago y extender: con bloqueo, dos aprobaciones simultáneas no pisan la fecha. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Suscripcion s where s.tenantId = :tenantId")
    Optional<Suscripcion> paraActualizar(UUID tenantId);
}
