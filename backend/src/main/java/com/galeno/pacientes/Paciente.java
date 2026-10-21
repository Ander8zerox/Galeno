package com.galeno.pacientes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import com.galeno.pacientes.DatosPersona.Cambio;

@Entity
@Table(name = "pacientes")
public class Paciente {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "tipo_documento", nullable = false, length = 5)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 30)
    private String numeroDocumento;

    @Column(nullable = false, length = 120)
    private String nombres;

    @Column(nullable = false, length = 120)
    private String apellidos;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(nullable = false, length = 1)
    private String sexo;

    @Column(length = 30)
    private String telefono;

    @Column(length = 190)
    private String email;

    @Column(length = 2000)
    private String alergias;

    @Column(length = 4000)
    private String antecedentes;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Creado por una importación sin nombre (el RIPS no lo trae): hay que completar sus datos. */
    @Column(name = "datos_incompletos", nullable = false)
    private boolean datosIncompletos;

    @Column(name = "lote_importacion_id")
    private UUID loteImportacionId;

    protected Paciente() {
    }

    public Paciente(String tipoDocumento, String numeroDocumento) {
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.creadoEn = Instant.now();
    }

    /** Paciente creado desde una importación: se identifica por documento hasta que alguien complete el nombre. */
    static Paciente importado(String tipoDocumento, String numeroDocumento, LocalDate fechaNacimiento, String sexo,
                              UUID loteImportacionId) {
        Paciente p = new Paciente(tipoDocumento, numeroDocumento);
        p.nombres = "Paciente";
        p.apellidos = tipoDocumento + " " + numeroDocumento;
        p.fechaNacimiento = fechaNacimiento;
        p.sexo = sexo;
        p.datosIncompletos = true;
        p.loteImportacionId = loteImportacionId;
        p.actualizadoEn = p.creadoEn;
        return p;
    }

    /** Paciente nuevo que llega en un listado de otro sistema (con nombre, a diferencia del RIPS). */
    static Paciente desdeListado(String tipoDocumento, String numeroDocumento, DatosPersona d, UUID loteImportacionId) {
        Paciente p = new Paciente(tipoDocumento, numeroDocumento);
        p.nombres = d.nombres();
        p.apellidos = d.apellidos();
        p.fechaNacimiento = d.fechaNacimiento();
        p.sexo = d.sexo();
        p.telefono = d.telefono();
        p.email = d.email();
        p.loteImportacionId = loteImportacionId;
        p.actualizadoEn = p.creadoEn;
        return p;
    }

    /**
     * Qué cambiaría un listado de otro sistema. Si el paciente tiene datos incompletos (vino del RIPS sin nombre),
     * el listado pone el nombre; si no, solo llena teléfono y correo vacíos. Nunca reemplaza lo que alguien ya
     * registró en Galeno: las diferencias se muestran en la revisión, no se aplican.
     */
    List<Cambio> cambiosDesde(DatosPersona d) {
        List<Cambio> cambios = new ArrayList<>();
        if (datosIncompletos && d.nombres() != null && d.apellidos() != null) {
            cambios.add(new Cambio("nombres", nombres, d.nombres()));
            cambios.add(new Cambio("apellidos", apellidos, d.apellidos()));
            cambios.add(new Cambio("datosIncompletos", "true", "false"));
        }
        if (telefono == null && d.telefono() != null) {
            cambios.add(new Cambio("telefono", null, d.telefono()));
        }
        if (email == null && d.email() != null) {
            cambios.add(new Cambio("email", null, d.email()));
        }
        return cambios;
    }

    /** Diferencias con Galeno que el listado no va a aplicar (para avisar en la revisión). */
    List<String> diferenciasCon(DatosPersona d) {
        List<String> diferencias = new ArrayList<>();
        if (!datosIncompletos && d.nombres() != null && d.apellidos() != null
                && !(d.nombres() + " " + d.apellidos()).equalsIgnoreCase(nombres + " " + apellidos)) {
            diferencias.add("El nombre en el listado es «" + d.nombres() + " " + d.apellidos() + "»; se conserva «"
                    + nombres + " " + apellidos + "»");
        }
        if (d.fechaNacimiento() != null && !d.fechaNacimiento().equals(fechaNacimiento)) {
            diferencias.add("La fecha de nacimiento en el listado es " + d.fechaNacimiento() + "; se conserva " + fechaNacimiento);
        }
        if (d.sexo() != null && !d.sexo().equals(sexo)) {
            diferencias.add("El sexo en el listado es " + d.sexo() + "; se conserva " + sexo);
        }
        if (telefono != null && d.telefono() != null && !d.telefono().equals(telefono)) {
            diferencias.add("El teléfono en el listado es " + d.telefono() + "; se conserva " + telefono);
        }
        if (email != null && d.email() != null && !d.email().equalsIgnoreCase(email)) {
            diferencias.add("El correo en el listado es " + d.email() + "; se conserva " + email);
        }
        return diferencias;
    }

    void aplicar(List<Cambio> cambios) {
        cambios.forEach(c -> poner(c.campo(), c.despues()));
        this.actualizadoEn = Instant.now();
    }

    /** Deshace los cambios de una importación, salvo en los campos que alguien editó después. */
    void revertir(List<Cambio> cambios) {
        for (Cambio c : cambios) {
            if (Objects.equals(valor(c.campo()), c.despues())) {
                poner(c.campo(), c.antes());
            }
        }
        this.actualizadoEn = Instant.now();
    }

    private String valor(String campo) {
        return switch (campo) {
            case "nombres" -> nombres;
            case "apellidos" -> apellidos;
            case "telefono" -> telefono;
            case "email" -> email;
            case "datosIncompletos" -> String.valueOf(datosIncompletos);
            default -> throw new IllegalArgumentException(campo);
        };
    }

    private void poner(String campo, String valor) {
        switch (campo) {
            case "nombres" -> nombres = valor;
            case "apellidos" -> apellidos = valor;
            case "telefono" -> telefono = valor;
            case "email" -> email = valor;
            case "datosIncompletos" -> datosIncompletos = Boolean.parseBoolean(valor);
            default -> throw new IllegalArgumentException(campo);
        }
    }

    void actualizarDemograficos(String nombres, String apellidos, LocalDate fechaNacimiento, String sexo,
                                String telefono, String email) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.fechaNacimiento = fechaNacimiento;
        this.sexo = sexo;
        this.telefono = telefono;
        this.email = email;
        this.datosIncompletos = false;
        this.actualizadoEn = Instant.now();
    }

    /** Solo personal con permiso HISTORIA_VER (médicos). */
    void actualizarClinicos(String alergias, String antecedentes) {
        this.alergias = alergias;
        this.antecedentes = antecedentes;
        this.actualizadoEn = Instant.now();
    }

    public int edad() {
        return Period.between(fechaNacimiento, LocalDate.now()).getYears();
    }

    /**
     * Contexto clínico para la IA sin datos que identifiquen al paciente (nombre, documento,
     * contacto): se envía solo lo necesario para el razonamiento clínico.
     */
    public String resumenAnonimizado() {
        return """
                Paciente: %d años, sexo %s.
                Alergias conocidas: %s
                Antecedentes: %s""".formatted(edad(), sexo, valueOrNone(alergias), valueOrNone(antecedentes));
    }

    private static String valueOrNone(String value) {
        return value == null || value.isBlank() ? "no registradas" : value.strip();
    }

    public UUID getId() {
        return id;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public String getSexo() {
        return sexo;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getEmail() {
        return email;
    }

    public String getAlergias() {
        return alergias;
    }

    public boolean isDatosIncompletos() {
        return datosIncompletos;
    }

    public UUID getLoteImportacionId() {
        return loteImportacionId;
    }

    public String getAntecedentes() {
        return antecedentes;
    }
}
