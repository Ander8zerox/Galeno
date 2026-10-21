package com.galeno.suscripciones;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ComprobantePagoRepository extends JpaRepository<ComprobantePago, UUID> {
}
