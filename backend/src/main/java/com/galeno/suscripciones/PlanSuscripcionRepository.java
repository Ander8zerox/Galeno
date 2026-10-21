package com.galeno.suscripciones;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface PlanSuscripcionRepository extends JpaRepository<PlanSuscripcion, String> {

    List<PlanSuscripcion> findByActivoTrueOrderByMeses();

    List<PlanSuscripcion> findAllByOrderByMeses();
}
