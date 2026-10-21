package com.galeno.importacion;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.galeno.pacientes.DatosPersona;
import com.galeno.pacientes.TiposDocumento;

/**
 * Interpreta un listado de pacientes exportado de otro sistema: reconoce las columnas por su encabezado y
 * normaliza los valores (tipos de documento escritos de mil formas, fechas de Excel, nombres en mayúsculas).
 * No toca la base de datos.
 */
final class ListadoPacientes {

    enum Campo {
        TIPO_DOCUMENTO,
        NUMERO_DOCUMENTO,
        NOMBRES,
        APELLIDOS,
        PRIMER_NOMBRE,
        SEGUNDO_NOMBRE,
        PRIMER_APELLIDO,
        SEGUNDO_APELLIDO,
        /** Nombres y apellidos en una sola columna: se separan según {@link OrdenNombre}. */
        NOMBRE_COMPLETO,
        FECHA_NACIMIENTO,
        SEXO,
        TELEFONO,
        EMAIL
    }

    enum OrdenNombre {
        NOMBRES_APELLIDOS,
        APELLIDOS_NOMBRES
    }

    /** Qué columna (índice desde 0) va a cada campo, más lo que el archivo no trae. */
    record Mapeo(Map<Campo, Integer> columnas, String tipoDocumentoPorDefecto, OrdenNombre ordenNombre) {
    }

    record FilaPersona(int fila, String tipoDocumento, String numeroDocumento, DatosPersona datos,
                       List<String> errores, List<String> advertencias) {
    }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> PARTICULAS = Set.of("de", "del", "la", "las", "los", "y", "san");
    private static final List<DateTimeFormatter> FORMATOS_FECHA = Arrays.stream(new String[] {
            "uuuu-MM-dd", "d/M/uuuu", "d-M-uuuu", "uuuu/M/d", "d.M.uuuu", "uuuuMMdd"})
            .map(p -> DateTimeFormatter.ofPattern(p, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)).toList();

    private ListadoPacientes() {
    }

    // --- columnas ---

    /** Propone el mapeo a partir de los encabezados; cada campo se asigna a una sola columna (la primera). */
    static Map<Campo, Integer> sugerir(List<String> encabezados) {
        Map<Campo, Integer> mapeo = new EnumMap<>(Campo.class);
        for (int i = 0; i < encabezados.size(); i++) {
            Campo campo = reconocer(encabezados.get(i));
            if (campo != null) {
                mapeo.putIfAbsent(campo, i);
            }
        }
        // Si hay nombres separados, la columna "nombre completo" sobra (y al revés no aplica)
        if (mapeo.containsKey(Campo.NOMBRES) || mapeo.containsKey(Campo.PRIMER_NOMBRE)) {
            mapeo.remove(Campo.NOMBRE_COMPLETO);
        }
        return mapeo;
    }

