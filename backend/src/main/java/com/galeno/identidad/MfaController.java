package com.galeno.identidad;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.galeno.identidad.MfaService.Configuracion;
import com.galeno.identidad.MfaService.Estado;
import com.galeno.shared.security.AuthUser;

/**
 * Verificación en dos pasos de la persona que tiene la sesión, en cualquiera de las tres entradas (consultorio,
 * plataforma, partners). {@code /configuracion} también sirve con el token pendiente del login, cuando se pide
 * configurarla antes de entrar.
 */
@RestController
@RequestMapping("/api/v1/mfa")
public class MfaController {

    private final MfaService mfa;

    MfaController(MfaService mfa) {
        this.mfa = mfa;
    }

    @GetMapping
    public Estado estado() {
        return mfa.estado(AuthUser.current().id());
    }

    /** Un secreto nuevo para escanear: no queda activo hasta confirmarlo con {@code /activacion}. */
    @PostMapping("/configuracion")
    public Configuracion configurar() {
        return mfa.configurar();
    }

    @PostMapping("/activacion")
    public CodigosRecuperacion activar(@Valid @RequestBody CodigoMfa request) {
        return new CodigosRecuperacion(mfa.activar(AuthUser.current().id(), request.codigo()));
    }

    @PostMapping("/codigos-recuperacion")
    public CodigosRecuperacion regenerar(@Valid @RequestBody CodigoMfa request) {
        return new CodigosRecuperacion(mfa.regenerarCodigos(AuthUser.current().id(), request.codigo()));
    }

    @PostMapping("/desactivacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@Valid @RequestBody CodigoMfa request) {
        mfa.desactivar(AuthUser.current().id(), request.codigo());
    }

    @DeleteMapping("/equipos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void olvidarEquipos() {
        mfa.olvidarEquipos(AuthUser.current().id());
    }

    /** @param codigo de la app (6 dígitos) o de recuperación (XXXX-XXXX) */
    public record CodigoMfa(@NotBlank @Size(max = 20) String codigo) {
    }

    public record CodigosRecuperacion(List<String> codigos) {
    }
}
