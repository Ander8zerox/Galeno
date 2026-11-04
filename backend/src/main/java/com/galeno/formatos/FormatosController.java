package com.galeno.formatos;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.formatos.FormatosService.FormatoCatalogo;
import com.galeno.formatos.FormatosService.FormatoDisponible;

/**
 * {@code /consultorio}: el administrador activa formatos (FORMATOS_GESTIONAR). {@code /disponibles}: el médico ve los
 * activos (HISTORIA_VER) y elige los suyos por defecto (HISTORIA_REGISTRAR). Ver SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/formatos")
public class FormatosController {

    private final FormatosService formatos;

    FormatosController(FormatosService formatos) {
        this.formatos = formatos;
    }

    /** Todo el catálogo de Galenum, con los que están activos en este consultorio. */
    @GetMapping("/consultorio")
    public List<FormatoCatalogo> catalogo() {
        return formatos.catalogo();
    }

    @PutMapping("/consultorio/{formatoId}")
    public FormatoCatalogo activar(@PathVariable UUID formatoId, @Valid @RequestBody Activacion r) {
        return formatos.activar(formatoId, r.activo());
    }

    /** Los activos en el consultorio; vacío si no usa ninguno. */
    @GetMapping("/disponibles")
    public List<FormatoDisponible> disponibles() {
        return formatos.disponibles();
    }

    /** Agregarlo solo (o no) a las atenciones nuevas de quien consulta. */
    @PutMapping("/disponibles/{formatoId}/por-defecto")
    public FormatoDisponible porDefecto(@PathVariable UUID formatoId, @Valid @RequestBody PorDefecto r) {
        return formatos.porDefecto(formatoId, r.porDefecto());
    }

    public record Activacion(@NotNull Boolean activo) {
    }

    public record PorDefecto(@NotNull Boolean porDefecto) {
    }
}
