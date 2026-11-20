package com.galeno.historia;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preconsulta de una cita (PRECONSULTA_REGISTRAR, ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/preconsulta")
public class PreconsultaController {

    private final PreconsultaService service;

    PreconsultaController(PreconsultaService service) {
        this.service = service;
    }

    /** 204 si la cita todavía no tiene preconsulta. */
    @GetMapping("/citas/{citaId}")
    public ResponseEntity<PreconsultaService.Vista> deCita(@PathVariable UUID citaId) {
        return service.deCita(citaId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/citas/{citaId}")
    public PreconsultaService.Vista guardar(@PathVariable UUID citaId, @Valid @RequestBody PreconsultaRequest r) {
        return service.guardar(citaId, new Preconsulta.Datos(r.motivoConsulta(), r.taSistolica(), r.taDiastolica(),
                r.frecuenciaCardiaca(), r.frecuenciaRespiratoria(), r.temperatura(), r.saturacionO2(), r.pesoKg(),
                r.tallaCm(), r.medicamentosActuales(), r.observaciones()));
    }

    /** Los mismos rangos que la atención. */
    public record PreconsultaRequest(@Size(max = 2000) String motivoConsulta,
                                     @Min(40) @Max(300) Integer taSistolica,
                                     @Min(20) @Max(200) Integer taDiastolica,
                                     @Min(20) @Max(250) Integer frecuenciaCardiaca,
                                     @Min(4) @Max(80) Integer frecuenciaRespiratoria,
                                     @DecimalMin("30.0") @DecimalMax("45.0") BigDecimal temperatura,
                                     @Min(40) @Max(100) Integer saturacionO2,
                                     @DecimalMin("0.3") @DecimalMax("400.0") BigDecimal pesoKg,
                                     @DecimalMin("20.0") @DecimalMax("250.0") BigDecimal tallaCm,
                                     @Size(max = 2000) String medicamentosActuales,
                                     @Size(max = 2000) String observaciones) {
    }
}
