package com.galeno.rips;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Las atenciones firmadas de un pagador en un período que van en una factura (o en un RIPS sin factura). En borrador se
 * revisa y se ajusta; al generarse guarda el JSON tal cual y bloquea sus atenciones; anulado, las libera.
 */
@Entity
@Table(name = "rips_lotes")
class Lote {

    enum Estado { BORRADOR, GENERADO, ANULADO }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "pagador_id", nullable = false, updatable = false)
    private UUID pagadorId;

    /** RIPS sin factura (tipo de nota RS): profesional independiente no obligado a facturar. */
    @Column(name = "sin_factura", nullable = false, updatable = false)
    private boolean sinFactura;

    @Column(nullable = false, updatable = false)
    private LocalDate desde;

    @Column(nullable = false, updatable = false)
    private LocalDate hasta;

    @Column(name = "numero_factura", length = 20)
    private String numeroFactura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "xml_nit", length = 20)
    private String xmlNit;

    @Column(name = "xml_numero", length = 20)
    private String xmlNumero;

    @Column(name = "xml_desde")
    private LocalDate xmlDesde;

    @Column(name = "xml_hasta")
    private LocalDate xmlHasta;

    @Column(name = "xml_pagos_moderadores", precision = 14, scale = 2)
    private BigDecimal xmlPagosModeradores;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(length = 16_777_215)
    private String json;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private UUID creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "generado_por")
    private UUID generadoPor;

    @Column(name = "generado_en")
    private Instant generadoEn;

    @Column(name = "anulado_por")
    private UUID anuladoPor;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rips_lote_atenciones", joinColumns = @JoinColumn(name = "lote_id"))
    private List<LoteAtencion> atenciones = new ArrayList<>();

    protected Lote() {
    }

    Lote(UUID pagadorId, boolean sinFactura, LocalDate desde, LocalDate hasta, List<UUID> atencionIds, UUID usuarioId) {
        this.pagadorId = pagadorId;
        this.sinFactura = sinFactura;
        this.desde = desde;
        this.hasta = hasta;
        this.estado = Estado.BORRADOR;
        atencionIds.forEach(a -> atenciones.add(new LoteAtencion(a)));
        this.creadoPor = usuarioId;
        this.creadoEn = Instant.now();
        this.actualizadoEn = creadoEn;
    }

    void quitar(UUID atencionId) {
        exigirBorrador();
        if (!atenciones.removeIf(a -> a.getAtencionId().equals(atencionId))) {
            throw ApiProblem.notFound("La atención no está en el lote");
        }
        tocar();
    }

    void ajustar(UUID atencionId, ArmadorRips.Ajustes ajustes) {
        exigirBorrador();
        atencion(atencionId).orElseThrow(() -> ApiProblem.notFound("La atención no está en el lote")).ajustar(ajustes);
        tocar();
    }

    void cambiarFactura(String numero) {
        exigirBorrador();
        if (sinFactura && numero != null) {
            throw ApiProblem.badRequest("Un RIPS sin factura no lleva número de factura");
        }
        this.numeroFactura = numero;
        tocar();
    }

    void cargarXml(ArmadorRips.DatosXml x) {
        exigirBorrador();
        if (sinFactura) {
            throw ApiProblem.badRequest("Un RIPS sin factura no lleva el XML de la factura");
        }
        this.xmlNit = x == null ? null : x.nit();
        this.xmlNumero = x == null ? null : x.numero();
        this.xmlDesde = x == null ? null : x.desde();
        this.xmlHasta = x == null ? null : x.hasta();
        this.xmlPagosModeradores = x == null ? null : x.pagosModeradores();
        if (x != null && numeroFactura == null && x.numero() != null && x.numero().length() <= 20) {
            this.numeroFactura = x.numero();
        }
        tocar();
    }

    void generar(String json, UUID usuarioId) {
        exigirBorrador();
        this.json = json;
        this.estado = Estado.GENERADO;
        this.generadoPor = usuarioId;
        this.generadoEn = Instant.now();
        this.actualizadoEn = generadoEn;
    }

    /** Libera las atenciones para otro lote; el JSON generado se conserva como constancia. */
    void anular(String motivo, UUID usuarioId) {
        if (estado == Estado.ANULADO) {
            throw ApiProblem.conflict("El lote ya está anulado");
        }
        this.estado = Estado.ANULADO;
        this.motivoAnulacion = motivo;
        this.anuladoPor = usuarioId;
        this.anuladoEn = Instant.now();
        this.actualizadoEn = anuladoEn;
        atenciones.forEach(LoteAtencion::liberar);
    }

    void exigirBorrador() {
        if (estado != Estado.BORRADOR) {
            throw ApiProblem.conflict(estado == Estado.GENERADO
                    ? "El lote ya está generado: para corregirlo, anúlelo y genere otro"
                    : "El lote está anulado");
        }
    }

    Optional<LoteAtencion> atencion(UUID atencionId) {
        return atenciones.stream().filter(a -> a.getAtencionId().equals(atencionId)).findFirst();
    }

    ArmadorRips.DatosXml xml() {
        return xmlNit == null && xmlNumero == null && xmlDesde == null && xmlPagosModeradores == null ? null
                : new ArmadorRips.DatosXml(xmlNit, xmlNumero, xmlDesde, xmlHasta, xmlPagosModeradores);
    }

    private void tocar() {
        this.actualizadoEn = Instant.now();
    }

    UUID getId() {
        return id;
    }

    UUID getPagadorId() {
        return pagadorId;
    }

    boolean isSinFactura() {
        return sinFactura;
    }

    LocalDate getDesde() {
        return desde;
    }

    LocalDate getHasta() {
        return hasta;
    }

    String getNumeroFactura() {
        return numeroFactura;
    }

    Estado getEstado() {
        return estado;
    }

    String getJson() {
        return json;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    Instant getGeneradoEn() {
        return generadoEn;
    }

    Instant getAnuladoEn() {
        return anuladoEn;
    }

    String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    List<LoteAtencion> getAtenciones() {
        return atenciones;
    }
}
