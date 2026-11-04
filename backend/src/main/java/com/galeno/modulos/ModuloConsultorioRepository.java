package com.galeno.modulos;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ModuloConsultorioRepository extends JpaRepository<ModuloConsultorio, UUID> {

    Optional<ModuloConsultorio> findByModulo(Modulo modulo);

    boolean existsByModulo(Modulo modulo);
}
