package com.galeno.importacion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Lee la primera hoja de un .xlsx o un .csv como texto: encabezados y filas. No interpreta formatos (fechas,
 * números): eso lo decide quien sabe qué significa cada columna (ver {@link ListadoPacientes}).
 *
 * <p>El .xlsx se lee con StAX y sin DTD ni entidades externas; el .csv detecta UTF-8 o Windows-1252 y el
 * separador (punto y coma, coma o tabulador), porque Excel en español guarda CSV con ";" y en ANSI.
 */
final class LectorTabla {

    static final int MAX_FILAS = 50_000;
    static final int MAX_COLUMNAS = 100;
    private static final long MAX_BYTES_XML = 200L * 1024 * 1024;

    record Tabla(List<String> encabezados, List<List<String>> filas) {
    }

    static Tabla leer(String nombre, byte[] contenido) {
        String n = nombre.toLowerCase(Locale.ROOT);
        List<List<String>> filas;
        if (n.endsWith(".xlsx")) {
            filas = xlsx(contenido);
        } else if (n.endsWith(".csv") || n.endsWith(".txt")) {
            filas = csv(texto(contenido));
        } else if (n.endsWith(".xls")) {
            throw new IllegalArgumentException("El formato .xls (Excel 97-2003) no se admite: guárdelo como .xlsx o .csv");
        } else {
            throw new IllegalArgumentException("Suba un archivo .xlsx o .csv");
        }
        // Encabezados: la primera fila con algo escrito
        int inicio = 0;
        while (inicio < filas.size() && filas.get(inicio).stream().allMatch(String::isBlank)) {
            inicio++;
        }
        if (inicio == filas.size()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        List<String> encabezados = filas.get(inicio).stream().map(String::strip).toList();
        if (encabezados.size() > MAX_COLUMNAS) {
            throw new IllegalArgumentException("Máximo " + MAX_COLUMNAS + " columnas");
        }
        List<List<String>> datos = new ArrayList<>();
        for (List<String> fila : filas.subList(inicio + 1, filas.size())) {
            List<String> completa = new ArrayList<>(fila.stream().map(String::strip).toList());
            while (completa.size() < encabezados.size()) {
                completa.add("");
            }
            datos.add(completa);
        }
        // Las filas vacías del final (Excel suele dejarlas) no cuentan
        while (!datos.isEmpty() && datos.getLast().stream().allMatch(String::isBlank)) {
            datos.removeLast();
        }
        if (datos.size() > MAX_FILAS) {
            throw new IllegalArgumentException("Máximo " + MAX_FILAS + " filas por archivo");
        }
        return new Tabla(encabezados, datos);
    }

    // --- CSV ---

    private static String texto(byte[] b) {
        int desde = b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer bytes = ByteBuffer.wrap(b, desde, b.length - desde);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(bytes).toString();
        } catch (CharacterCodingException e) {
            return new String(b, desde, b.length - desde, Charset.forName("windows-1252"));
        }
    }

