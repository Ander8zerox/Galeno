package com.galeno.importacion;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** Un paciente del lote con todo lo que se importaría de él (en {@code datos}, como JSON). */
@Entity
@Table(name = "registros_importacion")
class RegistroImportacion {

    enum Estado {
        /** No existe en Galeno: se crea con datos incompletos. */
        NUEVO,
        /** Ya existe (mismo tipo y número de documento): solo se le agregan atenciones. */
        EXISTENTE,
        /** No se puede importar (p. ej. sin documento válido). */
        ERROR
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "lote_id", nullable = false, updatable = false)
    private UUID loteId;

    @Column(nullable = false, updatable = false)
    private int orden;

    @Column(name = "tipo_documento", nullable = false, updatable = false, length = 5)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, updatable = false, length = 30)
    private String numeroDocumento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(nullable = false)
    private boolean incluido;

    @Column(nullable = false)
    private int atenciones;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String datos;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String mensajes;

    @Column(name = "paciente_id")
    private UUID pacienteId;

    protected RegistroImportacion() {
    }

    RegistroImportacion(UUID loteId, int orden, String tipoDocumento, String numeroDocumento, Estado estado,
                        int atenciones, String datos, String mensajes, UUID pacienteId) {
        this.loteId = loteId;
        this.orden = orden;
        this.tipoDocumento = recortar(tipoDocumento, 5);
        this.numeroDocumento = recortar(numeroDocumento, 30);
        this.estado = estado;
        this.incluido = estado != Estado.ERROR;
        this.atenciones = atenciones;
        this.datos = datos;
        this.mensajes = mensajes;
        this.pacienteId = pacienteId;
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    void incluir(boolean incluido) {
        this.incluido = incluido && estado != Estado.ERROR;
    }

    void importadoComo(UUID pacienteId) {
        this.pacienteId = pacienteId;
    }

    UUID getId() {
        return id;
    }

    UUID getLoteId() {
        return loteId;
    }

    int getOrden() {
        return orden;
    }

    String getTipoDocumento() {
        return tipoDocumento;
    }

    String getNumeroDocumento() {
        return numeroDocumento;
    }

    Estado getEstado() {
        return estado;
    }

    boolean isIncluido() {
        return incluido;
    }

    int getAtenciones() {
        return atenciones;
    }

    String getDatos() {
        return datos;
    }

    String getMensajes() {
        return mensajes;
    }

    UUID getPacienteId() {
        return pacienteId;
    }
}
