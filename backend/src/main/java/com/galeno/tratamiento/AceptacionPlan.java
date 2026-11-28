package com.galeno.tratamiento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Aceptación del paciente: la primera (número 1) o un adicional (2, 3…). No cambia nunca. */
@Entity
@Immutable
@Table(name = "planes_tratamiento_aceptaciones")
class AceptacionPlan {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(nullable = false)
    private int numero;

    @Column(name = "aceptado_en", nullable = false)
    private Instant aceptadoEn;

    @Column(name = "registrado_por", nullable = false)
    private UUID registradoPor;

    /** {@code data:image/png;base64,…}; nula si fue constancia verbal. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String firma;

    @Column(name = "nombre_firmante", length = 120)
    private String nombreFirmante;

    @Column(name = "constancia_verbal", nullable = false)
    private boolean constanciaVerbal;

    /** Total de lo aceptado en esta aceptación. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    protected AceptacionPlan() {
    }

    AceptacionPlan(UUID planId, int numero, UUID registradoPor, String firma, String nombreFirmante, BigDecimal total) {
        this.planId = planId;
        this.numero = numero;
        this.registradoPor = registradoPor;
        this.firma = firma;
        this.nombreFirmante = nombreFirmante;
        this.constanciaVerbal = firma == null;
        this.total = total;
        this.aceptadoEn = Instant.now();
    }

    UUID getId() {
        return id;
    }

    UUID getPlanId() {
        return planId;
    }

    int getNumero() {
        return numero;
    }

    Instant getAceptadoEn() {
        return aceptadoEn;
    }

    UUID getRegistradoPor() {
        return registradoPor;
    }

    String getFirma() {
        return firma;
    }

    String getNombreFirmante() {
        return nombreFirmante;
    }

    boolean isConstanciaVerbal() {
        return constanciaVerbal;
    }

    BigDecimal getTotal() {
        return total;
    }
}
