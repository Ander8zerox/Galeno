package com.galeno.rips;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.galeno.historia.HistoriaService.AtencionRips;
import com.galeno.historia.HistoriaService.Cie10Vista;
import com.galeno.historia.HistoriaService.DiagnosticoVista;
import com.galeno.historia.HistoriaService.ProcedimientoVista;
import com.galeno.shared.web.ApiExceptionHandler.ApiProblem;

/**
 * Arma el JSON del RIPS de un lote (Documento Técnico 1 v003, consultas y procedimientos) y revisa lo que Galenum puede
 * revisar por su cuenta. No consulta la base: recibe todo en {@link Entrada}, así se prueba sin Spring.
 *
 * <p>Los hallazgos de nivel {@link Nivel#RECHAZO} impiden generar (el MUV rechazaría el archivo, o falta un dato
 * obligatorio); los de nivel {@link Nivel#AVISO} son las reglas que el MUV trata como notificación.
 */
final class ArmadorRips {

    static final String POR_EVENTO = "04";
    private static final String NO_APLICA = CobrosService.NO_APLICA;
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Tipos de usuario SOAT y lesionado sin SOAT: piden el registro SIRAS (U12, RVC095). */
    private static final Set<String> CON_SIRAS = CobrosService.CON_SIRAS;

    /**
     * Numeral 1.8 del DT1: por cobertura o plan de beneficios, las modalidades de pago, los tipos de usuario y los
     * conceptos de recaudo con que se puede reportar (lo demás genera rechazo). Sin el 04 (anticipo), que solo va en la
     * factura. El DT1 v003 marca la 01 como inactiva y la reparte en la 16 (contributivo) y la 17 (subsidiado), pero
     * SISPRO todavía publica la 01 y no las nuevas: la 01 admite lo de las dos.
     */
    static final Map<String, Cruce> CRUCES = Map.ofEntries(
            cruce("01", "01 02 03 04", "01 02 03 04", "01 02 05"),
            cruce("02", "04", "01 02 03 04", "01 02 05"),
            cruce("03", "", "", ""),
            cruce("04", "01 04", "10", "05"),
            cruce("05", "01 02 03 04", "09", "05"),
            cruce("06", "01 04", "01 02 03 04 05 06 07 08 09 10 11 12 13 14", "05"),
            cruce("07", "01 03 04", "01 02 03 04 05 06 07 08 09 10 11 12 13", "05"),
            cruce("08", "04", "05", "05"),
            cruce("09", "04", "05", "05"),
            cruce("10", "01 02 03 04", "11", "03 05"),
            cruce("11", "01 02 03 04", "11", "03 05"),
            cruce("12", "01 04", "11", "03 05"),
            cruce("13", "01 02 03 04", "06 07 13", "01 02 05"),
            cruce("14", "01 02 03 04", "08", "05"),
            cruce("15", "01 04", "12", "05"),
            cruce("16", "01 02 03 04", "01 02 03", "02 05"),
            cruce("17", "01 02 03 04", "04", "01 02 05"));

    record Cruce(Set<String> modalidades, Set<String> tiposUsuario, Set<String> conceptos) {
    }

    private static Map.Entry<String, Cruce> cruce(String cobertura, String modalidades, String tipos, String conceptos) {
        return Map.entry(cobertura, new Cruce(conjunto(modalidades), conjunto(tipos), conjunto(conceptos)));
    }

    private static Set<String> conjunto(String s) {
        return s.isBlank() ? Set.of() : Set.of(s.split(" "));
    }

    enum Nivel { RECHAZO, AVISO }

    /** Dónde se corrige: la pantalla enlaza a ese lugar. */
    enum Donde { LOTE, CONSULTORIO, PRESTADOR, PROFESIONAL, PACIENTE, PAGADOR, COBRO, ATENCION, PRECIOS }

    /**
     * @param atencionId {@code null} si es del lote completo
     * @param regla la del DT1 (RVC0xx), si la hay
     */
    record HallazgoRips(UUID atencionId, UUID pacienteId, Nivel nivel, String regla, Donde donde, String mensaje) {
    }

    // --- entrada ---

    /** Lo que el pagador aporta al RIPS (de la factura: cobertura y modalidad de pago). */
    record PagadorRips(UUID id, String nombre, Pagador.Tipo tipo, String cobertura, String modalidadPago) {
    }

