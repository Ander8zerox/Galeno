package com.galeno.suscripciones;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Archivo del comprobante de un pago (solo se lee al descargarlo, después de verificar el pago). */
@Entity
@Table(name = "comprobantes_pago")
class ComprobantePago {

    @Id
    @Column(name = "pago_id")
    private UUID pagoId;

    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] contenido;

    protected ComprobantePago() {
    }

    ComprobantePago(UUID pagoId, byte[] contenido) {
        this.pagoId = pagoId;
        this.contenido = contenido;
    }

    byte[] getContenido() {
        return contenido;
    }
}
