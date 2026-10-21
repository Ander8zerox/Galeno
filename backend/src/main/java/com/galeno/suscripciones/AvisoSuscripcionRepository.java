package com.galeno.suscripciones;

import org.springframework.data.jpa.repository.JpaRepository;

interface AvisoSuscripcionRepository extends JpaRepository<AvisoSuscripcion, AvisoSuscripcion.Clave> {
}
