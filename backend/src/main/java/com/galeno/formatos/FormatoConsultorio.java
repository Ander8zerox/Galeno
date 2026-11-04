package com.galeno.formatos;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** El formato está activo en el consultorio: sus médicos lo pueden agregar a las atenciones. */
@Entity
@Table(name = "formatos_consultorio")
class FormatoConsultorio {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "formato_id", nullable = false, updatable = false)
    private UUID formatoId;

    @Column(name = "activado_en", nullable = false, updatable = false)
    private Instant activadoEn;

    protected FormatoConsultorio() {
    }

    FormatoConsultorio(UUID formatoId) {
        this.formatoId = formatoId;
        this.activadoEn = Instant.now();
    }

    UUID getFormatoId() {
        return formatoId;
    }
}
