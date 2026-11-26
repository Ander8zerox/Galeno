package com.galeno.importacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.galeno.historia.Diagnostico;
import com.galeno.importacion.LectorTabla.Tabla;
import com.galeno.importacion.PlantillaGalenum.AtencionFila;
import com.galeno.importacion.PlantillaGalenum.ColA;
import com.galeno.importacion.PlantillaGalenum.ColP;
import com.galeno.importacion.PlantillaGalenum.PacienteFila;

class PlantillaGalenumTest {

    private static final List<String> ENC_P = Arrays.stream(ColP.values()).map(c -> c.titulo + (c.obligatoria ? " *" : "")).toList();
    private static final List<String> ENC_A = Arrays.stream(ColA.values()).map(c -> c.titulo + (c.obligatoria ? " *" : "")).toList();

    @Test
    void leePacientesYAtencionesConSuContenido() {
        List<String> p = filaP(Map.of(ColP.TIPO_DOCUMENTO, "Cédula", ColP.NUMERO_DOCUMENTO, "1.020.304", ColP.PRIMER_NOMBRE, "ANA",
                ColP.SEGUNDO_NOMBRE, "MARÍA", ColP.PRIMER_APELLIDO, "PÉREZ", ColP.FECHA_NACIMIENTO, "05/03/1990", ColP.SEXO, "F",
                ColP.ALERGIAS, "Penicilina", ColP.RESPONSABLE_NOMBRE, "LUIS PÉREZ", ColP.RESPONSABLE_PARENTESCO, "padre"));
        List<String> a = filaA(Map.ofEntries(Map.entry(ColA.TIPO_DOCUMENTO, "CC"), Map.entry(ColA.NUMERO_DOCUMENTO, "1020304"),
                Map.entry(ColA.FECHA, "20/11/2025"), Map.entry(ColA.HORA, "2:30 pm"), Map.entry(ColA.ID_ANTERIOR, "h-17"),
                Map.entry(ColA.PROFESIONAL, "Jairo Guerrero"), Map.entry(ColA.MOTIVO, "Tos"), Map.entry(ColA.FC, "80"),
                Map.entry(ColA.PESO, "44,5"), Map.entry(ColA.DX_PRINCIPAL, "j45.9"), Map.entry(ColA.DX_1, "R05"),
                Map.entry(ColA.TIPO_DX, "Confirmado nuevo"), Map.entry(ColA.IMPRESION, "Asma"),
                Map.entry(ColA.PLAN, "Control en un mes"), Map.entry(ColA.MEDICAMENTOS, "Salbutamol")));

        PlantillaGalenum.Resultado r = PlantillaGalenum.leer(libro(List.of(p), List.of(a)));

        PacienteFila paciente = r.pacientes().get("CC 1020304");
        assertThat(paciente.errores).isEmpty();
        assertThat(paciente.persona.datos().nombres()).isEqualTo("Ana María");
        assertThat(paciente.persona.datos().apellidos()).isEqualTo("Pérez");
        assertThat(paciente.extras.alergias()).isEqualTo("Penicilina");
        assertThat(paciente.extras.responsableNombre()).isEqualTo("Luis Pérez");
        assertThat(paciente.extras.responsableParentesco()).isEqualTo("Padre");

        AtencionFila at = paciente.atenciones.getFirst();
        assertThat(at.fecha()).isEqualTo(LocalDateTime.of(2025, 11, 20, 14, 30));
        assertThat(at.clave()).isEqualTo("plantilla:h-17");
        assertThat(at.fc()).isEqualTo(80);
        assertThat(at.peso()).isEqualByComparingTo(new BigDecimal("44.5"));
        assertThat(at.diagnosticos()).extracting(PlantillaGalenum.Dx::codigo).containsExactly("J459", "R05");
        assertThat(at.diagnosticos().getFirst().principal()).isTrue();
        assertThat(at.diagnosticos().getFirst().tipo()).isEqualTo(Diagnostico.Tipo.CONFIRMADO_NUEVO);
        assertThat(at.analisis()).isEqualTo("Impresión diagnóstica: Asma");
        assertThat(at.plan()).isEqualTo("Control en un mes\n\nMedicamentos formulados: Salbutamol");
    }

