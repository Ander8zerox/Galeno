package com.galeno.agenda;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CitaRepository extends JpaRepository<Cita, UUID> {

    @Query("""
            select c from Cita c
            where c.medicoMembresiaId = :medicoId and c.inicio >= :desde and c.inicio < :hasta
            order by c.inicio
            """)
    List<Cita> enRango(UUID medicoId, Instant desde, Instant hasta);

    /** Citas que ocupan tiempo (todas menos las canceladas) y se cruzan con [inicio, fin). */
    @Query("""
            select c from Cita c
            where c.medicoMembresiaId = :medicoId and c.estado <> com.galeno.agenda.Cita.Estado.CANCELADA
              and c.inicio < :fin and c.fin > :inicio
            """)
    List<Cita> cruces(UUID medicoId, Instant inicio, Instant fin);

    /**
     * Igual que {@link #cruces}, pero como lectura con bloqueo ({@code FOR UPDATE}). En REPEATABLE READ una lectura
     * normal usa la foto tomada al inicio de la transacción y no ve la cita que otra reserva acaba de confirmar;
     * una lectura con bloqueo siempre ve lo último confirmado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Cita c
            where c.medicoMembresiaId = :medicoId and c.estado <> com.galeno.agenda.Cita.Estado.CANCELADA
              and c.inicio < :fin and c.fin > :inicio
            """)
    List<Cita> crucesParaReserva(UUID medicoId, Instant inicio, Instant fin);

    @Query("""
            select c from Cita c
            where c.pacienteId = :pacienteId and c.inicio >= :desde
            order by c.inicio
            """)
    List<Cita> proximasDelPaciente(UUID pacienteId, Instant desde);
}
