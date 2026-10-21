package com.galeno.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.galeno.agenda.Disponibilidad.Intervalo;

class DisponibilidadTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate LUNES = LocalDate.of(2030, 3, 4);
    private static final List<Bloque> MANANA = List.of(new Bloque(1, LocalTime.of(8, 0), LocalTime.of(9, 0)));
    private static final Instant ANTES = Instant.parse("2030-01-01T00:00:00Z");

    private static Instant bogota(int hora, int minuto) {
        return LUNES.atTime(hora, minuto).atZone(BOGOTA).toInstant();
    }

    @Test
    void divideElBloqueEnCuposDeLaDuracion() {
        List<Intervalo> cupos = Disponibilidad.cupos(LUNES, BOGOTA, MANANA, 20, List.of(), ANTES);

        assertThat(cupos).extracting(Intervalo::inicio).containsExactly(bogota(8, 0), bogota(8, 20), bogota(8, 40));
    }

    @Test
    void noOfreceCuposQueSeCruzanConCitas() {
        List<Intervalo> ocupados = List.of(new Intervalo(bogota(8, 10), bogota(8, 30)));

        List<Intervalo> cupos = Disponibilidad.cupos(LUNES, BOGOTA, MANANA, 20, ocupados, ANTES);

        assertThat(cupos).extracting(Intervalo::inicio).containsExactly(bogota(8, 40));
    }

    @Test
    void noOfreceCuposPasadosNiDeOtroDia() {
        assertThat(Disponibilidad.cupos(LUNES, BOGOTA, MANANA, 20, List.of(), bogota(8, 30)))
                .extracting(Intervalo::inicio).containsExactly(bogota(8, 40));
        assertThat(Disponibilidad.cupos(LUNES.plusDays(1), BOGOTA, MANANA, 20, List.of(), ANTES)).isEmpty();
    }

    @Test
    void elCupoQueNoCabeCompletoNoSeOfrece() {
        assertThat(Disponibilidad.cupos(LUNES, BOGOTA, MANANA, 25, List.of(), ANTES)).hasSize(2);
    }

    @Test
    void validaQueLaCitaQuedeDentroDelHorario() {
        assertThat(Disponibilidad.dentroDelHorario(new Intervalo(bogota(8, 30), bogota(9, 0)), BOGOTA, MANANA)).isTrue();
        assertThat(Disponibilidad.dentroDelHorario(new Intervalo(bogota(8, 50), bogota(9, 10)), BOGOTA, MANANA)).isFalse();
        assertThat(Disponibilidad.dentroDelHorario(new Intervalo(bogota(7, 40), bogota(8, 0)), BOGOTA, MANANA)).isFalse();
    }
}
