package com.galeno.importacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.galeno.historia.Diagnostico;
import com.galeno.importacion.LectorTabla.Tabla;
import com.galeno.importacion.ListadoPacientes.Campo;
import com.galeno.importacion.ListadoPacientes.FilaPersona;
import com.galeno.importacion.ListadoPacientes.Mapeo;
import com.galeno.importacion.ListadoPacientes.OrdenNombre;
import com.galeno.pacientes.ExtrasPersona;
import com.galeno.pacientes.Nombres;

/**
 * Lee la plantilla de importación de Galenum: un .xlsx con las hojas «Pacientes» y «Atenciones» y columnas fijas (se
 * reconocen por el encabezado, sin importar el orden, las tildes ni el asterisco de las obligatorias). No consulta la
 * base: valida cada fila y deja los mensajes con la hoja y la fila, como las ve la persona en Excel.
 */
final class PlantillaGalenum {

    static final String HOJA_PACIENTES = "Pacientes";
    static final String HOJA_ATENCIONES = "Atenciones";
    static final int MAX_MOTIVO = 1000;
    static final int MAX_TEXTO = 20_000;
    private static final Pattern CIE10 = Pattern.compile("^[A-Z][0-9]{2}[0-9X]?$");
    private static final Pattern HORA = Pattern.compile("^(\\d{1,2})[:.](\\d{2})(?:[:.]\\d{2})?\\s*([ap]\\.?\\s*m\\.?)?$",
            Pattern.CASE_INSENSITIVE);

    /** Columnas de la hoja Pacientes. */
    enum ColP {
        TIPO_DOCUMENTO("Tipo de documento", true), NUMERO_DOCUMENTO("Número de documento", true),
        PRIMER_NOMBRE("Primer nombre", true), SEGUNDO_NOMBRE("Segundo nombre", false),
        PRIMER_APELLIDO("Primer apellido", true), SEGUNDO_APELLIDO("Segundo apellido", false),
        FECHA_NACIMIENTO("Fecha de nacimiento", true), SEXO("Sexo", true), TELEFONO("Teléfono", false),
        CORREO("Correo", false), ALERGIAS("Alergias", false), ANTECEDENTES("Antecedentes", false),
        RESPONSABLE_NOMBRE("Responsable: nombre", false), RESPONSABLE_PARENTESCO("Responsable: parentesco", false),
        RESPONSABLE_TELEFONO("Responsable: teléfono", false);

        final String titulo;
        final boolean obligatoria;

        ColP(String titulo, boolean obligatoria) {
            this.titulo = titulo;
            this.obligatoria = obligatoria;
        }
    }

    /** Columnas de la hoja Atenciones. */
    enum ColA {
        TIPO_DOCUMENTO("Tipo de documento", true), NUMERO_DOCUMENTO("Número de documento", true),
        FECHA("Fecha de la atención", true), HORA("Hora", false), ID_ANTERIOR("ID en el sistema anterior", false),
        PROFESIONAL("Profesional que atendió", true), REGISTRO_MEDICO("Registro médico", false),
        MOTIVO("Motivo de consulta", false), ENFERMEDAD_ACTUAL("Enfermedad actual", false),
        REVISION("Revisión por sistemas", false), EXAMEN("Examen físico", false), TA_SISTOLICA("TA sistólica", false),
        TA_DIASTOLICA("TA diastólica", false), FC("FC", false), FR("FR", false), TEMPERATURA("Temperatura", false),
        SATURACION("Saturación", false), PESO("Peso (kg)", false), TALLA("Talla (cm)", false),
        DX_PRINCIPAL("Diagnóstico principal (CIE-10)", false), DX_1("Relacionado 1 (CIE-10)", false),
        DX_2("Relacionado 2 (CIE-10)", false), DX_3("Relacionado 3 (CIE-10)", false),
        TIPO_DX("Tipo de diagnóstico", false), IMPRESION("Impresión diagnóstica (texto)", false),
        ANALISIS("Análisis", false), PLAN("Plan", false), MEDICAMENTOS("Medicamentos formulados", false);

        final String titulo;
        final boolean obligatoria;

        ColA(String titulo, boolean obligatoria) {
            this.titulo = titulo;
            this.obligatoria = obligatoria;
        }
    }

    record Dx(String codigo, boolean principal, Diagnostico.Tipo tipo) {
    }

