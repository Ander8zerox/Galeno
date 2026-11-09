// Definición de un formato clínico (ver docs/diseno-formatos.md y formatos/DefinicionFormato.java). El servidor valida
// lo mismo al guardar y al firmar; aquí solo se usa para dibujar y para mostrar los errores a tiempo.

import { lineasHallazgos, type ValorOdontograma } from "./odontograma";

export type Tipo = "texto" | "texto_largo" | "numero" | "fecha" | "seleccion" | "multiple" | "si_no" | "tabla" | "escala" | "odontograma" | "firma";

export interface Campo {
  clave: string;
  tipo: Tipo;
  etiqueta: string;
  ayuda?: string;
  obligatorio?: boolean;
  /** Parte del valor de la última atención firmada del paciente con este formato. */
  arrastrar?: boolean;
  opciones?: string[];
  unidad?: string;
  min?: number;
  max?: number;
  visibleSi?: { campo: string; igual: unknown };
  columnas?: { clave: string; etiqueta: string; tipo: Tipo; opciones?: string[] }[];
  filas?: { clave: string; etiqueta: string }[];
  items?: { clave: string; etiqueta: string; opciones: { etiqueta: string; puntaje: number }[] }[];
  rangos?: { min: number; max: number; interpretacion: string }[];
  /** Fecha calculada (la pone el servidor): `desde` más `dias` menos uno. */
  calculo?: { desde: string; dias: string };
}

export interface Seccion {
  clave: string;
  titulo: string;
  campos: Campo[];
}

export interface Definicion {
  secciones: Seccion[];
  /** El formato se entrega también como PDF propio (incapacidad, remisión, consentimiento). */
  documento?: { titulo: string; diagnosticos?: boolean };
}

/** El valor de un campo calculado, igual que lo calcula el servidor al guardar. */
export function calcular(c: Campo, valores: Record<string, unknown> | undefined): string | null {
  if (!c.calculo) return null;
  const desde = valores?.[c.calculo.desde];
  const dias = valores?.[c.calculo.dias];
  if (typeof desde !== "string" || typeof dias !== "number" || dias < 1 || !Number.isInteger(dias)) return null;
  const f = new Date(`${desde}T12:00:00Z`);
  f.setUTCDate(f.getUTCDate() + dias - 1);
  return f.toISOString().slice(0, 10);
}

/** {"seccion": {"campo": valor}} */
export type Respuestas = Record<string, Record<string, unknown>>;
export type Fila = Record<string, unknown> & { fila?: string };

/** Mismo criterio que el servidor: el otro campo tiene ese valor, o lo incluye si es de selección múltiple. */
export function visible(c: Campo, valores: Record<string, unknown> | undefined): boolean {
  if (!c.visibleSi) return true;
  const otro = valores?.[c.visibleSi.campo];
  if (otro === undefined || otro === null) return false;
  return Array.isArray(otro) ? otro.includes(c.visibleSi.igual) : otro === c.visibleSi.igual;
}

export function vacio(v: unknown): boolean {
  if (v === undefined || v === null) return true;
  if (typeof v === "string") return v.trim() === "";
  if (Array.isArray(v)) return v.length === 0 || v.every((f) => typeof f === "object" && f !== null && Object.entries(f).every(([k, x]) => k === "fila" || vacio(x)));
  if (typeof v === "object") return Object.values(v).every(vacio);
  return false;
}

export function total(c: Campo, v: unknown): { puntaje: number; maximo: number; interpretacion?: string } | null {
  const items = c.items ?? [];
  const r = (v ?? {}) as Record<string, number>;
  if (!items.length || items.some((i) => typeof r[i.clave] !== "number")) return null;
  const puntaje = items.reduce((s, i) => s + r[i.clave], 0);
  const maximo = items.reduce((s, i) => s + Math.max(...i.opciones.map((o) => o.puntaje)), 0);
  return { puntaje, maximo, interpretacion: c.rangos?.find((x) => puntaje >= x.min && puntaje <= x.max)?.interpretacion };
}

/** Error a mostrar junto al campo mientras se escribe (el servidor responde lo mismo al guardar). */
export function errorCampo(c: Campo, v: unknown): string | null {
  if (c.tipo === "numero" && typeof v === "number") {
    if ((c.min !== undefined && v < c.min) || (c.max !== undefined && v > c.max)) return `Debe estar entre ${c.min ?? "—"} y ${c.max ?? "—"}`;
  }
  return null;
}

/** Valor de un campo sencillo como texto legible. */
export function textoValor(tipo: Tipo, v: unknown, unidad?: string): string {
  if (tipo === "si_no") return v ? "Sí" : "No";
  if (tipo === "fecha" && typeof v === "string") return v.split("-").reverse().join("/");
  if (Array.isArray(v)) return v.join(", ");
  return `${v}${unidad ? ` ${unidad}` : ""}`;
}

/** Lo diligenciado como texto ordenado, para el apoyo diagnóstico con IA: nunca la definición completa. */
export function textoFormatos(formatos: { nombre?: string; definicion: Definicion; respuestas: Respuestas }[]): string {
  const lineas: string[] = [];
  for (const f of formatos) {
    for (const s of f.definicion.secciones) {
      const valores = f.respuestas[s.clave];
      for (const c of s.campos) {
        const v = valores?.[c.clave];
        if (vacio(v) || !visible(c, valores)) continue;
        const prefijo = `${f.nombre} › ${s.titulo} — ${c.etiqueta}`;
        if (c.tipo === "odontograma") {
          const l = lineasHallazgos(v as ValorOdontograma);
          lineas.push(`${prefijo}: ${l.length ? l.join("; ") : "sin hallazgos"}`);
        } else if (c.tipo === "escala") {
          const t = total(c, v);
          if (t) lineas.push(`${prefijo}: ${t.puntaje}/${t.maximo}${t.interpretacion ? ` (${t.interpretacion})` : ""}`);
        } else if (c.tipo === "firma") {
          const nombre = (v as { nombre?: string }).nombre;
          lineas.push(`${prefijo}: firmado${nombre ? ` por ${nombre}` : ""}`);
        } else if (c.tipo === "tabla") {
          const filas = (v as Fila[]).map((fila) => {
            const nombre = c.filas?.find((x) => x.clave === fila.fila)?.etiqueta;
            const celdas = (c.columnas ?? []).filter((col) => !vacio(fila[col.clave]))
              .map((col) => `${col.etiqueta}: ${textoValor(col.tipo, fila[col.clave])}`).join(", ");
            return nombre ? `${nombre} (${celdas})` : celdas;
          });
          lineas.push(`${prefijo}: ${filas.join("; ")}`);
        } else {
          lineas.push(`${prefijo}: ${textoValor(c.tipo, v, c.unidad)}`);
        }
      }
    }
  }
  return lineas.join("\n");
}
