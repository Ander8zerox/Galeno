package com.galeno.historia;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccesoHistoriaRepository extends JpaRepository<AccesoHistoria, UUID> {
}
