package com.galeno.suscripciones;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Plan que ofrece la plataforma (mensual o anual). El precio lo define el operador. */
@Entity
@Table(name = "planes_suscripcion")
class PlanSuscripcion {

    @Id
    @Column(length = 20)
    private String codigo;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(nullable = false)
    private int meses;

    @Column(nullable = false)
    private long precio;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected PlanSuscripcion() {
    }

    void actualizar(String nombre, long precio, boolean activo) {
        this.nombre = nombre;
        this.precio = precio;
        this.activo = activo;
        this.actualizadoEn = Instant.now();
    }

    String getCodigo() {
        return codigo;
    }

    String getNombre() {
        return nombre;
    }

    int getMeses() {
        return meses;
    }

    long getPrecio() {
        return precio;
    }

    boolean isActivo() {
        return activo;
    }
}
