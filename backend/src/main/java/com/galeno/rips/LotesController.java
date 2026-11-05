package com.galeno.rips;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.galeno.rips.LotesService.Archivo;
import com.galeno.rips.LotesService.LoteRipsResumen;
import com.galeno.rips.LotesService.LoteRipsVista;

/** Lotes del RIPS: RIPS_GESTIONAR y el módulo de RIPS activo (ver SecurityConfig y ModulosService). */
@RestController
@RequestMapping("/api/v1/rips/lotes")
public class LotesController {

    private final LotesService lotes;

    LotesController(LotesService lotes) {
        this.lotes = lotes;
    }

    @GetMapping
    public List<LoteRipsResumen> listar() {
        return lotes.listar();
    }

    /** Con las atenciones firmadas del período de ese pagador que no estén en otro lote. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoteRipsVista crear(@Valid @RequestBody NuevoLote r) {
        return lotes.crear(r.pagadorId(), r.desde(), r.hasta(), Boolean.TRUE.equals(r.sinFactura()));
    }

    /** Con la revisión: lo que falta o no pasa una regla, por atención. Queda en la auditoría de la historia. */
    @GetMapping("/{id}")
    public LoteRipsVista obtener(@PathVariable UUID id) {
        return lotes.obtener(id);
    }

    /** Solo en borrador. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id) {
        lotes.eliminar(id);
    }

    @DeleteMapping("/{id}/atenciones/{atencionId}")
    public LoteRipsVista quitarAtencion(@PathVariable UUID id, @PathVariable UUID atencionId) {
        return lotes.quitarAtencion(id, atencionId);
    }

    /** Lo que cambia para esa atención (vacío: el valor por defecto del prestador). */
    @PutMapping("/{id}/atenciones/{atencionId}")
    public LoteRipsVista ajustar(@PathVariable UUID id, @PathVariable UUID atencionId, @Valid @RequestBody AjustesAtencion r) {
        return lotes.ajustar(id, atencionId, new ArmadorRips.Ajustes(vacio(r.finalidad()), vacio(r.causaExterna()),
                vacio(r.modalidadAtencion()), vacio(r.grupoServicios()), vacio(r.servicio()), vacio(r.viaIngreso())));
    }

    @PutMapping("/{id}/factura")
    public LoteRipsVista cambiarFactura(@PathVariable UUID id, @Valid @RequestBody NumeroFactura r) {
        return lotes.cambiarFactura(id, r.numeroFactura());
    }

    /** El XML de la factura electrónica (opcional): se cruzan NIT, número, período y pagos moderadores. */
    @PostMapping(path = "/{id}/xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LoteRipsVista cargarXml(@PathVariable UUID id, @RequestPart("archivo") MultipartFile archivo) throws IOException {
        if (archivo.getSize() > FacturaXml.MAX_BYTES) {
            throw com.galeno.shared.web.ApiExceptionHandler.ApiProblem.badRequest("El XML de la factura pesa más de 5 MB");
        }
        return lotes.cargarXml(id, archivo.getBytes());
    }

    @DeleteMapping("/{id}/xml")
    public LoteRipsVista quitarXml(@PathVariable UUID id) {
        return lotes.cargarXml(id, null);
    }

    /** Guarda el JSON tal cual y bloquea las atenciones. Con problemas que el Ministerio rechazaría, responde 400. */
    @PostMapping("/{id}/generacion")
    public LoteRipsVista generar(@PathVariable UUID id) {
        return lotes.generar(id);
    }

    /** Libera las atenciones para otro lote (el JSON generado se conserva). */
    @PostMapping("/{id}/anulacion")
    public LoteRipsVista anular(@PathVariable UUID id, @Valid @RequestBody Anulacion r) {
        return lotes.anular(id, r.motivo());
    }

    @GetMapping("/{id}/json")
    public ResponseEntity<byte[]> descargar(@PathVariable UUID id) {
        Archivo a = lotes.descargar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(a.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(a.contenido());
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** @param sinFactura RIPS sin factura (RS), para el profesional no obligado a facturar que atiende particulares */
    public record NuevoLote(@NotNull UUID pagadorId, @NotNull LocalDate desde, @NotNull LocalDate hasta,
                            Boolean sinFactura) {
    }

    public record AjustesAtencion(@Size(max = 2) String finalidad, @Size(max = 2) String causaExterna,
                                  @Size(max = 2) String modalidadAtencion, @Size(max = 2) String grupoServicios,
                                  @Size(max = 4) String servicio, @Size(max = 2) String viaIngreso) {
    }

    public record NumeroFactura(@Size(max = 20) String numeroFactura) {
    }

    public record Anulacion(@NotBlank @Size(max = 300) String motivo) {
    }
}
