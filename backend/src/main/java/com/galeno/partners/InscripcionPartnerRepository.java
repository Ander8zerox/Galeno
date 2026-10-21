package com.galeno.partners;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

interface InscripcionPartnerRepository extends JpaRepository<InscripcionPartner, UUID> {

    Optional<InscripcionPartner> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InscripcionPartner i where i.tokenHash = :tokenHash")
    Optional<InscripcionPartner> paraConfirmar(String tokenHash);

    @Modifying
    @Query("delete from InscripcionPartner i where i.confirmadoEn is null and i.expiraEn < :ahora")
    int borrarVencidas(Instant ahora);
}
