package com.galeno.plataforma;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cuenta de Galeno con acceso a la consola de la plataforma (ver {@link OperadoresDesdeConfiguracion}). */
@Entity
@Table(name = "operadores_plataforma")
class OperadorPlataforma {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected OperadorPlataforma() {
    }

    OperadorPlataforma(UUID usuarioId) {
        this.usuarioId = usuarioId;
        this.creadoEn = Instant.now();
    }
}
