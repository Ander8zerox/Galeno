package com.galeno.importacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.galeno.importacion.ListadoPacientes.Campo;
import com.galeno.importacion.ListadoPacientes.FilaPersona;
import com.galeno.importacion.ListadoPacientes.Mapeo;
import com.galeno.importacion.ListadoPacientes.OrdenNombre;
import com.galeno.pacientes.DatosPersona;

class ListadoPacientesTest {

    @Test
    void reconoceEncabezadosHabituales() {
        Map<Campo, Integer> m = ListadoPacientes.sugerir(List.of("Tipo Doc.", "Nro. Identificación", "Primer Nombre",
                "Segundo Nombre", "Primer Apellido", "Segundo Apellido", "Fecha de Nac.", "Género", "Celular", "Teléfono",
                "Correo electrónico", "EPS"));

        assertThat(m).containsExactlyInAnyOrderEntriesOf(Map.of(Campo.TIPO_DOCUMENTO, 0, Campo.NUMERO_DOCUMENTO, 1,
                Campo.PRIMER_NOMBRE, 2, Campo.SEGUNDO_NOMBRE, 3, Campo.PRIMER_APELLIDO, 4, Campo.SEGUNDO_APELLIDO, 5,
                Campo.FECHA_NACIMIENTO, 6, Campo.SEXO, 7, Campo.TELEFONO, 8, Campo.EMAIL, 10));
    }

    @Test
    void reconoceNombreCompletoYSuOrden() {
        assertThat(ListadoPacientes.sugerir(List.of("Cédula", "Paciente"))).containsEntry(Campo.NOMBRE_COMPLETO, 1)
                .containsEntry(Campo.NUMERO_DOCUMENTO, 0);
        assertThat(ListadoPacientes.reconocer("APELLIDOS Y NOMBRES")).isEqualTo(Campo.NOMBRE_COMPLETO);
        assertThat(ListadoPacientes.apellidosPrimero("APELLIDOS Y NOMBRES")).isTrue();
        assertThat(ListadoPacientes.reconocer("Nombres")).isEqualTo(Campo.NOMBRES);
        assertThat(ListadoPacientes.reconocer("Apellidos")).isEqualTo(Campo.APELLIDOS);
        assertThat(ListadoPacientes.reconocer("Observaciones")).isNull();
    }

    @Test
    void separaElNombreCompleto() {
        assertThat(ListadoPacientes.separarNombre("María José Peña Ruiz", OrdenNombre.NOMBRES_APELLIDOS))
                .containsExactly("María José", "Peña Ruiz");
        assertThat(ListadoPacientes.separarNombre("Carlos Peña Ruiz", OrdenNombre.NOMBRES_APELLIDOS))
                .containsExactly("Carlos", "Peña Ruiz");
        assertThat(ListadoPacientes.separarNombre("Peña Ruiz María José", OrdenNombre.APELLIDOS_NOMBRES))
                .containsExactly("María José", "Peña Ruiz");
        assertThat(ListadoPacientes.separarNombre("Ana de la Hoz", OrdenNombre.NOMBRES_APELLIDOS))
                .containsExactly("Ana", "de la Hoz");
        assertThat(ListadoPacientes.separarNombre("Cher", OrdenNombre.NOMBRES_APELLIDOS)).containsExactly("Cher", null);
    }

    @Test
    void normalizaValores() {
        assertThat(ListadoPacientes.tipoDocumento("C.C.")).isEqualTo("CC");
        assertThat(ListadoPacientes.tipoDocumento("Tarjeta de Identidad")).isEqualTo("TI");
        assertThat(ListadoPacientes.tipoDocumento("PPT")).isEqualTo("PT");
        assertThat(ListadoPacientes.tipoDocumento("XYZ")).isNull();
        assertThat(ListadoPacientes.numeroDocumento(" 1.020.304.050 ")).isEqualTo("1020304050");
        assertThat(ListadoPacientes.numeroDocumento("ab123456")).isEqualTo("AB123456");
        assertThat(ListadoPacientes.capitalizar("MARÍA JOSÉ DE LA HOZ")).isEqualTo("María José de la Hoz");
        assertThat(ListadoPacientes.capitalizar("McAllister")).isEqualTo("McAllister");
        assertThat(ListadoPacientes.sexo("Femenino")).isEqualTo("F");
        assertThat(ListadoPacientes.sexo("M")).isEqualTo("M");
        assertThat(ListadoPacientes.sexo("Hombre")).isEqualTo("M");
        assertThat(ListadoPacientes.telefono("(+57) 300-123-4567")).isEqualTo("+57 300 123 4567");
    }

    @Test
    void entiendeFechasDeExcelYEscritas() {
        assertThat(ListadoPacientes.fecha("33604")).isEqualTo(LocalDate.of(1992, 1, 1));
        assertThat(ListadoPacientes.fecha("15/03/1985")).isEqualTo(LocalDate.of(1985, 3, 15));
        assertThat(ListadoPacientes.fecha("1985-03-15")).isEqualTo(LocalDate.of(1985, 3, 15));
        assertThat(ListadoPacientes.fecha("1985-03-15 00:00:00")).isEqualTo(LocalDate.of(1985, 3, 15));
        assertThat(ListadoPacientes.fecha("5/3/1985")).isEqualTo(LocalDate.of(1985, 3, 5));
        assertThat(ListadoPacientes.fecha("31/02/1985")).isNull();
        assertThat(ListadoPacientes.fecha("ayer")).isNull();
    }

    @Test
    void interpretaUnaFila() {
        Mapeo mapeo = new Mapeo(Map.of(Campo.NUMERO_DOCUMENTO, 0, Campo.NOMBRE_COMPLETO, 1, Campo.FECHA_NACIMIENTO, 2,
                Campo.SEXO, 3, Campo.EMAIL, 4), "CC", OrdenNombre.NOMBRES_APELLIDOS);

        FilaPersona f = ListadoPacientes.interpretar(2, List.of("1.020.304.050", "MARIA JOSE PEÑA RUIZ", "15/03/1985", "F",
                "MARIA@CORREO.COM"), mapeo);

        assertThat(f.errores()).isEmpty();
        assertThat(f.tipoDocumento()).isEqualTo("CC");
        assertThat(f.numeroDocumento()).isEqualTo("1020304050");
        assertThat(f.datos()).isEqualTo(new DatosPersona("Maria Jose", "Peña Ruiz", LocalDate.of(1985, 3, 15), "F", null,
                "maria@correo.com"));
    }

    @Test
    void reportaErroresYAdvertenciasDeLaFila() {
        Mapeo mapeo = new Mapeo(Map.of(Campo.TIPO_DOCUMENTO, 0, Campo.NUMERO_DOCUMENTO, 1, Campo.NOMBRES, 2,
                Campo.APELLIDOS, 3, Campo.EMAIL, 4, Campo.FECHA_NACIMIENTO, 5), null, OrdenNombre.NOMBRES_APELLIDOS);

        FilaPersona f = ListadoPacientes.interpretar(7, List.of("Licencia", "", "Ana", "Ruiz", "no-es-correo", "2999-01-01"), mapeo);

        assertThat(f.errores()).containsExactly("Tipo de documento desconocido: Licencia", "Sin número de documento");
        assertThat(f.advertencias()).containsExactly("Fecha de nacimiento fuera de rango: 2999-01-01",
                "Correo no válido, no se importa: no-es-correo");
        assertThat(f.datos().email()).isNull();
        assertThat(f.datos().fechaNacimiento()).isNull();
    }
}
