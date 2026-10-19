package com.galeno.identidad;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.identidad.SesionService.ConsultorioOpcion;
import com.galeno.identidad.SesionService.MeResponse;
import com.galeno.identidad.SesionService.SesionResponse;
import com.galeno.shared.security.AuthUser;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SesionService sesiones;

    AuthController(SesionService sesiones) {
        this.sesiones = sesiones;
    }

    /** Si la persona tiene un solo consultorio, entra directo; si tiene varios, debe elegir con {@code /sesion}. */
    @PostMapping("/login")
    public SesionResponse login(@Valid @RequestBody LoginRequest request) {
        return sesiones.login(request.email(), request.password());
    }

    @GetMapping("/consultorios")
    public List<ConsultorioOpcion> consultorios() {
        return sesiones.consultorios(AuthUser.current().id());
    }

    /** Elegir consultorio tras el login, o cambiar de consultorio en medio de la sesión. */
    @PostMapping("/sesion")
    public SesionResponse sesion(@Valid @RequestBody ElegirConsultorio request) {
        return sesiones.elegir(AuthUser.current().id(), request.consultorioId());
    }

    @GetMapping("/me")
    public MeResponse me() {
        return sesiones.me(AuthUser.current());
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record ElegirConsultorio(@NotNull UUID consultorioId) {
    }
}
