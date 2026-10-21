package com.galeno.formulacion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.galeno.shared.ai.jobs.AiJobController.AiJobAccepted;
import com.galeno.shared.ai.jobs.AiJobService;
import com.galeno.shared.ai.jobs.AiJobType;
import com.galeno.shared.pdf.PdfRenderer;
import com.galeno.shared.security.AuthUser;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

@RestController
@RequestMapping("/api/v1/formulacion")
public class FormulacionController {

    private final AiJobService jobs;
    private final PacienteService pacientes;
    private final FormulaRepository formulas;
    private final IdentidadService identidad;
    private final HistoriaService historia;
    private final PdfRenderer pdf;

    FormulacionController(AiJobService jobs, PacienteService pacientes, FormulaRepository formulas,
                          IdentidadService identidad, HistoriaService historia, PdfRenderer pdf) {
        this.jobs = jobs;
        this.pacientes = pacientes;
        this.formulas = formulas;
        this.identidad = identidad;
        this.historia = historia;
        this.pdf = pdf;
    }

    /** Encola la redacción con IA; el resultado se consulta en {@code GET /api/v1/ai-jobs/{jobId}}. */
    @PostMapping("/borradores")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiJobAccepted solicitarBorrador(@Valid @RequestBody SolicitudBorrador request) {
        pacientes.get(request.pacienteId()); // 404 si no existe en este consultorio
        var entrada = new BorradorFormulaHandler.Entrada(request.pacienteId(), request.texto(), request.diagnostico());
        return AiJobAccepted.of(jobs.enqueue(AiJobType.BORRADOR_FORMULA, request.pacienteId(), entrada,
                BorradorFormulaHandler.PROMPT));
    }

    /** El médico revisa y edita el borrador; al firmar se guarda la fórmula definitiva. */
    @PostMapping("/formulas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public FormulaResponse firmar(@Valid @RequestBody FirmarFormula request) {
        pacientes.get(request.pacienteId());
        AuthUser medico = AuthUser.current();
        String registroMedico = identidad.registroMedico(medico.id())
                .orElseThrow(() -> ApiProblem.conflict("Para firmar fórmulas debe tener su registro médico en el perfil"));
        if (request.atencionId() != null) {
            historia.verificarAtencionDelPaciente(request.atencionId(), request.pacienteId());
        }
        List<FormulaItem> items = request.items().stream()
                .map(i -> new FormulaItem(i.medicamento(), i.concentracion(), i.formaFarmaceutica(), i.via(), i.dosis(),
                        i.frecuencia(), i.duracion(), i.cantidad(), i.indicaciones()))
                .toList();
        Formula formula = formulas.save(new Formula(request.pacienteId(), medico.id(), registroMedico, request.aiJobId(), request.atencionId(),
                request.observaciones(), items));
        return FormulaResponse.of(formula);
    }

    /** Fórmula impresa para entregar al paciente. Queda en la auditoría de la historia. */
    @GetMapping(value = "/formulas/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        Formula f = formulas.findById(id).orElseThrow(() -> ApiProblem.notFound("Fórmula no encontrada"));
        Paciente paciente = pacientes.get(f.getPacienteId());
        String diagnostico = f.getAtencionId() == null ? null : historia.diagnosticoPrincipal(f.getAtencionId()).orElse(null);
        byte[] contenido = pdf.render(FormulaPdf.html(f, paciente, identidad.consultorio(AuthUser.current().tenantId()),
                identidad.profesional(f.getMedicoId()), diagnostico));
        historia.registrarAcceso(f.getPacienteId(), AccesoHistoria.Accion.IMPRIMIR_FORMULA, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("formula-" + id + ".pdf").build().toString())
                .body(contenido);
    }

    @GetMapping("/formulas")
    @Transactional(readOnly = true)
    public List<FormulaResponse> porPaciente(@RequestParam UUID pacienteId) {
        return formulas.findByPacienteIdOrderByFirmadaEnDesc(pacienteId).stream().map(FormulaResponse::of).toList();
    }

    public record SolicitudBorrador(@NotNull UUID pacienteId, @NotBlank @Size(max = 4000) String texto,
                                    @Size(max = 300) String diagnostico) {
    }

    /** @param atencionId atención en la que se emite (opcional) */
    public record FirmarFormula(@NotNull UUID pacienteId, UUID aiJobId, UUID atencionId, @Size(max = 1000) String observaciones,
                                @NotEmpty List<@Valid ItemRequest> items) {
    }

    public record ItemRequest(@NotBlank @Size(max = 200) String medicamento, @Size(max = 60) String concentracion,
                              @Size(max = 60) String formaFarmaceutica, @Size(max = 40) String via,
                              @NotBlank @Size(max = 60) String dosis, @NotBlank @Size(max = 60) String frecuencia,
                              @NotBlank @Size(max = 60) String duracion, @Size(max = 60) String cantidad,
                              @Size(max = 500) String indicaciones) {
    }

    public record FormulaResponse(UUID id, UUID pacienteId, UUID medicoId, String registroMedico, UUID aiJobId,
                                  UUID atencionId, String observaciones,
                                  Instant firmadaEn, List<ItemRequest> items) {

        static FormulaResponse of(Formula f) {
            List<ItemRequest> items = f.getItems().stream()
                    .map(i -> new ItemRequest(i.getMedicamento(), i.getConcentracion(), i.getFormaFarmaceutica(),
                            i.getVia(), i.getDosis(), i.getFrecuencia(), i.getDuracion(), i.getCantidad(),
                            i.getIndicaciones()))
                    .toList();
            return new FormulaResponse(f.getId(), f.getPacienteId(), f.getMedicoId(), f.getRegistroMedico(), f.getAiJobId(), f.getAtencionId(),
                    f.getObservaciones(), f.getFirmadaEn(), items);
        }
    }
}
