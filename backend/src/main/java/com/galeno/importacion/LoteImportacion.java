package com.galeno.importacion;

import java.time.Instant;
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

import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Una carga de archivos de otro sistema. Nada llega a la historia mientras está EN_REVISION; al confirmarla
 * se crean pacientes y atenciones, y se puede deshacer mientras nadie haya trabajado sobre lo importado.
 */
@Entity
@Table(name = "lotes_importacion")
class LoteImportacion {

    enum Origen {
        RIPS_JSON,
        /** Excel o CSV con los datos de los pacientes (nombres y contacto). */
        PACIENTES_LISTADO
    }

    enum Estado {
        EN_REVISION,
        CONFIRMADO,
        DESHECHO,
        DESCARTADO
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Origen origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(nullable = false)
    private int archivos;

    @Column(nullable = false)
    private int facturas;

    @Column(length = 500)
    private String prestadores;

    @Column(nullable = false)
    private int pacientes;

    @Column(nullable = false)
    private int atenciones;

    @Column(nullable = false)
    private int errores;

    @Column(nullable = false)
    private int omitidos;

    @JdbcTypeCode(SqlTypes.JSON)
    private String resultado;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private UUID creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "confirmado_en")
    private Instant confirmadoEn;

    @Column(name = "deshecho_en")
    private Instant deshechoEn;

    protected LoteImportacion() {
    }

    LoteImportacion(Origen origen, int archivos, int facturas, String prestadores, int omitidos, UUID creadoPor) {
        this.origen = origen;
        this.estado = Estado.EN_REVISION;
        this.archivos = archivos;
        this.facturas = facturas;
        this.prestadores = prestadores != null && prestadores.length() > 500 ? prestadores.substring(0, 500) : prestadores;
        this.omitidos = omitidos;
        this.creadoPor = creadoPor;
        this.creadoEn = Instant.now();
    }

    void totales(int pacientes, int atenciones, int errores) {
        this.pacientes = pacientes;
        this.atenciones = atenciones;
        this.errores = errores;
    }

    void exigir(Estado esperado, String accion) {
        if (estado != esperado) {
            throw ApiProblem.conflict("No se puede " + accion + " un lote en estado " + estado);
        }
    }

    void confirmar(String resultado) {
        exigir(Estado.EN_REVISION, "confirmar");
        this.estado = Estado.CONFIRMADO;
        this.resultado = resultado;
        this.confirmadoEn = Instant.now();
    }

    void deshacer(String resultado) {
        exigir(Estado.CONFIRMADO, "deshacer");
        this.estado = Estado.DESHECHO;
        this.resultado = resultado;
        this.deshechoEn = Instant.now();
    }

    void descartar() {
        exigir(Estado.EN_REVISION, "descartar");
        this.estado = Estado.DESCARTADO;
    }

    UUID getId() {
        return id;
    }

    Origen getOrigen() {
        return origen;
    }

    Estado getEstado() {
        return estado;
    }

    int getArchivos() {
        return archivos;
    }

    int getFacturas() {
        return facturas;
    }

    String getPrestadores() {
        return prestadores;
    }

    int getPacientes() {
        return pacientes;
    }

    int getAtenciones() {
        return atenciones;
    }

    int getErrores() {
        return errores;
    }

    int getOmitidos() {
        return omitidos;
    }

    String getResultado() {
        return resultado;
    }

    UUID getCreadoPor() {
        return creadoPor;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    Instant getConfirmadoEn() {
        return confirmadoEn;
    }

    Instant getDeshechoEn() {
        return deshechoEn;
    }
}
