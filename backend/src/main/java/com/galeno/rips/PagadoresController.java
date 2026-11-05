package com.galeno.rips;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.rips.CobrosService.CobroCita;
import com.galeno.rips.CobrosService.CobroVista;
import com.galeno.rips.CobrosService.DatosCobro;
import com.galeno.rips.PagadoresService.PagadorVista;
import com.galeno.rips.PreciosService.PrecioVista;

/**
 * Pagadores, tarifas y cobros. La lista de pagadores la lee cualquier miembro (la usan la cita y la ficha); crearlos y
 * sus tarifas, RIPS_GESTIONAR. El cobro de la cita, quien gestiona la agenda; el de la atención, quien la registra.
 * Todo exige el módulo de RIPS activo (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/rips")
public class PagadoresController {

    private final PagadoresService pagadores;
    private final CobrosService cobros;

    PagadoresController(PagadoresService pagadores, CobrosService cobros) {
        this.pagadores = pagadores;
        this.cobros = cobros;
    }

    @GetMapping("/pagadores")
    public List<PagadorVista> pagadores() {
        return pagadores.listar();
    }

    @PostMapping("/pagadores")
    @ResponseStatus(HttpStatus.CREATED)
    public PagadorVista crear(@Valid @RequestBody DatosPagador r) {
        return pagadores.crear(r.datos());
    }

    @PutMapping("/pagadores/{id}")
    public PagadorVista actualizar(@PathVariable UUID id, @Valid @RequestBody DatosPagador r) {
        return pagadores.actualizar(id, r.datos());
    }

    @GetMapping("/pagadores/{id}/tarifas")
    public List<PrecioVista> tarifas(@PathVariable UUID id) {
        return pagadores.tarifas(id);
    }

    @PutMapping("/pagadores/{id}/tarifas/{codigoCups}")
    public PrecioVista fijarTarifa(@PathVariable UUID id, @PathVariable String codigoCups,
                                   @Valid @RequestBody PreciosController.Precio r) {
        return pagadores.fijarTarifa(id, codigoCups, r.valor());
    }

    @DeleteMapping("/pagadores/{id}/tarifas/{codigoCups}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarTarifa(@PathVariable UUID id, @PathVariable String codigoCups) {
        pagadores.quitarTarifa(id, codigoCups);
    }

    /** El cobro de cada cita del día (guardado o el que se propone); a lo sumo 200 por consulta. */
    @GetMapping("/cobros/citas")
    public List<CobroCita> cobrosCitas(@RequestParam @Size(max = 200) List<UUID> ids) {
        return cobros.citas(ids);
    }

    @GetMapping("/cobros/citas/{citaId}")
    public CobroVista cobroCita(@PathVariable UUID citaId) {
        return cobros.cita(citaId);
    }

    @PutMapping("/cobros/citas/{citaId}")
    public CobroVista guardarCobroCita(@PathVariable UUID citaId, @Valid @RequestBody RegistroCobro r) {
        return cobros.guardarCita(citaId, r.datos());
    }

    @GetMapping("/cobros/atenciones/{atencionId}")
    public CobroVista cobroAtencion(@PathVariable UUID atencionId) {
        return cobros.atencion(atencionId);
    }

    @PutMapping("/cobros/atenciones/{atencionId}")
    public CobroVista guardarCobroAtencion(@PathVariable UUID atencionId, @Valid @RequestBody RegistroCobro r) {
        return cobros.guardarAtencion(atencionId, r.datos());
    }

    public record DatosPagador(@NotNull @Size(max = 150) String nombre, @NotNull Pagador.Tipo tipo,
                               @Size(max = 20) String codigoEntidad, @Size(max = 20) String nit,
                               @Size(max = 2) String cobertura, @Size(max = 2) String modalidadPago,
                               @Size(max = 64) String cucon, @Size(max = 30) String numeroPoliza,
                               @Size(max = 2) String facturaSinContrato, Boolean exigeAutorizacion, Boolean activo) {
        Pagador.Datos datos() {
            return new Pagador.Datos(nombre, tipo, codigoEntidad, nit, cobertura, modalidadPago, cucon, numeroPoliza,
                    facturaSinContrato, Boolean.TRUE.equals(exigeAutorizacion), activo == null || activo);
        }
    }

    public record RegistroCobro(@NotNull UUID pagadorId, @Size(max = 30) String numeroAutorizacion,
                        @Size(max = 2) String conceptoRecaudo,
                        @DecimalMin("0.00") @DecimalMax("999999999.99") BigDecimal valorPagoModerador,
                        @Size(max = 20) String facturaPagoModerador, @Size(max = 60) String registroSiras) {
        DatosCobro datos() {
            return new DatosCobro(pagadorId, numeroAutorizacion, conceptoRecaudo, valorPagoModerador, facturaPagoModerador,
                    registroSiras);
        }
    }
}
