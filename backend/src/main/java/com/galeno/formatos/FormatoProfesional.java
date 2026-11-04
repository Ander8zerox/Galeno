package com.galeno.formatos;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** El profesional quiere este formato en todas sus atenciones nuevas. */
@Entity
@Table(name = "formatos_profesional")
class FormatoProfesional {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "formato_id", nullable = false, updatable = false)
    private UUID formatoId;

    protected FormatoProfesional() {
    }

    FormatoProfesional(UUID usuarioId, UUID formatoId) {
        this.usuarioId = usuarioId;
        this.formatoId = formatoId;
    }

    UUID getFormatoId() {
        return formatoId;
    }
}
