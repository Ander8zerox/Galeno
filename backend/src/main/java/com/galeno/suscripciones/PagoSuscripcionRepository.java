package com.galeno.suscripciones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface PagoSuscripcionRepository extends JpaRepository<PagoSuscripcion, UUID> {

    List<PagoSuscripcion> findAllByOrderByReportadoEnDesc();

    List<PagoSuscripcion> findByEstadoOrderByReportadoEn(PagoSuscripcion.Estado estado);

    boolean existsByEstado(PagoSuscripcion.Estado estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PagoSuscripcion p where p.id = :id")
    Optional<PagoSuscripcion> paraRevisar(UUID id);
}