    /** Una fila de la hoja Atenciones ya validada (los textos, recortados a lo que admite la atención). */
    record AtencionFila(int fila, String clave, LocalDateTime fecha, String profesional, String registroMedico,
                        String motivo, String enfermedadActual, String revision, String examen, Integer taSistolica,
                        Integer taDiastolica, Integer fc, Integer fr, BigDecimal temperatura, Integer saturacion,
                        BigDecimal peso, BigDecimal talla, List<Dx> diagnosticos, String analisis, String plan) {
    }

    /** Un paciente de la plantilla: su fila en la hoja Pacientes (si está) y sus atenciones. */
    static final class PacienteFila {
        final String tipoDocumento;
        final String numeroDocumento;
        FilaPersona persona;
        ExtrasPersona extras;
        final List<AtencionFila> atenciones = new ArrayList<>();
        final List<String> errores = new ArrayList<>();
        final List<String> advertencias = new ArrayList<>();

        PacienteFila(String tipoDocumento, String numeroDocumento) {
            this.tipoDocumento = tipoDocumento;
            this.numeroDocumento = numeroDocumento;
        }
    }

    /** @param omitidas filas vacías o de atenciones que no se pudieron leer */
    record Resultado(Map<String, PacienteFila> pacientes, List<String> erroresSueltos, int omitidas) {
    }

    private PlantillaGalenum() {
    }

    static Resultado leer(Map<String, Tabla> hojas) {
        Tabla pacientes = hoja(hojas, HOJA_PACIENTES);
        Tabla atenciones = hoja(hojas, HOJA_ATENCIONES);
        if (pacientes == null || atenciones == null) {
            throw new IllegalArgumentException("El archivo no es la plantilla de Galenum: debe tener las hojas «"
                    + HOJA_PACIENTES + "» y «" + HOJA_ATENCIONES + "». Descárguela de esta pantalla y llénela sin cambiar los nombres");
        }
        Map<ColP, Integer> colP = columnas(pacientes, ColP.values(), c -> c.titulo, c -> c.obligatoria, HOJA_PACIENTES);
        Map<ColA, Integer> colA = columnas(atenciones, ColA.values(), c -> c.titulo, c -> c.obligatoria, HOJA_ATENCIONES);

        Map<String, PacienteFila> porDocumento = new LinkedHashMap<>();
        List<String> sueltos = new ArrayList<>();
        int omitidas = 0;

        Mapeo mapeo = mapeoPacientes(colP);
        for (int i = 0; i < pacientes.filas().size(); i++) {
            List<String> fila = pacientes.filas().get(i);
            int numero = i + 2;
            if (fila.stream().allMatch(String::isBlank)) {
                omitidas++;
                continue;
            }
            FilaPersona fp = ListadoPacientes.interpretar(numero, fila, mapeo);
            if (fp.tipoDocumento() == null || fp.numeroDocumento() == null || fp.numeroDocumento().length() > 20) {
                sueltos.add("Pacientes, fila " + numero + ": " + String.join("; ", fp.errores()));
                omitidas++;
                continue;
            }
            String clave = fp.tipoDocumento() + " " + fp.numeroDocumento();
            PacienteFila p = porDocumento.computeIfAbsent(clave, k -> new PacienteFila(fp.tipoDocumento(), fp.numeroDocumento()));
            if (p.persona != null) {
                p.advertencias.add("Pacientes: también aparece en la fila " + numero + "; se usa la fila " + p.persona.fila());
                continue;
            }
            p.persona = fp;
            fp.errores().forEach(e -> p.errores.add("Pacientes, fila " + numero + ": " + e));
            fp.advertencias().forEach(a -> p.advertencias.add("Pacientes, fila " + numero + ": " + a));
            for (String nombre : new String[] {fp.datos().nombres(), fp.datos().apellidos()}) {
                if (nombre != null && !Nombres.valido(nombre)) {
                    p.errores.add("Pacientes, fila " + numero + ": «" + nombre + "» tiene caracteres que no son de un nombre");
                }
            }
            p.extras = extras(fila, colP, numero, p.advertencias);
        }

        for (int i = 0; i < atenciones.filas().size(); i++) {
            List<String> fila = atenciones.filas().get(i);
            int numero = i + 2;
            if (fila.stream().allMatch(String::isBlank)) {
                omitidas++;
                continue;
            }
            String tipoCrudo = celda(fila, colA, ColA.TIPO_DOCUMENTO);
            String tipo = tipoCrudo == null ? null : ListadoPacientes.tipoDocumento(tipoCrudo);
            String documento = ListadoPacientes.numeroDocumento(celda(fila, colA, ColA.NUMERO_DOCUMENTO));
            if (tipo == null || documento == null) {
                sueltos.add("Atenciones, fila " + numero + ": " + (tipo == null
                        ? (tipoCrudo == null ? "sin tipo de documento" : "tipo de documento desconocido: " + tipoCrudo)
                        : "sin número de documento") + "; no se importa");
                omitidas++;
                continue;
            }
            PacienteFila p = porDocumento.computeIfAbsent(tipo + " " + documento, k -> new PacienteFila(tipo, documento));
            List<String> problemas = new ArrayList<>();
            AtencionFila a = atencion(numero, fila, colA, tipo + " " + documento, problemas, p.advertencias);
            if (a == null) {
                p.advertencias.add("Atenciones, fila " + numero + " no se importa: " + String.join("; ", problemas));
                omitidas++;
            } else {
                p.atenciones.add(a);
            }
        }
        return new Resultado(porDocumento, sueltos, omitidas);
    }

