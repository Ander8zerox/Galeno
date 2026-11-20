package com.galeno.pacientes;

import java.time.LocalDate;
import java.time.Period;

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
        @NotBlank @Size(max = 120) @Pattern(regexp = Nombres.PATRON, message = Nombres.MENSAJE) String nombres,
        @NotBlank @Size(max = 120) @Pattern(regexp = Nombres.PATRON, message = Nombres.MENSAJE) String apellidos,
        @NotNull @Past LocalDate fechaNacimiento,
        @NotBlank @Pattern(regexp = "F|M|I", message = "Sexo debe ser F, M o I") String sexo,
        @NotBlank @Size(max = 30) @Pattern(regexp = Telefonos.PATRON, message = Telefonos.MENSAJE) String telefono,
        @Email @Size(max = 190) @Pattern(regexp = Correos.PATRON_OPCIONAL, message = Correos.MENSAJE) String email,
        @Size(max = 2000) String alergias,
        @Size(max = 4000) String antecedentes,
        /** El paciente pidió no recibir correos sobre sus citas; null: se queda como estaba. */
        Boolean sinCorreosCitas,
        /**
         * El paciente no tiene correo. El correo es obligatorio salvo con esta marca: así nadie inventa uno para pasar
         * el formulario (un correo inventado rebota, y los rebotes ponen en riesgo el envío de correos de todos).
         */
        Boolean sinCorreo,
        /** Quien responde por un menor de edad (obligatorio si tiene menos de 18 años). */
        @Size(max = 200) @Pattern(regexp = Nombres.PATRON_OPCIONAL, message = Nombres.MENSAJE) String responsableNombre,
        @Size(max = 40) String responsableParentesco,
        @Pattern(regexp = TiposDocumento.PATRON, message = "Tipo de documento no válido") String responsableTipoDocumento,
        @Size(max = 30) String responsableNumeroDocumento,
        @Size(max = 30) @Pattern(regexp = Telefonos.PATRON, message = Telefonos.MENSAJE) String responsableTelefono,
        /** Prioridad en la atención; null: se queda como estaba. Gestante solo aplica a sexo F. */
        Boolean gestante,
        Boolean discapacidad,
        /**
         * El paciente autorizó el tratamiento de sus datos (Ley 1581 de 2012). Obligatorio al crear; al editar, true
         * registra la autorización si aún no estaba (la ya registrada no cambia).
         */
        Boolean autorizaDatos,
        @Pattern(regexp = "ESCRITA|VERBAL|DIGITAL", message = "Medio de autorización no válido") String medioAutorizacion) {

    /** El correo que se guarda: ninguno si el paciente no tiene. */
    public String emailAGuardar() {
        return Boolean.TRUE.equals(sinCorreo) || email == null || email.isBlank() ? null : email.strip();
    }

    @JsonIgnore
    @AssertTrue(message = "Escriba el correo o marque que el paciente no tiene correo")
    public boolean isCorreoResuelto() {
        return Boolean.TRUE.equals(sinCorreo) || (email != null && !email.isBlank());
    }

    public boolean esMenor() {
        return fechaNacimiento != null && Period.between(fechaNacimiento, LocalDate.now()).getYears() < 18;
    }

    @JsonIgnore
    @AssertTrue(message = "Para un menor de edad, escriba el nombre, el parentesco y el teléfono de su responsable")
    public boolean isResponsableCompleto() {
        return !esMenor() || (lleno(responsableNombre) && lleno(responsableParentesco) && lleno(responsableTelefono));
    }

    @JsonIgnore
    @AssertTrue(message = "Indique cómo autorizó el paciente el tratamiento de datos (escrita, verbal o digital)")
    public boolean isMedioAutorizacionIndicado() {
        return !Boolean.TRUE.equals(autorizaDatos) || lleno(medioAutorizacion);
    }

    private static boolean lleno(String s) {
        return s != null && !s.isBlank();
    }
}
