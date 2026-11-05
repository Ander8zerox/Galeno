/**
 * Mensajes de validación de los formularios en español. Sin esto, el navegador los muestra en su propio idioma
 * ("Please fill out this field"). Un solo lugar para toda la app: escucha el evento "invalid" de cualquier campo, pone el
 * mensaje según lo que falló y lo quita en cuanto la persona corrige el campo.
 */
type Campo = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;

const esCampo = (el: EventTarget | null): el is Campo =>
  el instanceof HTMLInputElement || el instanceof HTMLSelectElement || el instanceof HTMLTextAreaElement;

export function mensajeDeValidacion(el: Campo): string {
  const v = el.validity;
  if (v.valueMissing) {
    if (el instanceof HTMLSelectElement) return "Elija una opción de la lista.";
    if (el instanceof HTMLInputElement && el.type === "checkbox") return "Marque esta casilla para continuar.";
    if (el instanceof HTMLInputElement && el.type === "file") return "Elija un archivo.";
    return "Complete este campo.";
  }
  if (v.typeMismatch) {
    if (el instanceof HTMLInputElement && el.type === "email") return "Escriba un correo válido, por ejemplo nombre@dominio.com.";
    if (el instanceof HTMLInputElement && el.type === "url") return "Escriba una dirección web válida.";
    return "El valor no tiene el formato esperado.";
  }
  if (v.badInput) return "Escriba un número válido.";
  if (v.tooShort && !(el instanceof HTMLSelectElement)) return `Escriba al menos ${el.minLength} caracteres (lleva ${el.value.length}).`;
  if (v.tooLong && !(el instanceof HTMLSelectElement)) return `Escriba como máximo ${el.maxLength} caracteres.`;
  if (v.patternMismatch) return el.title || "El valor no tiene el formato esperado.";
  if (el instanceof HTMLInputElement) {
    if (v.rangeUnderflow) return `El valor debe ser ${el.min} o mayor.`;
    if (v.rangeOverflow) return `El valor debe ser ${el.max} o menor.`;
    if (v.stepMismatch) return "El valor no es válido para este campo.";
  }
  return "";
}

export function instalarMensajesValidacion() {
  document.addEventListener("invalid", (e) => {
    if (!esCampo(e.target)) return;
    e.target.setCustomValidity(""); // para leer el error real, no el mensaje anterior
    e.target.setCustomValidity(mensajeDeValidacion(e.target));
  }, true);
  // Al corregir, el mensaje se quita: si no, el campo seguiría inválido aunque ya esté bien
  const limpiar = (e: Event) => { if (esCampo(e.target)) e.target.setCustomValidity(""); };
  document.addEventListener("input", limpiar, true);
  document.addEventListener("change", limpiar, true);
}
