package com.galeno.identidad;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/**
 * Identidad global de una persona: una sola cuenta aunque trabaje en varios consultorios.
 * Lo que hace en cada consultorio está en {@link Membresia}.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected Usuario() {
    }

    public Usuario(String email, String passwordHash, String nombre) {
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.activo = true;
        this.creadoEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActivo() {
        return activo;
    }
}