    static Campo reconocer(String encabezado) {
        String h = " " + normalizar(encabezado) + " ";
        if (h.isBlank()) {
            return null;
        }
        if (h.contains(" tipo ") && (h.contains(" doc") || h.contains(" ident") || h.contains(" id "))) {
            return Campo.TIPO_DOCUMENTO;
        }
        if (h.contains("primer nombre") || h.contains("nombre 1 ") || h.equals(" nombre1 ")) {
            return Campo.PRIMER_NOMBRE;
        }
        if (h.contains("segundo nombre") || h.contains("nombre 2 ") || h.equals(" nombre2 ")) {
            return Campo.SEGUNDO_NOMBRE;
        }
        if (h.contains("primer apellido") || h.contains("apellido 1 ") || h.equals(" apellido1 ")) {
            return Campo.PRIMER_APELLIDO;
        }
        if (h.contains("segundo apellido") || h.contains("apellido 2 ") || h.equals(" apellido2 ")) {
            return Campo.SEGUNDO_APELLIDO;
        }
        if (apellidosPrimero(encabezado) || h.contains("nombre completo") || h.contains("nombres y apellidos") || h.contains("nombre y apellido")
                || h.equals(" paciente ") || h.equals(" nombre del paciente ") || h.equals(" nombre paciente ")) {
            return Campo.NOMBRE_COMPLETO;
        }
        if (h.contains(" apellido")) {
            return Campo.APELLIDOS;
        }
        if (h.contains(" nombre")) {
            return Campo.NOMBRES;
        }
        if (h.contains(" nac") || h.contains("nacimiento") || h.contains(" f nac") || h.contains("cumpleanos")) {
            return Campo.FECHA_NACIMIENTO;
        }
        if (h.contains(" sexo ") || h.contains(" genero ")) {
            return Campo.SEXO;
        }
        if (h.contains("correo") || h.contains("email") || h.contains(" e mail") || h.contains(" mail ")) {
            return Campo.EMAIL;
        }
        if (h.contains("celular") || h.contains("telefono") || h.contains(" tel ") || h.contains("movil")
                || h.contains("whatsapp")) {
            return Campo.TELEFONO;
        }
        if (h.contains("documento") || h.contains("identificacion") || h.contains("cedula") || h.contains(" no doc")
                || h.contains(" num doc") || h.equals(" id ") || h.equals(" cc ") || h.equals(" nit ")) {
            return Campo.NUMERO_DOCUMENTO;
        }
        return null;
    }

    /** "Apellidos y nombres": una sola columna con los apellidos primero. */
    static boolean apellidosPrimero(String encabezado) {
        String h = normalizar(encabezado);
        return h.contains("apellidos y nombres") || h.contains("apellido y nombre") || h.contains("apellidos nombres");
    }

