package com.galeno.rips;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/** El consultorio como prestador en el RIPS: código de habilitación y valores por defecto de cada servicio. */
@Entity
@Table(name = "rips_prestador")
class RipsPrestador {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** codPrestador: el código de habilitación con la sede (12 caracteres). */
    @Column(name = "codigo_habilitacion", length = 12)
    private String codigoHabilitacion;

    @Column(length = 4)
    private String servicio;

    @Column(name = "modalidad_atencion", length = 2)
    private String modalidadAtencion;

    @Column(name = "grupo_servicios", length = 2)
    private String grupoServicios;

    @Column(length = 2)
    private String finalidad;

    @Column(name = "causa_externa", length = 2)
    private String causaExterna;

    /** Solo para procedimientos. */
    @Column(name = "via_ingreso", length = 2)
    private String viaIngreso;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected RipsPrestador() {
    }

    static RipsPrestador nuevo() {
        return new RipsPrestador();
    }

    void actualizar(String codigoHabilitacion, String servicio, String modalidadAtencion, String grupoServicios,
                    String finalidad, String causaExterna, String viaIngreso) {
        this.codigoHabilitacion = codigoHabilitacion;
        this.servicio = servicio;
        this.modalidadAtencion = modalidadAtencion;
        this.grupoServicios = grupoServicios;
        this.finalidad = finalidad;
        this.causaExterna = causaExterna;
        this.viaIngreso = viaIngreso;
        this.actualizadoEn = Instant.now();
    }

    String getCodigoHabilitacion() {
        return codigoHabilitacion;
    }

    String getServicio() {
        return servicio;
    }

    String getModalidadAtencion() {
        return modalidadAtencion;
    }

    String getGrupoServicios() {
        return grupoServicios;
    }

    String getFinalidad() {
        return finalidad;
    }

    String getCausaExterna() {
        return causaExterna;
    }

    String getViaIngreso() {
        return viaIngreso;
    }
}
