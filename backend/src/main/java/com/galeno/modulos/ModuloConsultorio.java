package com.galeno.modulos;

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

/** El módulo está activo en el consultorio. */
@Entity
@Table(name = "modulos_consultorio")
class ModuloConsultorio {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private Modulo modulo;

    @Column(name = "activado_en", nullable = false, updatable = false)
    private Instant activadoEn;

    protected ModuloConsultorio() {
    }

    ModuloConsultorio(Modulo modulo) {
        this.modulo = modulo;
        this.activadoEn = Instant.now();
    }

    Modulo getModulo() {
        return modulo;
    }
}
