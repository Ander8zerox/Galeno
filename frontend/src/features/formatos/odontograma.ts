// Catálogo del odontograma: el mismo de formatos/Odontograma.java (el servidor valida contra ese).

export type Denticion = "permanente" | "temporal" | "mixta";
export type Superficie = "V" | "L" | "M" | "D" | "O";

export interface Diente {
  hallazgos?: string[];
  superficies?: Partial<Record<Superficie, string>>;
  nota?: string;
}

export interface ValorOdontograma {
  denticion: Denticion;
  dientes: Record<string, Diente>;
}

export const DENTICIONES: Record<Denticion, string> = { permanente: "Permanente", temporal: "Temporal", mixta: "Mixta" };

export const SUPERFICIES: Record<Superficie, string> = {
  V: "vestibular", L: "lingual o palatina", M: "mesial", D: "distal", O: "oclusal o incisal",
};

/** Rojo lo que hay que tratar, azul lo tratado (los mismos colores del PDF). */
export const HALLAZGOS_SUPERFICIE: Record<string, { nombre: string; color: string }> = {
  caries: { nombre: "Caries", color: "#d64545" },
  resina: { nombre: "Obturación en resina", color: "#2f6fd6" },
  amalgama: { nombre: "Obturación en amalgama", color: "#5b6577" },
  sellante: { nombre: "Sellante", color: "#2a9d8f" },
};

export const HALLAZGOS_DIENTE: Record<string, { nombre: string; sigla: string }> = {
  ausente: { nombre: "Ausente", sigla: "AU" },
  extraccion_indicada: { nombre: "Extracción indicada", sigla: "EI" },
  corona: { nombre: "Corona", sigla: "CO" },
  endodoncia: { nombre: "Endodoncia", sigla: "EN" },
  implante: { nombre: "Implante", sigla: "IM" },
  protesis: { nombre: "Prótesis", sigla: "PR" },
  fractura: { nombre: "Fractura", sigla: "FR" },
};

const cuadrante = (c: number, n: number, haciaLaLinea: boolean) =>
  Array.from({ length: n }, (_, i) => c * 10 + (haciaLaLinea ? n - i : i + 1));

/** Filas a dibujar: lado derecho del paciente a la izquierda de quien mira; en mixta, las temporales en medio. */
export function filas(d: Denticion): number[][] {
  const perm = [[...cuadrante(1, 8, true), ...cuadrante(2, 8, false)], [...cuadrante(4, 8, true), ...cuadrante(3, 8, false)]];
  const temp = [[...cuadrante(5, 5, true), ...cuadrante(6, 5, false)], [...cuadrante(8, 5, true), ...cuadrante(7, 5, false)]];
  if (d === "permanente") return perm;
  if (d === "temporal") return temp;
  return [perm[0], temp[0], temp[1], perm[1]];
}

export function dientesDe(d: Denticion): Set<string> {
  return new Set(filas(d).flat().map(String));
}

/** Superior: vestibular arriba. Lado derecho del paciente: la mesial (hacia la línea media) a la derecha. */
export function orientacion(numero: number) {
  const c = Math.floor(numero / 10);
  const superior = [1, 2, 5, 6].includes(c);
  const derecho = [1, 4, 5, 8].includes(c);
  return {
    arriba: (superior ? "V" : "L") as Superficie,
    abajo: (superior ? "L" : "V") as Superficie,
    izquierda: (derecho ? "D" : "M") as Superficie,
    derecha: (derecho ? "M" : "D") as Superficie,
  };
}

/** Lo mismo que Odontograma.hallazgos en el servidor: un renglón por diente con hallazgos. */
export function lineasHallazgos(v: ValorOdontograma): string[] {
  return Object.keys(v.dientes ?? {}).sort((a, b) => Number(a) - Number(b)).flatMap((n) => {
    const d = v.dientes[n];
    const partes = (d.hallazgos ?? []).map((h) => HALLAZGOS_DIENTE[h]?.nombre.toLowerCase() ?? h);
    const porHallazgo = new Map<string, string[]>();
    (Object.keys(SUPERFICIES) as Superficie[]).forEach((s) => {
      const h = d.superficies?.[s];
      if (h) porHallazgo.set(h, [...(porHallazgo.get(h) ?? []), SUPERFICIES[s]]);
    });
    porHallazgo.forEach((sups, h) => partes.push(`${HALLAZGOS_SUPERFICIE[h]?.nombre.toLowerCase() ?? h} ${sups.join(" y ")}`));
    let texto = partes.join("; ");
    if (d.nota) texto = texto ? `${texto} (${d.nota})` : d.nota;
    return texto ? [`${n}: ${texto}`] : [];
  });
}
