package com.galeno.agenda;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BloqueoAgendaRepository extends JpaRepository<BloqueoAgenda, UUID> {

    /** Los que se cruzan con [desde, hasta), de todos los médicos y del consultorio. */
    @Query("""
            select b from BloqueoAgenda b
            where b.inicio < :hasta and b.fin > :desde
            order by b.inicio
            """)
    List<BloqueoAgenda> cruces(Instant desde, Instant hasta);
}
