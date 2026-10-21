package com.galeno.identidad;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** El tenant: cada consultorio cliente ve solo sus propios datos. */
@Entity
@Table(name = "consultorios")
public class Consultorio {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 30)
    private String nit;

    @Column(name = "zona_horaria", nullable = false, length = 40)
    private String zonaHoraria;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    /** Versión de los términos y de la política de datos que aceptó quien registró el consultorio (Ley 1581). */
    @Column(name = "terminos_version", length = 20)
    private String terminosVersion;

    @Column(name = "terminos_aceptados_en")
    private Instant terminosAceptadosEn;

    @Column(name = "terminos_aceptados_por")
    private UUID terminosAceptadosPor;

    protected Consultorio() {
    }

    public Consultorio(String nombre, String nit) {
        this.nombre = nombre;
        this.nit = nit;
        this.zonaHoraria = "America/Bogota";
        this.creadoEn = Instant.now();
    }

    void aceptarTerminos(String version, Instant aceptadosEn, UUID usuarioId) {
        this.terminosVersion = version;
        this.terminosAceptadosEn = aceptadosEn;
        this.terminosAceptadosPor = usuarioId;
    }

    public UUID getId() {
        return id;
    }

    public String getNit() {
        return nit;
    }

    public String getZonaHoraria() {
        return zonaHoraria;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public String getNombre() {
        return nombre;
    }
}
