package com.galeno.historia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Catálogo CIE-10 de MinSalud (global, no pertenece a ningún consultorio). */
@Entity
@Immutable
@Table(name = "cie10")
public class Cie10 {

    @Id
    @Column(length = 10)
    private String codigo;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(nullable = false)
    private boolean habilitado;

    /** A = ambos, F = femenino, M = masculino. */
    @Column(nullable = false, length = 1)
    private String sexo;

    @Column(name = "edad_min")
    private Integer edadMin;

    @Column(name = "edad_max")
    private Integer edadMax;

    @Column(length = 200)
    private String capitulo;

    protected Cie10() {
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getSexo() {
        return sexo;
    }

    public Integer getEdadMin() {
        return edadMin;
    }

    public Integer getEdadMax() {
        return edadMax;
    }

    public String getCapitulo() {
        return capitulo;
    }
}
