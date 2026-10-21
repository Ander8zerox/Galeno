package com.galeno.suscripciones;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.galeno.suscripciones.SuscripcionService.EstadoVista;
import com.galeno.suscripciones.SuscripcionService.OpcionesPago;
import com.galeno.suscripciones.SuscripcionService.PagoVista;

/** Suscripción del consultorio de la sesión. El estado lo ve cualquier miembro; lo demás, SUSCRIPCION_GESTIONAR. */
@Validated
@RestController
@RequestMapping("/api/v1/suscripcion")
public class SuscripcionController {

    private final SuscripcionService suscripciones;

    SuscripcionController(SuscripcionService suscripciones) {
        this.suscripciones = suscripciones;
    }

    @GetMapping
    public EstadoVista estado() {
        return suscripciones.estado();
    }

    /** Planes con su precio y los datos para pagar por transferencia o consignación. */
    @GetMapping("/opciones")
    public OpcionesPago opciones() {
        return suscripciones.opciones();
    }

    @GetMapping("/pagos")
    public List<PagoVista> pagos() {
        return suscripciones.misPagos();
    }

    @PostMapping(path = "/pagos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PagoVista reportar(@RequestParam @NotBlank String plan, @RequestParam @Positive long monto,
                              @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaPago,
                              @RequestParam @NotBlank @Size(max = 100) String referencia,
                              @RequestPart(name = "comprobante", required = false) MultipartFile comprobante) {
        byte[] bytes = null;
        String nombre = null;
        if (comprobante != null && !comprobante.isEmpty()) {
            try {
                bytes = comprobante.getBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            nombre = comprobante.getOriginalFilename();
        }
        return suscripciones.reportarPago(new SuscripcionService.ReportePago(plan, monto, fechaPago, referencia, nombre, bytes));
    }
}
