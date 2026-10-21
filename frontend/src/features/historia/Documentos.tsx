import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { descargarArchivo, subirArchivo, type Schemas } from "../../api/client";

type Documento = Schemas["DocumentoVista"];
type TipoDocumento = NonNullable<Documento["tipo"]>;

export const TIPO_DOCUMENTO: Record<TipoDocumento, string> = {
  RESULTADO_LABORATORIO: "Resultado de laboratorio",
  IMAGEN_DIAGNOSTICA: "Imagen diagnóstica",
  HISTORIA_EXTERNA: "Historia de otro sistema",
  CONSENTIMIENTO: "Consentimiento",
  REMISION: "Remisión",
  OTRO: "Otro",
};

export function tamano(bytes = 0): string {
  return bytes > 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

export function BotonDescargar({ id, nombre, children }: { id: string; nombre: string; children?: React.ReactNode }) {
  return <BotonArchivo ruta={`/api/v1/historia/documentos/${id}/archivo`} nombre={nombre}>{children ?? "Descargar"}</BotonArchivo>;
}

/** Descarga un archivo de la API con el token de la sesión (PDF de fórmula, atención, documento). */
export function BotonArchivo({ ruta, nombre, children, className = "btn-link" }: { ruta: string; nombre: string; children: React.ReactNode; className?: string }) {
  const [error, setError] = useState<string | null>(null);
  const [bajando, setBajando] = useState(false);
  return (
    <>
      <button type="button" className={className} disabled={bajando} onClick={() => {
        setBajando(true);
        setError(null);
        descargarArchivo(ruta, nombre).catch((e: Error) => setError(e.message)).finally(() => setBajando(false));
      }}>
        {bajando ? "Generando…" : children}
      </button>
      {error && <small className="field-error">{error}</small>}
    </>
  );
}

/** Subir un PDF o imagen a la historia del paciente (opcionalmente ligado a una atención). */
export function SubirDocumento({ pacienteId, atencionId }: { pacienteId: string; atencionId?: string }) {
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [tipo, setTipo] = useState<TipoDocumento>(atencionId ? "RESULTADO_LABORATORIO" : "HISTORIA_EXTERNA");
  const [descripcion, setDescripcion] = useState("");
  const [archivo, setArchivo] = useState<File | null>(null);

  const subir = useMutation({
    mutationFn: async () => {
      const datos = new FormData();
      datos.append("archivo", archivo!);
      datos.append("tipo", tipo);
      if (descripcion) datos.append("descripcion", descripcion);
      if (atencionId) datos.append("atencionId", atencionId);
      return subirArchivo<Documento>(`/api/v1/historia/pacientes/${pacienteId}/documentos`, datos);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["historia", pacienteId] });
      queryClient.invalidateQueries({ queryKey: ["atencion"] });
      setArchivo(null);
      setDescripcion("");
      if (input.current) input.current.value = "";
    },
  });

  return (
    <div className="stack">
      <div className="form-grid">
        <label>Archivo (PDF, PNG o JPG, máx. 20 MB)
          <input ref={input} type="file" accept="application/pdf,image/png,image/jpeg" onChange={(e) => setArchivo(e.target.files?.[0] ?? null)} />
        </label>
        <label>Tipo
          <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoDocumento)}>
            {Object.entries(TIPO_DOCUMENTO).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
          </select>
        </label>
        <label className="wide">Descripción (opcional)<input value={descripcion} onChange={(e) => setDescripcion(e.target.value)} placeholder="Ej.: Perfil lipídico de octubre" /></label>
      </div>
      {subir.error && <p className="alert alert-error">{subir.error.message}</p>}
      <div><button type="button" className="btn btn-secondary" disabled={!archivo || subir.isPending} onClick={() => subir.mutate()}>
        {subir.isPending ? "Subiendo…" : "Subir documento"}
      </button></div>
    </div>
  );
}
