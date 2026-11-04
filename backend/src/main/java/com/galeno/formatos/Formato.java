package com.galeno.formatos;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** Formato del catálogo global de Galenum (no pertenece a ningún consultorio). */
@Entity
@Table(name = "formatos")
class Formato {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 60)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String especialidad;

    @Column(length = 500)
    private String descripcion;

    protected Formato() {
    }

    Formato(String codigo) {
        this.codigo = codigo;
    }

    /** El nombre y la descripción no son parte de la versión: se pueden corregir sin publicar otra. */
    void describir(String nombre, String especialidad, String descripcion) {
        this.nombre = nombre;
        this.especialidad = especialidad;
        this.descripcion = descripcion;
    }

    UUID getId() {
        return id;
    }

    String getCodigo() {
        return codigo;
    }

    String getNombre() {
        return nombre;
    }

    String getEspecialidad() {
        return especialidad;
    }

    String getDescripcion() {
        return descripcion;
    }
}
