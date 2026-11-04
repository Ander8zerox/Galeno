package com.galeno.formatos;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Un formato diligenciado dentro de una atención. Apunta a la versión exacta con la que se llenó. Sigue las reglas de
 * su atención: la de borrador e inmutabilidad la aplica {@code HistoriaService} antes de llamar a este módulo.
 */
@Entity
@Table(name = "atencion_formatos")
class AtencionFormato {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "atencion_id", nullable = false, updatable = false)
    private UUID atencionId;

    @Column(name = "formato_version_id", nullable = false, updatable = false)
    private UUID formatoVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String respuestas;

    @Column(nullable = false)
    private int orden;

    protected AtencionFormato() {
    }

    AtencionFormato(UUID atencionId, UUID formatoVersionId, int orden) {
        this.atencionId = atencionId;
        this.formatoVersionId = formatoVersionId;
        this.respuestas = "{}";
        this.orden = orden;
    }

    void responder(String respuestas) {
        this.respuestas = respuestas;
    }

    UUID getId() {
        return id;
    }

    UUID getAtencionId() {
        return atencionId;
    }

    UUID getFormatoVersionId() {
        return formatoVersionId;
    }

    String getRespuestas() {
        return respuestas;
    }

    int getOrden() {
        return orden;
    }
}
