package com.galeno.rips;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Nota crédito, débito o de ajuste de un lote generado. En borrador se marcan los servicios y se revisa; al generarse
 * guarda el JSON tal cual. La NC al 100% nace generada y sin JSON: la factura se devolvió completa.
 */
@Entity
@Table(name = "rips_notas")
class Nota {

    enum Estado { BORRADOR, GENERADA, ANULADA }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "lote_id", nullable = false, updatable = false)
    private UUID loteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 2)
    private ArmadorNota.Tipo tipo;

    @Column(nullable = false, updatable = false)
    private boolean total;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column(nullable = false, length = 300)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    /** Los servicios afectados, en JSON (lista de {@link ArmadorNota.ItemNota}). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String servicios;

    @Column(name = "xml_numero", length = 20)
    private String xmlNumero;

    @Column(name = "xml_factura", length = 20)
    private String xmlFactura;

    @Column(name = "xml_valor", precision = 14, scale = 2)
    private BigDecimal xmlValor;

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

    protected Nota() {
    }

    /** @param total NC al 100%: queda generada de una vez, sin servicios */
    Nota(UUID loteId, ArmadorNota.Tipo tipo, boolean total, String numero, String motivo, UUID usuarioId) {
        this.loteId = loteId;
        this.tipo = tipo;
        this.total = total;
        this.numero = numero;
        this.motivo = motivo;
        this.servicios = "[]";
        this.creadoPor = usuarioId;
        this.creadoEn = Instant.now();
        this.actualizadoEn = creadoEn;
        this.estado = Estado.BORRADOR;
        if (total) {
            this.estado = Estado.GENERADA;
            this.generadoPor = usuarioId;
            this.generadoEn = creadoEn;
        }
    }

    void actualizar(String numero, String motivo, String servicios) {
        exigirBorrador();
        this.numero = numero;
        this.motivo = motivo;
        this.servicios = servicios;
        this.actualizadoEn = Instant.now();
    }

    void cargarXml(ArmadorNota.XmlNota x) {
        exigirBorrador();
        if (tipo == ArmadorNota.Tipo.NA) {
            throw ApiProblem.badRequest("La nota de ajuste no lleva documento de la DIAN");
        }
        this.xmlNumero = x == null ? null : recortar(x.numero());
        this.xmlFactura = x == null ? null : recortar(x.factura());
        this.xmlValor = x == null ? null : x.valor();
        this.actualizadoEn = Instant.now();
    }

    private static String recortar(String s) {
        return s == null ? null : s.length() > 20 ? s.substring(0, 20) : s;
    }

    void generar(String json, UUID usuarioId) {
        exigirBorrador();
        this.json = json;
        this.estado = Estado.GENERADA;
        this.generadoPor = usuarioId;
        this.generadoEn = Instant.now();
        this.actualizadoEn = generadoEn;
    }

    void anular(String motivo, UUID usuarioId) {
        if (estado != Estado.GENERADA) {
            throw ApiProblem.conflict(estado == Estado.BORRADOR ? "Una nota en borrador no se anula: elimínela"
                    : "La nota ya está anulada");
        }
        this.estado = Estado.ANULADA;
        this.motivoAnulacion = motivo;
        this.anuladoPor = usuarioId;
        this.anuladoEn = Instant.now();
        this.actualizadoEn = anuladoEn;
    }

    void exigirBorrador() {
        if (estado != Estado.BORRADOR) {
            throw ApiProblem.conflict(estado == Estado.GENERADA
                    ? "La nota ya está generada: para corregirla, anúlela y haga otra"
                    : "La nota está anulada");
        }
    }

    ArmadorNota.XmlNota xml() {
        return xmlNumero == null && xmlFactura == null && xmlValor == null ? null
                : new ArmadorNota.XmlNota(xmlNumero, xmlFactura, xmlValor);
    }

    UUID getId() {
        return id;
    }

    UUID getLoteId() {
        return loteId;
    }

    ArmadorNota.Tipo getTipo() {
        return tipo;
    }

    boolean isTotal() {
        return total;
    }

    String getNumero() {
        return numero;
    }

    String getMotivo() {
        return motivo;
    }

    Estado getEstado() {
        return estado;
    }

    String getServicios() {
        return servicios;
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
}
