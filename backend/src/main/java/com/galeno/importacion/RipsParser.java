package com.galeno.importacion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.galeno.pacientes.TiposDocumento;

/**
 * Lee RIPS en JSON (un archivo por factura electrónica) y agrupa por paciente todo lo atendido en todos los archivos.
 * No toca la base de datos: el resultado se revisa antes de confirmar.
 *
 * <p>Revisado contra el Documento Técnico 1 v003 de la Res. 948 de 2026 (15 de julio de 2026); lee también los RIPS
 * de la Res. 2275 de 2023, que tienen los mismos nombres de campo salvo los nuevos.
 *
 * <p>Se importan consultas, procedimientos, urgencias y hospitalización como atenciones. Medicamentos,
 * recién nacidos y otros servicios se cuentan como omitidos en esta versión.
 *
 * <p>Diagnósticos: Galenum usa CIE-10. Los pares CIE-11 (`...CIE11`, opcionales en la 948) se ignoran, pero si un
 * diagnóstico viene solo en CIE-11 se avisa en la revisión.
 */
final class RipsParser {

    static final int MAX_ARCHIVOS = 5000;
    static final long MAX_BYTES_DESCOMPRIMIDOS = 300L * 1024 * 1024;

    private static final Set<String> OMITIDOS = Set.of("medicamentos", "recienNacidos", "otrosServicios");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private final JsonMapper mapper;

    RipsParser(JsonMapper mapper) {
        this.mapper = mapper;
    }

    record Archivo(String nombre, byte[] contenido) {
    }

    record DxRips(String codigo, boolean principal, String tipoRips) {
    }

    /**
     * @param clave       factura + consecutivo del usuario + tipo + consecutivo del servicio: identifica el
     *                    servicio aunque el archivo se suba dos veces
     * @param profesional documento del profesional que atendió, tal como viene en el RIPS
     * @param codigoVida  identificador de la atención en la interoperabilidad de historia clínica (IHCE), si viene
     */
    record AtencionRips(String clave, String tipo, LocalDateTime fecha, String codigoCups, String profesional,
                        String factura, List<DxRips> diagnosticos, String codigoVida) {
    }

    /**
     * Campos de diagnóstico CIE-10 de cada servicio, en orden; el primero que venga es el principal. En urgencias y
     * hospitalización el principal es el de egreso (`PrincipalE`, el definitivo) y el de ingreso queda relacionado.
     */
    private static final Map<String, List<String>> CAMPOS_DX = Map.of(
            "consultas", List.of("codDiagnosticoPrincipal", "codDiagnosticoRelacionado1", "codDiagnosticoRelacionado2",
                    "codDiagnosticoRelacionado3"),
            "procedimientos", List.of("codDiagnosticoPrincipal", "codDiagnosticoRelacionado", "codComplicacion"),
            "urgencias", List.of("codDiagnosticoPrincipalE", "codDiagnosticoPrincipal", "codDiagnosticoRelacionadoE1",
                    "codDiagnosticoRelacionadoE2", "codDiagnosticoRelacionadoE3", "codDiagnosticoCausaMuerte"),
            "hospitalizacion", List.of("codDiagnosticoPrincipalE", "codDiagnosticoPrincipal",
                    "codDiagnosticoRelacionadoE1", "codDiagnosticoRelacionadoE2", "codDiagnosticoRelacionadoE3",
                    "codComplicacion", "codDiagnosticoCausaMuerte"));

    /** Un paciente con todo lo que trae en todos los archivos. */
    static final class PacienteRips {
        String tipoDocumento;
        String numeroDocumento;
        LocalDate fechaNacimiento;
        String sexo;
        // Datos del usuario para el RIPS (U03, U06 a U09, U11): el último valor que venga en los archivos
        String tipoUsuario;
        String paisResidencia;
        String municipio;
        String zona;
        String paisOrigen;
        final List<AtencionRips> atenciones = new ArrayList<>();
        final List<String> errores = new ArrayList<>();
        final List<String> advertencias = new ArrayList<>();

        String clave() {
            return tipoDocumento + " " + numeroDocumento;
        }
    }

    record Resultado(int archivos, Set<String> facturas, Set<String> prestadores, Map<String, PacienteRips> pacientes,
                     int omitidos, List<String> erroresArchivo) {
    }

