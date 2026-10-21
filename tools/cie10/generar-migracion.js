// Genera la migración Flyway con la tabla CIE-10 de MinSalud (SISPRO, "Tablas de referencia").
//
// Uso:
//   1. Descargue TablaReferencia_CIE10__1.xlsx y descomprímalo (un .xlsx es un .zip):
//        unzip TablaReferencia_CIE10__1.xlsx -d /tmp/cie10
//   2. node tools/cie10/generar-migracion.js /tmp/cie10 backend/src/main/resources/db/migration/V5__cie10_minsalud.sql
//
// Cuando MinSalud publique una versión nueva, genere otra migración (V<n>__cie10_actualizacion.sql):
// se usa INSERT ... ON DUPLICATE KEY UPDATE, así que actualiza nombres y agrega códigos nuevos.
const fs = require("fs");
const { leer } = require("./leer-xlsx");

const [dir, salida] = process.argv.slice(2);
if (!dir || !salida) {
  console.error("Uso: node generar-migracion.js <xlsx-descomprimido> <archivo-sql>");
  process.exit(1);
}

const filas = leer(dir);
const cab = filas[0];
const col = (nombre) => {
  const i = cab.indexOf(nombre);
  if (i < 0) throw new Error(`No se encontró la columna ${nombre}`);
  return i;
};
const C = {
  codigo: col("Codigo"), nombre: col("Nombre"), habilitado: col("Habilitado"),
  sexo: col("Extra_X:Sexo"), edadMin: col("Extra_II:EdadMinima"), edadMax: col("Extra_III:EdadMaxima"),
  // En el archivo de MinSalud el nombre del capítulo viene en Extra_V; "Extra_VI:Capitulo" trae el código del grupo (A00)
  capitulo: col("Extra_V"),
};

const sql = (s) => `'${String(s).replace(/\\/g, "\\\\").replace(/'/g, "''")}'`;
const entero = (s) => (/^\d+$/.test(String(s).trim()) ? String(Number(s)) : "NULL");

const registros = [];
const vistos = new Set();
for (const f of filas.slice(1)) {
  const codigo = String(f[C.codigo] ?? "").trim().toUpperCase();
  if (!codigo) continue;
  if (!/^[A-Z][0-9]{2}[0-9X]?$/.test(codigo)) throw new Error(`Código con formato inesperado: ${codigo}`);
  if (vistos.has(codigo)) throw new Error(`Código repetido: ${codigo}`);
  vistos.add(codigo);
  const sexo = ["F", "M"].includes(f[C.sexo]) ? f[C.sexo] : "A";
  registros.push(`(${sql(codigo)}, ${sql(String(f[C.nombre]).trim())}, ${f[C.habilitado] === "SI" ? 1 : 0}, '${sexo}', ` +
    `${entero(f[C.edadMin])}, ${entero(f[C.edadMax])}, ${sql(String(f[C.capitulo] ?? "").trim())})`);
}

const lotes = [];
for (let i = 0; i < registros.length; i += 500) lotes.push(registros.slice(i, i + 500));

const encabezado = `-- Tabla CIE-10 completa de MinSalud (SISPRO, tablas de referencia), ${registros.length} códigos.
-- Archivo generado con tools/cie10/generar-migracion.js: no editar a mano.
-- Sexo: A = ambos, F = femenino, M = masculino. Edades en años (999 = sin límite).

ALTER TABLE cie10
    ADD COLUMN habilitado BIT          NOT NULL DEFAULT 1,
    ADD COLUMN sexo       VARCHAR(1)   NOT NULL DEFAULT 'A',
    ADD COLUMN edad_min   INT,
    ADD COLUMN edad_max   INT,
    ADD COLUMN capitulo   VARCHAR(200);
`;

const cuerpo = lotes.map((l) =>
  `INSERT INTO cie10 (codigo, descripcion, habilitado, sexo, edad_min, edad_max, capitulo) VALUES\n${l.join(",\n")}\n` +
  `ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion), habilitado = VALUES(habilitado), sexo = VALUES(sexo),\n` +
  `    edad_min = VALUES(edad_min), edad_max = VALUES(edad_max), capitulo = VALUES(capitulo);\n`).join("\n");

fs.writeFileSync(salida, `${encabezado}\n${cuerpo}`);
console.log(`${registros.length} códigos en ${lotes.length} lotes -> ${salida}`);
