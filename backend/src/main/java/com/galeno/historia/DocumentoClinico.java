package com.galeno.historia;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** Archivo de la historia (PDF o imagen). Inmutable: el original se conserva tal como se subió. */
@Entity
@Immutable
@Table(name = "documentos_clinicos")
public class DocumentoClinico {

    public enum Tipo {
        RESULTADO_LABORATORIO,
        IMAGEN_DIAGNOSTICA,
        HISTORIA_EXTERNA,
        CONSENTIMIENTO,
        REMISION,
        OTRO
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "atencion_id")
    private UUID atencionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Tipo tipo;

    @Column(length = 300)
    private String descripcion;

    @Column(name = "nombre_archivo", nullable = false)
    private String nombreArchivo;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 400)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Atencion.Origen origen;

    @Column(name = "subido_por", nullable = false)
    private UUID subidoPor;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected DocumentoClinico() {
    }

    DocumentoClinico(UUID pacienteId, UUID atencionId, Tipo tipo, String descripcion, String nombreArchivo,
                     String contentType, long tamanoBytes, String sha256, String storageKey, UUID subidoPor) {
        this.pacienteId = pacienteId;
        this.atencionId = atencionId;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.nombreArchivo = nombreArchivo;
        this.contentType = contentType;
        this.tamanoBytes = tamanoBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.origen = Atencion.Origen.GALENO;
        this.subidoPor = subidoPor;
        this.creadoEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPacienteId() {
        return pacienteId;
    }

    public UUID getAtencionId() {
        return atencionId;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public String getContentType() {
        return contentType;
    }

    public long getTamanoBytes() {
        return tamanoBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
