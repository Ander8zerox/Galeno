import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "../../api/client";
import { pesos } from "./modulos";
import { NO_APLICA, usePagadores, type Cobro } from "./pagadores";
import { nombrePropio, useReferencia } from "./referencias";

const numero = (v: string) => Number(v.replace(/[.\s$]/g, "").replace(",", "."));

/** Pagador, pago moderador y autorización. En la cita lo registra la secretaria; en la atención, quien atiende. */
export function CobroForm({ donde, id, cobro, onGuardado, onCancelar }: {
  donde: "cita" | "atencion"; id: string; cobro: Cobro; onGuardado: (c: Cobro) => void; onCancelar?: () => void;
}) {
  const queryClient = useQueryClient();
  const pagadores = usePagadores();
  const conceptos = useReferencia("concepto_recaudo");
  const [form, setForm] = useState({
    pagadorId: cobro.pagadorId ?? "",
    conceptoRecaudo: cobro.conceptoRecaudo ?? NO_APLICA,
    valor: cobro.valorPagoModerador ? String(cobro.valorPagoModerador) : "",
    numeroAutorizacion: cobro.numeroAutorizacion ?? "",
    facturaPagoModerador: cobro.facturaPagoModerador ?? "",
    registroSiras: cobro.registroSiras ?? "",
  });
  const pagador = pagadores.data?.find((p) => p.id === form.pagadorId);
  const particular = pagador?.tipo === "PARTICULAR";
  const sinCobro = form.conceptoRecaudo === NO_APLICA || particular;
  // Accidente de tránsito: el RIPS exige el radicado de SIRAS (ADRES). Con otro pagador el servidor lo descarta
  const pideSiras = pagador?.tipo === "SOAT" || (cobro.pideSiras && pagador?.id === cobro.pagadorId);
  const guardar = useMutation({
    mutationFn: async () => {
      const body = {
        pagadorId: form.pagadorId,
        conceptoRecaudo: sinCobro ? NO_APLICA : form.conceptoRecaudo,
        valorPagoModerador: sinCobro ? 0 : numero(form.valor),
        numeroAutorizacion: form.numeroAutorizacion.trim() || undefined,
        facturaPagoModerador: sinCobro ? undefined : form.facturaPagoModerador.trim() || undefined,
        registroSiras: pideSiras ? form.registroSiras.trim() || undefined : undefined,
      };
      return donde === "cita"
        ? unwrap(await api.PUT("/api/v1/rips/cobros/citas/{citaId}", { params: { path: { citaId: id } }, body }))
        : unwrap(await api.PUT("/api/v1/rips/cobros/atenciones/{atencionId}", { params: { path: { atencionId: id } }, body }));
    },
    onSuccess: (c) => {
      queryClient.invalidateQueries({ queryKey: ["rips-cobros-citas"] });
      queryClient.invalidateQueries({ queryKey: ["rips-cobro"] });
      queryClient.invalidateQueries({ queryKey: ["cups"] }); // el precio puede cambiar con el pagador (tarifas)
      onGuardado(c);
    },
  });
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  return (
    <form className="form-grid cobro" onSubmit={(e) => { e.preventDefault(); guardar.mutate(); }}>
      <label>Pagador
        <select value={form.pagadorId} onChange={set("pagadorId")} required>
          <option value="">—</option>
          {pagadores.data?.filter((p) => p.activo || p.id === form.pagadorId).map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
        </select>
      </label>
      <label>Autorización {pagador?.exigeAutorizacion && <span className="muted small">(la exige)</span>}
        <input value={form.numeroAutorizacion} maxLength={30} placeholder={pagador?.exigeAutorizacion ? "Número de la EPS" : "Si la hay"}
          onChange={(e) => setForm({ ...form, numeroAutorizacion: e.target.value.replace(/[^A-Za-z0-9-]/g, "") })} />
      </label>
      {!particular && (
        <label>Cobro al paciente
          <select value={form.conceptoRecaudo} onChange={set("conceptoRecaudo")}>
            {conceptos.data?.filter((c) => c.codigo !== "04").map((c) => <option key={c.codigo} value={c.codigo}>{nombrePropio(c.nombre)}</option>)}
          </select>
        </label>
      )}
      {!sinCobro && (
        <label>Valor cobrado
          <input inputMode="numeric" value={form.valor} onChange={set("valor")} placeholder="$" required />
        </label>
      )}
      {!sinCobro && (
        <label>Factura del cobro <span className="muted small">(opcional)</span>
          <input value={form.facturaPagoModerador} maxLength={20} onChange={(e) => setForm({ ...form, facturaPagoModerador: e.target.value.replace(/[^A-Za-z0-9-]/g, "") })} />
        </label>
      )}
      {pideSiras && (
        <label>Registro SIRAS <span className="muted small">(accidente de tránsito)</span>
          <input value={form.registroSiras} maxLength={60} placeholder="Radicado de la ADRES"
            onChange={(e) => setForm({ ...form, registroSiras: e.target.value.replace(/[^A-Za-z0-9-]/g, "") })} />
        </label>
      )}
      {particular && <p className="muted small wide">El particular paga el servicio: no hay copago ni cuota moderadora.</p>}
      <div className="wide row">
        <button className="btn" disabled={guardar.isPending || !form.pagadorId}>Guardar</button>
        {onCancelar && <button type="button" className="btn-link" onClick={onCancelar}>Cancelar</button>}
        {guardar.error && <span className="field-error">{guardar.error.message}</span>}
      </div>
    </form>
  );
}

/** "Nueva EPS · cuota moderadora $4.500 · aut. 123", para mostrar el cobro en una línea. */
export function resumenCobro(c: Cobro, conceptos?: { codigo?: string; nombre?: string }[]): string {
  const partes = [c.pagadorNombre ?? "Sin pagador"];
  if (c.conceptoRecaudo && c.conceptoRecaudo !== NO_APLICA) {
    const nombre = conceptos?.find((x) => x.codigo === c.conceptoRecaudo)?.nombre;
    partes.push(`${nombrePropio(nombre).toLowerCase() || "cobro"} ${pesos(c.valorPagoModerador)}`);
  }
  if (c.numeroAutorizacion) partes.push(`aut. ${c.numeroAutorizacion}`);
  if (c.registroSiras) partes.push(`SIRAS ${c.registroSiras}`);
  return partes.join(" · ");
}

/** El pagador exige autorización y no la tiene. */
export const faltaAutorizacion = (c?: Cobro) => !!c?.exigeAutorizacion && !c.numeroAutorizacion;
