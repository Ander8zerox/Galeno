package com.galeno.identidad;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface MfaUsuarioRepository extends JpaRepository<MfaUsuario, UUID> {

    /** Con bloqueo: dos peticiones simultáneas con el mismo código no pueden pasar las dos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MfaUsuario m where m.usuarioId = :usuarioId")
    Optional<MfaUsuario> paraVerificar(UUID usuarioId);
}
