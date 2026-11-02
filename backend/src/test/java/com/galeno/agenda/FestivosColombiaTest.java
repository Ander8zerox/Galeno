package com.galeno.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.galeno.agenda.FestivosColombia.Festivo;

class FestivosColombiaTest {

    @Test
    void pascuaDeVariosAnios() {
        assertThat(FestivosColombia.pascua(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(FestivosColombia.pascua(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(FestivosColombia.pascua(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
    }

    /** Calendario oficial de 2026. */
    @Test
    void losDieciochoFestivosDe2026() {
        assertThat(FestivosColombia.del(2026)).extracting(Festivo::fecha).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 3, 23),
                LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 7),
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 25));
    }

    @Test
    void hoyEsTodosLosSantos() {
        assertThat(FestivosColombia.en(LocalDate.of(2026, 11, 2))).map(Festivo::nombre).contains("Todos los Santos");
        assertThat(FestivosColombia.en(LocalDate.of(2026, 11, 3))).isEmpty();
    }
}
