package com.galeno.pacientes;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.pacientes.AntecedentesService.Clinicos;

/** Alergias y antecedentes estructurados (datos clínicos: HISTORIA_VER, ver AntecedentesService). */
@RestController
@RequestMapping("/api/v1/pacientes/{pacienteId}/clinicos")
public class AntecedentesController {

    private final AntecedentesService service;

    AntecedentesController(AntecedentesService service) {
        this.service = service;
    }

    @GetMapping
    public Clinicos ver(@PathVariable UUID pacienteId) {
        return service.ver(pacienteId);
    }

    @PutMapping("/alergias/estado")
    public Clinicos estadoAlergias(@PathVariable UUID pacienteId, @Valid @RequestBody EstadoAlergias r) {
        return service.estadoAlergias(pacienteId, r.estado());
    }

    @PostMapping("/alergias")
    public Clinicos agregarAlergia(@PathVariable UUID pacienteId, @Valid @RequestBody AlergiaRequest r) {
        return service.guardarAlergia(pacienteId, null, r.tipo(), r.agente(), r.reaccion(), r.severidad());
    }

    @PutMapping("/alergias/{alergiaId}")
    public Clinicos editarAlergia(@PathVariable UUID pacienteId, @PathVariable UUID alergiaId,
                                  @Valid @RequestBody AlergiaRequest r) {
        return service.guardarAlergia(pacienteId, alergiaId, r.tipo(), r.agente(), r.reaccion(), r.severidad());
    }

    @DeleteMapping("/alergias/{alergiaId}")
    public Clinicos quitarAlergia(@PathVariable UUID pacienteId, @PathVariable UUID alergiaId) {
        return service.quitarAlergia(pacienteId, alergiaId);
    }

    @PostMapping("/antecedentes")
    public Clinicos agregarAntecedente(@PathVariable UUID pacienteId, @Valid @RequestBody AntecedenteRequest r) {
        return service.guardarAntecedente(pacienteId, null, r.tipo(), r.descripcion(), r.desde(), r.vigente());
    }

    @PutMapping("/antecedentes/{antecedenteId}")
    public Clinicos editarAntecedente(@PathVariable UUID pacienteId, @PathVariable UUID antecedenteId,
                                      @Valid @RequestBody AntecedenteRequest r) {
        return service.guardarAntecedente(pacienteId, antecedenteId, r.tipo(), r.descripcion(), r.desde(), r.vigente());
    }

    @DeleteMapping("/antecedentes/{antecedenteId}")
    public Clinicos quitarAntecedente(@PathVariable UUID pacienteId, @PathVariable UUID antecedenteId) {
        return service.quitarAntecedente(pacienteId, antecedenteId);
    }

    /** Borra el texto libre anterior de alergias o de antecedentes, una vez pasado a ítems. */
    @PostMapping("/texto-anterior/borrar")
    public Clinicos borrarTextoAnterior(@PathVariable UUID pacienteId, @RequestBody BorrarTextoAnterior r) {
        return service.borrarTextoAnterior(pacienteId, r.alergias(), r.antecedentes());
    }

    public record EstadoAlergias(@NotNull Paciente.EstadoAlergias estado) {
    }

    public record AlergiaRequest(@NotNull AlergiaPaciente.Tipo tipo, @NotBlank @Size(max = 200) String agente,
                                 @Size(max = 300) String reaccion, AlergiaPaciente.Severidad severidad) {
    }

    /** @param vigente null: vigente */
    public record AntecedenteRequest(@NotNull AntecedentePaciente.Tipo tipo, @NotBlank @Size(max = 500) String descripcion,
                                     @Size(max = 20) String desde, Boolean vigente) {
    }

    public record BorrarTextoAnterior(boolean alergias, boolean antecedentes) {
    }
}
