package com.galeno.agenda;

import java.util.UUID;

/**
 * Si una cita ya tiene una atención abierta en la historia clínica. Lo implementa el módulo de historia (agenda no
 * depende de él): una cita con atención no se marca como "no asistió" ni se cancela, o la agenda y la historia se
 * contradirían.
 */
public interface AtencionesDeCitas {

    boolean tieneAtencion(UUID citaId);
}
