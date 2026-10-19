package com.galeno.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface MembresiaRepository extends JpaRepository<Membresia, UUID> {

    /** Con el tenant del sistema devuelve las membresías de todos los consultorios (login). */
    List<Membresia> findByUsuarioId(UUID usuarioId);

    Optional<Membresia> findByUsuarioIdAndTenantId(UUID usuarioId, UUID tenantId);
}
