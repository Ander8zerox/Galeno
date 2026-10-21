package com.galeno.identidad;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RegistroPendienteRepository extends JpaRepository<RegistroPendiente, UUID> {

    Optional<RegistroPendiente> findByTokenHash(String tokenHash);

    /** Al confirmar: dos clics seguidos en el enlace no crean dos consultorios. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistroPendiente r where r.tokenHash = :tokenHash")
    Optional<RegistroPendiente> paraConfirmar(String tokenHash);

    /** Las solicitudes vencidas sin confirmar no sirven para nada y tienen datos personales: se borran. */
    @Modifying
    @Query("delete from RegistroPendiente r where r.confirmadoEn is null and r.expiraEn < :ahora")
    int borrarVencidas(Instant ahora);
}
