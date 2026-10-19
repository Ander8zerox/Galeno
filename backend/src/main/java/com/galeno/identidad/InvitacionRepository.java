package com.galeno.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface InvitacionRepository extends JpaRepository<Invitacion, UUID> {

    Optional<Invitacion> findByTokenHash(String tokenHash);

    List<Invitacion> findByEstadoOrderByCreadoEnDesc(Invitacion.Estado estado);

    List<Invitacion> findByEmailAndEstado(String email, Invitacion.Estado estado);
}
