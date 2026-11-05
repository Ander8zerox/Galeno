package com.galeno.rips;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fila de una tabla de referencia de SISPRO (V22, catálogo global, solo lectura). */
@Entity
@Immutable
@IdClass(Referencia.Clave.class)
@Table(name = "rips_referencias")
class Referencia {

    @Id
    @Column(length = 30)
    private String tabla;

    @Id
    @Column(length = 20)
    private String codigo;

    @Column(nullable = false, length = 300)
    private String nombre;

    @Column(nullable = false)
    private boolean habilitado;

    /** Las columnas "Extra" de SISPRO con dato, en JSON (p. ej. el departamento de un municipio). */
    @JdbcTypeCode(SqlTypes.JSON)
    private String extra;

    protected Referencia() {
    }

    String getTabla() {
        return tabla;
    }

    String getCodigo() {
        return codigo;
    }

    String getNombre() {
        return nombre;
    }

    boolean isHabilitado() {
        return habilitado;
    }

    String getExtra() {
        return extra;
    }

    record Clave(String tabla, String codigo) implements Serializable {

        Clave() {
            this(null, null);
        }
    }
}
