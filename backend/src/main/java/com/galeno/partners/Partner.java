package com.galeno.partners;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/** Persona o empresa que vende Galeno a consultorios a cambio de una comisión recurrente (ver V13). */
@Entity
@Table(name = "partners")
class Partner {

    enum Estado {
        /** Se inscribió; la plataforma aún no lo aprueba. */
        PENDIENTE,
        ACTIVO,
        /** No genera comisiones nuevas ni su código sirve para registrar consultorios. */
        SUSPENDIDO,
        RECHAZADO
    }

    enum TipoPersona {
        NATURAL,
        JURIDICA
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_persona", nullable = false, length = 10)
    private TipoPersona tipoPersona;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(name = "tipo_documento", nullable = false, length = 5)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 30)
    private String numeroDocumento;

    @Column(nullable = false, length = 30)
    private String telefono;

    @Column(length = 80)
    private String banco;

    @Column(name = "tipo_cuenta", length = 20)
    private String tipoCuenta;

    @Column(name = "numero_cuenta", length = 40)
    private String numeroCuenta;

    @Column(length = 20)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(length = 300)
    private String motivo;

    @Column(name = "terminos_version", nullable = false, updatable = false, length = 20)
    private String terminosVersion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "aprobado_en")
    private Instant aprobadoEn;

    protected Partner() {
    }

    Partner(UUID usuarioId, DatosPartner d, String terminosVersion) {
        this.usuarioId = usuarioId;
        this.tipoPersona = d.tipoPersona();
        this.nombre = d.nombre();
        this.tipoDocumento = d.tipoDocumento();
        this.numeroDocumento = d.numeroDocumento();
        this.telefono = d.telefono();
        this.banco = d.banco();
        this.tipoCuenta = d.tipoCuenta();
        this.numeroCuenta = d.numeroCuenta();
        this.estado = Estado.PENDIENTE;
        this.terminosVersion = terminosVersion;
        this.creadoEn = Instant.now();
    }

    /** Al aprobar recibe su código de referido (y lo conserva si se suspende y se reactiva). */
    void aprobar(String codigoNuevo) {
        if (estado != Estado.PENDIENTE && estado != Estado.SUSPENDIDO) {
            throw ApiProblem.conflict("No se puede aprobar un partner " + estado);
        }
        if (codigo == null) {
            codigo = codigoNuevo;
            aprobadoEn = Instant.now();
        }
        estado = Estado.ACTIVO;
        motivo = null;
    }

    void rechazar(String motivo) {
        if (estado != Estado.PENDIENTE) {
            throw ApiProblem.conflict("Solo se rechaza una inscripción pendiente");
        }
        this.estado = Estado.RECHAZADO;
        this.motivo = motivo;
    }

    void suspender(String motivo) {
        if (estado != Estado.ACTIVO) {
            throw ApiProblem.conflict("Solo se suspende un partner activo");
        }
        this.estado = Estado.SUSPENDIDO;
        this.motivo = motivo;
    }

    void actualizarPago(String banco, String tipoCuenta, String numeroCuenta, String telefono) {
        this.banco = banco;
        this.tipoCuenta = tipoCuenta;
        this.numeroCuenta = numeroCuenta;
        this.telefono = telefono;
    }

    boolean activo() {
        return estado == Estado.ACTIVO;
    }

    UUID getId() {
        return id;
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    TipoPersona getTipoPersona() {
        return tipoPersona;
    }

    String getNombre() {
        return nombre;
    }

    String getTipoDocumento() {
        return tipoDocumento;
    }

    String getNumeroDocumento() {
        return numeroDocumento;
    }

    String getTelefono() {
        return telefono;
    }

    String getBanco() {
        return banco;
    }

    String getTipoCuenta() {
        return tipoCuenta;
    }

    String getNumeroCuenta() {
        return numeroCuenta;
    }

    String getCodigo() {
        return codigo;
    }

    Estado getEstado() {
        return estado;
    }

    String getMotivo() {
        return motivo;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    Instant getAprobadoEn() {
        return aprobadoEn;
    }
}
