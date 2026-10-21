package com.galeno.importacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Procedimiento de la Clasificación Única de Procedimientos en Salud (catálogo global, solo lectura). */
@Entity
@Immutable
@Table(name = "cups")
class Cups {

    @Id
    @Column(length = 10)
    private String codigo;

    @Column(nullable = false, length = 400)
    private String descripcion;

    protected Cups() {
    }

    String getCodigo() {
        return codigo;
    }

    String getDescripcion() {
        return descripcion;
    }
}
