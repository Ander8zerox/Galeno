package com.galeno.rips;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

import com.galeno.rips.NotasService.NotaRipsResumen;
import com.galeno.rips.NotasService.NotaRipsVista;
import com.galeno.rips.NotasService.ServicioVista;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/** Notas crédito, débito y de ajuste: RIPS_GESTIONAR y el módulo de RIPS activo (ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/rips")
public class NotasController {

    private final NotasService notas;

    NotasController(NotasService notas) {
        this.notas = notas;
    }

    /** Los servicios del RIPS del lote generado, con lo que ya acreditaron o sumaron sus notas. */
    @GetMapping("/lotes/{id}/servicios")
    public List<ServicioVista> servicios(@PathVariable UUID id) {
        return notas.servicios(id);
    }

    @GetMapping("/lotes/{id}/notas")
    public List<NotaRipsResumen> listar(@PathVariable UUID id) {
        return notas.listar(id);
    }

    /** {@code total}: NC al 100% (la factura se devolvió completa), registrada sin RIPS. */
    @PostMapping("/lotes/{id}/notas")
    @ResponseStatus(HttpStatus.CREATED)
    public NotaRipsVista crear(@PathVariable UUID id, @Valid @RequestBody NuevaNotaRips r) {
        return notas.crear(id, r.tipo(), Boolean.TRUE.equals(r.total()), r.numero(), r.motivo());
    }

    /** Con la revisión, si está en borrador. Queda en la auditoría de la historia de los pacientes afectados. */
    @GetMapping("/notas/{notaId}")
    public NotaRipsVista obtener(@PathVariable UUID notaId) {
        return notas.obtener(notaId);
    }

    @PutMapping("/notas/{notaId}")
    public NotaRipsVista guardar(@PathVariable UUID notaId, @Valid @RequestBody DatosNotaRips r) {
        return notas.guardar(notaId, r.numero(), r.motivo(), r.servicios() == null ? null : r.servicios().stream()
                .map(s -> new ArmadorNota.ItemNota(s.clave(), s.valor(), s.datos())).toList());
    }

    @DeleteMapping("/notas/{notaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID notaId) {
        notas.eliminar(notaId);
    }

    /** El XML de la nota crédito o débito (opcional): se cruzan número, factura referenciada y valor. */
    @PostMapping(path = "/notas/{notaId}/xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public NotaRipsVista cargarXml(@PathVariable UUID notaId, @RequestPart("archivo") MultipartFile archivo)
            throws IOException {
        if (archivo.getSize() > FacturaXml.MAX_BYTES) {
            throw ApiProblem.badRequest("El XML de la nota pesa más de 5 MB");
        }
        return notas.cargarXml(notaId, archivo.getBytes());
    }

    @DeleteMapping("/notas/{notaId}/xml")
    public NotaRipsVista quitarXml(@PathVariable UUID notaId) {
        return notas.cargarXml(notaId, null);
    }

    @PostMapping("/notas/{notaId}/generacion")
    public NotaRipsVista generar(@PathVariable UUID notaId) {
        return notas.generar(notaId);
    }

    @PostMapping("/notas/{notaId}/anulacion")
    public NotaRipsVista anular(@PathVariable UUID notaId, @Valid @RequestBody AnulacionNota r) {
        return notas.anular(notaId, r.motivo());
    }

    @GetMapping("/notas/{notaId}/json")
    public ResponseEntity<byte[]> descargar(@PathVariable UUID notaId) {
        LotesService.Archivo a = notas.descargar(notaId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(a.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(a.contenido());
    }

    public record NuevaNotaRips(@NotNull ArmadorNota.Tipo tipo, Boolean total, @NotBlank @Size(max = 20) String numero,
                                @NotBlank @Size(max = 300) String motivo) {
    }

    /** @param servicios los servicios afectados; {@code null} los deja como están */
    public record DatosNotaRips(@NotBlank @Size(max = 20) String numero, @NotBlank @Size(max = 300) String motivo,
                                @Size(max = 2000) List<@Valid ServicioNota> servicios) {
    }

    /**
     * @param valor NC: lo glosado; ND: lo que se suma; NA: no aplica
     * @param datos lo que se corrige (campo del RIPS → valor); vacío borra un relacionado o la autorización
     */
    public record ServicioNota(@NotBlank @Size(max = 40) String clave,
                               @DecimalMin("0.00") @DecimalMax("999999999.99") BigDecimal valor,
                               @Size(max = 12) Map<String, @Size(max = 30) String> datos) {
    }

    public record AnulacionNota(@NotBlank @Size(max = 300) String motivo) {
    }
}
