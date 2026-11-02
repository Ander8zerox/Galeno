package com.galeno.identidad;

import java.time.Instant;
import java.util.List;
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

    /** Consultorios creados desde una IP: cuenta solo lo confirmado (las solicitudes vencidas se borran). */
    long countByIpAndConfirmadoEnAfter(String ip, Instant desde);

    /** Correos con los que se crearon consultorios desde una fecha (se comparan por buzón, ver ControlRegistro). */
    @Query("select r.email from RegistroPendiente r where r.confirmadoEn > :desde")
    List<String> correosConfirmadosDesde(Instant desde);

    /** Las solicitudes vencidas sin confirmar no sirven para nada y tienen datos personales: se borran. */
    @Modifying
    @Query("delete from RegistroPendiente r where r.confirmadoEn is null and r.expiraEn < :ahora")
    int borrarVencidas(Instant ahora);
}
