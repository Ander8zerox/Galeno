import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { subirArchivo, type Schemas } from "../../api/client";

type Columnas = Schemas["Columnas"];
type Mapeo = Schemas["MapeoColumnas"];
type Campo = NonNullable<Schemas["Asignacion"]["campo"]>;
type Lote = Schemas["LoteVista"];

const CAMPOS: [Campo, string][] = [
  ["TIPO_DOCUMENTO", "Tipo de documento"],
  ["NUMERO_DOCUMENTO", "Número de documento"],
  ["NOMBRES", "Nombres"],
  ["APELLIDOS", "Apellidos"],
  ["PRIMER_NOMBRE", "Primer nombre"],
  ["SEGUNDO_NOMBRE", "Segundo nombre"],
  ["PRIMER_APELLIDO", "Primer apellido"],
  ["SEGUNDO_APELLIDO", "Segundo apellido"],
  ["NOMBRE_COMPLETO", "Nombre completo"],
  ["FECHA_NACIMIENTO", "Fecha de nacimiento"],
  ["SEXO", "Sexo"],
  ["TELEFONO", "Teléfono"],
  ["EMAIL", "Correo"],
];

const TIPOS = ["CC", "TI", "CE", "PA", "RC", "PT", "PE"];

/**
 * Listado de pacientes de otro sistema (Excel o CSV): primero se ve qué columnas trae y cómo se van a leer,
 * luego se arma el lote para revisarlo. Sirve sobre todo para poner nombre a los pacientes que llegaron del RIPS.
 */
export function SubirListado() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [archivo, setArchivo] = useState<File | null>(null);
  const [columnas, setColumnas] = useState<Columnas | null>(null);
  // Columna (índice) → campo; "" es "no importar"
  const [asignado, setAsignado] = useState<Record<number, Campo | "">>({});
  const [tipoPorDefecto, setTipoPorDefecto] = useState("CC");
  const [orden, setOrden] = useState<NonNullable<Mapeo["ordenNombre"]>>("NOMBRES_APELLIDOS");

  const analizar = useMutation({
    mutationFn: async (f: File) => {
      const datos = new FormData();
      datos.append("archivo", f);
      return subirArchivo<Columnas>("/api/v1/importaciones/pacientes/columnas", datos);
    },
    onSuccess: (c) => {
      setColumnas(c);
      const inicial: Record<number, Campo | ""> = {};
      c.sugerido?.asignaciones?.forEach((a) => { inicial[a.columna!] = a.campo!; });
      setAsignado(inicial);
      setTipoPorDefecto(c.sugerido?.tipoDocumentoPorDefecto ?? "CC");
      setOrden(c.sugerido?.ordenNombre ?? "NOMBRES_APELLIDOS");
    },
  });

  const usados = Object.values(asignado).filter(Boolean) as Campo[];
  const mapeo: Mapeo = {
    asignaciones: Object.entries(asignado).filter(([, c]) => c).map(([i, c]) => ({ columna: Number(i), campo: c as Campo })),
    tipoDocumentoPorDefecto: usados.includes("TIPO_DOCUMENTO") ? undefined : tipoPorDefecto,
    ordenNombre: orden,
  };

  const crear = useMutation({
    mutationFn: async () => {
      const datos = new FormData();
      datos.append("archivo", archivo!);
      datos.append("mapeo", new Blob([JSON.stringify(mapeo)], { type: "application/json" }));
      return subirArchivo<Lote>("/api/v1/importaciones/pacientes", datos);
    },
    onSuccess: (lote) => {
      queryClient.invalidateQueries({ queryKey: ["importaciones"] });
      navigate(`/importar/${lote.id}`);
    },
  });

  function asignar(columna: number, campo: Campo | "") {
    const nuevo = { ...asignado };
    // Un campo va a una sola columna: si ya estaba en otra, se quita de allá
    if (campo) {
      for (const [i, c] of Object.entries(nuevo)) if (c === campo) nuevo[Number(i)] = "";
    }
    nuevo[columna] = campo;
    setAsignado(nuevo);
  }

  function reiniciar() {
    setArchivo(null);
    setColumnas(null);
    analizar.reset();
    crear.reset();
  }

  return (
    <div className="card stack">
      <h3>Subir listado de pacientes</h3>
      <p className="muted small">
        El Excel (.xlsx) o CSV con la lista de pacientes que exporta su sistema anterior. Se cruza por tipo y número de
        documento: completa los pacientes que el RIPS dejó sin nombre, agrega teléfono y correo donde falten y crea los que
        no existen. Nunca reemplaza datos que ya están en Galeno.
      </p>
      {!columnas && (
        <div className="row">
          <input type="file" accept=".xlsx,.csv,.txt" onChange={(e) => setArchivo(e.target.files?.[0] ?? null)} />
          <button className="btn" disabled={!archivo || analizar.isPending} onClick={() => analizar.mutate(archivo!)}>
            {analizar.isPending ? "Leyendo…" : "Ver columnas"}
          </button>
        </div>
      )}
      {analizar.error && <p className="alert alert-error">{analizar.error.message}</p>}

      {columnas && (
        <>
          <div className="row">
            <strong>{archivo?.name}</strong>
            <span className="muted small">{columnas.filas} filas</span>
            {columnas.recordado && <span className="badge badge-ok">Mapeo recordado de la última vez</span>}
            <span className="spacer" />
            <button className="btn-link" onClick={reiniciar}>Elegir otro archivo</button>
          </div>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Columna del archivo</th><th>Ejemplos</th><th>Se importa como</th></tr></thead>
              <tbody>
                {columnas.encabezados?.map((h, i) => (
                  <tr key={i}>
                    <td>{h || <span className="muted">(sin encabezado)</span>}</td>
                    <td className="muted small">{columnas.muestra?.map((f) => f[i]).filter(Boolean).slice(0, 3).join(" · ") || "—"}</td>
                    <td>
                      <select aria-label={`Campo para ${h}`} value={asignado[i] ?? ""} onChange={(e) => asignar(i, e.target.value as Campo | "")}>
                        <option value="">No importar</option>
                        {CAMPOS.map(([c, nombre]) => <option key={c} value={c}>{nombre}</option>)}
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="row">
            {!usados.includes("TIPO_DOCUMENTO") && (
              <label>El archivo no trae tipo de documento; usar
                <select value={tipoPorDefecto} onChange={(e) => setTipoPorDefecto(e.target.value)}>
                  {TIPOS.map((t) => <option key={t}>{t}</option>)}
                </select>
              </label>
            )}
            {usados.includes("NOMBRE_COMPLETO") && (
              <label>El nombre completo viene como
                <select value={orden} onChange={(e) => setOrden(e.target.value as typeof orden)}>
                  <option value="NOMBRES_APELLIDOS">Nombres y luego apellidos</option>
                  <option value="APELLIDOS_NOMBRES">Apellidos y luego nombres</option>
                </select>
              </label>
            )}
          </div>
          {crear.error && <p className="alert alert-error">{crear.error.message}</p>}
          <div>
            <button className="btn" disabled={!usados.includes("NUMERO_DOCUMENTO") || crear.isPending} onClick={() => crear.mutate()}>
              {crear.isPending ? "Preparando revisión…" : "Revisar pacientes"}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
