// Lee la primera hoja de un .xlsx ya descomprimido y devuelve las filas como arreglos de texto.
// Acepta etiquetas con o sin prefijo de espacio de nombres (<x:row> o <row>).
// Uso: node leer-xlsx.js <carpeta-descomprimida> [n]  -> imprime las primeras n filas en JSON
const fs = require("fs");

const P = "(?:[a-z]+:)?";
const re = (patron, flags = "g") => new RegExp(patron.replaceAll("§", P), flags);

function decode(s) {
  return s.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n))).replace(/&amp;/g, "&");
}

function leer(dir) {
  const ss = fs.readFileSync(`${dir}/xl/sharedStrings.xml`, "utf8");
  const compartidos = [];
  for (const si of ss.matchAll(re("<§si>([\\s\\S]*?)</§si>"))) {
    const textos = [...si[1].matchAll(re("<§t(?:\\s[^>]*)?>([\\s\\S]*?)</§t>"))].map((m) => m[1]);
    compartidos.push(decode(textos.join("")));
  }
  const hoja = fs.readFileSync(`${dir}/xl/worksheets/sheet1.xml`, "utf8");
  const filas = [];
  for (const row of hoja.matchAll(re("<§row[^>]*>([\\s\\S]*?)</§row>"))) {
    const fila = [];
    for (const c of row[1].matchAll(re("<§c r=\"([A-Z]+)\\d+\"([^>]*?)(?:/>|>([\\s\\S]*?)</§c>)"))) {
      const col = c[1].split("").reduce((n, ch) => n * 26 + ch.charCodeAt(0) - 64, 0) - 1;
      const attrs = c[2] || "";
      const cuerpo = c[3] || "";
      let valor = "";
      const v = cuerpo.match(re("<§v>([\\s\\S]*?)</§v>", ""));
      if (/t="s"/.test(attrs) && v) valor = compartidos[Number(v[1])];
      else if (/t="inlineStr"/.test(attrs)) valor = decode((cuerpo.match(re("<§t(?:\\s[^>]*)?>([\\s\\S]*?)</§t>", "")) || [, ""])[1]);
      else if (v) valor = decode(v[1]);
      fila[col] = valor;
    }
    filas.push(Array.from(fila, (x) => x ?? ""));
  }
  return filas;
}

module.exports = { leer };

if (require.main === module) {
  const filas = leer(process.argv[2]);
  const n = Number(process.argv[3] || 5);
  console.log("filas:", filas.length);
  for (const f of filas.slice(0, n)) console.log(JSON.stringify(f));
}
