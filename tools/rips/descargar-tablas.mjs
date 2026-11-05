// Descarga completas las tablas de referencia de SISPRO que usa el RIPS (Res. 948 de 2026), pasando las páginas de su
// consulta pública (10 filas por página; "Exportar a Excel" exige sesión en SISPRO).
// Uso: node tools/rips/descargar-tablas.mjs <carpeta de salida> <Tabla> [Tabla...]
import fs from "node:fs";
import path from "node:path";

const [salida, ...tablas] = process.argv.slice(2);
fs.mkdirSync(salida, { recursive: true });
const BASE = "https://web.sispro.gov.co/WebPublico/Consultas/ConsultarDetalleReferenciaBasica.aspx?Code=";
const decode = (s) => s.replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">");
const texto = (s) => decode(s.replace(/<[^>]*>/g, "").replace(/&nbsp;/g, "")).replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(+n)).trim();
const SIGUIENTE = "ctl00$cntContenido$dpggrvTablaReferencia$imgNext";

/** La consulta no siempre manda UTF-8: si salen caracteres de reemplazo, se lee como Windows-1252. */
async function leerHtml(r) {
  const buf = Buffer.from(await r.arrayBuffer());
  const utf8 = new TextDecoder("utf-8").decode(buf);
  return utf8.includes("�") ? new TextDecoder("windows-1252").decode(buf) : utf8;
}

function campos(html) {
  const f = new URLSearchParams();
  for (const m of html.matchAll(/<input[^>]*>/g)) {
    const tag = m[0];
    const nombre = /name="([^"]+)"/.exec(tag)?.[1];
    const tipo = /type="([^"]+)"/.exec(tag)?.[1] ?? "text";
    if (!nombre || ["submit", "image", "checkbox", "button"].includes(tipo)) continue;
    f.set(decode(nombre), decode(/value="([^"]*)"/.exec(tag)?.[1] ?? ""));
  }
  for (const m of html.matchAll(/<select[^>]*name="([^"]+)"[\s\S]*?<\/select>/g)) {
    f.set(decode(m[1]), /<option[^>]*selected[^>]*value="([^"]*)"/.exec(m[0])?.[1] ?? "");
  }
  return f;
}

function filas(html, t) {
  return [...html.matchAll(/<tr[^>]*>([\s\S]*?)<\/tr>/g)]
    .map((m) => [...m[1].matchAll(/<td[^>]*>([\s\S]*?)<\/td>/g)].map((c) => texto(c[1])))
    .filter((f) => f.length > 3 && f[0].toLowerCase() === t.toLowerCase()); // a veces responde con otra mayúscula
}

for (const t of tablas) {
  let r = await fetch(BASE + t);
  let cookies = (r.headers.getSetCookie?.() ?? []).map((c) => c.split(";")[0]).join("; ");
  let html = await leerHtml(r);
  const encabezados = [...html.matchAll(/<th[^>]*>([\s\S]*?)<\/th>/g)].map((m) => texto(m[1]));
  const todas = new Map();
  for (let pagina = 1; pagina <= 400; pagina++) {
    const nuevas = filas(html, t);
    let agregadas = 0;
    for (const f of nuevas) if (!todas.has(f[1])) { todas.set(f[1], f); agregadas++; }
    if (!agregadas || !html.includes(SIGUIENTE.replaceAll("$", "$"))) break;
    const f = campos(html);
    f.set("__EVENTTARGET", SIGUIENTE);
    f.set("__EVENTARGUMENT", "");
    r = await fetch(BASE + t, { method: "POST", body: f, headers: { Cookie: cookies, Referer: BASE + t, "Content-Type": "application/x-www-form-urlencoded" } });
    const nuevasCookies = (r.headers.getSetCookie?.() ?? []).map((c) => c.split(";")[0]);
    if (nuevasCookies.length) cookies = nuevasCookies.join("; ");
    html = await leerHtml(r);
    if (r.status !== 200) { console.log(`${t}: página ${pagina + 1} respondió ${r.status}`); break; }
  }
  const lista = [...todas.values()];
  fs.writeFileSync(path.join(salida, `${t}.json`), JSON.stringify({ tabla: t, encabezados, filas: lista }, null, 1));
  console.log(`${t}: ${lista.length} filas`);
}
