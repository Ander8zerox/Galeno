package com.galeno.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RecuperacionContrasenaRepository extends JpaRepository<RecuperacionContrasena, UUID> {

    Optional<RecuperacionContrasena> findByTokenHash(String tokenHash);

    /** Al restablecer: el mismo enlace abierto en dos pestañas no se usa dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecuperacionContrasena r where r.tokenHash = :tokenHash")
    Optional<RecuperacionContrasena> paraUsar(String tokenHash);

    @Query("select r from RecuperacionContrasena r where r.usuarioId = :usuarioId and r.usadoEn is null")
    List<RecuperacionContrasena> sinUsar(UUID usuarioId);
}
