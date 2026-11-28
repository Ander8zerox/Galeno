package com.galeno.tratamiento;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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

import com.galeno.historia.AccesoHistoria;
import com.galeno.historia.HistoriaService;
import com.galeno.identidad.IdentidadService;
import com.galeno.pacientes.Paciente;
import com.galeno.pacientes.PacienteService;
import com.galeno.shared.pdf.NombreArchivo;
import com.galeno.shared.pdf.PdfRenderer;
import com.galeno.shared.security.AuthUser;
import com.galeno.tratamiento.TratamientosService.ItemDatos;
import com.galeno.tratamiento.TratamientosService.PlanTratamientoVista;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Planes de tratamiento (odontología). Ver: odontólogos, administración y secretaría; editar: el odontólogo; registrar
 * la aceptación: el odontólogo o la secretaría (ver {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/tratamientos")
public class TratamientosController {

    private final TratamientosService servicio;
    private final PacienteService pacientes;
    private final IdentidadService identidad;
    private final HistoriaService historia;
    private final PdfRenderer pdf;

    TratamientosController(TratamientosService servicio, PacienteService pacientes, IdentidadService identidad,
                           HistoriaService historia, PdfRenderer pdf) {
        this.servicio = servicio;
        this.pacientes = pacientes;
        this.identidad = identidad;
        this.historia = historia;
        this.pdf = pdf;
    }

    @GetMapping
    public List<PlanTratamientoVista> planesDelPaciente(@RequestParam UUID pacienteId) {
        return servicio.delPaciente(pacienteId);
    }

    /** Procedimientos que sugiere el último odontograma firmado (no guarda nada). */
    @GetMapping("/sugerencias")
    public TratamientosService.SugerenciasPlan sugerenciasPlan(@RequestParam UUID pacienteId) {
        return servicio.sugerencias(pacienteId);
    }

    @GetMapping("/{id}")
    public PlanTratamientoVista verPlan(@PathVariable UUID id) {
        return servicio.ver(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanTratamientoVista crearPlan(@Valid @RequestBody CrearPlanTratamiento r) {
        return servicio.crear(r.pacienteId(), r.observaciones(), r.vigenciaDias());
    }

    @PutMapping("/{id}")
    public PlanTratamientoVista datosPlan(@PathVariable UUID id, @Valid @RequestBody DatosPlanTratamiento r) {
        return servicio.datos(id, r.observaciones(), r.vigenciaDias());
    }

    @PostMapping("/{id}/presentar")
    public PlanTratamientoVista presentarPlan(@PathVariable UUID id) {
        return servicio.presentar(id);
    }

    @PostMapping("/{id}/borrador")
    public PlanTratamientoVista planABorrador(@PathVariable UUID id) {
        return servicio.volverABorrador(id);
    }

    @PostMapping("/{id}/aceptacion")
    public PlanTratamientoVista aceptarPlan(@PathVariable UUID id, @Valid @RequestBody AceptacionPlanTratamiento r) {
        return servicio.aceptar(id, r.firma(), r.nombreFirmante(), Boolean.TRUE.equals(r.constanciaVerbal()));
    }

    @PostMapping("/{id}/terminar")
    public PlanTratamientoVista terminarPlan(@PathVariable UUID id, @Valid @RequestBody MotivoCierrePlan r) {
        return servicio.terminar(id, r.motivo());
    }

    @PostMapping("/{id}/cancelar")
    public PlanTratamientoVista cancelarPlan(@PathVariable UUID id, @Valid @RequestBody MotivoCierrePlan r) {
        return servicio.cancelar(id, r.motivo());
    }

    @PostMapping("/{id}/items")
    public PlanTratamientoVista agregarItemPlan(@PathVariable UUID id, @Valid @RequestBody ItemPlanTratamiento r) {
        return servicio.agregarItem(id, r.datos());
    }

    @PutMapping("/{id}/items/{itemId}")
    public PlanTratamientoVista editarItemPlan(@PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody ItemPlanTratamiento r) {
        return servicio.editarItem(id, itemId, r.datos());
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public PlanTratamientoVista quitarItemPlan(@PathVariable UUID id, @PathVariable UUID itemId) {
        return servicio.quitarItem(id, itemId);
    }

    @PostMapping("/{id}/items/{itemId}/cancelar")
    public PlanTratamientoVista cancelarItemPlan(@PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody MotivoCierrePlan r) {
        return servicio.cancelarItem(id, itemId, r.motivo());
    }

    /** Presupuesto para entregar o firmar. Queda en la auditoría de la historia del paciente. */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional
    public ResponseEntity<byte[]> presupuestoPdf(@PathVariable UUID id) {
        TratamientosService.Presupuesto pr = servicio.presupuesto(id);
        Paciente paciente = pacientes.get(pr.plan().getPacienteId());
        var consultorio = identidad.consultorio(AuthUser.current().tenantId());
        byte[] contenido = pdf.render(PresupuestoPdf.html(pr, paciente, consultorio,
                identidad.profesional(pr.plan().getOdontologoId())));
        String nombre = NombreArchivo.pdf("presupuesto", paciente.getApellidos(), paciente.getNombres(),
                pr.plan().getActualizadoEn(), consultorio.zona());
        historia.registrarAcceso(paciente.getId(), AccesoHistoria.Accion.IMPRIMIR_PRESUPUESTO, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(nombre, StandardCharsets.UTF_8).build().toString())
                .body(contenido);
    }

    public record CrearPlanTratamiento(@NotNull UUID pacienteId, @Size(max = 1000) String observaciones,
                            @Min(1) @Max(TratamientosService.MAX_VIGENCIA) Integer vigenciaDias) {
    }

    public record DatosPlanTratamiento(@Size(max = 1000) String observaciones,
                            @Min(1) @Max(TratamientosService.MAX_VIGENCIA) Integer vigenciaDias) {
    }

    /** @param constanciaVerbal sin firma, el paciente aceptó de palabra y quien atiende lo registra */
    public record AceptacionPlanTratamiento(@Size(max = TratamientosService.MAX_FIRMA) String firma, @Size(max = 120) String nombreFirmante,
                             Boolean constanciaVerbal) {
    }

    public record MotivoCierrePlan(@Size(max = 300) String motivo) {
    }

    /** @param valorUnitario vacío toma el de la lista de precios */
    public record ItemPlanTratamiento(@Min(1) @Max(TratamientosService.MAX_FASES) int fase, @Size(max = 80) String faseNombre,
                       @NotNull @Size(max = 10) String codigoCups, @Size(max = 400) String descripcion,
                       @Size(max = 2) String diente, @Size(max = 5) String superficies, @Min(1) @Max(99) int cantidad,
                       BigDecimal valorUnitario, BigDecimal descuento) {
        ItemDatos datos() {
            return new ItemDatos(fase, faseNombre, codigoCups, descripcion, diente, superficies, cantidad, valorUnitario,
                    descuento);
        }
    }
}
