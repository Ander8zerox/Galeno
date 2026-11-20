package com.galeno.agenda;

import java.util.List;
import java.util.UUID;

/**
 * Los datos mínimos del RIPS que una cita necesita para confirmarse (pagador y tipo de usuario del paciente). Lo
 * implementa el módulo de RIPS (agenda no depende de él); con el módulo inactivo no falta nada.
 */
public interface DatosRipsDeCitas {

    /** Lo que falta (por ejemplo, "Pagador"); vacía si está completo o el módulo de RIPS no está activo. */
    List<String> faltanParaConfirmar(UUID pacienteId);
}
