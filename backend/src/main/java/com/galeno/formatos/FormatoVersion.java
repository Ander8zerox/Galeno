package com.galeno.formatos;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** Versión publicada de un formato: no cambia nunca (para corregirla se publica la siguiente). */
@Entity
@Immutable
@Table(name = "formato_versiones")
class FormatoVersion {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "formato_id", nullable = false)
    private UUID formatoId;

    @Column(nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String definicion;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "publicada_en", nullable = false)
    private Instant publicadaEn;

    protected FormatoVersion() {
    }

    FormatoVersion(UUID formatoId, int version, String definicion, String hash) {
        this.formatoId = formatoId;
        this.version = version;
        this.definicion = definicion;
        this.hash = hash;
        this.publicadaEn = Instant.now();
    }

    UUID getId() {
        return id;
    }

    UUID getFormatoId() {
        return formatoId;
    }

    int getVersion() {
        return version;
    }

    String getDefinicion() {
        return definicion;
    }

    String getHash() {
        return hash;
    }
}
