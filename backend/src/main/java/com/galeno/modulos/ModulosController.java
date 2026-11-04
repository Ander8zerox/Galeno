package com.galeno.modulos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.modulos.ModulosService.ModuloVista;

/** Ver los módulos: cualquier miembro (la pantalla los necesita). Activar: MODULOS_GESTIONAR (ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/modulos")
public class ModulosController {

    private final ModulosService modulos;

    ModulosController(ModulosService modulos) {
        this.modulos = modulos;
    }

    @GetMapping
    public List<ModuloVista> listar() {
        return modulos.vista();
    }

    @PutMapping("/{modulo}")
    public List<ModuloVista> activar(@PathVariable Modulo modulo, @Valid @RequestBody Activacion r) {
        return modulos.activar(modulo, r.activo());
    }

    public record Activacion(@NotNull Boolean activo) {
    }
}
