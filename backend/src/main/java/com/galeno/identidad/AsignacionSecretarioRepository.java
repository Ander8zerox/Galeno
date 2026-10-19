package com.galeno.identidad;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AsignacionSecretarioRepository extends JpaRepository<AsignacionSecretario, UUID> {

    List<AsignacionSecretario> findBySecretarioMembresiaId(UUID secretarioMembresiaId);

    void deleteBySecretarioMembresiaId(UUID secretarioMembresiaId);

    void deleteByMedicoMembresiaId(UUID medicoMembresiaId);
}
