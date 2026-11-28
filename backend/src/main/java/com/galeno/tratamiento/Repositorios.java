package com.galeno.tratamiento;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PlanRepository extends JpaRepository<PlanTratamiento, UUID> {

    List<PlanTratamiento> findByPacienteIdOrderByCreadoEnDesc(UUID pacienteId);

    boolean existsByPacienteIdAndEstadoIn(UUID pacienteId, java.util.Collection<PlanTratamiento.Estado> estados);

    List<PlanTratamiento> findByEstado(PlanTratamiento.Estado estado);
}

interface ItemPlanRepository extends JpaRepository<ItemPlan, UUID> {

    List<ItemPlan> findByPlanIdOrderByFaseAscOrdenAsc(UUID planId);

    List<ItemPlan> findByPlanIdIn(java.util.Collection<UUID> planIds);
}

interface AceptacionPlanRepository extends JpaRepository<AceptacionPlan, UUID> {

    List<AceptacionPlan> findByPlanIdOrderByNumeroAsc(UUID planId);

    List<AceptacionPlan> findByPlanIdIn(java.util.Collection<UUID> planIds);
}
