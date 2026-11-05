package com.galeno.rips;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** Documento de quien atiende: va en cada consulta y procedimiento del RIPS. */
@Entity
@Table(name = "rips_profesionales")
class RipsProfesional {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "tipo_documento", nullable = false, length = 2)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected RipsProfesional() {
    }

    RipsProfesional(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    void actualizar(String tipoDocumento, String numeroDocumento) {
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.actualizadoEn = Instant.now();
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    String getTipoDocumento() {
        return tipoDocumento;
    }

    String getNumeroDocumento() {
        return numeroDocumento;
    }
}
