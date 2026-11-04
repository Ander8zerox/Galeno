package com.galeno.formatos;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FormatoRepository extends JpaRepository<Formato, UUID> {

    Optional<Formato> findByCodigo(String codigo);
}