    record PrestadorRips(String codigoHabilitacion, String servicio, String modalidadAtencion, String grupoServicios,
                         String finalidad, String causaExterna, String viaIngreso) {
    }

    /** Ficha del paciente y sus datos del RIPS ({@code faltantes}: lo que el RIPS pide y no está). */
    record PacienteRips(UUID id, String nombre, String tipoDocumento, String numeroDocumento, LocalDate fechaNacimiento,
                        String sexo, String tipoUsuario, String paisResidencia, String municipioResidencia,
                        String zonaResidencia, String paisOrigen, List<String> faltantes) {

        /** Como particular: tipo de usuario 12, y ya no falta. */
        PacienteRips particular() {
            return new PacienteRips(id, nombre, tipoDocumento, numeroDocumento, fechaNacimiento, sexo, PARTICULAR,
                    paisResidencia, municipioResidencia, zonaResidencia, paisOrigen,
                    faltantes.stream().filter(f -> !f.equals("Tipo de usuario")).toList());
        }
    }

    /** Tipo de usuario 12: el único que admite la cobertura 15 (particular) en el numeral 1.8. */
    static final String PARTICULAR = "12";

    record ProfesionalRips(String tipoDocumento, String numeroDocumento) {
    }

    /** Lo que se cambió para una atención en la revisión ({@code null}: el valor por defecto del prestador). */
    record Ajustes(String finalidad, String causaExterna, String modalidadAtencion, String grupoServicios,
                   String servicio, String viaIngreso) {

        static final Ajustes NINGUNO = new Ajustes(null, null, null, null, null, null);
    }

    /**
     * @param precios el precio vigente de cada CUPS sin valor en la atención (tarifa del pagador o lista general)
     * @param profesional {@code null} si quien atendió no tiene su documento registrado
     */
    record AtencionLote(AtencionRips atencion, PacienteRips paciente, CobrosService.CobroVista cobro,
                        ProfesionalRips profesional, Ajustes ajustes, Map<String, BigDecimal> precios) {
    }

    /** Fila de una tabla de referencia: si está habilitada y sus columnas extra. */
    record Fila(boolean habilitado, Map<String, String> extra) {
    }

    /** Lo que se leyó del XML de la factura electrónica. */
    record DatosXml(String nit, String numero, LocalDate desde, LocalDate hasta, BigDecimal pagosModeradores) {
    }

    /**
     * @param nitObligado NIT del consultorio (con o sin dígito de verificación)
     * @param cupsRips filas de CUPSRips por código; {@code finalidades}, de la tabla de finalidades
     * @param xml {@code null} si no se cargó
     */
    /** @param numeroEnvio del RIPS sin factura (numNota): se asigna al generar; en la revisión, {@code null} */
    record Entrada(String nitObligado, boolean sinFactura, String numeroFactura, Integer numeroEnvio, PagadorRips pagador,
                   PrestadorRips prestador, List<AtencionLote> atenciones, Map<String, Fila> cupsRips,
                   Map<String, Cie10Vista> cie10, Map<String, Fila> finalidades, DatosXml xml, ZoneId zona,
                   Instant ahora) {
    }

    record Resultado(Map<String, Object> json, List<HallazgoRips> hallazgos) {

        boolean hayRechazos() {
            return hallazgos.stream().anyMatch(h -> h.nivel() == Nivel.RECHAZO);
        }
    }

    private ArmadorRips() {
    }

    // --- armado ---

