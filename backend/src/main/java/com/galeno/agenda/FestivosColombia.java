package com.galeno.agenda;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Los 18 festivos de Colombia, calculados (sin servicio externo): los fijos, los que la Ley 51 de 1983 (Ley Emiliani)
 * pasa al lunes siguiente, y los que dependen de la Pascua.
 */
public final class FestivosColombia {

    public record Festivo(LocalDate fecha, String nombre) {
    }

    private FestivosColombia() {
    }

    public static List<Festivo> del(int anio) {
        List<Festivo> f = new ArrayList<>();
        // Fijos: se celebran el día que caen
        f.add(new Festivo(LocalDate.of(anio, Month.JANUARY, 1), "Año Nuevo"));
        f.add(new Festivo(LocalDate.of(anio, Month.MAY, 1), "Día del Trabajo"));
        f.add(new Festivo(LocalDate.of(anio, Month.JULY, 20), "Día de la Independencia"));
        f.add(new Festivo(LocalDate.of(anio, Month.AUGUST, 7), "Batalla de Boyacá"));
        f.add(new Festivo(LocalDate.of(anio, Month.DECEMBER, 8), "Inmaculada Concepción"));
        f.add(new Festivo(LocalDate.of(anio, Month.DECEMBER, 25), "Navidad"));
        // Ley Emiliani: al lunes siguiente si no caen en lunes
        f.add(new Festivo(alLunes(LocalDate.of(anio, Month.JANUARY, 6)), "Reyes Magos"));
        f.add(new Festivo(alLunes(LocalDate.of(anio, Month.MARCH, 19)), "San José"));
        f.add(new Festivo(alLunes(LocalDate.of(anio, Month.JUNE, 29)), "San Pedro y San Pablo"));
        f.add(new Festivo(alLunes(LocalDate.of(anio, Month.AUGUST, 15)), "Asunción de la Virgen"));
        f.add(new Festivo(alLunes(LocalDate.of(anio, Month.OCTOBER, 12)), "Día de la Raza"));
        f.add(new Festivo(alLunes(LocalDate.of(anio, Month.NOVEMBER, 1)), "Todos los Santos"));
        f.add(new Festivo(alLunes(LocalDate.of(anio, Month.NOVEMBER, 11)), "Independencia de Cartagena"));
        // Según la Pascua: Jueves y Viernes Santo el día que caen; los otros tres, al lunes siguiente
        LocalDate pascua = pascua(anio);
        f.add(new Festivo(pascua.minusDays(3), "Jueves Santo"));
        f.add(new Festivo(pascua.minusDays(2), "Viernes Santo"));
        f.add(new Festivo(alLunes(pascua.plusDays(39)), "Ascensión del Señor"));
        f.add(new Festivo(alLunes(pascua.plusDays(60)), "Corpus Christi"));
        f.add(new Festivo(alLunes(pascua.plusDays(68)), "Sagrado Corazón"));
        f.sort(Comparator.comparing(Festivo::fecha));
        return f;
    }

    public static Optional<Festivo> en(LocalDate fecha) {
        return del(fecha.getYear()).stream().filter(x -> x.fecha().equals(fecha)).findFirst();
    }

    private static LocalDate alLunes(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** Domingo de Pascua (algoritmo de Meeus/Jones/Butcher, calendario gregoriano). */
    static LocalDate pascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }
}
