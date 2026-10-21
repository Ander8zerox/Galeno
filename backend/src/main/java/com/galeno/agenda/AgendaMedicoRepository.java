package com.galeno.agenda;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AgendaMedicoRepository extends JpaRepository<AgendaMedico, UUID> {

    /**
     * Bloquea la agenda del médico hasta que termine la transacción: dos secretarias que agendan
     * a la vez el mismo cupo quedan en fila y la segunda ve el cupo ocupado.
     */
    @Query(value = "select count(*) from agendas_medico where medico_membresia_id = :id for update", nativeQuery = true)
    long bloquear(UUID id);
}
