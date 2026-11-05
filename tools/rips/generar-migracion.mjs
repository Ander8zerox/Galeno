// Genera la migración Flyway con las tablas de referencia del RIPS a partir de los JSON de descargar-tablas.mjs.
//
// Uso:
//   1. node tools/rips/descargar-tablas.mjs <carpeta> <Tabla> [Tabla...]   (las de la migración, en MIGRACIONES)
//   2. node tools/rips/generar-migracion.mjs <carpeta> <V22|V24|V26> <archivo-sql>
//
// La V22 crea la tabla; las siguientes solo agregan filas. Para actualizar una tabla ya cargada, generar con sus
// tablas y publicar el resultado como una migración nueva (borrando antes las filas de esas tablas).
//
// Cada fila guarda el código, el nombre, si está habilitada y las columnas "Extra" que tengan dato (en JSON): ahí van
// el departamento del municipio, el código ISO del país, o si una finalidad aplica a consultas o procedimientos.
import fs from "node:fs";
import path from "node:path";

/** Las que usa Galenum, con el nombre corto con que las guarda, agrupadas por la migración que las cargó. */
const V22 = {
  RIPSTipoUsuarioVersion2: "tipo_usuario",
  Pais: "pais",
  Departamento: "departamento",
  Municipio: "municipio",
  ZonaVersion2: "zona",
  RIPSFinalidadConsultaVersion2: "finalidad",
  RIPSCausaExternaVersion2: "causa_externa",
  ModalidadAtencion: "modalidad_atencion",
  GrupoServicios: "grupo_servicios",
  Servicios: "servicio",
  ViaIngresoUsuario: "via_ingreso",
  conceptoRecaudo: "concepto_recaudo",
  modalidadPago: "modalidad_pago",
  RIPSTipoDiagnosticoPrincipalVersion2: "tipo_diagnostico",
};
const V24 = {
  CodigoEAPByNit: "eapb",
  CoberturaPlanBeneficios: "cobertura",
  facturaSinContrato: "factura_sin_contrato",
};
const V26 = {
  CUPSRips: "cups_rips",
};
export const MIGRACIONES = { V22, V24, V26 };

/**
 * En CodigoEAPByNit las celdas no coinciden con los encabezados (el NIT llega partido en número y dígito), así que se
 * toman por posición. Se guardan régimen, NIT con dígito, población y tipo de entidad; no los correos.
 */
const EXTRA = {
  eapb: (f) => ({ regimen: f[8], nit: f[12], poblacion: f[15], tipo: f[16].replace(/^(\d+)\.\s*/, "$1. ") }),
  // De CUPSRips, lo que revisa el lote: uso (AC consulta, AP procedimiento, AT otros servicios), sexo (Z ambos), ámbito,
  // cuántas veces por paciente y día, y cobertura. Las inhabilitadas traen "N" en todo: se guardan sin extra.
  cups_rips: (f) => (f[4] === "SI" ? { uso: f[8], sexo: f[13], ambito: f[14], maximo: f[11], cobertura: f[16] } : {}),
};

/** La fuente trae algunos caracteres perdidos (una tilde, un guion largo como "?"): se corrigen aquí. */
const CORRECCIONES = { "tr nsito": "tránsito", " ? ": " – " };

const [carpeta, version, salida] = process.argv.slice(2);
const TABLAS = MIGRACIONES[version];
if (!carpeta || !TABLAS || !salida) {
  console.error(`Uso: node generar-migracion.mjs <carpeta-json> <${Object.keys(MIGRACIONES).join("|")}> <archivo-sql>`);
  process.exit(1);
}
const sql = (s) => (s === null || s === undefined ? "NULL" : `'${String(s).replace(/\\/g, "\\\\").replace(/'/g, "''")}'`);
const limpiar = (s) => Object.entries(CORRECCIONES).reduce((t, [mal, bien]) => t.replaceAll(mal, bien), s).replace(/\s+/g, " ").trim();

const valores = [];
const resumen = [];
for (const [fuente, corto] of Object.entries(TABLAS)) {
  const { encabezados, filas } = JSON.parse(fs.readFileSync(path.join(carpeta, `${fuente}.json`), "utf8"));
  const iExtra = encabezados.map((e, i) => [e, i]).filter(([e]) => e.startsWith("Extra_"));
  const iHab = encabezados.indexOf("Habilitado");
  for (const f of filas) {
    // "Extra_I:Departamento" → clave "Departamento"; sin nombre, "Extra_I"
    const extra = EXTRA[corto] ? Object.fromEntries(Object.entries(EXTRA[corto](f)).filter(([, v]) => v))
      : Object.fromEntries(iExtra.filter(([, i]) => f[i]).map(([e, i]) => [e.includes(":") ? e.split(":")[1] : e, f[i]]));
    valores.push(`(${sql(corto)}, ${sql(f[1])}, ${sql(limpiar(f[2]))}, ${f[iHab] === "SI" ? "TRUE" : "FALSE"}, ${Object.keys(extra).length ? sql(JSON.stringify(extra)) : "NULL"})`);
  }
  resumen.push(`${corto}: ${filas.length}`);
}

const origen = `-- Tablas de referencia del RIPS (Res. 948 de 2026), tomadas de la consulta pública de SISPRO
-- (web.sispro.gov.co, "Consulta Tablas de Referencia") el ${new Date().toISOString().slice(0, 10)}.
-- Generado con tools/rips/generar-migracion.mjs ${version}: no editar a mano. ${resumen.join(", ")}.
`;
const crear = `CREATE TABLE rips_referencias (
    tabla      VARCHAR(30)  NOT NULL,
    codigo     VARCHAR(20)  NOT NULL,
    nombre     VARCHAR(300) NOT NULL,
    habilitado BOOLEAN      NOT NULL,
    extra      JSON,
    PRIMARY KEY (tabla, codigo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

`;
const insertar = `INSERT INTO rips_referencias (tabla, codigo, nombre, habilitado, extra) VALUES
`;
fs.writeFileSync(salida, origen + (version === "V22" ? crear : "") + insertar + valores.join(",\n") + ";\n");
console.log(`${valores.length} filas → ${salida}\n${resumen.join("\n")}`);
