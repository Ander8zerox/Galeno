package com.galeno.pacientes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.shared.security.AuthUser;
import com.galeno.shared.security.Permiso;

@RestController
@RequestMapping("/api/v1/pacientes")
public class PacienteController {

    private final PacienteService service;
    private final com.galeno.shared.correo.SupresionCorreos supresion;

    PacienteController(PacienteService service, com.galeno.shared.correo.SupresionCorreos supresion) {
        this.supresion = supresion;
        this.service = service;
    }

    @GetMapping
    public PacientePage list(@RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "false") boolean incompletos,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        Page<Paciente> result = service.search(q, incompletos, PageRequest.of(page, Math.min(size, 100), Sort.by("apellidos", "nombres")));
        return new PacientePage(result.map(p -> PacienteResponse.of(p, clinicos())).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements());
    }

    @GetMapping("/{id}")
    public PacienteResponse get(@PathVariable UUID id) {
        return conCorreo(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PacienteResponse create(@Valid @RequestBody PacienteRequest request) {
        return conCorreo(service.create(request, clinicos()));
    }

    @PutMapping("/{id}")
    public PacienteResponse update(@PathVariable UUID id, @Valid @RequestBody PacienteRequest request) {
        return conCorreo(service.update(id, request, clinicos()));
    }

    /** Una sola ficha: con el estado de su correo (en las listas no se consulta, para no hacer una consulta por fila). */
    private PacienteResponse conCorreo(Paciente p) {
        return PacienteResponse.of(p, clinicos(), supresion.motivo(p.getEmail()).orElse(null));
    }

    /** Los datos clínicos solo los ve y edita quien tiene HISTORIA_VER (no el secretario ni el administrador). */
    private static boolean clinicos() {
        return AuthUser.current().puede(Permiso.HISTORIA_VER);
    }

    /** {@code alergias} y {@code antecedentes} van en null para quien no tiene permiso de historia clínica. */
    public record PacienteResponse(UUID id, String tipoDocumento, String numeroDocumento, String nombres,
                                   String apellidos, LocalDate fechaNacimiento, int edad, String sexo,
                                   String telefono, String email, String alergias, String antecedentes,
                                   boolean datosIncompletos, boolean sinCorreosCitas, String correoRechazado) {

        static PacienteResponse of(Paciente p, boolean clinicos) {
            return of(p, clinicos, null);
        }

        /** @param correoRechazado por qué no se le pueden enviar correos (rebotó o se quejó); null si no hay problema */
        static PacienteResponse of(Paciente p, boolean clinicos, String correoRechazado) {
            return new PacienteResponse(p.getId(), p.getTipoDocumento(), p.getNumeroDocumento(), p.getNombres(),
                    p.getApellidos(), p.getFechaNacimiento(), p.edad(), p.getSexo(), p.getTelefono(), p.getEmail(),
                    clinicos ? p.getAlergias() : null, clinicos ? p.getAntecedentes() : null, p.isDatosIncompletos(),
                    p.isSinCorreosCitas(), correoRechazado);
        }
    }

    /** Formato de paginación uniforme para toda la API. */
    public record PacientePage(List<PacienteResponse> items, int page, int size, long total) {
    }
}
