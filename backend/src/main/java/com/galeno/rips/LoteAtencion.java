package com.galeno.rips;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Una atención del lote y lo que se cambió para ella en la revisión (vacío: el valor por defecto del prestador).
 * {@code vigente} repite el id de la atención mientras el lote no esté anulado: su UNIQUE impide que la atención esté en
 * dos lotes vivos.
 */
@Embeddable
class LoteAtencion {

    @Column(name = "atencion_id", nullable = false)
    private UUID atencionId;

    @Column
    private UUID vigente;

    @Column(length = 2)
    private String finalidad;

    @Column(name = "causa_externa", length = 2)
    private String causaExterna;

    @Column(name = "modalidad_atencion", length = 2)
    private String modalidadAtencion;

    @Column(name = "grupo_servicios", length = 2)
    private String grupoServicios;

    @Column(length = 4)
    private String servicio;

    @Column(name = "via_ingreso", length = 2)
    private String viaIngreso;

    protected LoteAtencion() {
    }

    LoteAtencion(UUID atencionId) {
        this.atencionId = atencionId;
        this.vigente = atencionId;
    }

    void ajustar(ArmadorRips.Ajustes a) {
        this.finalidad = a.finalidad();
        this.causaExterna = a.causaExterna();
        this.modalidadAtencion = a.modalidadAtencion();
        this.grupoServicios = a.grupoServicios();
        this.servicio = a.servicio();
        this.viaIngreso = a.viaIngreso();
    }

    void liberar() {
        this.vigente = null;
    }

    ArmadorRips.Ajustes ajustes() {
        return new ArmadorRips.Ajustes(finalidad, causaExterna, modalidadAtencion, grupoServicios, servicio, viaIngreso);
    }

    UUID getAtencionId() {
        return atencionId;
    }
}