    static Resultado armar(Entrada entrada) {
        Entrada e = comoParticular(entrada);
        List<HallazgoRips> h = new ArrayList<>();
        String obligado = nitSinDigito(e.nitObligado());
        revisarLote(e, obligado, h);

        // Un usuario por paciente, en el orden de su primera atención
        Map<UUID, List<AtencionLote>> porPaciente = e.atenciones().stream()
                .sorted((a, b) -> a.atencion().fecha().compareTo(b.atencion().fecha()))
                .collect(Collectors.groupingBy(a -> a.paciente().id(), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> usuarios = new ArrayList<>();
        BigDecimal moderadores = BigDecimal.ZERO;
        for (List<AtencionLote> delPaciente : porPaciente.values()) {
            PacienteRips p = delPaciente.get(0).paciente();
            revisarPaciente(e, p, delPaciente, h);
            List<Map<String, Object>> consultas = new ArrayList<>();
            List<Map<String, Object>> procedimientos = new ArrayList<>();
            Map<String, Integer> porDia = new HashMap<>();
            for (AtencionLote a : delPaciente) {
                moderadores = moderadores.add(armarAtencion(e, a, consultas, procedimientos, porDia, h));
            }
            Map<String, Object> servicios = new LinkedHashMap<>();
            if (!consultas.isEmpty()) {
                servicios.put("consultas", consultas);
            }
            if (!procedimientos.isEmpty()) {
                servicios.put("procedimientos", procedimientos);
            }
            Map<String, Object> u = new LinkedHashMap<>();
            // En el orden del ejemplo oficial del DT1 (el MUV no depende del orden, pero así se compara a ojo)
            u.put("consecutivo", usuarios.size() + 1);
            u.put("tipoDocumentoIdentificacion", p.tipoDocumento());
            u.put("numDocumentoIdentificacion", p.numeroDocumento());
            u.put("tipoUsuario", p.tipoUsuario());
            u.put("fechaNacimiento", p.fechaNacimiento() == null ? null : p.fechaNacimiento().toString());
            u.put("codSexo", p.sexo());
            u.put("codPaisResidencia", p.paisResidencia());
            u.put("codMunicipioResidencia", p.municipioResidencia());
            u.put("codZonaTerritorialResidencia", p.zonaResidencia());
            u.put("incapacidad", delPaciente.stream().anyMatch(a -> a.atencion().incapacidad()) ? "SI" : "NO");
            u.put("codPaisOrigen", p.paisOrigen());
            boolean conSiras = p.tipoUsuario() != null && CON_SIRAS.contains(p.tipoUsuario());
            u.put("registroSIRAS", conSiras ? siras(delPaciente, h) : null);
            u.put("servicios", servicios);
            usuarios.add(u);
        }
        revisarXml(e, obligado, moderadores, h);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("numDocumentoIdObligado", obligado);
        json.put("numFactura", e.sinFactura() ? null : texto(e.numeroFactura()));
        json.put("tipoNota", e.sinFactura() ? "RS" : null);
        // Sin factura, un número propio del prestador y único por envío (lineamientos del Ministerio, numeral 4.4.2)
        json.put("numNota", e.sinFactura() && e.numeroEnvio() != null ? String.valueOf(e.numeroEnvio()) : null);
        json.put("usuarios", usuarios);
        return new Resultado(json, h);
    }

    /**
     * Con el pagador particular, todos van como tipo de usuario 12 aunque la ficha diga otro: un afiliado a una EPS
     * (01) que paga una consulta de su bolsillo es particular en esa factura. La ficha queda como está.
     */
    private static Entrada comoParticular(Entrada e) {
        if (e.pagador().tipo() != Pagador.Tipo.PARTICULAR) {
            return e;
        }
        List<AtencionLote> atenciones = e.atenciones().stream().map(a -> new AtencionLote(a.atencion(),
                a.paciente().particular(), a.cobro(), a.profesional(), a.ajustes(), a.precios())).toList();
        return new Entrada(e.nitObligado(), e.sinFactura(), e.numeroFactura(), e.numeroEnvio(), e.pagador(), e.prestador(),
                atenciones,
                e.cupsRips(), e.cie10(), e.finalidades(), e.xml(), e.zona(), e.ahora());
    }

    /** Agrega los servicios de la atención y devuelve el pago moderador que reportó (va en su primer servicio). */
    private static BigDecimal armarAtencion(Entrada e, AtencionLote a, List<Map<String, Object>> consultas,
                                            List<Map<String, Object>> procedimientos, Map<String, Integer> porDia,
                                            List<HallazgoRips> h) {
        AtencionRips at = a.atencion();
        PacienteRips p = a.paciente();
        Revision r = new Revision(at.id(), p.id(), h);
        var cobro = a.cobro();
        PrestadorRips pr = e.prestador();
        Ajustes aj = a.ajustes() == null ? Ajustes.NINGUNO : a.ajustes();
        String finalidad = valor(aj.finalidad(), pr.finalidad());
        String causa = valor(aj.causaExterna(), pr.causaExterna());
        String modalidad = valor(aj.modalidadAtencion(), pr.modalidadAtencion());
        String grupo = valor(aj.grupoServicios(), pr.grupoServicios());
        String servicio = valor(aj.servicio(), pr.servicio());
        String via = valor(aj.viaIngreso(), pr.viaIngreso());
        LocalDate dia = LocalDate.ofInstant(at.fecha(), e.zona());

        if (at.fecha().isAfter(e.ahora())) {
            r.rechazo("RVC013", Donde.ATENCION, "La fecha de la atención es posterior a hoy");
        }
        if (cobro == null || cobro.pagadorId() == null || !cobro.pagadorId().equals(e.pagador().id())) {
            r.rechazo(null, Donde.COBRO, "El pagador de la atención no es " + e.pagador().nombre());
        }
        if (a.profesional() == null) {
            r.rechazo(null, Donde.PROFESIONAL, "Falta el documento de " + at.medicoNombre() + " (pestaña Profesionales)");
        }
        if (modalidad == null || grupo == null || servicio == null || finalidad == null) {
            r.rechazo(null, Donde.PRESTADOR, "Faltan la modalidad, el grupo, el servicio o la finalidad: fíjelos en el "
                    + "prestador o en esta atención");
        }
        if (cobro != null && cobro.exigeAutorizacion() && cobro.numeroAutorizacion() == null) {
            r.aviso(null, Donde.COBRO, cobro.pagadorNombre() + " exige número de autorización y no está registrado");
        }
        String concepto = cobro == null || cobro.conceptoRecaudo() == null ? NO_APLICA : cobro.conceptoRecaudo();
        BigDecimal moderador = cobro == null || cobro.valorPagoModerador() == null ? BigDecimal.ZERO
                : cobro.valorPagoModerador();
        Cruce cruce = CRUCES.get(e.pagador().cobertura());
        if (cruce != null && !cruce.conceptos().contains(concepto)) {
            r.rechazo(null, Donde.COBRO, "Con la cobertura " + e.pagador().cobertura() + " del pagador no se reporta el "
                    + "concepto de recaudo " + concepto + " (numeral 1.8 del DT1)");
        }
        if (cobro != null && e.pagador().tipo() != null) {
            try {
                CobrosService.validar(e.pagador().tipo(), concepto, moderador, p.tipoUsuario());
            } catch (ApiProblem ex) {
                r.rechazo(null, Donde.COBRO, ex.getMessage());
            }
        }
        if (finalidad != null) {
            revisarFinalidad(e, r, finalidad, p, dia);
        }

        // Diagnósticos: el principal y los relacionados sin repetir (RVC086, RVC087)
        DiagnosticoVista principal = at.diagnosticos().stream().filter(DiagnosticoVista::principal).findFirst()
                .orElse(null);
        if (principal == null) {
            r.rechazo(null, Donde.ATENCION, "La atención no tiene diagnóstico principal");
        }
        List<String> relacionados = new ArrayList<>();
        for (DiagnosticoVista d : at.diagnosticos()) {
            if (!d.principal() && (principal == null || !d.codigoCie10().equals(principal.codigoCie10()))
                    && !relacionados.contains(d.codigoCie10()) && relacionados.size() < 3) {
                relacionados.add(d.codigoCie10());
            }
        }
        for (DiagnosticoVista d : at.diagnosticos()) {
            revisarCie10(e, r, d.codigoCie10(), p, dia);
        }

        if (at.procedimientos().isEmpty()) {
            r.rechazo(null, Donde.ATENCION, "La atención no tiene la consulta ni procedimientos registrados (CUPS): "
                    + "quítela del lote");
        }
        boolean primero = true;
        BigDecimal reportado = BigDecimal.ZERO;
        for (ProcedimientoVista pv : at.procedimientos()) {
            String codigo = pv.codigoCups();
            Fila cups = e.cupsRips().get(codigo);
            String uso = cups == null ? null : cups.extra().get("uso");
            boolean esConsulta = uso == null ? pv.consulta() : "AC".equals(uso);
            if (cups == null || !cups.habilitado()) {
                r.aviso("RVC096", Donde.ATENCION, "El CUPS " + codigo + " no está habilitado en la tabla CUPSRips");
            } else if ("AT".equals(uso)) {
                r.rechazo(null, Donde.ATENCION, "El CUPS " + codigo + " se reporta en \"otros servicios\", que Galenum "
                        + "todavía no genera: quite la atención del lote");
                continue;
            }
            if (cups != null && cups.habilitado()) {
                String sexo = cups.extra().getOrDefault("sexo", "Z");
                if (("F".equals(sexo) || "M".equals(sexo)) && p.sexo() != null && !"I".equals(p.sexo())
                        && !sexo.equals(p.sexo())) {
                    r.aviso("RVC016", Donde.ATENCION, "El CUPS " + codigo + " es solo para sexo " + sexo);
                }
            }
            if (!esConsulta && principal != null) {
                String dx = principal.codigoCie10();
                if (dx.startsWith("R")) {
                    r.aviso("RVC032", Donde.ATENCION, "El diagnóstico principal del procedimiento " + codigo
                            + " es de síntomas (R00-R99)");
                } else if (dx.startsWith("Z")) {
                    r.aviso("RVC033", Donde.ATENCION, "El diagnóstico principal del procedimiento " + codigo
                            + " es un factor que influye en el estado de salud (Z00-Z99)");
                }
                if (via == null) {
                    r.rechazo(null, Donde.PRESTADOR, "Falta la vía de ingreso para los procedimientos");
                }
            }
            BigDecimal unitario = pv.valorUnitario() != null ? pv.valorUnitario() : a.precios().get(codigo);
            boolean porEvento = e.sinFactura() || POR_EVENTO.equals(e.pagador().modalidadPago());
            if (porEvento && (unitario == null || unitario.signum() <= 0)) {
                r.rechazo("RVC034", Donde.PRECIOS, "El CUPS " + codigo + " no tiene valor: fije su precio o la tarifa "
                        + "del pagador");
            }
            int maximo = entero(cups == null ? null : cups.extra().get("maximo"), 1);
            for (int i = 0; i < pv.cantidad(); i++) {
                String clave = dia + "|" + codigo;
                int veces = porDia.merge(clave, 1, Integer::sum);
                if (veces == maximo + 1) {
                    r.aviso("RVC018", Donde.ATENCION, "El CUPS " + codigo + " se reporta más de " + maximo
                            + (maximo == 1 ? " vez" : " veces") + " el " + dia + " para el mismo paciente");
                }
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("codPrestador", pr.codigoHabilitacion());
                s.put("fechaInicioAtencion", FECHA_HORA.format(at.fecha().atZone(e.zona())));
                s.put("numAutorizacion", cobro == null ? null : cobro.numeroAutorizacion());
                if (esConsulta) {
                    s.put("codConsulta", codigo);
                } else {
                    s.put("idMIPRES", null);
                    s.put("codProcedimiento", codigo);
                    s.put("viaIngresoServicioSalud", via);
                }
                s.put("modalidadGrupoServicioTecSal", modalidad);
                s.put("grupoServicios", grupo);
                s.put("codServicio", servicio == null ? null : entero(servicio, 0));
                s.put("finalidadTecnologiaSalud", finalidad);
                if (esConsulta) {
                    s.put("causaMotivoAtencion", causa);
                    s.put("codDiagnosticoPrincipal", principal == null ? null : principal.codigoCie10());
                    s.put("codDiagnosticoPrincipalCIE11", null);
                    s.put("nomCodDiagnosticoPrincipalCIE11", null);
                    for (int n = 1; n <= 3; n++) {
                        s.put("codDiagnosticoRelacionado" + n, relacionados.size() >= n ? relacionados.get(n - 1) : null);
                        s.put("codDiagnosticoRelacionado" + n + "CIE11", null);
                        s.put("nomCodDiagnosticoRelacionado" + n + "CIE11", null);
                    }
                    s.put("tipoDiagnosticoPrincipal", principal == null ? null : tipoDiagnostico(principal));
                    s.put("tipoDocumentoIdentificacion", a.profesional() == null ? null : a.profesional().tipoDocumento());
                    s.put("numDocumentoIdentificacion", a.profesional() == null ? null : a.profesional().numeroDocumento());
                } else {
                    s.put("tipoDocumentoIdentificacion", a.profesional() == null ? null : a.profesional().tipoDocumento());
                    s.put("numDocumentoIdentificacion", a.profesional() == null ? null : a.profesional().numeroDocumento());
                    s.put("codDiagnosticoPrincipal", principal == null ? null : principal.codigoCie10());
                    s.put("codDiagnosticoPrincipalCIE11", null);
                    s.put("nomCodDiagnosticoPrincipalCIE11", null);
                    s.put("codDiagnosticoRelacionado", relacionados.isEmpty() ? null : relacionados.get(0));
                    s.put("codDiagnosticoRelacionadoCIE11", null);
                    s.put("nomCodDiagnosticoRelacionadoCIE11", null);
                    s.put("codComplicacion", null);
                    s.put("codComplicacionCIE11", null);
                    s.put("nomCodComplicacionCIE11", null);
                }
                // RVC034: mayor que 0 por evento, 0 en las demás modalidades. RVC091: sin factura, lo que pagó el paciente.
                s.put("vrServicio", numero(porEvento && unitario != null ? unitario : BigDecimal.ZERO));
                s.put("conceptoRecaudo", primero ? concepto : NO_APLICA);
                s.put("valorPagoModerador", numero(primero ? moderador : BigDecimal.ZERO));
                s.put("numFEVPagoModerador", primero && cobro != null ? cobro.facturaPagoModerador() : null);
                s.put("codigoVIDA", null);
                List<Map<String, Object>> lista = esConsulta ? consultas : procedimientos;
                s.put("consecutivo", lista.size() + 1);
                lista.add(s);
                if (primero) {
                    reportado = moderador;
                }
                primero = false;
            }
        }
        return reportado;
    }

    // --- revisiones ---

    private static void revisarLote(Entrada e, String obligado, List<HallazgoRips> h) {
        Revision r = new Revision(null, null, h);
        if (obligado == null) {
            r.rechazo(null, Donde.CONSULTORIO, "Falta el NIT del consultorio (numDocumentoIdObligado)");
        } else if (obligado.length() < 4 || obligado.length() > 12) {
            r.rechazo(null, Donde.CONSULTORIO, "El NIT del consultorio debe tener entre 4 y 12 dígitos sin el de verificación");
        }
        if (e.prestador().codigoHabilitacion() == null) {
            r.rechazo(null, Donde.PRESTADOR, "Falta el código de habilitación del prestador (pestaña Prestador)");
        }
        if (!e.sinFactura() && texto(e.numeroFactura()) == null) {
            r.rechazo(null, Donde.LOTE, "Escriba el número de la factura electrónica");
        }
        PagadorRips p = e.pagador();
        if (p.modalidadPago() == null || p.cobertura() == null) {
            r.rechazo(null, Donde.PAGADOR, "Faltan la cobertura o la modalidad de pago de " + p.nombre());
        } else {
            Cruce c = CRUCES.get(p.cobertura());
            if (c != null && !c.modalidades().contains(p.modalidadPago())) {
                r.rechazo(null, Donde.PAGADOR, "Con la cobertura " + p.cobertura() + " no se reporta la modalidad de pago "
                        + p.modalidadPago() + " (numeral 1.8 del DT1)");
            }
        }
        if (e.atenciones().isEmpty()) {
            r.rechazo(null, Donde.LOTE, "El lote no tiene atenciones");
        }
    }

    private static void revisarPaciente(Entrada e, PacienteRips p, List<AtencionLote> atenciones, List<HallazgoRips> h) {
        Revision r = new Revision(null, p.id(), h);
        if (!p.faltantes().isEmpty()) {
            r.rechazo(null, Donde.PACIENTE, "Faltan datos del RIPS de " + p.nombre() + ": "
                    + String.join(", ", p.faltantes()).toLowerCase());
        }
        LocalDate hoy = LocalDate.ofInstant(e.ahora(), e.zona());
        if (p.fechaNacimiento() != null && p.fechaNacimiento().isAfter(hoy)) {
            r.rechazo("RVC006", Donde.PACIENTE, "La fecha de nacimiento de " + p.nombre() + " es posterior a hoy");
        }
        if (p.fechaNacimiento() != null) {
            LocalDate ultima = atenciones.stream().map(a -> LocalDate.ofInstant(a.atencion().fecha(), e.zona()))
                    .max(LocalDate::compareTo).orElse(hoy);
            int edad = Period.between(p.fechaNacimiento(), ultima).getYears();
            if (!documentoAcordeConEdad(p.tipoDocumento(), edad)) {
                r.aviso("RVC007", Donde.PACIENTE, "El tipo de documento " + p.tipoDocumento() + " de " + p.nombre()
                        + " no corresponde a su edad (" + edad + " años)");
            }
        }
        if (p.tipoUsuario() != null && CON_SIRAS.contains(p.tipoUsuario())
                && atenciones.stream().allMatch(a -> a.cobro() == null || a.cobro().registroSiras() == null)) {
            // El DT1 lo hace obligatorio en el campo U12, aunque la regla RVC095 sea de notificación
            r.rechazo("RVC095", Donde.COBRO, "Falta el registro SIRAS de " + p.nombre() + " (tipo de usuario "
                    + p.tipoUsuario() + "): regístrelo en el cobro de la atención");
        }
        Cruce c = CRUCES.get(e.pagador().cobertura());
        if (c != null && p.tipoUsuario() != null && !c.tiposUsuario().contains(p.tipoUsuario())) {
            r.rechazo(null, Donde.PACIENTE, "El tipo de usuario " + p.tipoUsuario() + " de " + p.nombre() + " no se "
                    + "reporta con la cobertura " + e.pagador().cobertura() + " del pagador (numeral 1.8 del DT1)");
        }
    }

    /**
     * El registro SIRAS del usuario: uno por usuario en el RIPS, el de la primera atención que lo tenga. Si sus atenciones
     * traen registros distintos (dos accidentes en la misma factura), avisa.
     */
    private static String siras(List<AtencionLote> atenciones, List<HallazgoRips> h) {
        List<String> distintos = atenciones.stream().map(a -> a.cobro() == null ? null : a.cobro().registroSiras())
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (distintos.size() > 1) {
            PacienteRips p = atenciones.get(0).paciente();
            new Revision(null, p.id(), h).aviso("RVC095", Donde.COBRO, p.nombre() + " tiene registros SIRAS distintos ("
                    + String.join(", ", distintos) + "): el RIPS lleva uno por usuario y se reporta " + distintos.get(0));
        }
        return distintos.isEmpty() ? null : distintos.get(0);
    }

    /** RVC007: RC hasta 7 años, TI de 7 a 17, CC desde 18, con un año de tolerancia. Los demás documentos no se revisan. */
    static boolean documentoAcordeConEdad(String tipo, int edad) {
        return switch (tipo == null ? "" : tipo) {
            case "RC" -> edad < 8;
            case "TI" -> edad >= 6 && edad <= 18;
            case "CC" -> edad >= 17;
            default -> true;
        };
    }

    private static void revisarFinalidad(Entrada e, Revision r, String finalidad, PacienteRips p, LocalDate dia) {
        Fila f = e.finalidades().get(finalidad);
        if (f == null) {
            return;
        }
        String sexo = f.extra().getOrDefault("Extra_VI", "Z");
        if (("F".equals(sexo) || "M".equals(sexo)) && p.sexo() != null && !"I".equals(p.sexo()) && !sexo.equals(p.sexo())) {
            r.aviso("RVC051", Donde.ATENCION, "La finalidad " + finalidad + " es solo para sexo " + sexo);
        }
        if (p.fechaNacimiento() != null) {
            int edad = Period.between(p.fechaNacimiento(), dia).getYears();
            int min = entero(f.extra().get("Extra_VII"), 0);
            int max = entero(f.extra().get("Extra_VIII"), 999);
            if (edad < min || edad > max) {
                r.aviso("RVC051", Donde.ATENCION, "La finalidad " + finalidad + " es para edades de " + min + " a " + max
                        + " años");
            }
        }
    }

    private static void revisarCie10(Entrada e, Revision r, String codigo, PacienteRips p, LocalDate dia) {
        Cie10Vista c = e.cie10().get(codigo);
        if (c == null) {
            return;
        }
        if (("F".equals(c.sexo()) || "M".equals(c.sexo())) && p.sexo() != null && !"I".equals(p.sexo())
                && !c.sexo().equals(p.sexo())) {
            r.aviso("RVC028", Donde.ATENCION, "El diagnóstico " + codigo + " es solo para sexo " + c.sexo());
        }
        if (p.fechaNacimiento() != null) {
            int edad = Period.between(p.fechaNacimiento(), dia).getYears();
            int min = c.edadMin() == null ? 0 : c.edadMin();
            int max = c.edadMax() == null ? 999 : c.edadMax();
            if (edad < min || edad > max) {
                r.aviso("RVC028", Donde.ATENCION, "El diagnóstico " + codigo + " es para edades de " + min + " a " + max
                        + " años");
            }
        }
    }

    private static void revisarXml(Entrada e, String obligado, BigDecimal moderadores, List<HallazgoRips> h) {
        DatosXml x = e.xml();
        if (x == null) {
            return;
        }
        Revision r = new Revision(null, null, h);
        if (x.nit() != null && obligado != null && !x.nit().equals(obligado)) {
            r.rechazo("RVC001", Donde.CONSULTORIO, "El NIT de la factura (" + x.nit() + ") no es el del consultorio ("
                    + obligado + ")");
        }
        String numero = texto(e.numeroFactura());
        if (x.numero() != null && numero != null && !x.numero().equals(numero)) {
            r.rechazo("RVC004", Donde.LOTE, "El número de la factura del XML (" + x.numero() + ") no es el del lote ("
                    + numero + ")");
        }
        if (x.desde() != null && x.hasta() != null) {
            for (AtencionLote a : e.atenciones()) {
                LocalDate dia = LocalDate.ofInstant(a.atencion().fecha(), e.zona());
                if (dia.isBefore(x.desde()) || dia.isAfter(x.hasta())) {
                    h.add(new HallazgoRips(a.atencion().id(), a.paciente().id(), Nivel.RECHAZO, "RVC014", Donde.LOTE,
                            "La atención del " + dia + " está fuera del período de la factura (" + x.desde() + " a "
                                    + x.hasta() + ")"));
                }
            }
        }
        BigDecimal enXml = x.pagosModeradores() == null ? BigDecimal.ZERO : x.pagosModeradores();
        if (enXml.compareTo(moderadores) != 0) {
            r.rechazo("RVC036", Donde.LOTE, "Los pagos moderadores de la factura ($" + enXml.toPlainString()
                    + ") no suman lo de las atenciones ($" + moderadores.toPlainString() + ")");
        }
    }

    // --- utilidades ---

    /** "900123456-7" → "900123456"; solo dígitos. {@code null} si no hay. */
    static String nitSinDigito(String nit) {
        if (nit == null || nit.isBlank()) {
            return null;
        }
        String base = nit.contains("-") ? nit.substring(0, nit.indexOf('-')) : nit;
        String digitos = base.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /** Impresión diagnóstica 01, confirmado nuevo 02, confirmado repetido 03 (tabla RIPSTipoDiagnosticoPrincipal). */
    private static String tipoDiagnostico(DiagnosticoVista d) {
        return switch (d.tipo()) {
            case IMPRESION_DIAGNOSTICA -> "01";
            case CONFIRMADO_NUEVO -> "02";
            case CONFIRMADO_REPETIDO -> "03";
        };
    }

    /** Pesos enteros como número entero; con centavos, con dos decimales. */
    static Number numero(BigDecimal v) {
        BigDecimal s = v.setScale(2, java.math.RoundingMode.HALF_UP);
        return s.remainder(BigDecimal.ONE).signum() == 0 ? (Number) s.longValueExact() : s;
    }

    private static String valor(String ajuste, String porDefecto) {
        return ajuste != null ? ajuste : porDefecto;
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static int entero(String s, int porDefecto) {
        try {
            return s == null || s.isBlank() ? porDefecto : Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    /** Acumula hallazgos de una atención (o del lote) sin repetir el mismo mensaje. */
    private record Revision(UUID atencionId, UUID pacienteId, List<HallazgoRips> hallazgos) {

        void rechazo(String regla, Donde donde, String mensaje) {
            agregar(Nivel.RECHAZO, regla, donde, mensaje);
        }

        void aviso(String regla, Donde donde, String mensaje) {
            agregar(Nivel.AVISO, regla, donde, mensaje);
        }

        private void agregar(Nivel nivel, String regla, Donde donde, String mensaje) {
            HallazgoRips nuevo = new HallazgoRips(atencionId, pacienteId, nivel, regla, donde, mensaje);
            if (!hallazgos.contains(nuevo)) {
                hallazgos.add(nuevo);
            }
        }
    }
}