    static List<List<String>> csv(String texto) {
        char separador = separador(texto);
        List<List<String>> filas = new ArrayList<>();
        List<String> fila = new ArrayList<>();
        StringBuilder celda = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (comillas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    celda.append('"');
                    i++;
                } else if (c == '"') {
                    comillas = false;
                } else {
                    celda.append(c);
                }
            } else if (c == '"' && celda.isEmpty()) {
                comillas = true;
            } else if (c == separador) {
                fila.add(celda.toString());
                celda.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                fila.add(celda.toString());
                celda.setLength(0);
                filas.add(fila);
                fila = new ArrayList<>();
                if (filas.size() > MAX_FILAS + 1) {
                    throw new IllegalArgumentException("Máximo " + MAX_FILAS + " filas por archivo");
                }
            } else {
                celda.append(c);
            }
        }
        if (!celda.isEmpty() || !fila.isEmpty()) {
            fila.add(celda.toString());
            filas.add(fila);
        }
        return filas;
    }

    /** El que más aparece en la primera línea (fuera de comillas). */
    private static char separador(String texto) {
        int fin = texto.indexOf('\n');
        String linea = fin < 0 ? texto : texto.substring(0, fin);
        char mejor = ',';
        long max = -1;
        for (char c : new char[] {';', ',', '\t', '|'}) {
            long n = linea.chars().filter(x -> x == c).count();
            if (n > max) {
                max = n;
                mejor = c;
            }
        }
        return mejor;
    }

    // --- XLSX ---

    private static List<List<String>> xlsx(byte[] contenido) {
        Map<String, byte[]> partes = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String nombre = e.getName();
                boolean util = nombre.equals("xl/workbook.xml") || nombre.equals("xl/_rels/workbook.xml.rels")
                        || nombre.equals("xl/sharedStrings.xml") || nombre.startsWith("xl/worksheets/sheet");
                if (!util) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = zip.read(buffer)) > 0) {
                    total += n;
                    if (total > MAX_BYTES_XML) {
                        throw new IllegalArgumentException("El Excel descomprimido es demasiado grande");
                    }
                    out.write(buffer, 0, n);
                }
                partes.put(nombre, out.toByteArray());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el Excel: ¿es un .xlsx válido?");
        }
        if (!partes.containsKey("xl/workbook.xml")) {
            throw new IllegalArgumentException("No se pudo leer el Excel: ¿es un .xlsx válido?");
        }
        try {
            List<String> compartidas = partes.containsKey("xl/sharedStrings.xml")
                    ? textosCompartidos(partes.get("xl/sharedStrings.xml")) : List.of();
            byte[] hoja = partes.get(primeraHoja(partes));
            if (hoja == null) {
                throw new IllegalArgumentException("El Excel no tiene hojas");
            }
            return celdas(hoja, compartidas);
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("No se pudo leer el Excel: el contenido está dañado");
        }
    }

    private static XMLStreamReader xml(byte[] b) throws XMLStreamException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        return f.createXMLStreamReader(new ByteArrayInputStream(b));
    }

    /** Sin espacios de nombres el prefijo queda en el nombre (p. ej. "x:row"): se compara solo la parte local. */
    private static String local(XMLStreamReader r) {
        String n = r.getLocalName();
        int dos = n.indexOf(':');
        return dos < 0 ? n : n.substring(dos + 1);
    }

    private static String atributo(XMLStreamReader r, String nombre) {
        for (int i = 0; i < r.getAttributeCount(); i++) {
            String n = r.getAttributeLocalName(i);
            int dos = n.indexOf(':');
            if ((dos < 0 ? n : n.substring(dos + 1)).equals(nombre)) {
                return r.getAttributeValue(i);
            }
        }
        return null;
    }

    /** La hoja que el libro pone primero (no siempre es sheet1.xml). */
    private static String primeraHoja(Map<String, byte[]> partes) throws XMLStreamException {
        String relId = null;
        XMLStreamReader r = xml(partes.get("xl/workbook.xml"));
        while (r.hasNext() && relId == null) {
            if (r.next() == XMLStreamConstants.START_ELEMENT && local(r).equals("sheet")) {
                relId = atributo(r, "id");
            }
        }
        byte[] rels = partes.get("xl/_rels/workbook.xml.rels");
        if (relId != null && rels != null) {
            XMLStreamReader rr = xml(rels);
            while (rr.hasNext()) {
                if (rr.next() == XMLStreamConstants.START_ELEMENT && local(rr).equals("Relationship")
                        && relId.equals(atributo(rr, "Id"))) {
                    String destino = atributo(rr, "Target");
                    return destino.startsWith("/") ? destino.substring(1) : "xl/" + destino;
                }
            }
        }
        return "xl/worksheets/sheet1.xml";
    }

    private static List<String> textosCompartidos(byte[] b) throws XMLStreamException {
        List<String> textos = new ArrayList<>();
        XMLStreamReader r = xml(b);
        StringBuilder actual = null;
        boolean fonetico = false;
        while (r.hasNext()) {
            int evento = r.next();
            if (evento == XMLStreamConstants.START_ELEMENT) {
                switch (local(r)) {
                    case "si" -> actual = new StringBuilder();
                    case "rPh" -> fonetico = true; // guía fonética (japonés): no es parte del texto
                    case "t" -> {
                        if (actual != null && !fonetico) {
                            actual.append(r.getElementText());
                        }
                    }
                    default -> { }
                }
            } else if (evento == XMLStreamConstants.END_ELEMENT) {
                if (local(r).equals("si") && actual != null) {
                    textos.add(actual.toString());
                    actual = null;
                } else if (local(r).equals("rPh")) {
                    fonetico = false;
                }
            }
        }
        return textos;
    }

    private static List<List<String>> celdas(byte[] hoja, List<String> compartidas) throws XMLStreamException {
        List<List<String>> filas = new ArrayList<>();
        XMLStreamReader r = xml(hoja);
        List<String> fila = null;
        String tipo = null;
        int columna = 0;
        String valor = null;
        while (r.hasNext()) {
            int evento = r.next();
            if (evento == XMLStreamConstants.START_ELEMENT) {
                switch (local(r)) {
                    case "row" -> {
                        String numero = atributo(r, "r");
                        // Las filas vacías no vienen en el XML: se rellenan para que la numeración coincida con Excel
                        if (numero != null) {
                            while (filas.size() < Integer.parseInt(numero) - 1) {
                                filas.add(new ArrayList<>());
                            }
                        }
                        if (filas.size() > MAX_FILAS + 1) {
                            throw new IllegalArgumentException("Máximo " + MAX_FILAS + " filas por archivo");
                        }
                        fila = new ArrayList<>();
                        columna = 0;
                    }
                    case "c" -> {
                        tipo = atributo(r, "t");
                        String ref = atributo(r, "r");
                        if (ref != null) {
                            columna = indiceColumna(ref);
                        }
                        valor = null;
                    }
                    case "v" -> valor = r.getElementText();
                    case "t" -> valor = (valor == null ? "" : valor) + r.getElementText(); // inlineStr
                    default -> { }
                }
            } else if (evento == XMLStreamConstants.END_ELEMENT) {
                if (local(r).equals("c") && fila != null) {
                    if (columna < MAX_COLUMNAS * 2) {
                        while (fila.size() < columna) {
                            fila.add("");
                        }
                        fila.add(texto(tipo, valor, compartidas));
                    }
                    columna++;
                } else if (local(r).equals("row") && fila != null) {
                    filas.add(fila);
                    fila = null;
                }
            }
        }
        return filas;
    }

    private static String texto(String tipo, String valor, List<String> compartidas) {
        if (valor == null) {
            return "";
        }
        if ("s".equals(tipo)) {
            int i = Integer.parseInt(valor.strip());
            return i < compartidas.size() ? compartidas.get(i) : "";
        }
        if ("b".equals(tipo)) {
            return "1".equals(valor) ? "VERDADERO" : "FALSO";
        }
        if (tipo == null || "n".equals(tipo)) {
            // 1.02030405E9 → 1020304050; 33604.0 → 33604
            try {
                return new BigDecimal(valor.strip()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                return valor;
            }
        }
        return valor;
    }

    /** "C12" → 2. */
    static int indiceColumna(String referencia) {
        int i = 0;
        int n = 0;
        while (i < referencia.length() && Character.isLetter(referencia.charAt(i))) {
            n = n * 26 + (Character.toUpperCase(referencia.charAt(i)) - 'A' + 1);
            i++;
        }
        return n - 1;
    }
}
