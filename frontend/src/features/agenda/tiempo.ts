/** Fechas y horas siempre en la zona horaria del consultorio, no en la del navegador. */

/** "2026-10-21" de hoy en la zona. */
export function hoyEn(zona: string): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: zona }).format(new Date());
}

export function sumarDias(fecha: string, dias: number): string {
  const d = new Date(`${fecha}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + dias);
  return d.toISOString().slice(0, 10);
}

export function hora(iso: string, zona: string): string {
  return new Intl.DateTimeFormat("es-CO", { timeZone: zona, hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(iso));
}

export function fechaLarga(fecha: string): string {
  return new Intl.DateTimeFormat("es-CO", { timeZone: "UTC", weekday: "long", day: "numeric", month: "long" })
    .format(new Date(`${fecha}T12:00:00Z`));
}

export function fechaHoraCorta(iso: string, zona: string): string {
  return new Intl.DateTimeFormat("es-CO", { timeZone: zona, weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", hour12: false })
    .format(new Date(iso));
}

/** "2026-10-21" + "07:30" en la zona → ISO con el desfase correcto ("2026-10-21T07:30:00-05:00"). */
export function aIso(fecha: string, horaLocal: string, zona: string): string {
  const aprox = new Date(`${fecha}T${horaLocal}:00Z`);
  const nombre = new Intl.DateTimeFormat("en-US", { timeZone: zona, timeZoneName: "longOffset" })
    .formatToParts(aprox).find((p) => p.type === "timeZoneName")?.value ?? "GMT";
  const offset = nombre === "GMT" ? "+00:00" : nombre.replace("GMT", "");
  return `${fecha}T${horaLocal}:00${offset}`;
}

/** Las horas de la API pueden venir como "08:00" o "08:00:00". */
export const hhmm = (t?: string) => (t ?? "").slice(0, 5);

export const DIAS = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
