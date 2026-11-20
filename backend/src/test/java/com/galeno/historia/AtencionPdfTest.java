package com.galeno.historia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.galeno.identidad.IdentidadService.ConsultorioResumen;

class AtencionPdfTest {

    private static final ConsultorioResumen CONSULTORIO =
            new ConsultorioResumen(UUID.randomUUID(), "Consultorio Prueba", "900123456", ZoneId.of("America/Bogota"));

    @Test
    void elCuerpoSigueElOrdenDeLosBloquesDelRda() {
        Atencion a = Atencion.nueva(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Dra. Prueba", null,
                "Patológicos: Hipertensión arterial");
        a.registrarEncuentro("01", "01", "38", "Hogar");
        a.registrarProcedimientos(List.of(new Procedimiento("895100", "Electrocardiograma", 1, null, null, null)
                .conResultado("Ritmo sinusal")));
        a.registrarOrdenes(List.of(new OrdenMedica(OrdenMedica.Tipo.LABORATORIO, "903841", "Glucosa en suero", 1,
                "En ayunas", OrdenMedica.Prioridad.RUTINARIA)));
        a.registrarFactoresRiesgo(List.of(new FactorRiesgo(FactorRiesgo.Codigo.TABAQUISMO, "10 al día")));
        a.actualizar(new Atencion.Contenido("Control", null, "Patológicos: Hipertensión arterial", null, null, null, null,
                null, null, null, null, null, null, "Estable", "Continuar", List.of(new Diagnostico("I10X",
                "Hipertensión esencial", true, Diagnostico.Tipo.CONFIRMADO_REPETIDO))));

        StringBuilder b = new StringBuilder();
        AtencionPdf.cuerpo(b, a, CONSULTORIO, List.of(), "", new AtencionPdf.Extras("EPS Prueba · autorización 123",
                "01 · Intramural", null, "38 · Enfermedad general", List.of("Losartán 50 mg · 1 tableta cada 12 horas")));
        String html = b.toString();

        List<String> enOrden = List.of("Entidad responsable", "Datos del encuentro", "Motivo de consulta", "Antecedentes",
                "Diagnósticos (CIE-10)", "Medicamentos", "Procedimientos realizados", "Órdenes médicas",
                "Factores de riesgo", "Dra. Prueba");
        int previo = -1;
        for (String titulo : enOrden) {
            int i = html.indexOf(titulo);
            assertThat(i).as(titulo).isGreaterThan(previo);
            previo = i;
        }
        assertThat(html).contains("Ritmo sinusal", "Glucosa en suero", "Tabaquismo (10 al día)", "Entorno: Hogar",
                "01 · Intramural", "Losartán 50 mg");
    }
}