    @Test
    void lasFilasConProblemasNoDetienenElResto() {
        List<String> sinProfesional = filaA(Map.of(ColA.TIPO_DOCUMENTO, "CC", ColA.NUMERO_DOCUMENTO, "1", ColA.FECHA, "01/01/2025"));
        List<String> fueraDeRango = filaA(Map.of(ColA.TIPO_DOCUMENTO, "CC", ColA.NUMERO_DOCUMENTO, "1", ColA.FECHA, "02/01/2025",
                ColA.PROFESIONAL, "Dr. X", ColA.FC, "12028", ColA.DX_PRINCIPAL, "ZZZ", ColA.MOTIVO, "x".repeat(1200)));
        List<String> sinDocumento = filaA(Map.of(ColA.FECHA, "03/01/2025", ColA.PROFESIONAL, "Dr. X"));

        PlantillaGalenum.Resultado r = PlantillaGalenum.leer(libro(List.of(), List.of(sinProfesional, fueraDeRango, sinDocumento)));

        PacienteFila p = r.pacientes().get("CC 1");
        assertThat(p.atenciones).hasSize(1);
        AtencionFila a = p.atenciones.getFirst();
        assertThat(a.fc()).isNull();
        assertThat(a.diagnosticos()).isEmpty();
        assertThat(a.motivo()).hasSize(PlantillaGalenum.MAX_MOTIVO);
        assertThat(a.enfermedadActual()).startsWith("Motivo de consulta (texto completo): xxx");
        assertThat(p.advertencias).anyMatch(m -> m.contains("fila 2 no se importa") && m.contains("profesional"))
                .anyMatch(m -> m.contains("FC fuera de rango"))
                .anyMatch(m -> m.contains("CIE-10 no válido"));
        assertThat(r.erroresSueltos()).anyMatch(m -> m.startsWith("Atenciones, fila 4"));
        assertThat(r.omitidas()).isEqualTo(2);
        // Sin ID del sistema anterior, la clave sale del contenido: la misma fila da la misma clave
        assertThat(a.clave()).startsWith("plantilla:").hasSize("plantilla:".length() + 40);
    }

    @Test
    void unNombreConNumerosEsError() {
        List<String> p = filaP(Map.of(ColP.TIPO_DOCUMENTO, "CC", ColP.NUMERO_DOCUMENTO, "9", ColP.PRIMER_NOMBRE, "Ana2",
                ColP.PRIMER_APELLIDO, "Pérez", ColP.FECHA_NACIMIENTO, "05/03/1990", ColP.SEXO, "F"));
        PacienteFila paciente = PlantillaGalenum.leer(libro(List.of(p), List.of())).pacientes().get("CC 9");
        assertThat(paciente.errores).anyMatch(e -> e.contains("Ana2"));
    }

    @Test
    void exigeLasHojasYLasColumnasObligatorias() {
        assertThatThrownBy(() -> PlantillaGalenum.leer(Map.of("Hoja1", new Tabla(List.of("a"), List.of()))))
                .hasMessageContaining("Pacientes").hasMessageContaining("Atenciones");
        Map<String, Tabla> sinProfesional = new LinkedHashMap<>();
        sinProfesional.put("Pacientes", new Tabla(ENC_P, List.of()));
        sinProfesional.put("Atenciones", new Tabla(ENC_A.stream().filter(h -> !h.startsWith("Profesional")).toList(), List.of()));
        assertThatThrownBy(() -> PlantillaGalenum.leer(sinProfesional)).hasMessageContaining("Profesional que atendió");
    }

    /** La plantilla que se descarga (tools/plantilla-importacion/generar.mjs) tiene que leerse sin errores. */
    @Test
    void laPlantillaDescargableTieneLasColumnasQueSeLeen() throws java.io.IOException {
        java.nio.file.Path ruta = java.nio.file.Path.of("../frontend/public/plantillas/plantilla-importacion-galenum.xlsx");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(ruta), "sin el frontend al lado");
        Map<String, Tabla> hojas = LectorTabla.leerHojas("plantilla.xlsx", java.nio.file.Files.readAllBytes(ruta));
        assertThat(hojas.get("Pacientes").encabezados()).hasSize(ColP.values().length);
        assertThat(hojas.get("Atenciones").encabezados()).hasSize(ColA.values().length);
        assertThat(PlantillaGalenum.leer(hojas).pacientes()).isEmpty();
    }

    @Test
    void reconoceHoras() {
        assertThat(PlantillaGalenum.hora("08:05")).isEqualTo(LocalTime.of(8, 5));
        assertThat(PlantillaGalenum.hora("12:10 a. m.")).isEqualTo(LocalTime.of(0, 10));
        assertThat(PlantillaGalenum.hora("0.5")).isEqualTo(LocalTime.of(12, 0));
        assertThat(PlantillaGalenum.hora("25:00")).isNull();
    }

    private static Map<String, Tabla> libro(List<List<String>> pacientes, List<List<String>> atenciones) {
        Map<String, Tabla> hojas = new LinkedHashMap<>();
        hojas.put("Instrucciones", new Tabla(List.of("Plantilla"), List.of()));
        hojas.put("Pacientes", new Tabla(ENC_P, pacientes));
        hojas.put("Atenciones", new Tabla(ENC_A, atenciones));
        return hojas;
    }

    private static List<String> filaP(Map<ColP, String> valores) {
        List<String> f = new ArrayList<>();
        for (ColP c : ColP.values()) {
            f.add(valores.getOrDefault(c, ""));
        }
        return f;
    }

    private static List<String> filaA(Map<ColA, String> valores) {
        List<String> f = new ArrayList<>();
        for (ColA c : ColA.values()) {
            f.add(valores.getOrDefault(c, ""));
        }
        return f;
    }
}
