package com.galeno.historia;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.galeno.historia.HistoriaService.Archivo;
import com.galeno.historia.HistoriaService.AtencionVista;
import com.galeno.historia.HistoriaService.Cie10Vista;
import com.galeno.historia.HistoriaService.DocumentoVista;
import com.galeno.historia.HistoriaService.EventoHistoria;

/** Lectura: permiso HISTORIA_VER. Escritura: HISTORIA_REGISTRAR (ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/historia")
public class HistoriaController {

    private final HistoriaService historia;

    HistoriaController(HistoriaService historia) {
        this.historia = historia;
    }

    @GetMapping("/pacientes/{pacienteId}")
    public List<EventoHistoria> linea(@PathVariable UUID pacienteId) {
        return historia.linea(pacienteId);
    }

    @PostMapping("/atenciones")
    @ResponseStatus(HttpStatus.CREATED)
    public AtencionVista crear(@Valid @RequestBody NuevaAtencion r) {
        return historia.crear(r.pacienteId(), r.citaId());
    }

    @GetMapping("/atenciones/{id}")
    public AtencionVista obtener(@PathVariable UUID id) {
        return historia.obtener(id);
    }

    @GetMapping(value = "/atenciones/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("atencion-" + id + ".pdf").build().toString())
                .body(historia.pdfAtencion(id));
    }

    @PutMapping("/atenciones/{id}")
    public AtencionVista guardar(@PathVariable UUID id, @Valid @RequestBody GuardarAtencion r) {
        List<Diagnostico> dx = r.diagnosticos().stream()
                .map(d -> new Diagnostico(d.codigoCie10(), d.descripcion(), d.principal(), d.tipo())).toList();
        return historia.guardar(id, r.version(), new Atencion.Contenido(r.motivoConsulta(), r.enfermedadActual(),
                r.antecedentes(), r.revisionSistemas(), r.taSistolica(), r.taDiastolica(), r.frecuenciaCardiaca(),
                r.frecuenciaRespiratoria(), r.temperatura(), r.saturacionO2(), r.pesoKg(), r.tallaCm(),
                r.examenFisico(), r.analisis(), r.plan(), dx));
    }

    /** Firma el borrador: queda inmutable. Si viene de una cita, la cita pasa a ATENDIDA. */
    @PostMapping("/atenciones/{id}/firma")
    public AtencionVista firmar(@PathVariable UUID id) {
        return historia.firmar(id);
    }

    @DeleteMapping("/atenciones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarBorrador(@PathVariable UUID id) {
        historia.eliminarBorrador(id);
    }

    @PostMapping("/atenciones/{id}/notas")
    @ResponseStatus(HttpStatus.CREATED)
    public AtencionVista agregarNota(@PathVariable UUID id, @Valid @RequestBody NuevaNota r) {
        return historia.agregarNota(id, r.texto());
    }

    @PostMapping(path = "/pacientes/{pacienteId}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoVista subirDocumento(@PathVariable UUID pacienteId,
                                         @RequestPart("archivo") MultipartFile archivo,
                                         @RequestParam DocumentoClinico.Tipo tipo,
                                         @RequestParam(required = false) String descripcion,
                                         @RequestParam(required = false) UUID atencionId) throws IOException {
        return historia.subirDocumento(pacienteId, atencionId, tipo, descripcion, archivo.getOriginalFilename(),
                archivo.getBytes());
    }

    @GetMapping("/documentos/{id}/archivo")
    public ResponseEntity<byte[]> descargar(@PathVariable UUID id) {
        Archivo archivo = historia.descargar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(archivo.contenido());
    }

    @GetMapping("/cie10")
    public List<Cie10Vista> cie10(@RequestParam String q) {
        return historia.buscarCie10(q);
    }

    public record NuevaAtencion(@NotNull UUID pacienteId, UUID citaId) {
    }

    public record NuevaNota(@NotBlank @Size(max = 4000) String texto) {
    }

    public record DiagnosticoRequest(@NotBlank @Size(max = 10) String codigoCie10,
                                     @NotBlank @Size(max = 300) String descripcion, boolean principal,
                                     Diagnostico.Tipo tipo) {
    }

    /** @param version la que se leyó; si otra pestaña guardó antes, responde 409 */
    public record GuardarAtencion(long version,
                                  @Size(max = 1000) String motivoConsulta,
                                  @Size(max = 20000) String enfermedadActual,
                                  @Size(max = 20000) String antecedentes,
                                  @Size(max = 20000) String revisionSistemas,
                                  @Min(40) @Max(300) Integer taSistolica,
                                  @Min(20) @Max(200) Integer taDiastolica,
                                  @Min(20) @Max(250) Integer frecuenciaCardiaca,
                                  @Min(4) @Max(80) Integer frecuenciaRespiratoria,
                                  @DecimalMin("30.0") @DecimalMax("45.0") BigDecimal temperatura,
                                  @Min(40) @Max(100) Integer saturacionO2,
                                  @DecimalMin("0.3") @DecimalMax("400.0") BigDecimal pesoKg,
                                  @DecimalMin("20.0") @DecimalMax("250.0") BigDecimal tallaCm,
                                  @Size(max = 20000) String examenFisico,
                                  @Size(max = 20000) String analisis,
                                  @Size(max = 20000) String plan,
                                  @NotNull @Size(max = 20) List<@Valid DiagnosticoRequest> diagnosticos) {
    }
}
