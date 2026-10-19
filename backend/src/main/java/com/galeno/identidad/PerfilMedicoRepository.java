package com.galeno.identidad;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PerfilMedicoRepository extends JpaRepository<PerfilMedico, UUID> {
}
