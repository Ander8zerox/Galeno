package com.galeno.identidad;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, UUID> {

    List<CodigoRecuperacion> findByUsuarioId(UUID usuarioId);

    @Modifying
    @Query("delete from CodigoRecuperacion c where c.usuarioId = :usuarioId")
    void borrarDe(UUID usuarioId);
}
