import { useEffect, useRef, useState } from "react";

/** Valor de un campo "firma": la imagen PNG, quién firma y cuándo (la fecha la pone el servidor). */
export interface ValorFirma {
  imagen?: string;
  nombre?: string;
  firmadaEn?: string;
}

const ANCHO = 600;
const ALTO = 180;

/**
 * Firma manuscrita en pantalla: con el dedo en un celular o tableta, o con el mouse. Al soltar, la firma pasa al
 * formato como PNG; "Borrar" la quita. Una firma ya guardada se muestra como imagen hasta que se pide firmar de nuevo.
 */
export function FirmaPad({ valor, onChange }: { valor: ValorFirma | null; onChange: (v: ValorFirma | null) => void }) {
  const lienzo = useRef<HTMLCanvasElement>(null);
  const dibujando = useRef(false);
  const [trazos, setTrazos] = useState(false);
  const [rehacer, setRehacer] = useState(!valor?.imagen);
  const nombre = valor?.nombre ?? "";

  useEffect(() => {
    const ctx = lienzo.current?.getContext("2d");
    if (!ctx) return;
    ctx.lineWidth = 2.5;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.strokeStyle = "#10222c";
  }, [rehacer]);

  function punto(e: React.PointerEvent<HTMLCanvasElement>) {
    const r = e.currentTarget.getBoundingClientRect();
    return { x: ((e.clientX - r.left) / r.width) * ANCHO, y: ((e.clientY - r.top) / r.height) * ALTO };
  }

  function empezar(e: React.PointerEvent<HTMLCanvasElement>) {
    const ctx = e.currentTarget.getContext("2d");
    if (!ctx) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    dibujando.current = true;
    const p = punto(e);
    ctx.beginPath();
    ctx.moveTo(p.x, p.y);
    ctx.lineTo(p.x + 0.1, p.y + 0.1);
    ctx.stroke();
  }

  function mover(e: React.PointerEvent<HTMLCanvasElement>) {
    if (!dibujando.current) return;
    const ctx = e.currentTarget.getContext("2d");
    if (!ctx) return;
    const p = punto(e);
    ctx.lineTo(p.x, p.y);
    ctx.stroke();
  }

  function terminar(e: React.PointerEvent<HTMLCanvasElement>) {
    if (!dibujando.current) return;
    dibujando.current = false;
    setTrazos(true);
    // Firma nueva: sin fecha, para que el servidor ponga la de ahora
    onChange({ imagen: e.currentTarget.toDataURL("image/png"), nombre: nombre || undefined });
  }

  function borrar() {
    const c = lienzo.current;
    c?.getContext("2d")?.clearRect(0, 0, ANCHO, ALTO);
    setTrazos(false);
    onChange(nombre ? { nombre } : null);
  }

  const campoNombre = (
    <label>Nombre de quien firma
      <input value={nombre} maxLength={120} placeholder="Nombre completo"
        onChange={(e) => onChange({ ...(valor ?? {}), nombre: e.target.value || undefined })} />
    </label>
  );

  if (valor?.imagen && !rehacer) {
    return (
      <div className="stack-sm">
        <img className="firma-guardada" src={valor.imagen} alt={`Firma${valor.nombre ? ` de ${valor.nombre}` : ""}`} />
        <div className="row">
          {valor.firmadaEn && <span className="muted small">Firmada el {new Date(valor.firmadaEn).toLocaleString("es-CO", { dateStyle: "medium", timeStyle: "short" })}</span>}
          <button type="button" className="btn-link" onClick={() => { setRehacer(true); onChange(nombre ? { nombre } : null); }}>Firmar de nuevo</button>
        </div>
        {campoNombre}
      </div>
    );
  }

  return (
    <div className="stack-sm">
      <canvas ref={lienzo} className="firma-lienzo" width={ANCHO} height={ALTO} role="img"
        aria-label="Espacio para firmar con el dedo o el mouse"
        onPointerDown={empezar} onPointerMove={mover} onPointerUp={terminar} onPointerCancel={terminar} />
      <div className="row between">
        <span className="muted small">{trazos ? "Firma lista. Guarde la atención para conservarla." : "Firme dentro del recuadro con el dedo o el mouse."}</span>
        <button type="button" className="btn-link" disabled={!trazos} onClick={borrar}>Borrar</button>
      </div>
      {campoNombre}
    </div>
  );
}
