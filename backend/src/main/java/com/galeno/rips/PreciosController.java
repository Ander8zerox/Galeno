package com.galeno.rips;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.rips.PreciosService.PrecioVista;

/** Lista de precios por CUPS: RIPS_GESTIONAR y el módulo de RIPS activo (ver SecurityConfig y ModulosService). */
@RestController
@RequestMapping("/api/v1/rips/precios")
public class PreciosController {

    private final PreciosService precios;

    PreciosController(PreciosService precios) {
        this.precios = precios;
    }

    @GetMapping
    public List<PrecioVista> listar() {
        return precios.listar();
    }

    @PutMapping("/{codigoCups}")
    public PrecioVista fijar(@PathVariable String codigoCups, @Valid @RequestBody Precio r) {
        return precios.fijar(codigoCups, r.valor());
    }

    @DeleteMapping("/{codigoCups}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitar(@PathVariable String codigoCups) {
        precios.quitar(codigoCups);
    }

    public record Precio(@NotNull @DecimalMin("0.00") @DecimalMax("999999999.99") BigDecimal valor) {
    }
}
