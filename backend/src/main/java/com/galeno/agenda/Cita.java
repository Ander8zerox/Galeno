package com.galeno.agenda;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

@Entity
@Table(name = "citas")
public class Cita {

    public enum Estado {
        PROGRAMADA,
        CONFIRMADA,
        ATENDIDA,
        NO_ASISTIO,
        CANCELADA;

        /** Estados desde los que todavía se puede cambiar la cita. */
        static final Set<Estado> ABIERTOS = EnumSet.of(PROGRAMADA, CONFIRMADA);
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "medico_membresia_id", nullable = false, updatable = false)
    private UUID medicoMembresiaId;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(length = 300)
    private String motivo;

    @Column(nullable = false)
    private boolean sobrecupo;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    @Column(name = "creada_por", nullable = false, updatable = false)
    private UUID creadaPor;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    protected Cita() {
    }

    public Cita(UUID medicoMembresiaId, UUID pacienteId, Instant inicio, Instant fin, String motivo, boolean sobrecupo,
                UUID creadaPor) {
        this.medicoMembresiaId = medicoMembresiaId;
        this.pacienteId = pacienteId;
        this.inicio = inicio;
        this.fin = fin;
        this.motivo = motivo;
        this.sobrecupo = sobrecupo;
        this.estado = Estado.PROGRAMADA;
        this.creadaPor = creadaPor;
        this.creadaEn = Instant.now();
        this.actualizadaEn = creadaEn;
    }

    void cambiarEstado(Estado nuevo, String motivoCancelacion) {
        if (!Estado.ABIERTOS.contains(estado)) {
            throw ApiProblem.conflict("La cita ya está " + estado.name().toLowerCase().replace('_', ' ') + " y no se puede cambiar");
        }
        if (nuevo == Estado.PROGRAMADA || nuevo == estado) {
            throw ApiProblem.badRequest("Cambio de estado no válido");
        }
        if (nuevo == Estado.CANCELADA && (motivoCancelacion == null || motivoCancelacion.isBlank())) {
            throw ApiProblem.badRequest("Indique el motivo de la cancelación");
        }
        this.estado = nuevo;
        this.motivoCancelacion = nuevo == Estado.CANCELADA ? motivoCancelacion.strip() : null;
        this.actualizadaEn = Instant.now();
    }

    void reprogramar(Instant nuevoInicio, Instant nuevoFin, boolean sobrecupo) {
        if (!Estado.ABIERTOS.contains(estado)) {
            throw ApiProblem.conflict("Solo se reprograman citas programadas o confirmadas");
        }
        this.inicio = nuevoInicio;
        this.fin = nuevoFin;
        this.sobrecupo = sobrecupo;
        this.estado = Estado.PROGRAMADA; // hay que volver a confirmarla
        this.actualizadaEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMedicoMembresiaId() {
        return medicoMembresiaId;
    }

    public UUID getPacienteId() {
        return pacienteId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public Estado getEstado() {
        return estado;
    }

    public String getMotivo() {
        return motivo;
    }

    public boolean isSobrecupo() {
        return sobrecupo;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }
}
