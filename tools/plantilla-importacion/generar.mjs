// Genera la plantilla de importación de Galenum (vacía) que se descarga desde «Importar historias».
// Los encabezados deben coincidir con PlantillaGalenum.ColP y ColA (backend).
// Uso: node tools/plantilla-importacion/generar.mjs  →  frontend/public/plantillas/plantilla-importacion-galenum.xlsx
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { xlsx } from "./xlsx.mjs";

const E = (titulo, obligatorio, ancho) => ({ titulo, obligatorio, ancho });

export const PACIENTES = [E("Tipo de documento", 1, 12), E("Número de documento", 1, 16), E("Primer nombre", 1), E("Segundo nombre"),
  E("Primer apellido", 1), E("Segundo apellido"), E("Fecha de nacimiento", 1, 14), E("Sexo", 1, 8), E("Teléfono", 0, 16),
  E("Correo", 0, 28), E("Alergias", 0, 24), E("Antecedentes", 0, 24), E("Responsable: nombre", 0, 26),
  E("Responsable: parentesco"), E("Responsable: teléfono", 0, 16)];

export const ATENCIONES = [E("Tipo de documento", 1, 12), E("Número de documento", 1, 16), E("Fecha de la atención", 1, 14),
  E("Hora", 0, 8), E("ID en el sistema anterior", 0, 24), E("Profesional que atendió", 1, 28), E("Registro médico", 0, 14),
  E("Motivo de consulta", 0, 40), E("Enfermedad actual", 0, 30), E("Revisión por sistemas", 0, 30), E("Examen físico", 0, 30),
  E("TA sistólica", 0, 10), E("TA diastólica", 0, 10), E("FC", 0, 8), E("FR", 0, 8), E("Temperatura", 0, 10), E("Saturación", 0, 10),
  E("Peso (kg)", 0, 10), E("Talla (cm)", 0, 10), E("Diagnóstico principal (CIE-10)", 0, 14), E("Relacionado 1 (CIE-10)", 0, 12),
  E("Relacionado 2 (CIE-10)", 0, 12), E("Relacionado 3 (CIE-10)", 0, 12), E("Tipo de diagnóstico", 0, 18),
  E("Impresión diagnóstica (texto)", 0, 36), E("Análisis", 0, 30), E("Plan", 0, 40), E("Medicamentos formulados", 0, 30)];

const TIPOS = ["CC", "TI", "RC", "CE", "PA", "PT", "PE", "CN", "MS", "AS", "SC", "CD", "DE", "NV", "SI"];
const SEXOS = ["M", "F", "I"];
const TIPOS_DX = ["Impresión diagnóstica", "Confirmado nuevo", "Confirmado repetido"];

export const INSTRUCCIONES = [
  ["Plantilla de importación de Galenum"],
  [""],
  ["Sirve para traer a Galenum los pacientes y las historias de otro sistema (o de un Excel propio)."],
  ["Se sube en Importar historias → Plantilla de Galenum. Antes de importar se revisa cada paciente; nada entra a la historia hasta confirmar."],
  [""],
  ["Cómo llenarla"],
  ["• Las columnas con * son obligatorias. No cambie los nombres de las hojas ni de las columnas (sí puede cambiar el orden)."],
  ["• Fechas en formato dd/mm/aaaa (por ejemplo 05/03/1990). Hora en hh:mm, de 24 horas (14:30)."],
  ["• Tipo de documento y sexo: elija de la lista (CC, TI, RC, CE, PA…; M, F o I)."],
  ["• Diagnósticos: códigos CIE-10 (J45.9 o J459). Si el sistema anterior no usaba códigos, escriba el diagnóstico en «Impresión diagnóstica (texto)»."],
  [""],
  ["Hoja Pacientes: una fila por paciente"],
  ["• Si el paciente ya existe en Galenum (mismo tipo y número de documento), solo se llenan los datos que estén vacíos; nunca se reemplazan."],
  ["• Para crear un paciente nuevo se necesitan nombre, apellido, fecha de nacimiento y sexo."],
  ["• Los pacientes importados quedan sin la autorización de tratamiento de datos (Ley 1581): regístrela al editar el paciente en su próxima visita."],
  [""],
  ["Hoja Atenciones: una fila por consulta"],
  ["• El paciente se reconoce por tipo y número de documento: debe estar en la hoja Pacientes o existir ya en Galenum."],
  ["• Las atenciones quedan firmadas, marcadas como importadas y con el nombre del profesional del sistema anterior. No se pueden editar; sí se les pueden agregar notas aclaratorias."],
  ["• «ID en el sistema anterior» evita duplicados si el archivo se sube dos veces. Si no lo tiene, Galenum reconoce la misma fila por su contenido."],
  ["• Los signos vitales fuera de rango (por ejemplo una FC de 12.028) no se importan y se avisan en la revisión."],
  ["• «Medicamentos formulados» queda como texto dentro del plan, no como fórmula."],
  [""],
  ["Máximo 50.000 filas por hoja."],
];

export function libro({ pacientes = [], atenciones = [], instrucciones = INSTRUCCIONES, extra = [] } = {}) {
  const max = Math.max(TIPOS.length, SEXOS.length, TIPOS_DX.length);
  const listas = [["Tipo de documento", "Sexo", "Tipo de diagnóstico"]];
  for (let i = 0; i < max; i++) listas.push([TIPOS[i] ?? "", SEXOS[i] ?? "", TIPOS_DX[i] ?? ""]);
  const finTipos = `$A$${TIPOS.length + 1}`;
  return xlsx([
    { nombre: "Instrucciones", filas: instrucciones, titulo: true, anchos: [150] },
    { nombre: "Pacientes", encabezados: PACIENTES, filas: pacientes,
      listas: [{ col: "A", ref: `Listas!$A$2:${finTipos}` }, { col: "H", ref: `Listas!$B$2:$B$${SEXOS.length + 1}` }] },
    { nombre: "Atenciones", encabezados: ATENCIONES, filas: atenciones,
      listas: [{ col: "A", ref: `Listas!$A$2:${finTipos}` }, { col: "X", ref: `Listas!$C$2:$C$${TIPOS_DX.length + 1}` }] },
    { nombre: "Listas", filas: listas, anchos: [20, 10, 24], proteger: true },
    ...extra,
  ]);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const destino = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../frontend/public/plantillas/plantilla-importacion-galenum.xlsx");
  fs.mkdirSync(path.dirname(destino), { recursive: true });
  fs.writeFileSync(destino, libro());
  console.log("Plantilla generada:", destino);
}
