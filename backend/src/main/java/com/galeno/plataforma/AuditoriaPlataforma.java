package com.galeno.plataforma;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** Qué hizo cada operador en la consola (aprobar pagos, suspender, regalar días, cambiar precios). */
@Entity
@Table(name = "auditoria_plataforma")
class AuditoriaPlataforma {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "operador_id", nullable = false, updatable = false)
    private UUID operadorId;

    @Column(nullable = false, updatable = false, length = 40)
    private String accion;

    @Column(name = "consultorio_id", updatable = false)
    private UUID consultorioId;

    @Column(updatable = false, length = 500)
    private String detalle;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected AuditoriaPlataforma() {
    }

    AuditoriaPlataforma(UUID operadorId, String accion, UUID consultorioId, String detalle) {
        this.operadorId = operadorId;
        this.accion = accion;
        this.consultorioId = consultorioId;
        this.detalle = detalle != null && detalle.length() > 500 ? detalle.substring(0, 500) : detalle;
        this.creadoEn = Instant.now();
    }

    UUID getOperadorId() {
        return operadorId;
    }

    String getAccion() {
        return accion;
    }

    UUID getConsultorioId() {
        return consultorioId;
    }

    String getDetalle() {
        return detalle;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }
}
