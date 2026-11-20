package com.galeno.agenda;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TipoCitaRepository extends JpaRepository<TipoCita, UUID> {

    List<TipoCita> findAllByOrderByCreadoEn();

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, UUID id);

    boolean existsByNombreIgnoreCase(String nombre);
}
