package com.galeno.identidad;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    /**
     * Si la persona tiene un solo consultorio, entra directo; si tiene varios, debe elegir con {@code /sesion}. Si
     * usa verificación en dos pasos (o debe configurarla), antes manda el código a {@code /mfa}.
     */
    @PostMapping("/login")
    public SesionResponse login(@Valid @RequestBody LoginRequest request) {
        return sesiones.login(request.email(), request.password(), request.equipo());
    }

    /** Con el token pendiente del login. */
    @PostMapping("/mfa")
    public SesionResponse mfa(@Valid @RequestBody CodigoMfa request) {
        return sesiones.completarMfa(request.codigo(), request.recordar());
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

    /** @param equipo el token de "recordar este equipo" que guardó el navegador para este correo, si hay */
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, @Size(max = 100) String equipo) {
    }

    /** @param codigo de la app (6 dígitos) o de recuperación (XXXX-XXXX) */
    public record CodigoMfa(@NotBlank @Size(max = 20) String codigo, boolean recordar) {
    }

    public record ElegirConsultorio(@NotNull UUID consultorioId) {
    }
}
