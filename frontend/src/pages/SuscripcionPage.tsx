import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, subirArchivo, unwrap, type Schemas } from "../api/client";
import { useAuth } from "../auth/AuthContext";

type Estado = Schemas["EstadoVista"];
type Pago = Schemas["PagoVista"];

const fecha = new Intl.DateTimeFormat("es-CO", { dateStyle: "long" });
const fechaCorta = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium" });
export const pesos = (v?: number) => new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(v ?? 0);

export const ESTADO_SUSCRIPCION: Record<NonNullable<Estado["estado"]>, [string, string]> = {
  PRUEBA: ["Prueba gratis", "badge-warn"],
  ACTIVA: ["Activa", "badge-ok"],
  VENCIDA: ["Vencida (en gracia)", "badge-danger"],
  SUSPENDIDA: ["Solo lectura", "badge-danger"],
};

const ESTADO_PAGO: Record<NonNullable<Pago["estado"]>, [string, string]> = {
  REPORTADO: ["En revisión", "badge-warn"],
  APROBADO: ["Aprobado", "badge-ok"],
  RECHAZADO: ["Rechazado", "badge-danger"],
};

export function useSuscripcion() {
  return useQuery({
    queryKey: ["suscripcion"],
    queryFn: async () => unwrap(await api.GET("/api/v1/suscripcion")),
    refetchInterval: 5 * 60_000,
  });
}

/** Aviso para todo el equipo: prueba por terminar, vencida en gracia o en solo lectura. */
export function AvisoSuscripcion() {
  const { puede } = useAuth();
  const { data: s } = useSuscripcion();
  if (!s) return null;
  const gestiona = puede("SUSCRIPCION_GESTIONAR");
  const enlace = gestiona && <> <Link to="/suscripcion">Ir a Suscripción</Link></>;
  const dias = s.diasRestantes ?? 0;

  if (s.estado === "SUSPENDIDA") {
    return (
      <div className="alert alert-error aviso-suscripcion">
        <strong>El consultorio está en solo lectura.</strong> Puede consultar y descargar la información, pero no registrar
        nada nuevo. {s.motivoSuspension ? `Motivo: ${s.motivoSuspension}. ` : ""}
        {s.pagoEnRevision ? "Su pago está en revisión." : gestiona ? "Reporte su pago para reactivarlo." : "Avise al administrador del consultorio."}
        {enlace}
      </div>
    );
  }
  if (s.estado === "VENCIDA") {
    return (
      <div className="alert alert-error aviso-suscripcion">
        <strong>La suscripción venció.</strong> Todo sigue funcionando hasta el {fecha.format(new Date(s.graciaHasta!))};
        después el consultorio quedará en solo lectura. {s.pagoEnRevision && "Su pago está en revisión."}{enlace}
      </div>
    );
  }
  if (dias <= 7 && !s.pagoEnRevision) {
    const que = s.estado === "PRUEBA" ? "La prueba gratis" : "La suscripción";
    return (
      <div className="alert aviso-suscripcion">
        {que} termina {dias <= 1 ? "mañana" : `en ${dias} días`} ({fecha.format(new Date(s.venceEn!))}).{enlace}
      </div>
    );
  }
  return null;
}

