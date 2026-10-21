package com.galeno.importacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import com.galeno.importacion.RipsParser.Archivo;
import com.galeno.importacion.RipsParser.AtencionRips;
import com.galeno.importacion.RipsParser.DxRips;
import com.galeno.importacion.RipsParser.PacienteRips;
import com.galeno.importacion.RipsParser.Resultado;

class RipsParserTest {

    private final RipsParser parser = new RipsParser(JsonMapper.builder().build());

    private static final String FACTURA_1 = """
            {
              "numDocumentoIdObligado": "900123456",
              "numFactura": "FE-1001",
              "usuarios": [
                {
                  "tipoDocumentoIdentificacion": "CC",
                  "numDocumentoIdentificacion": "1020304050",
                  "tipoUsuario": "01",
                  "fechaNacimiento": "1985-04-12",
                  "codSexo": "F",
                  "codMunicipioResidencia": "11001",
                  "consecutivo": 1,
                  "servicios": {
                    "consultas": [
                      {
                        "codConsulta": "890201",
                        "fechaInicioAtencion": "2026-03-10 08:30",
                        "codDiagnosticoPrincipal": "J029",
                        "codDiagnosticoRelacionado1": "R509",
                        "codDiagnosticoRelacionado2": null,
                        "tipoDiagnosticoPrincipal": "02",
                        "tipoDocumentoIdentificacion": "CC",
                        "numDocumentoIdentificacion": "79000111",
                        "consecutivo": 1
                      }
                    ],
                    "procedimientos": [
                      {
                        "codProcedimiento": "902210",
                        "fechaInicioAtencion": "2026-03-10 09:00",
                        "codDiagnosticoPrincipal": "J029",
                        "codDiagnosticoRelacionado": "R509",
                        "consecutivo": 1
                      }
                    ],
                    "medicamentos": [ { "codTecnologiaSalud": "x" }, { "codTecnologiaSalud": "y" } ]
                  }
                },
                {
                  "tipoDocumentoIdentificacion": "XX",
                  "numDocumentoIdentificacion": "1",
                  "fechaNacimiento": "no es fecha",
                  "codSexo": "Z",
                  "consecutivo": 2,
                  "servicios": {}
                }
              ]
            }
            """;

    /** Mismo paciente en otra factura, con una consulta anterior. */
    private static final String FACTURA_2 = """
            {
              "numDocumentoIdObligado": "900123456",
              "numFactura": "FE-1002",
              "usuarios": [
                {
                  "tipoDocumentoIdentificacion": "cc",
                  "numDocumentoIdentificacion": " 1020304050 ",
                  "fechaNacimiento": "1985-04-12",
                  "codSexo": "F",
                  "consecutivo": 1,
                  "servicios": {
                    "consultas": [
                      { "codConsulta": "890301", "fechaInicioAtencion": "2026-01-05 10:00",
                        "codDiagnosticoPrincipal": "i10x", "tipoDiagnosticoPrincipal": "03", "consecutivo": 1 }
                    ]
                  }
                }
              ]
            }
            """;

