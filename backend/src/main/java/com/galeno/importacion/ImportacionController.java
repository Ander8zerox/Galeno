package com.galeno.importacion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.galeno.importacion.ImportacionService.ArchivoSubido;
import com.galeno.importacion.ImportacionService.LoteDetalle;
import com.galeno.importacion.ImportacionService.LoteVista;
import com.galeno.importacion.ImportacionService.Pagina;
import com.galeno.importacion.ImportacionService.RegistroVista;

/** Permiso IMPORTAR_HISTORIAS (ver SecurityConfig). */
@Validated
@RestController
@RequestMapping("/api/v1/importaciones")
public class ImportacionController {

    private final ImportacionService importacion;

    ImportacionController(ImportacionService importacion) {
        this.importacion = importacion;
    }

    /** Uno o varios .json de RIPS, o un .zip con ellos. Arma el lote en revisión. */
    @PostMapping(path = "/rips", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LoteVista subirRips(@RequestPart("archivos") List<MultipartFile> archivos) {
        return importacion.crearDesdeRips(archivos.stream().map(ImportacionController::leer).toList());
    }

    @GetMapping
    public Pagina<LoteVista> listar(@RequestParam(defaultValue = "0") @Min(0) int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return importacion.listar(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public LoteDetalle obtener(@PathVariable UUID id,
                               @RequestParam(required = false) RegistroImportacion.Estado estado,
                               @RequestParam(defaultValue = "0") @Min(0) int page,
                               @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return importacion.obtener(id, estado, PageRequest.of(page, size));
    }

    public record Inclusion(boolean incluido) {
    }

    @PutMapping("/{id}/registros/{registroId}")
    public RegistroVista incluir(@PathVariable UUID id, @PathVariable UUID registroId, @RequestBody Inclusion r) {
        return importacion.incluir(id, registroId, r.incluido());
    }

    @PutMapping("/{id}/registros")
    public LoteVista incluirTodos(@PathVariable UUID id, @RequestBody Inclusion r) {
        return importacion.incluirTodos(id, r.incluido());
    }

    @PostMapping("/{id}/confirmar")
    public LoteVista confirmar(@PathVariable UUID id) {
        return importacion.confirmar(id);
    }

    @PostMapping("/{id}/deshacer")
    public LoteVista deshacer(@PathVariable UUID id) {
        return importacion.deshacer(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartar(@PathVariable UUID id) {
        importacion.descartar(id);
    }

    private static ArchivoSubido leer(MultipartFile f) {
        try {
            return new ArchivoSubido(f.getOriginalFilename() == null ? "archivo" : f.getOriginalFilename(), f.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