    // --- hoja Pacientes ---

    private static Mapeo mapeoPacientes(Map<ColP, Integer> c) {
        Map<Campo, Integer> m = new EnumMap<>(Campo.class);
        m.put(Campo.TIPO_DOCUMENTO, c.get(ColP.TIPO_DOCUMENTO));
        m.put(Campo.NUMERO_DOCUMENTO, c.get(ColP.NUMERO_DOCUMENTO));
        m.put(Campo.PRIMER_NOMBRE, c.get(ColP.PRIMER_NOMBRE));
        m.put(Campo.PRIMER_APELLIDO, c.get(ColP.PRIMER_APELLIDO));
        m.put(Campo.FECHA_NACIMIENTO, c.get(ColP.FECHA_NACIMIENTO));
        m.put(Campo.SEXO, c.get(ColP.SEXO));
        poner(m, Campo.SEGUNDO_NOMBRE, c.get(ColP.SEGUNDO_NOMBRE));
        poner(m, Campo.SEGUNDO_APELLIDO, c.get(ColP.SEGUNDO_APELLIDO));
        poner(m, Campo.TELEFONO, c.get(ColP.TELEFONO));
        poner(m, Campo.EMAIL, c.get(ColP.CORREO));
        return new Mapeo(m, null, OrdenNombre.NOMBRES_APELLIDOS);
    }

    private static void poner(Map<Campo, Integer> m, Campo campo, Integer columna) {
        if (columna != null) {
            m.put(campo, columna);
        }
    }

    private static ExtrasPersona extras(List<String> fila, Map<ColP, Integer> c, int numero, List<String> advertencias) {
        String alergias = recortar(celda(fila, c, ColP.ALERGIAS), 2000);
        String antecedentes = recortar(celda(fila, c, ColP.ANTECEDENTES), 4000);
        String nombre = ListadoPacientes.capitalizar(celda(fila, c, ColP.RESPONSABLE_NOMBRE));
        String parentesco = ListadoPacientes.capitalizar(celda(fila, c, ColP.RESPONSABLE_PARENTESCO));
        String telefono = recortar(ListadoPacientes.telefono(celda(fila, c, ColP.RESPONSABLE_TELEFONO)), 30);
        if (nombre != null && (nombre.length() > 200 || !Nombres.valido(nombre))) {
            advertencias.add("Pacientes, fila " + numero + ": el nombre del responsable tiene caracteres no válidos; no se importa el responsable");
            nombre = null;
        }
        if (nombre == null) {
            parentesco = null;
            telefono = null;
        }
        return new ExtrasPersona(alergias, antecedentes, nombre, recortar(parentesco, 40), telefono);
    }

    // --- hoja Atenciones ---

