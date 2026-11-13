package com.galeno.shared.web;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.galeno.shared.tenancy.TenantContext;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Topes de negocio por consultorio en 24 horas (ventana deslizante): cuántas citas y pacientes se pueden crear. Muy
 * por encima de lo que hace un consultorio real; frenan a un bot (o a una cuenta comprometida) que crea registros
 * en masa, y con ellos los correos a pacientes que dañarían la reputación del remitente en SES.
 *
 * <p>En memoria: se reinician al reiniciar la API, y con varias instancias el tope se multiplica. Basta para frenar
 * abusos; la importación de historias no pasa por aquí (crea muchos pacientes a propósito).
 */
@Component
public class TopesDiarios {

    private static final Logger log = LoggerFactory.getLogger(TopesDiarios.class);
    static final Duration VENTANA = Duration.ofHours(24);

    private final LimiteIntentos conteos;
    private final LimiteIntentos avisos = new LimiteIntentos();
    private final int citas;
    private final int pacientes;

    @Autowired
    public TopesDiarios(@Value("${app.limites.citas-por-dia:500}") int citas,
                        @Value("${app.limites.pacientes-por-dia:300}") int pacientes) {
        this(new LimiteIntentos(), citas, pacientes);
    }

    TopesDiarios(LimiteIntentos conteos, int citas, int pacientes) {
        this.conteos = conteos;
        this.citas = citas;
        this.pacientes = pacientes;
    }

    /** Antes de guardar una cita nueva (no al reprogramar). */
    public void cita() {
        consumir("citas", citas, "citas");
    }

    /** Antes de guardar un paciente creado a mano (no los importados). */
    public void paciente() {
        consumir("pacientes", pacientes, "pacientes");
    }

    private void consumir(String recurso, int tope, String nombre) {
        UUID consultorio = TenantContext.get();
        if (consultorio == null || TenantContext.SYSTEM.equals(consultorio)) {
            return;
        }
        String clave = recurso + ":" + consultorio;
        if (!conteos.permitir(clave, tope, VENTANA)) {
            if (avisos.permitir(clave, 1, Duration.ofHours(1))) {
                log.warn("Tope diario de {} alcanzado en el consultorio {} ({} en 24 horas)", recurso, consultorio, tope);
            }
            throw ApiProblem.tooManyRequests("El consultorio llegó al tope de " + tope + " " + nombre
                    + " creados en 24 horas. Si necesita más, escriba a soporte.");
        }
    }
}
