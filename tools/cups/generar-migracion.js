// Genera la migración Flyway con el catálogo CUPS (código y descripción) a partir del texto de la
// "Tabla 1. Procedimientos en salud" en PDF.
//
// Uso:
//   1. Extraiga el texto del PDF con PDFBox (está en el repositorio local de Maven después de compilar el backend):
//        java -Dstdout.encoding=UTF-8 -cp "<pdfbox>;<fontbox>;<pdfbox-io>;<commons-logging>" tools/cups/TextoPdf.java tabla.pdf > cups.txt
//   2. node tools/cups/generar-migracion.js cups.txt backend/src/main/resources/db/migration/V7__cups.sql
//
// Formato del texto: cada procedimiento empieza con un código de 6 dígitos; la descripción puede
// seguir en la misma línea o partirse en varias; al final viene el grupo quirúrgico o los puntos de
// tarifa (número), que se descartan. Los encabezados de página se repiten y se ignoran.
const fs = require("fs");

const [entrada, salida] = process.argv.slice(2);
if (!entrada || !salida) {
  console.error("Uso: node generar-migracion.js <texto-extraido> <archivo-sql>");
  process.exit(1);
}

const ENCABEZADOS = new Set(["Código", "CUPS", "DESCRIPCION CUPS", "Grupo", "Qx", "Puntos de", "SMLVD"]);
const VALOR = /^\d+(?:[.,]\d+)?$/;

const lineas = fs.readFileSync(entrada, "utf8").split(/\r?\n/).map((l) => l.trim());
const registros = new Map();
let actual = null;

function cerrar() {
  if (!actual) return;
  let texto = actual.partes.join(" ").replace(/\s+/g, " ").trim();
  // El último token numérico es el grupo quirúrgico o los puntos: no es parte de la descripción
  const tokens = texto.split(" ");
  if (tokens.length > 1 && VALOR.test(tokens[tokens.length - 1])) tokens.pop();
  texto = tokens.join(" ");
  if (!texto) throw new Error(`Código sin descripción: ${actual.codigo}`);
  if (registros.has(actual.codigo) && registros.get(actual.codigo) !== texto) {
    throw new Error(`Código repetido con descripción distinta: ${actual.codigo}`);
  }
  registros.set(actual.codigo, texto);
  actual = null;
}

for (const linea of lineas) {
  if (!linea || linea.startsWith("PAGINAS=") || ENCABEZADOS.has(linea)) continue;
  const m = linea.match(/^(\d{6})(?:\s+(.*))?$/);
  if (m) {
    cerrar();
    actual = { codigo: m[1], partes: m[2] ? [m[2]] : [] };
  } else if (actual) {
    actual.partes.push(linea);
  }
}
cerrar();

const sql = (s) => `'${String(s).replace(/\\/g, "\\\\").replace(/'/g, "''")}'`;
const filas = [...registros.entries()].sort(([a], [b]) => a.localeCompare(b));
const lotes = [];
for (let i = 0; i < filas.length; i += 500) lotes.push(filas.slice(i, i + 500));

const encabezado = `-- Catálogo CUPS (Clasificación Única de Procedimientos en Salud): ${filas.length} procedimientos.
-- Fuente: "Tabla 1. Procedimientos en salud" (PDF). Generado con tools/cups/generar-migracion.js: no editar a mano.
-- Se usa para mostrar el nombre de consultas y procedimientos que llegan por RIPS.

CREATE TABLE cups (
    codigo      VARCHAR(10)  NOT NULL PRIMARY KEY,
    descripcion VARCHAR(400) NOT NULL,
    FULLTEXT INDEX ft_cups_descripcion (descripcion)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
`;
const cuerpo = lotes.map((l) =>
  `INSERT INTO cups (codigo, descripcion) VALUES\n${l.map(([c, d]) => `(${sql(c)}, ${sql(d)})`).join(",\n")}\n` +
  `ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);\n`).join("\n");

fs.writeFileSync(salida, `${encabezado}\n${cuerpo}`);
const largo = Math.max(...filas.map(([, d]) => d.length));
console.log(`${filas.length} procedimientos en ${lotes.length} lotes (descripción más larga: ${largo}) -> ${salida}`);