/** Para el administrador: estado, planes, datos para pagar, reporte del pago e historial. */
export function SuscripcionPage() {
  const { data: s } = useSuscripcion();
  const opciones = useQuery({
    queryKey: ["suscripcion", "opciones"],
    queryFn: async () => unwrap(await api.GET("/api/v1/suscripcion/opciones")),
  });
  const pagos = useQuery({
    queryKey: ["suscripcion", "pagos"],
    queryFn: async () => unwrap(await api.GET("/api/v1/suscripcion/pagos")),
  });

  return (
    <section className="stack">
      <div className="page-header"><h1>Suscripción</h1></div>

      {s && (
        <div className="card stack">
          <div className="row">
            <span className={`badge ${ESTADO_SUSCRIPCION[s.estado!][1]}`}>{ESTADO_SUSCRIPCION[s.estado!][0]}</span>
            {s.pagoEnRevision && <span className="badge badge-warn">Pago en revisión</span>}
          </div>
          {s.estado === "SUSPENDIDA" ? (
            <p>El consultorio está en solo lectura desde el {fecha.format(new Date(s.graciaHasta!))}. Al aprobarse el pago vuelve a funcionar todo.</p>
          ) : (
            <p>
              {s.estado === "PRUEBA" ? "La prueba gratis" : "La suscripción"} {s.estado === "VENCIDA" ? "venció" : "vence"} el{" "}
              <strong>{fecha.format(new Date(s.venceEn!))}</strong>
              {s.estado !== "VENCIDA" && ` (${s.diasRestantes} días)`}.
              {s.estado === "VENCIDA" && ` Tiene hasta el ${fecha.format(new Date(s.graciaHasta!))} para ponerse al día.`}
            </p>
          )}
          <p className="muted small">Si paga antes de que venza, el tiempo nuevo se suma desde la fecha de vencimiento: no pierde días.</p>
        </div>
      )}

      {opciones.data && (
        <div className="card stack">
          <h3>Cómo pagar</h3>
          <div className="row">
            {opciones.data.planes?.map((p) => (
              <div key={p.codigo} className="card" style={{ minWidth: "12rem" }}>
                <strong>{p.nombre}</strong>
                <div style={{ fontSize: "1.4rem" }}>{p.precio ? pesos(p.precio) : "Precio por definir"}</div>
                <small className="muted">{p.meses === 1 ? "por mes" : `por ${p.meses} meses`}</small>
              </div>
            ))}
          </div>
          <p className="muted small">Pague por transferencia o consignación y luego repórtelo aquí con la referencia y el comprobante:</p>
          <pre className="card" style={{ whiteSpace: "pre-wrap", margin: 0, fontFamily: "inherit" }}>{opciones.data.datosPago}</pre>
          <ReportarPago planes={opciones.data.planes ?? []} bloqueado={!!s?.pagoEnRevision} />
        </div>
      )}

      <div className="card table-wrap">
        <h3>Pagos</h3>
        <table>
          <thead><tr><th>Reportado</th><th>Plan</th><th>Valor</th><th>Referencia</th><th>Estado</th><th>Periodo</th></tr></thead>
          <tbody>
            {pagos.data?.map((p) => (
              <tr key={p.id}>
                <td>{fechaCorta.format(new Date(p.reportadoEn!))}</td>
                <td>{p.plan === "ANUAL" ? "Anual" : "Mensual"}</td>
                <td>{pesos(p.monto)}</td>
                <td>{p.referencia}</td>
                <td>
                  <span className={`badge ${ESTADO_PAGO[p.estado!][1]}`}>{ESTADO_PAGO[p.estado!][0]}</span>
                  {p.motivoRechazo && <div className="small muted">{p.motivoRechazo}</div>}
                </td>
                <td className="small">{p.periodoHasta ? `${fechaCorta.format(new Date(p.periodoDesde!))} a ${fechaCorta.format(new Date(p.periodoHasta))}` : "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {pagos.data?.length === 0 && <p className="muted">Todavía no ha reportado pagos.</p>}
      </div>
    </section>
  );
}

function ReportarPago({ planes, bloqueado }: { planes: Schemas["PlanVista"][]; bloqueado: boolean }) {
  const queryClient = useQueryClient();
  const hoy = new Date().toLocaleDateString("en-CA"); // aaaa-mm-dd en hora local
  const [plan, setPlan] = useState(planes[0]?.codigo ?? "MENSUAL");
  const [monto, setMonto] = useState(String(planes[0]?.precio || ""));
  const [fechaPago, setFechaPago] = useState(hoy);
  const [referencia, setReferencia] = useState("");
  const [archivo, setArchivo] = useState<File | null>(null);

  const reportar = useMutation({
    mutationFn: async () => {
      const datos = new FormData();
      datos.append("plan", plan);
      datos.append("monto", monto);
      datos.append("fechaPago", fechaPago);
      datos.append("referencia", referencia);
      if (archivo) datos.append("comprobante", archivo);
      return subirArchivo<Pago>("/api/v1/suscripcion/pagos", datos);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["suscripcion"] });
      setReferencia("");
      setArchivo(null);
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    reportar.mutate();
  }

  if (bloqueado) return <p className="alert">Ya reportó un pago y está en revisión. Le avisaremos por correo cuando lo revisemos.</p>;
  return (
    <form className="form-grid" onSubmit={onSubmit}>
      <h3 className="wide">Reportar un pago</h3>
      <label>Plan
        <select value={plan} onChange={(e) => {
          setPlan(e.target.value);
          const p = planes.find((x) => x.codigo === e.target.value);
          if (p?.precio) setMonto(String(p.precio));
        }}>
          {planes.map((p) => <option key={p.codigo} value={p.codigo}>{p.nombre}</option>)}
        </select>
      </label>
      <label>Valor pagado (COP)
        <input type="number" min={1} value={monto} onChange={(e) => setMonto(e.target.value)} required />
      </label>
      <label>Fecha del pago
        <input type="date" max={hoy} value={fechaPago} onChange={(e) => setFechaPago(e.target.value)} required />
      </label>
      <label>Referencia o número de la transacción
        <input value={referencia} onChange={(e) => setReferencia(e.target.value)} required maxLength={100} />
      </label>
      <label className="wide">Comprobante (PDF o foto, hasta 5 MB)
        <input type="file" accept="application/pdf,image/png,image/jpeg" onChange={(e) => setArchivo(e.target.files?.[0] ?? null)} />
      </label>
      {reportar.error && <p className="alert alert-error wide">{reportar.error.message}</p>}
      {reportar.isSuccess && <p className="alert wide">Recibimos su reporte. Lo revisaremos y le avisaremos por correo.</p>}
      <div className="wide"><button className="btn" disabled={reportar.isPending}>{reportar.isPending ? "Enviando…" : "Reportar pago"}</button></div>
    </form>
  );
}
