package com.galeno.rips;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/**
 * Quien paga las atenciones: una EPS, una prepagada, una ARL, una aseguradora SOAT o el propio paciente (particular).
 * Guarda lo que la factura de salud necesita (Documento Técnico 2) para entregárselo al facturador del consultorio.
 */
@Entity
@Table(name = "rips_pagadores")
class Pagador {

    enum Tipo { EPS, PREPAGADA, ARL, SOAT, ENTIDAD_TERRITORIAL, REGIMEN_ESPECIAL, OTRO, PARTICULAR }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    /** Código de la entidad en la tabla CodigoEAPByNit de SISPRO (p. ej. EPS037); nulo si no está en ella. */
    @Column(name = "codigo_entidad", length = 20)
    private String codigoEntidad;

    @Column(length = 20)
    private String nit;

    @Column(length = 2)
    private String cobertura;

    @Column(name = "modalidad_pago", length = 2)
    private String modalidadPago;

    @Column(length = 64)
    private String cucon;

    @Column(name = "numero_poliza", length = 30)
    private String numeroPoliza;

    @Column(name = "factura_sin_contrato", length = 2)
    private String facturaSinContrato;

    @Column(name = "exige_autorizacion", nullable = false)
    private boolean exigeAutorizacion;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected Pagador() {
    }

    Pagador(Datos datos) {
        actualizar(datos);
    }

    void actualizar(Datos d) {
        this.nombre = d.nombre();
        this.tipo = d.tipo();
        this.codigoEntidad = d.codigoEntidad();
        this.nit = d.nit();
        this.cobertura = d.cobertura();
        this.modalidadPago = d.modalidadPago();
        this.cucon = d.cucon();
        this.numeroPoliza = d.numeroPoliza();
        this.facturaSinContrato = d.facturaSinContrato();
        this.exigeAutorizacion = d.exigeAutorizacion();
        this.activo = d.activo();
        this.actualizadoEn = Instant.now();
    }

    Datos datos() {
        return new Datos(nombre, tipo, codigoEntidad, nit, cobertura, modalidadPago, cucon, numeroPoliza,
                facturaSinContrato, exigeAutorizacion, activo);
    }

    UUID getId() {
        return id;
    }

    String getNombre() {
        return nombre;
    }

    Tipo getTipo() {
        return tipo;
    }

    boolean isExigeAutorizacion() {
        return exigeAutorizacion;
    }

    boolean isActivo() {
        return activo;
    }

    record Datos(String nombre, Tipo tipo, String codigoEntidad, String nit, String cobertura, String modalidadPago,
                 String cucon, String numeroPoliza, String facturaSinContrato, boolean exigeAutorizacion,
                 boolean activo) {
    }
}