    Resultado leer(List<Archivo> subidos) {
        List<Archivo> archivos = new ArrayList<>();
        List<String> erroresArchivo = new ArrayList<>();
        for (Archivo a : subidos) {
            if (a.nombre().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                archivos.addAll(descomprimir(a, erroresArchivo));
            } else {
                archivos.add(a);
            }
        }
        if (archivos.size() > MAX_ARCHIVOS) {
            throw new IllegalArgumentException("Máximo " + MAX_ARCHIVOS + " archivos por lote");
        }

        Set<String> facturas = new LinkedHashSet<>();
        Set<String> prestadores = new LinkedHashSet<>();
        Map<String, PacienteRips> pacientes = new LinkedHashMap<>();
        int omitidos = 0;
        int leidos = 0;
        for (Archivo archivo : archivos) {
            JsonNode raiz;
            try {
                raiz = mapper.readTree(quitarBom(archivo.contenido()));
            } catch (JacksonException e) {
                erroresArchivo.add(archivo.nombre() + ": no es un JSON válido");
                continue;
            }
            JsonNode usuarios = raiz.path("usuarios");
            if (!usuarios.isArray()) {
                erroresArchivo.add(archivo.nombre() + ": no tiene el bloque \"usuarios\" de un RIPS");
                continue;
            }
            leidos++;
            String factura = texto(raiz, "numFactura");
            if (factura == null) {
                factura = archivo.nombre();
            }
            facturas.add(factura);
            String prestador = texto(raiz, "numDocumentoIdObligado");
            if (prestador != null) {
                prestadores.add(prestador);
            }
            for (JsonNode u : usuarios) {
                PacienteRips p = paciente(u, pacientes);
                String consecutivoUsuario = texto(u, "consecutivo");
                JsonNode servicios = u.path("servicios");
                for (String tipo : List.of("consultas", "procedimientos", "urgencias", "hospitalizacion")) {
                    for (JsonNode s : servicios.path(tipo)) {
                        AtencionRips at = atencion(tipo, s, factura, consecutivoUsuario, p);
                        if (at != null) {
                            p.atenciones.add(at);
                        }
                    }
                }
                for (String tipo : OMITIDOS) {
                    omitidos += servicios.path(tipo).size();
                }
            }
        }
        for (PacienteRips p : pacientes.values()) {
            p.atenciones.sort((a, b) -> a.fecha().compareTo(b.fecha()));
        }
        return new Resultado(leidos, facturas, prestadores, pacientes, omitidos, erroresArchivo);
    }

    private PacienteRips paciente(JsonNode u, Map<String, PacienteRips> pacientes) {
        String tipo = mayus(texto(u, "tipoDocumentoIdentificacion"));
        String numero = texto(u, "numDocumentoIdentificacion");
        PacienteRips nuevo = new PacienteRips();
        nuevo.tipoDocumento = tipo == null ? "?" : tipo;
        nuevo.numeroDocumento = numero == null ? "?" : numero.strip();
        PacienteRips p = pacientes.computeIfAbsent(nuevo.clave(), k -> nuevo);

        LocalDate nacimiento = fecha(texto(u, "fechaNacimiento"));
        String sexo = mayus(texto(u, "codSexo"));
        if (p == nuevo) {
            p.fechaNacimiento = nacimiento;
            p.sexo = sexo;
            if (tipo == null || numero == null || numero.isBlank()) {
                p.errores.add("Sin tipo o número de documento");
            } else if (!TiposDocumento.VALIDOS.contains(tipo)) {
                p.errores.add("Tipo de documento desconocido: " + tipo);
            }
            if (nacimiento == null) {
                p.errores.add("Sin fecha de nacimiento válida");
            }
            if (sexo == null || !Set.of("F", "M", "I").contains(sexo)) {
                p.errores.add("Sexo no válido: " + sexo);
            }
        } else if ((nacimiento != null && !nacimiento.equals(p.fechaNacimiento)) || (sexo != null && !sexo.equals(p.sexo))) {
            p.advertencias.add("La fecha de nacimiento o el sexo cambian entre archivos; se usa el primero");
        }
        // Un archivo que no trae el dato no borra el que trajo otro
        p.tipoUsuario = siViene(texto(u, "tipoUsuario"), p.tipoUsuario);
        p.paisResidencia = siViene(texto(u, "codPaisResidencia"), p.paisResidencia);
        p.municipio = siViene(texto(u, "codMunicipioResidencia"), p.municipio);
        p.zona = siViene(texto(u, "codZonaTerritorialResidencia"), p.zona);
        p.paisOrigen = siViene(texto(u, "codPaisOrigen"), p.paisOrigen);
        return p;
    }