    private static AtencionFila atencion(int numero, List<String> fila, Map<ColA, Integer> c, String documento,
                                         List<String> problemas, List<String> advertencias) {
        String prefijo = "Atenciones, fila " + numero + ": ";
        String fechaCruda = celda(fila, c, ColA.FECHA);
        LocalDate dia = ListadoPacientes.fecha(fechaCruda);
        if (dia == null) {
            problemas.add(fechaCruda == null ? "sin fecha de la atención" : "fecha no reconocida: " + fechaCruda);
        } else if (dia.isAfter(LocalDate.now()) || dia.getYear() < 1900) {
            problemas.add("fecha fuera de rango: " + dia);
        }
        String horaCruda = celda(fila, c, ColA.HORA);
        LocalTime hora = hora(horaCruda);
        if (horaCruda != null && hora == null) {
            advertencias.add(prefijo + "hora no reconocida (" + horaCruda + "); se registra sin hora");
        }
        String profesional = recortar(celda(fila, c, ColA.PROFESIONAL), 180);
        if (profesional == null) {
            problemas.add("sin el profesional que atendió");
        }
        if (!problemas.isEmpty()) {
            return null;
        }

        String motivo = texto(fila, c, ColA.MOTIVO);
        String enfermedad = texto(fila, c, ColA.ENFERMEDAD_ACTUAL);
        if (motivo != null && motivo.length() > MAX_MOTIVO) {
            // El motivo admite 1.000 caracteres: el texto completo queda al comienzo de la enfermedad actual
            enfermedad = unir("Motivo de consulta (texto completo): " + motivo, enfermedad);
            motivo = motivo.substring(0, MAX_MOTIVO - 1) + "…";
            advertencias.add(prefijo + "el motivo de consulta es muy largo; el texto completo queda en la enfermedad actual");
        }

        List<Dx> dx = new ArrayList<>();
        Diagnostico.Tipo tipoDx = tipoDiagnostico(celda(fila, c, ColA.TIPO_DX));
        for (ColA col : List.of(ColA.DX_PRINCIPAL, ColA.DX_1, ColA.DX_2, ColA.DX_3)) {
            String crudo = celda(fila, c, col);
            if (crudo == null) {
                continue;
            }
            String codigo = crudo.toUpperCase(Locale.ROOT).replaceAll("[\\s.\\-]", "");
            if (!CIE10.matcher(codigo).matches()) {
                advertencias.add(prefijo + "diagnóstico con código CIE-10 no válido omitido: " + crudo);
            } else if (dx.stream().noneMatch(d -> d.codigo().equals(codigo))) {
                dx.add(new Dx(codigo, dx.isEmpty(), tipoDx));
            }
        }

        String impresion = texto(fila, c, ColA.IMPRESION);
        String analisis = unir(impresion == null ? null : "Impresión diagnóstica: " + impresion, texto(fila, c, ColA.ANALISIS));
        String medicamentos = texto(fila, c, ColA.MEDICAMENTOS);
        String plan = unir(texto(fila, c, ColA.PLAN), medicamentos == null ? null : "Medicamentos formulados: " + medicamentos);

        String id = celda(fila, c, ColA.ID_ANTERIOR);
        LocalDateTime fecha = dia.atTime(hora == null ? LocalTime.MIDNIGHT : hora);
        String clave = id != null
                ? "plantilla:" + recortar(id.strip(), 140)
                : "plantilla:" + hash(documento + "|" + fecha + "|" + profesional + "|" + (motivo == null ? "" : motivo));

        return new AtencionFila(numero, clave, fecha, profesional, recortar(celda(fila, c, ColA.REGISTRO_MEDICO), 50),
                motivo, recortarTexto(enfermedad), recortarTexto(texto(fila, c, ColA.REVISION)),
                recortarTexto(texto(fila, c, ColA.EXAMEN)),
                entero(fila, c, ColA.TA_SISTOLICA, 40, 300, "TA sistólica", prefijo, advertencias),
                entero(fila, c, ColA.TA_DIASTOLICA, 20, 200, "TA diastólica", prefijo, advertencias),
                entero(fila, c, ColA.FC, 20, 250, "FC", prefijo, advertencias),
                entero(fila, c, ColA.FR, 4, 80, "FR", prefijo, advertencias),
                decimal(fila, c, ColA.TEMPERATURA, "30", "45", "temperatura", prefijo, advertencias),
                entero(fila, c, ColA.SATURACION, 40, 100, "saturación", prefijo, advertencias),
                decimal(fila, c, ColA.PESO, "0.3", "400", "peso", prefijo, advertencias),
                decimal(fila, c, ColA.TALLA, "20", "250", "talla", prefijo, advertencias),
                dx, recortarTexto(analisis), recortarTexto(plan));
    }

    static Diagnostico.Tipo tipoDiagnostico(String s) {
        if (s == null) {
            return Diagnostico.Tipo.IMPRESION_DIAGNOSTICA;
        }
        String n = ListadoPacientes.normalizar(s);
        if (n.contains("nuevo") || n.equals("2") || n.equals("02")) {
            return Diagnostico.Tipo.CONFIRMADO_NUEVO;
        }
        if (n.contains("repetido") || n.equals("3") || n.equals("03")) {
            return Diagnostico.Tipo.CONFIRMADO_REPETIDO;
        }
        return Diagnostico.Tipo.IMPRESION_DIAGNOSTICA;
    }

