package com.galeno.identidad;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import com.galeno.shared.security.Permiso;

/** Lo que una persona hace en un consultorio: sus roles y si está activa. */
@Entity
@Table(name = "membresias")
public class Membresia {

    public enum Estado {
        ACTIVA,
        SUSPENDIDA
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "membresia_roles", joinColumns = @JoinColumn(name = "membresia_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", length = 20)
    private Set<Rol> roles = EnumSet.noneOf(Rol.class);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected Membresia() {
    }

    /** Se crea dentro del contexto del consultorio: Hibernate asigna el {@code tenant_id}. */
    public Membresia(UUID usuarioId, Collection<Rol> roles) {
        this.usuarioId = usuarioId;
        this.roles = EnumSet.copyOf(roles);
        this.estado = Estado.ACTIVA;
        this.creadoEn = Instant.now();
    }

    void cambiarRoles(Collection<Rol> nuevos) {
        roles.clear();
        roles.addAll(nuevos);
    }

    void cambiarEstado(Estado nuevo) {
        this.estado = nuevo;
    }

    public boolean activa() {
        return estado == Estado.ACTIVA;
    }

    public boolean tiene(Rol rol) {
        return roles.contains(rol);
    }

    public Set<Permiso> permisos() {
        return Rol.permisosDe(roles);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Set<Rol> getRoles() {
        return roles;
    }

    public Estado getEstado() {
        return estado;
    }
}