    /** Minúsculas, sin tildes y con un espacio entre palabras: "Fecha de Nac." → "fecha de nac". */
    static String normalizar(String s) {
        String sinTildes = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    /** Firma de los encabezados, para recordar el mapeo cuando vuelva un archivo del mismo sistema. */
    static String firma(List<String> encabezados) {
        return encabezados.stream().map(ListadoPacientes::normalizar).collect(Collectors.joining("|"));
    }

    // --- filas ---

    static FilaPersona interpretar(int numeroFila, List<String> fila, Mapeo mapeo) {
        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();

        String tipoCrudo = celda(fila, mapeo, Campo.TIPO_DOCUMENTO);
        String tipo = tipoCrudo != null ? tipoDocumento(tipoCrudo) : mapeo.tipoDocumentoPorDefecto();
        if (tipoCrudo != null && tipo == null) {
            errores.add("Tipo de documento desconocido: " + tipoCrudo);
        } else if (tipo == null) {
            errores.add("Sin tipo de documento");
        }
        String numero = numeroDocumento(celda(fila, mapeo, Campo.NUMERO_DOCUMENTO));
        if (numero == null) {
            errores.add("Sin número de documento");
        } else if (numero.length() > 20) {
            errores.add("Número de documento demasiado largo: " + numero);
        }

        String nombres;
        String apellidos;
        boolean dudoso = false;
        String completo = celda(fila, mapeo, Campo.NOMBRE_COMPLETO);
        if (mapeo.columnas().containsKey(Campo.NOMBRE_COMPLETO) && completo != null) {
            String[] partes = separarNombre(completo, mapeo.ordenNombre());
            nombres = partes[0];
            apellidos = partes[1];
            if (apellidos == null) {
                advertencias.add("No se pudo separar el nombre «" + completo + "» en nombres y apellidos");
            } else {
                int cuantas = palabras(completo).size();
                dudoso = cuantas != 2 && cuantas != 4;
            }
        } else {
            nombres = unir(celda(fila, mapeo, Campo.NOMBRES), celda(fila, mapeo, Campo.PRIMER_NOMBRE),
                    celda(fila, mapeo, Campo.SEGUNDO_NOMBRE));
            apellidos = unir(celda(fila, mapeo, Campo.APELLIDOS), celda(fila, mapeo, Campo.PRIMER_APELLIDO),
                    celda(fila, mapeo, Campo.SEGUNDO_APELLIDO));
        }
        nombres = recortar(capitalizar(nombres), 120);
        apellidos = recortar(capitalizar(apellidos), 120);
        if (dudoso) {
            advertencias.add("Se separó como nombres «" + nombres + "» y apellidos «" + apellidos + "»: revíselo");
        }

        String fechaCruda = celda(fila, mapeo, Campo.FECHA_NACIMIENTO);
        LocalDate nacimiento = fecha(fechaCruda);
        if (fechaCruda != null && nacimiento == null) {
            advertencias.add("Fecha de nacimiento no reconocida: " + fechaCruda);
        } else if (nacimiento != null && (nacimiento.isAfter(LocalDate.now()) || nacimiento.getYear() < 1900)) {
            advertencias.add("Fecha de nacimiento fuera de rango: " + nacimiento);
            nacimiento = null;
        }

        String sexoCrudo = celda(fila, mapeo, Campo.SEXO);
        String sexo = sexo(sexoCrudo);
        if (sexoCrudo != null && sexo == null) {
            advertencias.add("Sexo no reconocido: " + sexoCrudo);
        }

        String telefono = recortar(telefono(celda(fila, mapeo, Campo.TELEFONO)), 30);
        String emailCrudo = celda(fila, mapeo, Campo.EMAIL);
        String email = emailCrudo == null ? null : emailCrudo.toLowerCase(Locale.ROOT);
        if (email != null && (!EMAIL.matcher(email).matches() || email.length() > 190)) {
            advertencias.add("Correo no válido, no se importa: " + emailCrudo);
            email = null;
        }

        return new FilaPersona(numeroFila, tipo, numero,
                new DatosPersona(nombres, apellidos, nacimiento, sexo, telefono, email), errores, advertencias);
    }

    private static String celda(List<String> fila, Mapeo mapeo, Campo campo) {
        Integer i = mapeo.columnas().get(campo);
        if (i == null || i < 0 || i >= fila.size()) {
            return null;
        }
        String v = fila.get(i).strip();
        return v.isEmpty() ? null : v;
    }

    private static String unir(String... partes) {
        String s = Arrays.stream(partes).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" "));
        return s.isEmpty() ? null : s;
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * Separa un nombre completo. Con 4 palabras o 2 no hay duda (2+2, 1+1); con 3 se asume un nombre y dos
     * apellidos, que es lo más común en Colombia, y con más, dos nombres y el resto apellidos. Las partículas
     * (de, del, la) van con la palabra siguiente: "María de los Ángeles Pérez" no se parte en "de".
     */
    static String[] separarNombre(String completo, OrdenNombre orden) {
        List<String> palabras = palabras(completo);
        if (palabras.size() < 2) {
            return new String[] {completo.strip(), null};
        }
        int primeros = switch (palabras.size()) {
            case 2 -> 1;
            case 3 -> orden == OrdenNombre.APELLIDOS_NOMBRES ? 2 : 1;
            default -> 2;
        };
        String a = String.join(" ", palabras.subList(0, primeros));
        String b = String.join(" ", palabras.subList(primeros, palabras.size()));
        return orden == OrdenNombre.APELLIDOS_NOMBRES ? new String[] {b, a} : new String[] {a, b};
    }

    /** Palabras del nombre con las partículas unidas a la siguiente: "Ana de la Hoz" → [Ana, de la Hoz]. */
    private static List<String> palabras(String completo) {
        List<String> palabras = new ArrayList<>();
        StringBuilder pendiente = new StringBuilder();
        for (String p : completo.strip().split("\\s+")) {
            pendiente.append(pendiente.isEmpty() ? "" : " ").append(p);
            if (!PARTICULAS.contains(p.toLowerCase(Locale.ROOT))) {
                palabras.add(pendiente.toString());
                pendiente.setLength(0);
            }
        }
        if (!pendiente.isEmpty()) {
            palabras.add(pendiente.toString());
        }
        return palabras;
    }

