package com.galeno.importacion;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** Último mapeo de columnas usado para un listado con estos encabezados (ver V9). */
@Entity
@Table(name = "plantillas_importacion")
class PlantillaImportacion {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false, length = 2000)
    private String firma;

    @Column(name = "firma_hash", nullable = false, updatable = false, length = 64)
    private String firmaHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String mapeo;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    protected PlantillaImportacion() {
    }

    PlantillaImportacion(String firma, String firmaHash, String mapeo) {
        this.firma = firma.length() > 2000 ? firma.substring(0, 2000) : firma;
        this.firmaHash = firmaHash;
        actualizar(mapeo);
    }

    void actualizar(String mapeo) {
        this.mapeo = mapeo;
        this.actualizadaEn = Instant.now();
    }

    String getMapeo() {
        return mapeo;
    }
}
