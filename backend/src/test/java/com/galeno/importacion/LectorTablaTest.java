package com.galeno.importacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.galeno.importacion.LectorTabla.Tabla;

class LectorTablaTest {

    @Test
    void csvDeExcelEnEspanolConPuntoYComaYAnsi() {
        String texto = "Cédula;Nombres;Apellidos\r\n1020304050;María José;Peña\r\n;;\r\n";
        Tabla t = LectorTabla.leer("pacientes.csv", texto.getBytes(Charset.forName("windows-1252")));

        assertThat(t.encabezados()).containsExactly("Cédula", "Nombres", "Apellidos");
        assertThat(t.filas()).containsExactly(List.of("1020304050", "María José", "Peña"));
    }

    @Test
    void csvUtf8ConBomComasYComillas() {
        String texto = "﻿documento,nombre,nota\n123,\"Ruiz, Carlos\",\"dijo \"\"hola\"\"\"\n456,Ana\n";
        Tabla t = LectorTabla.leer("p.csv", texto.getBytes(StandardCharsets.UTF_8));

        assertThat(t.encabezados()).containsExactly("documento", "nombre", "nota");
        assertThat(t.filas()).containsExactly(List.of("123", "Ruiz, Carlos", "dijo \"hola\""), List.of("456", "Ana", ""));
    }

    @Test
    void xlsxConTextosCompartidosNumerosYCeldasVacias() throws IOException {
        byte[] xlsx = xlsx("""
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                  <row r="2"><c r="A2" t="s"><v>0</v></c><c r="C2" t="s"><v>1</v></c></row>
                  <row r="3"><c r="A3"><v>1.02030405E9</v></c><c r="B3" t="inlineStr"><is><t>sin encabezado</t></is></c><c r="C3"><v>33604</v></c></row>
                </sheetData></worksheet>""", "<si><t>Documento</t></si><si><r><t>Fecha </t></r><r><t>nac.</t></r></si>");

        Tabla t = LectorTabla.leer("listado.xlsx", xlsx);

        assertThat(t.encabezados()).containsExactly("Documento", "", "Fecha nac.");
        assertThat(t.filas()).containsExactly(List.of("1020304050", "sin encabezado", "33604"));
    }

    @Test
    void rechazaFormatosQueNoLee() {
        assertThatThrownBy(() -> LectorTabla.leer("viejo.xls", new byte[] {1}))
                .hasMessageContaining(".xlsx o .csv");
        assertThatThrownBy(() -> LectorTabla.leer("foto.png", new byte[] {1})).hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> LectorTabla.leer("vacio.csv", "\n\n".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("vacío");
        assertThatThrownBy(() -> LectorTabla.leer("roto.xlsx", "no es zip".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("xlsx válido");
    }

    @Test
    void ignoraEntidadesExternasEnElExcel() throws IOException {
        byte[] xlsx = xlsx("""
                <?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>
                <worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>&e;</t></is></c></row></sheetData></worksheet>""", null);

        assertThatThrownBy(() -> LectorTabla.leer("malo.xlsx", xlsx)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void columnaDeLaReferencia() {
        assertThat(LectorTabla.indiceColumna("A1")).isZero();
        assertThat(LectorTabla.indiceColumna("C12")).isEqualTo(2);
        assertThat(LectorTabla.indiceColumna("AA3")).isEqualTo(26);
    }

    private static byte[] xlsx(String hoja, String compartidas) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            parte(zip, "xl/workbook.xml", "<workbook><sheets><sheet name=\"Pacientes\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            parte(zip, "xl/_rels/workbook.xml.rels",
                    "<Relationships><Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            parte(zip, "xl/worksheets/sheet1.xml", hoja);
            if (compartidas != null) {
                parte(zip, "xl/sharedStrings.xml", "<sst>" + compartidas + "</sst>");
            }
        }
        return bytes.toByteArray();
    }

    private static void parte(ZipOutputStream zip, String nombre, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
