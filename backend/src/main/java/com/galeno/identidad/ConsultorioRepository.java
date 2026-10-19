package com.galeno.identidad;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConsultorioRepository extends JpaRepository<Consultorio, UUID> {
}
