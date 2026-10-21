package com.galeno.historia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Catálogo CIE-10 (global, no pertenece a ningún consultorio). */
@Entity
@Immutable
@Table(name = "cie10")
public class Cie10 {

    @Id
    @Column(length = 10)
    private String codigo;

    @Column(nullable = false, length = 300)
    private String descripcion;

    protected Cie10() {
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
