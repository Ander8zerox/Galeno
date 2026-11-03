// Genera los archivos del consultorio demo: un listado de pacientes ficticios (.csv) y RIPS (JSON) con su historial.
// Uso: node gen-demo.mjs <carpeta de salida>
import fs from "node:fs";
import path from "node:path";

const salida = process.argv[2];
fs.mkdirSync(salida, { recursive: true });

// Documentos con prefijo 99: no corresponden a números de cédula expedidos hoy (todos los datos son inventados)
const pacientes = [
  { tipo: "CC", doc: "990000101", nombres: "María Fernanda", apellidos: "Rojas Medina", nac: "1988-03-14", sexo: "F", tel: "3000000101" },
  { tipo: "CC", doc: "990000102", nombres: "Carlos Andrés", apellidos: "Gómez Patiño", nac: "1975-11-02", sexo: "M", tel: "3000000102" },
  { tipo: "CC", doc: "990000103", nombres: "Luz Marina", apellidos: "Castaño Ríos", nac: "1962-07-21", sexo: "F", tel: "3000000103" },
  { tipo: "CC", doc: "990000104", nombres: "Jorge Iván", apellidos: "Martínez Suárez", nac: "1958-01-30", sexo: "M", tel: "3000000104" },
  { tipo: "CC", doc: "990000105", nombres: "Diana Carolina", apellidos: "López Herrera", nac: "1993-09-08", sexo: "F", tel: "3000000105" },
  { tipo: "CC", doc: "990000106", nombres: "Andrés Felipe", apellidos: "Ramírez Ortiz", nac: "2001-05-17", sexo: "M", tel: "3000000106" },
  { tipo: "CC", doc: "990000107", nombres: "Paula Andrea", apellidos: "Vargas Quintero", nac: "1985-12-03", sexo: "F", tel: "3000000107" },
  { tipo: "CC", doc: "990000108", nombres: "Hernán Darío", apellidos: "Cárdenas Mejía", nac: "1969-04-25", sexo: "M", tel: "3000000108" },
  { tipo: "CC", doc: "990000109", nombres: "Sandra Milena", apellidos: "Torres Bermúdez", nac: "1979-08-11", sexo: "F", tel: "3000000109" },
  { tipo: "CC", doc: "990000110", nombres: "Ricardo", apellidos: "Salazar Pinto", nac: "1990-02-19", sexo: "M", tel: "3000000110" },
  { tipo: "TI", doc: "990000111", nombres: "Valentina", apellidos: "Rojas Rojas", nac: "2012-06-09", sexo: "F", tel: "3000000101" },
  { tipo: "RC", doc: "990000112", nombres: "Samuel", apellidos: "Gómez Castaño", nac: "2019-10-28", sexo: "M", tel: "3000000102" },
];

// Consultas y procedimientos pasados (CUPS y CIE-10 que existen en las tablas de Galenum)
const C = {
  mgPrimera: "890201", mgControl: "890301", odPrimera: "890203", odControl: "890303",
  resina: "232102", detartraje: "997301", placa: "997310", exodoncia: "230101", educacion: "990203",
};
const historial = {
  "990000101": [["consultas", C.odPrimera, "2026-05-12 09:00", "K021"], ["procedimientos", C.resina, "2026-05-12 09:30", "K021"],
                ["consultas", C.odControl, "2026-08-20 10:00", "K050"], ["procedimientos", C.detartraje, "2026-08-20 10:20", "K050"]],
  "990000102": [["consultas", C.mgPrimera, "2026-04-03 08:00", "I10X"], ["consultas", C.mgControl, "2026-07-03 08:00", "I10X"],
                ["consultas", C.mgControl, "2026-10-02 08:30", "I10X"]],
  "990000103": [["consultas", C.mgPrimera, "2026-03-18 11:00", "E119"], ["consultas", C.mgControl, "2026-06-18 11:00", "E119"],
                ["consultas", C.mgControl, "2026-09-17 11:00", "E669"]],
  "990000104": [["consultas", C.odPrimera, "2026-06-05 15:00", "K051"], ["procedimientos", C.exodoncia, "2026-06-12 15:00", "K040"]],
  "990000105": [["consultas", C.mgPrimera, "2026-09-02 14:00", "J029"], ["consultas", C.mgControl, "2026-10-15 14:30", "F411"]],
  "990000106": [["consultas", C.odPrimera, "2026-07-22 16:00", "K036"], ["procedimientos", C.placa, "2026-07-22 16:20", "K036"]],
  "990000107": [["consultas", C.mgPrimera, "2026-08-08 09:30", "N390"]],
  "990000108": [["consultas", C.mgPrimera, "2026-05-27 10:30", "M545"], ["consultas", C.mgControl, "2026-06-24 10:30", "M545"]],
  "990000109": [["consultas", C.mgPrimera, "2026-09-25 08:00", "K297"]],
  "990000110": [["consultas", C.mgPrimera, "2026-10-09 17:00", "Z000"]],
  "990000111": [["consultas", C.odPrimera, "2026-07-01 15:00", "K021"], ["procedimientos", C.educacion, "2026-07-01 15:20", "K021"]],
  "990000112": [["consultas", C.mgPrimera, "2026-10-20 09:00", "J00X"]],
};

// --- Listado de pacientes (lo reconoce "Importar > Listado de pacientes" por los encabezados) ---
const csv = ["Tipo documento,Número documento,Nombres,Apellidos,Fecha nacimiento,Sexo,Teléfono"];
for (const p of pacientes) csv.push([p.tipo, p.doc, p.nombres, p.apellidos, p.nac, p.sexo, p.tel].join(","));
fs.writeFileSync(path.join(salida, "pacientes-demo.csv"), "﻿" + csv.join("\r\n") + "\r\n");

// --- RIPS (Res. 2275 de 2023): una factura ficticia con el historial de todos ---
const usuarios = pacientes.map((p, i) => {
  const servicios = { consultas: [], procedimientos: [] };
  (historial[p.doc] ?? []).forEach(([tipo, cups, fecha, dx], j) => {
    if (tipo === "consultas") {
      servicios.consultas.push({ codConsulta: cups, fechaInicioAtencion: fecha, codDiagnosticoPrincipal: dx,
        codDiagnosticoRelacionado1: null, tipoDiagnosticoPrincipal: "02", consecutivo: j + 1 });
    } else {
      servicios.procedimientos.push({ codProcedimiento: cups, fechaInicioAtencion: fecha, codDiagnosticoPrincipal: dx,
        codDiagnosticoRelacionado: null, consecutivo: j + 1 });
    }
  });
  return { tipoDocumentoIdentificacion: p.tipo, numDocumentoIdentificacion: p.doc, tipoUsuario: "04",
    fechaNacimiento: p.nac, codSexo: p.sexo, codMunicipioResidencia: "11001", consecutivo: i + 1, servicios };
});
fs.writeFileSync(path.join(salida, "rips-demo.json"),
  JSON.stringify({ numFactura: "DEMO-0001", usuarios }, null, 2) + "\n");

const total = Object.values(historial).reduce((n, h) => n + h.length, 0);
console.log(`${pacientes.length} pacientes, ${total} atenciones en el RIPS`);