    /** "MARÍA JOSÉ DE LA HOZ" → "María José de la Hoz". Si ya viene con mayúsculas y minúsculas, se respeta. */
    static String capitalizar(String s) {
        if (s == null) {
            return null;
        }
        String limpio = s.strip().replaceAll("\\s+", " ");
        if (!limpio.equals(limpio.toUpperCase(Locale.ROOT)) && !limpio.equals(limpio.toLowerCase(Locale.ROOT))) {
            return limpio;
        }
        String[] palabras = limpio.toLowerCase(Locale.ROOT).split(" ");
        for (int i = 0; i < palabras.length; i++) {
            String p = palabras[i];
            if (i > 0 && PARTICULAS.contains(p)) {
                continue;
            }
            palabras[i] = Arrays.stream(p.split("-"))
                    .map(x -> x.isEmpty() ? x : Character.toUpperCase(x.charAt(0)) + x.substring(1))
                    .collect(Collectors.joining("-"));
        }
        return String.join(" ", palabras);
    }

    static String tipoDocumento(String s) {
        String n = normalizar(s).replace(" ", "");
        String tipo = switch (n) {
            case "cc", "cedula", "ceduladeciudadania", "cedulaciudadania" -> "CC";
            case "ti", "tarjetadeidentidad", "tarjetaidentidad" -> "TI";
            case "ce", "ceduladeextranjeria", "cedulaextranjeria" -> "CE";
            case "rc", "registrocivil", "registrocivildenacimiento" -> "RC";
            case "pa", "pas", "pasaporte" -> "PA";
            case "pt", "ppt", "permisodeprotecciontemporal", "permisoporprotecciontemporal" -> "PT";
            case "pe", "pep", "permisoespecialdepermanencia" -> "PE";
            case "cn", "certificadodenacidovivo" -> "CN";
            case "ms", "menorsinidentificacion" -> "MS";
            case "as", "adultosinidentificacion" -> "AS";
            default -> n.toUpperCase(Locale.ROOT);
        };
        return TiposDocumento.VALIDOS.contains(tipo) ? tipo : null;
    }

    /** Sin puntos ni espacios ("1.020.304.050" → "1020304050"); los pasaportes conservan letras. */
    static String numeroDocumento(String s) {
        if (s == null) {
            return null;
        }
        String n = s.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s.,]", "").replace("-", "");
        return n.isEmpty() ? null : n;
    }

    static LocalDate fecha(String s) {
        if (s == null) {
            return null;
        }
        String v = s.strip();
        // Excel guarda las fechas como días desde el 30/12/1899 (si la celda no tiene formato de texto)
        if (v.matches("\\d{4,5}")) {
            int dias = Integer.parseInt(v);
            if (dias > 366 && dias < 73_000) {
                return LocalDate.of(1899, 12, 30).plusDays(dias);
            }
        }
        int espacio = v.indexOf(' ');
        int t = v.indexOf('T');
        if (t > 0 && (espacio < 0 || t < espacio)) {
            espacio = t;
        }
        String soloFecha = espacio > 0 ? v.substring(0, espacio) : v;
        for (DateTimeFormatter f : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(soloFecha, f);
            } catch (DateTimeParseException e) {
                // siguiente formato
            }
        }
        return null;
    }

    /** "M" es masculino (como en RIPS), no "mujer". */
    static String sexo(String s) {
        if (s == null) {
            return null;
        }
        return switch (normalizar(s)) {
            case "f", "femenino", "mujer", "fem" -> "F";
            case "m", "masculino", "hombre", "h", "masc" -> "M";
            case "i", "indeterminado", "intersexual" -> "I";
            default -> null;
        };
    }

    static String telefono(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replaceAll("[^0-9+ ]", " ").strip().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }
}
