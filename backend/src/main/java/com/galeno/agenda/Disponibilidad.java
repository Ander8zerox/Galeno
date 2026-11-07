package com.galeno.agenda;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Cálculo puro de cupos (sin base de datos): el horario está en hora local, las citas en UTC. */
final class Disponibilidad {

    private Disponibilidad() {
    }

    record Intervalo(Instant inicio, Instant fin) {

        boolean cruzaCon(Intervalo otro) {
            return inicio.isBefore(otro.fin) && fin.isAfter(otro.inicio);
        }
    }

    /** Cupos libres de un día: se recorre cada bloque en pasos de la duración de la cita. */
    static List<Intervalo> cupos(LocalDate fecha, ZoneId zona, List<Bloque> bloques, int duracionMinutos,
                                 List<Intervalo> ocupados, Instant ahora) {
        return espacios(fecha, zona, bloques, duracionMinutos, duracionMinutos, ocupados, ahora);
    }

    /**
     * Espacios libres de {@code minutos} en un día. Empiezan donde empieza un cupo (en pasos de la duración del cupo,
     * como los ofrece la agenda) y caben completos dentro de una franja del horario, sin cruzarse con otra cita.
     */
    static List<Intervalo> espacios(LocalDate fecha, ZoneId zona, List<Bloque> bloques, int duracionCupo, int minutos,
                                    List<Intervalo> ocupados, Instant ahora) {
        Duration paso = Duration.ofMinutes(duracionCupo);
        Duration largo = Duration.ofMinutes(minutos);
        List<Intervalo> libres = new ArrayList<>();
        bloques.stream()
                .filter(b -> b.dia() == fecha.getDayOfWeek())
                .sorted(Comparator.comparing(Bloque::getHoraInicio))
                .forEach(b -> {
                    ZonedDateTime cursor = fecha.atTime(b.getHoraInicio()).atZone(zona);
                    ZonedDateTime finBloque = fecha.atTime(b.getHoraFin()).atZone(zona);
                    while (!cursor.plus(largo).isAfter(finBloque)) {
                        Intervalo espacio = new Intervalo(cursor.toInstant(), cursor.plus(largo).toInstant());
                        if (espacio.inicio().isAfter(ahora) && ocupados.stream().noneMatch(espacio::cruzaCon)) {
                            libres.add(espacio);
                        }
                        cursor = cursor.plus(paso);
                    }
                });
        return libres;
    }

    /** ¿La cita cae completa dentro de un bloque del horario? */
    static boolean dentroDelHorario(Intervalo cita, ZoneId zona, List<Bloque> bloques) {
        ZonedDateTime inicio = cita.inicio().atZone(zona);
        ZonedDateTime fin = cita.fin().atZone(zona);
        if (!inicio.toLocalDate().equals(fin.toLocalDate())) {
            return false; // los bloques no cruzan la medianoche
        }
        LocalTime desde = inicio.toLocalTime();
        LocalTime hasta = fin.toLocalTime();
        return bloques.stream().anyMatch(b -> b.dia() == inicio.getDayOfWeek()
                && !desde.isBefore(b.getHoraInicio()) && !hasta.isAfter(b.getHoraFin()) && desde.isBefore(hasta));
    }
}
