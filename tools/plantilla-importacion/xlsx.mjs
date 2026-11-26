// Escritor mínimo de .xlsx (ZIP + SpreadsheetML) sin dependencias. Celdas de texto en línea.
import zlib from "node:zlib";

const esc = (s) => String(s).replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, "")
  .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const col = (i) => { let s = ""; i++; while (i) { const m = (i - 1) % 26; s = String.fromCharCode(65 + m) + s; i = (i - m - 1) / 26; } return s; };

/** hojas: [{ nombre, encabezados: [{titulo, obligatorio, ancho}], filas: [[...]], listas: [{col, ref}], soloTexto }] */
export function xlsx(hojas) {
  const archivos = {};
  archivos["[Content_Types].xml"] = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${hojas.map((_, i) => `<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join("")}</Types>`;
  archivos["_rels/.rels"] = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`;
  archivos["xl/workbook.xml"] = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${hojas.map((h, i) => `<sheet name="${esc(h.nombre)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>`).join("")}</sheets></workbook>`;
  archivos["xl/_rels/workbook.xml.rels"] = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${hojas.map((_, i) => `<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>`).join("")}<Relationship Id="rId${hojas.length + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>`;
  // estilos: 0 normal, 1 encabezado obligatorio, 2 encabezado opcional, 3 texto con ajuste, 4 título
  archivos["xl/styles.xml"] = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font><font><b/><sz val="14"/><name val="Calibri"/></font></fonts><fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0F766E"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FF64748B"/></patternFill></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="5"><xf/><xf fontId="1" fillId="2" applyFont="1" applyFill="1" applyAlignment="1"><alignment wrapText="1" vertical="center"/></xf><xf fontId="1" fillId="3" applyFont="1" applyFill="1" applyAlignment="1"><alignment wrapText="1" vertical="center"/></xf><xf applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf><xf fontId="2" applyFont="1"/></cellXfs></styleSheet>`;
  hojas.forEach((h, i) => {
    const filas = [];
    let r = 1;
    if (h.encabezados) {
      filas.push(`<row r="1" ht="32" customHeight="1">${h.encabezados.map((e, c) => `<c r="${col(c)}1" t="inlineStr" s="${e.obligatorio ? 1 : 2}"><is><t>${esc(e.titulo + (e.obligatorio ? " *" : ""))}</t></is></c>`).join("")}</row>`);
      r = 2;
    }
    for (const f of h.filas) {
      const celdas = f.map((v, c) => {
        if (v == null || v === "") return "";
        const s = h.titulo && r === 1 ? 4 : (h.ajustar ? 3 : 0);
        if (typeof v === "number") return `<c r="${col(c)}${r}" s="${s}"><v>${v}</v></c>`;
        return `<c r="${col(c)}${r}" t="inlineStr" s="${s}"><is><t xml:space="preserve">${esc(v)}</t></is></c>`;
      }).join("");
      filas.push(`<row r="${r}">${celdas}</row>`);
      r++;
    }
    const anchos = (h.encabezados ?? h.anchos?.map((a) => ({ ancho: a })) ?? []).map((e, c) => `<col min="${c + 1}" max="${c + 1}" width="${e.ancho ?? 18}" customWidth="1"/>`).join("");
    const congelar = h.encabezados ? `<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>` : "";
    const listas = (h.listas ?? []).length ? `<dataValidations count="${h.listas.length}">${h.listas.map((l) => `<dataValidation type="list" allowBlank="1" showErrorMessage="1" sqref="${l.col}2:${l.col}60000"><formula1>${esc(l.ref)}</formula1></dataValidation>`).join("")}</dataValidations>` : "";
    archivos[`xl/worksheets/sheet${i + 1}.xml`] = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">${congelar}${anchos ? `<cols>${anchos}</cols>` : ""}<sheetData>${filas.join("")}</sheetData>${listas}${h.proteger ? `<sheetProtection sheet="1" objects="1" scenarios="1"/>` : ""}</worksheet>`;
  });
  return zip(archivos);
}

function zip(archivos) {
  const partes = [], central = [];
  let offset = 0;
  for (const [nombre, contenido] of Object.entries(archivos)) {
    const datos = Buffer.from(contenido, "utf8");
    const comprimido = zlib.deflateRawSync(datos);
    const crc = zlib.crc32(datos);
    const n = Buffer.from(nombre, "utf8");
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(20, 4); local.writeUInt16LE(0x0800, 6); local.writeUInt16LE(8, 8);
    local.writeUInt32LE(crc, 14); local.writeUInt32LE(comprimido.length, 18); local.writeUInt32LE(datos.length, 22); local.writeUInt16LE(n.length, 26);
    partes.push(local, n, comprimido);
    const c = Buffer.alloc(46);
    c.writeUInt32LE(0x02014b50, 0); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6); c.writeUInt16LE(0x0800, 8); c.writeUInt16LE(8, 10);
    c.writeUInt32LE(crc, 16); c.writeUInt32LE(comprimido.length, 20); c.writeUInt32LE(datos.length, 24); c.writeUInt16LE(n.length, 28); c.writeUInt32LE(offset, 42);
    central.push(c, n);
    offset += 30 + n.length + comprimido.length;
  }
  const cd = Buffer.concat(central);
  const fin = Buffer.alloc(22);
  fin.writeUInt32LE(0x06054b50, 0); fin.writeUInt16LE(Object.keys(archivos).length, 8); fin.writeUInt16LE(Object.keys(archivos).length, 10);
  fin.writeUInt32LE(cd.length, 12); fin.writeUInt32LE(offset, 16);
  return Buffer.concat([...partes, cd, fin]);
}
