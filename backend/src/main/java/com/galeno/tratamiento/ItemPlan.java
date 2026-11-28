package com.galeno.tratamiento;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Un procedimiento planeado. Mientras {@code aceptacion} es nulo se edita o se quita; aceptado, solo cambia de estado
 * (realizado o cancelado con motivo): el presupuesto firmado no se reescribe.
 */
@Entity
@Table(name = "planes_tratamiento_items")
class ItemPlan {

    enum Estado { PENDIENTE, REALIZADO, CANCELADO }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(nullable = false)
    private int fase;

    @Column(name = "fase_nombre", length = 80)
    private String faseNombre;

    @Column(nullable = false)
    private int orden;

    @Column(name = "codigo_cups", nullable = false, length = 10)
    private String codigoCups;

    @Column(nullable = false, length = 400)
    private String descripcion;

    @Column(length = 2)
    private String diente;

    @Column(length = 5)
    private String superficies;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    /** Descuento en pesos sobre el subtotal del ítem. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Estado estado;

    private Integer aceptacion;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    @Column(name = "atencion_id")
    private UUID atencionId;

    @Column(name = "realizado_en")
    private Instant realizadoEn;

    protected ItemPlan() {
    }

    ItemPlan(UUID planId, Datos d) {
        this.planId = planId;
        this.estado = Estado.PENDIENTE;
        poner(d);
    }

    /** Datos ya validados y normalizados por el servicio. */
    record Datos(int fase, String faseNombre, int orden, String codigoCups, String descripcion, String diente,
                 String superficies, int cantidad, BigDecimal valorUnitario, BigDecimal descuento) {
    }

    void poner(Datos d) {
        this.fase = d.fase();
        this.faseNombre = d.faseNombre();
        this.orden = d.orden();
        this.codigoCups = d.codigoCups();
        this.descripcion = d.descripcion();
        this.diente = d.diente();
        this.superficies = d.superficies();
        this.cantidad = d.cantidad();
        this.valorUnitario = d.valorUnitario().setScale(2, RoundingMode.HALF_UP);
        this.descuento = d.descuento().setScale(2, RoundingMode.HALF_UP);
    }

    void aceptar(int numero) {
        this.aceptacion = numero;
    }

    void cancelar(String motivo) {
        this.estado = Estado.CANCELADO;
        this.motivoCancelacion = motivo;
    }

    boolean aceptado() {
        return aceptacion != null;
    }

    BigDecimal subtotal() {
        return valorUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    BigDecimal total() {
        return subtotal().subtract(descuento);
    }

    UUID getId() {
        return id;
    }

    UUID getPlanId() {
        return planId;
    }

    int getFase() {
        return fase;
    }

    String getFaseNombre() {
        return faseNombre;
    }

    int getOrden() {
        return orden;
    }

    String getCodigoCups() {
        return codigoCups;
    }

    String getDescripcion() {
        return descripcion;
    }

    String getDiente() {
        return diente;
    }

    String getSuperficies() {
        return superficies;
    }

    int getCantidad() {
        return cantidad;
    }

    BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    BigDecimal getDescuento() {
        return descuento;
    }

    Estado getEstado() {
        return estado;
    }

    Integer getAceptacion() {
        return aceptacion;
    }

    String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    UUID getAtencionId() {
        return atencionId;
    }

    Instant getRealizadoEn() {
        return realizadoEn;
    }
}