    private static Archivo json(String nombre, String contenido) {
        return new Archivo(nombre, contenido.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void agrupaAlPacienteDeVariasFacturasYOrdenaSusAtenciones() {
        Resultado r = parser.leer(List.of(json("a.json", FACTURA_1), json("b.json", FACTURA_2)));

        assertThat(r.archivos()).isEqualTo(2);
        assertThat(r.facturas()).containsExactly("FE-1001", "FE-1002");
        assertThat(r.prestadores()).containsExactly("900123456");
        assertThat(r.omitidos()).isEqualTo(2);
        assertThat(r.pacientes()).containsOnlyKeys("CC 1020304050", "XX 1");

        PacienteRips p = r.pacientes().get("CC 1020304050");
        assertThat(p.errores).isEmpty();
        assertThat(p.fechaNacimiento).isEqualTo(LocalDate.of(1985, 4, 12));
        assertThat(p.atenciones).extracting(AtencionRips::fecha).containsExactly(
                LocalDateTime.of(2026, 1, 5, 10, 0), LocalDateTime.of(2026, 3, 10, 8, 30), LocalDateTime.of(2026, 3, 10, 9, 0));
        assertThat(p.atenciones).extracting(AtencionRips::tipo).containsExactly("consultas", "consultas", "procedimientos");
    }

    @Test
    void leeCupsDiagnosticosYProfesional() {
        PacienteRips p = parser.leer(List.of(json("a.json", FACTURA_1))).pacientes().get("CC 1020304050");

        AtencionRips consulta = p.atenciones.getFirst();
        assertThat(consulta.codigoCups()).isEqualTo("890201");
        assertThat(consulta.profesional()).isEqualTo("CC 79000111");
        assertThat(consulta.diagnosticos()).containsExactly(new DxRips("J029", true, "02"), new DxRips("R509", false, null));
        assertThat(consulta.clave()).isEqualTo("FE-1001|1|consultas|1");

        AtencionRips procedimiento = p.atenciones.get(1);
        assertThat(procedimiento.codigoCups()).isEqualTo("902210");
        assertThat(procedimiento.clave()).isEqualTo("FE-1001|1|procedimientos|1");
    }

    @Test
    void normalizaElCodigoCie10() {
        PacienteRips p = parser.leer(List.of(json("b.json", FACTURA_2))).pacientes().get("CC 1020304050");

        assertThat(p.atenciones.getFirst().diagnosticos()).containsExactly(new DxRips("I10X", true, "03"));
    }

    @Test
    void marcaErroresDelPacienteSinDescartarElResto() {
        PacienteRips malo = parser.leer(List.of(json("a.json", FACTURA_1))).pacientes().get("XX 1");

        assertThat(malo.errores).containsExactly("Tipo de documento desconocido: XX", "Sin fecha de nacimiento válida",
                "Sexo no válido: Z");
    }

    @Test
    void reportaArchivosQueNoSonRips() {
        Resultado r = parser.leer(List.of(json("roto.json", "{no"), json("otro.json", "{\"hola\": 1}"),
                json("a.json", FACTURA_1)));

        assertThat(r.archivos()).isEqualTo(1);
        assertThat(r.erroresArchivo()).containsExactly("roto.json: no es un JSON válido",
                "otro.json: no tiene el bloque \"usuarios\" de un RIPS");
    }

    @Test
    void aceptaUnZipConVariosJsonEIgnoraLoDemas() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entrada(zip, "rips/a.json", FACTURA_1);
            entrada(zip, "rips/b.json", FACTURA_2);
            entrada(zip, "__MACOSX/rips/._a.json", "basura");
            entrada(zip, "rips/factura.xml", "<xml/>");
        }

        Resultado r = parser.leer(List.of(new Archivo("lote.zip", bytes.toByteArray())));

        assertThat(r.archivos()).isEqualTo(2);
        assertThat(r.erroresArchivo()).isEmpty();
        assertThat(r.pacientes().get("CC 1020304050").atenciones).hasSize(3);
    }

    @Test
    void cortaUnZipQueDescomprimidoPasaElLimite() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("grande.json"));
            byte[] ceros = new byte[1024 * 1024];
            for (long i = 0; i <= RipsParser.MAX_BYTES_DESCOMPRIMIDOS / ceros.length; i++) {
                zip.write(ceros);
            }
            zip.closeEntry();
        }

        assertThatThrownBy(() -> parser.leer(List.of(new Archivo("bomba.zip", bytes.toByteArray()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("300 MB");
    }

    @Test
    void aceptaVariosFormatosDeFecha() {
        assertThat(RipsParser.fechaHora("2026-03-10 08:30")).isEqualTo(LocalDateTime.of(2026, 3, 10, 8, 30));
        assertThat(RipsParser.fechaHora("2026-03-10T08:30:00")).isEqualTo(LocalDateTime.of(2026, 3, 10, 8, 30));
        assertThat(RipsParser.fechaHora("2026-03-10")).isEqualTo(LocalDateTime.of(2026, 3, 10, 0, 0));
        assertThat(RipsParser.fechaHora("10/03/2026")).isNull();
    }

    private static void entrada(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
