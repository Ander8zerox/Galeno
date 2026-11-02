package com.galeno.identidad;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface EquipoRecordadoRepository extends JpaRepository<EquipoRecordado, UUID> {

    Optional<EquipoRecordado> findByTokenHash(String tokenHash);

    @Query("select count(e) from EquipoRecordado e where e.usuarioId = :usuarioId and e.expiraEn > :ahora")
    long vigentes(UUID usuarioId, Instant ahora);

    @Modifying
    @Query("delete from EquipoRecordado e where e.usuarioId = :usuarioId")
    void borrarDe(UUID usuarioId);
}