    /** «14:30», «9.05», «2:30 pm» o la fracción de día con que Excel guarda una hora (0,604166…). */
    static LocalTime hora(String s) {
        if (s == null) {
            return null;
        }
        String v = s.strip();
        if (v.matches("0([.,]\\d+)?")) {
            double fraccion = Double.parseDouble(v.replace(',', '.'));
            int minutos = (int) Math.round(fraccion * 24 * 60);
            return minutos >= 24 * 60 ? null : LocalTime.of(minutos / 60, minutos % 60);
        }
        Matcher m = HORA.matcher(v);
        if (!m.matches()) {
            return null;
        }
        int h = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));
        if (m.group(3) != null) {
            boolean pm = m.group(3).toLowerCase(Locale.ROOT).startsWith("p");
            if (h < 1 || h > 12) {
                return null;
            }
            h = h % 12 + (pm ? 12 : 0);
        }
        return h > 23 || min > 59 ? null : LocalTime.of(h, min);
    }

    private static Integer entero(List<String> fila, Map<ColA, Integer> c, ColA col, int min, int max, String nombre,
                                  String prefijo, List<String> advertencias) {
        BigDecimal v = numero(celda(fila, c, col), nombre, prefijo, advertencias);
        if (v == null) {
            return null;
        }
        int n = v.setScale(0, RoundingMode.HALF_UP).intValue();
        if (n < min || n > max) {
            advertencias.add(prefijo + nombre + " fuera de rango (" + v.toPlainString() + "); no se importa");
            return null;
        }
        return n;
    }

    private static BigDecimal decimal(List<String> fila, Map<ColA, Integer> c, ColA col, String min, String max,
                                      String nombre, String prefijo, List<String> advertencias) {
        BigDecimal v = numero(celda(fila, c, col), nombre, prefijo, advertencias);
        if (v == null) {
            return null;
        }
        if (v.compareTo(new BigDecimal(min)) < 0 || v.compareTo(new BigDecimal(max)) > 0) {
            advertencias.add(prefijo + nombre + " fuera de rango (" + v.toPlainString() + "); no se importa");
            return null;
        }
        return v.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal numero(String s, String nombre, String prefijo, List<String> advertencias) {
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            advertencias.add(prefijo + nombre + " no es un número (" + s + "); no se importa");
            return null;
        }
    }

    // --- apoyo ---

    private static Tabla hoja(Map<String, Tabla> hojas, String nombre) {
        return hojas.entrySet().stream().filter(e -> ListadoPacientes.normalizar(e.getKey()).equals(ListadoPacientes.normalizar(nombre)))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    /** Ubica cada columna por su encabezado; si falta una obligatoria, el archivo no se puede leer. */
    private static <C extends Enum<C>> Map<C, Integer> columnas(Tabla t, C[] cols, java.util.function.Function<C, String> titulo,
                                                                java.util.function.Predicate<C> obligatoria, String hoja) {
        Map<C, Integer> m = new LinkedHashMap<>();
        if (t.encabezados().isEmpty()) {
            if (!t.filas().isEmpty()) {
                throw new IllegalArgumentException("La hoja «" + hoja + "» no tiene encabezados");
            }
            for (C c : cols) {
                m.put(c, -1); // hoja vacía: no hay filas que leer
            }
            return m;
        }
        List<String> encabezados = t.encabezados().stream().map(h -> ListadoPacientes.normalizar(h.replace("*", ""))).toList();
        List<String> faltan = new ArrayList<>();
        for (C c : cols) {
            int i = encabezados.indexOf(ListadoPacientes.normalizar(titulo.apply(c)));
            if (i >= 0) {
                m.put(c, i);
            } else if (obligatoria.test(c)) {
                faltan.add(titulo.apply(c));
            }
        }
        if (!faltan.isEmpty()) {
            throw new IllegalArgumentException("A la hoja «" + hoja + "» le faltan columnas obligatorias: " + String.join(", ", faltan));
        }
        return m;
    }

    private static <C> String celda(List<String> fila, Map<C, Integer> m, C col) {
        Integer i = m.get(col);
        if (i == null || i < 0 || i >= fila.size()) {
            return null;
        }
        String v = fila.get(i).strip();
        return v.isEmpty() ? null : v;
    }

    /** Texto libre: saltos de línea de Windows normalizados. */
    private static <C> String texto(List<String> fila, Map<C, Integer> m, C col) {
        String v = celda(fila, m, col);
        return v == null ? null : v.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String unir(String a, String b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + "\n\n" + b;
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String recortarTexto(String s) {
        return s == null || s.length() <= MAX_TEXTO ? s : s.substring(0, MAX_TEXTO - 1) + "…";
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 40);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
