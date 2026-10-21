package com.galeno.pacientes;

import java.time.LocalDate;

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
        @Size(max = 30) String telefono,
        @Email @Size(max = 190) String email,
        @Size(max = 2000) String alergias,
        @Size(max = 4000) String antecedentes) {
}
