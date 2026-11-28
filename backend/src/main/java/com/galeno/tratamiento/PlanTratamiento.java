package com.galeno.tratamiento;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Plan de tratamiento de un paciente (odontología): fases con procedimientos y su presupuesto, que el paciente acepta
 * y se va realizando en varias sesiones. Ver docs/diseno-plan-tratamiento.md.
 */
@Entity
@Table(name = "planes_tratamiento")
class PlanTratamiento {

    enum Estado {
        BORRADOR, PRESENTADO, ACEPTADO, TERMINADO, CANCELADO;

        boolean vigente() {
            return this == BORRADOR || this == PRESENTADO || this == ACEPTADO;
        }
    }

    static final int VIGENCIA_POR_DEFECTO = 30;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    @Column(name = "odontologo_id", nullable = false, updatable = false)
    private UUID odontologoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Estado estado;

    @Column(length = 1000)
    private String observaciones;

    /** Días que vale la cotización desde que se presenta. */
    @Column(name = "vigencia_dias", nullable = false)
    private int vigenciaDias;

    @Column(name = "presentado_en")
    private Instant presentadoEn;

    @Column(name = "cerrado_en")
    private Instant cerradoEn;

    @Column(name = "motivo_cierre", length = 300)
    private String motivoCierre;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected PlanTratamiento() {
    }

    PlanTratamiento(UUID pacienteId, UUID odontologoId, String observaciones, int vigenciaDias) {
        this.pacienteId = pacienteId;
        this.odontologoId = odontologoId;
        this.observaciones = observaciones;
        this.vigenciaDias = vigenciaDias;
        this.estado = Estado.BORRADOR;
        this.creadoEn = Instant.now();
        this.actualizadoEn = this.creadoEn;
    }

    void datos(String observaciones, int vigenciaDias) {
        this.observaciones = observaciones;
        this.vigenciaDias = vigenciaDias;
        tocar();
    }

    void presentar() {
        this.estado = Estado.PRESENTADO;
        this.presentadoEn = Instant.now();
        tocar();
    }

    void volverABorrador() {
        this.estado = Estado.BORRADOR;
        this.presentadoEn = null;
        tocar();
    }

    void aceptar() {
        this.estado = Estado.ACEPTADO;
        tocar();
    }

    void cerrar(Estado estado, String motivo) {
        this.estado = estado;
        this.motivoCierre = motivo;
        this.cerradoEn = Instant.now();
        tocar();
    }

    void tocar() {
        this.actualizadoEn = Instant.now();
    }

    UUID getId() {
        return id;
    }

    UUID getPacienteId() {
        return pacienteId;
    }

    UUID getOdontologoId() {
        return odontologoId;
    }

    Estado getEstado() {
        return estado;
    }

    String getObservaciones() {
        return observaciones;
    }

    int getVigenciaDias() {
        return vigenciaDias;
    }

    Instant getPresentadoEn() {
        return presentadoEn;
    }

    Instant getCerradoEn() {
        return cerradoEn;
    }

    String getMotivoCierre() {
        return motivoCierre;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
