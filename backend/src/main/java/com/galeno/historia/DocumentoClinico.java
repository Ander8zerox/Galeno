package com.galeno.historia;

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

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Archivo de la historia (PDF o imagen). El original se conserva tal como se subió: sus columnas no se actualizan.
 * Solo cambia al anularlo (y al desligarlo del borrador eliminado, si estaba anulado).
 */
@Entity
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

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    @Column(name = "atencion_id")
    private UUID atencionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private Tipo tipo;

    @Column(updatable = false, length = 300)
    private String descripcion;

    @Column(name = "nombre_archivo", nullable = false, updatable = false)
    private String nombreArchivo;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "tamano_bytes", nullable = false, updatable = false)
    private long tamanoBytes;

    @Column(nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 400)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Atencion.Origen origen;

    @Column(name = "subido_por", nullable = false, updatable = false)
    private UUID subidoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    @Column(name = "anulado_por")
    private UUID anuladoPor;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    protected DocumentoClinico() {
    }

    /** Subido por error: queda en la historia marcado como anulado, con el motivo. El archivo no se borra. */
    void anular(UUID por, String motivo) {
        if (anuladoEn != null) {
            throw ApiProblem.conflict("El documento ya está anulado");
        }
        if (motivo == null || motivo.isBlank()) {
            throw ApiProblem.badRequest("Indique por qué anula el documento");
        }
        this.anuladoEn = Instant.now();
        this.anuladoPor = por;
        this.motivoAnulacion = motivo.strip();
    }

    /** Al eliminar el borrador al que estaba adjunto (solo si está anulado): sigue en la historia del paciente. */
    void desligarDeAtencion() {
        if (anuladoEn == null) {
            throw new IllegalStateException("Solo se desliga un documento anulado");
        }
        this.atencionId = null;
    }

    public boolean anulado() {
        return anuladoEn != null;
    }

    public Instant getAnuladoEn() {
        return anuladoEn;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
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
