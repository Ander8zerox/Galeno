package com.galeno.pacientes;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PacienteRequest(
        @NotBlank @Pattern(regexp = TiposDocumento.PATRON, message = "Tipo de documento no válido") String tipoDocumento,
        @NotBlank @Size(max = 30) String numeroDocumento,
        @NotBlank @Size(max = 120) String nombres,
        @NotBlank @Size(max = 120) String apellidos,
        @NotNull @Past LocalDate fechaNacimiento,
        @NotBlank @Pattern(regexp = "F|M|I", message = "Sexo debe ser F, M o I") String sexo,
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "\\+?[0-9 ()-]{7,30}", message = "Escriba un teléfono válido: solo números, al menos 7")
        String telefono,
        @Email @Size(max = 190) String email,
        @Size(max = 2000) String alergias,
        @Size(max = 4000) String antecedentes,
        /** El paciente pidió no recibir correos sobre sus citas; null: se queda como estaba. */
        Boolean sinCorreosCitas,
        /**
         * El paciente no tiene correo. El correo es obligatorio salvo con esta marca: así nadie inventa uno para pasar
         * el formulario (un correo inventado rebota, y los rebotes ponen en riesgo el envío de correos de todos).
         */
        Boolean sinCorreo) {

    /** El correo que se guarda: ninguno si el paciente no tiene. */
    public String emailAGuardar() {
        return Boolean.TRUE.equals(sinCorreo) || email == null || email.isBlank() ? null : email.strip();
    }

    @JsonIgnore
    @AssertTrue(message = "Escriba el correo o marque que el paciente no tiene correo")
    public boolean isCorreoResuelto() {
        return Boolean.TRUE.equals(sinCorreo) || (email != null && !email.isBlank());
    }
}