    private static AtencionRips atencion(String tipo, JsonNode s, String factura, String consecutivoUsuario, PacienteRips p) {
        LocalDateTime fecha = fechaHora(texto(s, "fechaInicioAtencion"));
        if (fecha == null) {
            p.advertencias.add("Un servicio de " + tipo + " sin fecha válida no se importa");
            return null;
        }
        String cups = switch (tipo) {
            case "consultas" -> texto(s, "codConsulta");
            case "procedimientos" -> texto(s, "codProcedimiento");
            default -> null;
        };
        List<DxRips> dx = new ArrayList<>();
        for (String campo : CAMPOS_DX.get(tipo)) {
            String codigo = mayus(texto(s, campo));
            if (codigo == null) {
                String cie11 = texto(s, campo + "CIE11");
                if (cie11 != null) {
                    p.advertencias.add("Un diagnóstico de " + tipo + " viene solo en CIE-11 (" + cie11
                            + "): Galenum usa CIE-10 y no lo importa");
                }
                continue;
            }
            if (dx.stream().noneMatch(d -> d.codigo().equals(codigo))) {
                // Si falta el principal, un relacionado no pasa a serlo
                boolean principal = dx.isEmpty() && campo.startsWith("codDiagnosticoPrincipal");
                // El tipo (impresión, confirmado nuevo o repetido) solo lo traen las consultas y es del principal
                dx.add(new DxRips(codigo, principal, principal ? texto(s, "tipoDiagnosticoPrincipal") : null));
            }
        }
        String profesional = texto(s, "numDocumentoIdentificacion");
        if (profesional != null) {
            String tipoDoc = texto(s, "tipoDocumentoIdentificacion");
            profesional = (tipoDoc == null ? "" : tipoDoc + " ") + profesional;
        }
        String consecutivo = texto(s, "consecutivo");
        String clave = String.join("|", factura, String.valueOf(consecutivoUsuario), tipo,
                consecutivo != null ? consecutivo : fecha + "|" + cups);
        return new AtencionRips(clave, tipo, fecha, cups, profesional, factura, dx, texto(s, "codigoVIDA"));
    }

    private static List<Archivo> descomprimir(Archivo zip, List<String> errores) {
        List<Archivo> archivos = new ArrayList<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip.contenido()))) {
            ZipEntry entrada;
            while ((entrada = in.getNextEntry()) != null) {
                String nombre = entrada.getName();
                if (entrada.isDirectory() || nombre.contains("__MACOSX") || !nombre.toLowerCase(Locale.ROOT).endsWith(".json")) {
                    continue;
                }
                if (archivos.size() >= MAX_ARCHIVOS) {
                    throw new IllegalArgumentException("Máximo " + MAX_ARCHIVOS + " archivos por lote");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    total += n;
                    // Protección contra "bombas zip": se corta por tamaño real descomprimido, no por lo que diga el zip
                    if (total > MAX_BYTES_DESCOMPRIMIDOS) {
                        throw new IllegalArgumentException("El ZIP descomprimido supera 300 MB");
                    }
                    out.write(buffer, 0, n);
                }
                archivos.add(new Archivo(zip.nombre() + "/" + nombre, out.toByteArray()));
            }
        } catch (IOException e) {
            errores.add(zip.nombre() + ": no se pudo leer el ZIP");
        }
        if (archivos.isEmpty()) {
            errores.add(zip.nombre() + ": no contiene archivos .json");
        }
        return archivos;
    }

    private static byte[] quitarBom(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        }
        return b;
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asString().strip();
        return s.isEmpty() ? null : s;
    }

    private static String siViene(String nuevo, String actual) {
        return nuevo != null ? nuevo : actual;
    }

    private static String mayus(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT).replace(".", "");
    }

    private static LocalDate fecha(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** RIPS usa "AAAA-MM-DD HH:MM"; también se acepta ISO y fecha sola. */
    static LocalDateTime fechaHora(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, FECHA_HORA);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException e2) {
                LocalDate d = fecha(s);
                return d == null ? null : d.atStartOfDay();
            }
        }
    }

}
