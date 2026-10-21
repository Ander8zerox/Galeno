package com.galeno.identidad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.identidad.RegistroService.RegistroPublico;
import com.galeno.identidad.SesionService.SesionResponse;

/** Endpoints públicos del registro de consultorios. El token viaja en el cuerpo, nunca en la URL. */
@RestController
@RequestMapping("/api/v1/registro")
public class RegistroController {

    private final RegistroService registro;

    RegistroController(RegistroService registro) {
        this.registro = registro;
    }

    public record SolicitudRegistro(@NotBlank @Size(max = 200) String consultorioNombre, @Size(max = 30) String nit,
                                    @NotBlank @Size(max = 200) String nombre, @NotBlank @Email @Size(max = 190) String email,
                                    @NotBlank @Size(max = 200) String password, boolean esMedico,
                                    @Size(max = 50) String registroMedico, @Size(max = 120) String especialidad,
                                    boolean aceptaTerminos) {
    }

    public record RegistroSolicitado(String email) {
    }

    /** Siempre 202 si los datos son válidos, exista o no una cuenta con ese correo. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RegistroSolicitado solicitar(@Valid @RequestBody SolicitudRegistro r, HttpServletRequest request) {
        registro.solicitar(new RegistroService.Solicitud(r.consultorioNombre(), r.nit(), r.nombre(), r.email(),
                r.password(), r.esMedico(), r.registroMedico(), r.especialidad(), r.aceptaTerminos()), request.getRemoteAddr());
        return new RegistroSolicitado(r.email().strip().toLowerCase());
    }

    public record TokenRegistro(@NotBlank @Size(max = 100) String token) {
    }

    @PostMapping("/consultar")
    public RegistroPublico consultar(@Valid @RequestBody TokenRegistro r) {
        return registro.consultar(r.token());
    }

    /** {@code password} solo se pide si el correo ya tenía cuenta en Galeno. */
    public record ConfirmarRegistro(@NotBlank @Size(max = 100) String token, @Size(max = 200) String password) {
    }

    @PostMapping("/confirmar")
    public SesionResponse confirmar(@Valid @RequestBody ConfirmarRegistro r) {
        return registro.confirmar(r.token(), r.password());
    }
}
