import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, expectOk, unwrap, type Schemas } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ApoyoDiagnostico } from "../features/apoyo/ApoyoDiagnostico";
import { RedactorFormula } from "../features/formulacion/RedactorFormula";
import { DiagnosticosEditor, TIPO_DX, type DiagnosticoForm } from "../features/historia/DiagnosticosEditor";
import { BotonDescargar, SubirDocumento, TIPO_DOCUMENTO, tamano } from "../features/historia/Documentos";
import { fechaHoraCorta } from "../features/agenda/tiempo";

type Atencion = Schemas["AtencionVista"];

const TEXTOS = [
  ["motivoConsulta", "Motivo de consulta *"],
  ["enfermedadActual", "Enfermedad actual"],
  ["antecedentes", "Antecedentes"],
  ["revisionSistemas", "Revisión por sistemas"],
] as const;
const SIGNOS = [
  ["taSistolica", "TA sistólica (mmHg)"],
  ["taDiastolica", "TA diastólica (mmHg)"],
  ["frecuenciaCardiaca", "FC (lpm)"],
  ["frecuenciaRespiratoria", "FR (rpm)"],
  ["temperatura", "Temperatura (°C)"],
  ["saturacionO2", "SatO₂ (%)"],
  ["pesoKg", "Peso (kg)"],
  ["tallaCm", "Talla (cm)"],
] as const;
const FINALES = [
  ["examenFisico", "Examen físico"],
  ["analisis", "Análisis"],
  ["plan", "Plan"],
] as const;

type Campo = (typeof TEXTOS)[number][0] | (typeof SIGNOS)[number][0] | (typeof FINALES)[number][0];
type Form = Record<Campo, string>;

function aForm(a: Atencion): Form {
  const f = {} as Form;
  for (const [k] of [...TEXTOS, ...SIGNOS, ...FINALES]) {
    const v = a[k];
    f[k] = v === null || v === undefined ? "" : String(v);
  }
  return f;
}

const num = (v: string) => (v.trim() === "" ? undefined : Number(v.replace(",", ".")));
const txt = (v: string) => (v.trim() === "" ? undefined : v);

export function AtencionPage() {
  const { pacienteId, atencionId } = useParams<{ pacienteId: string; atencionId: string }>();
  const atencion = useQuery({
    queryKey: ["atencion", atencionId],
    queryFn: async () => unwrap(await api.GET("/api/v1/historia/atenciones/{id}", { params: { path: { id: atencionId! } } })),
  });
  const paciente = useQuery({
    queryKey: ["paciente", pacienteId],
    queryFn: async () => unwrap(await api.GET("/api/v1/pacientes/{id}", { params: { path: { id: pacienteId! } } })),
  });

  if (atencion.isLoading) return <p className="muted">Cargando…</p>;
  if (atencion.error || !atencion.data) return <p className="alert alert-error">{atencion.error?.message ?? "Atención no encontrada"}</p>;
  const a = atencion.data;
  const p = paciente.data;

  return (
    <section className="stack">
      <Link to={`/pacientes/${pacienteId}`} className="back">← {p ? `${p.nombres} ${p.apellidos}` : "Paciente"}</Link>
      {p && (
        <div className="card row between">
          <span><strong>{p.nombres} {p.apellidos}</strong> · {p.tipoDocumento} {p.numeroDocumento} · {p.edad} años</span>
          {p.alergias ? <span className="badge badge-danger">Alergias: {p.alergias}</span> : <span className="badge">Sin alergias registradas</span>}
        </div>
      )}
      {a.estado === "BORRADOR" && a.propia
        ? <EditorAtencion atencion={a} />
        : <LecturaAtencion atencion={a} />}
    </section>
  );
}

function EditorAtencion({ atencion }: { atencion: Atencion }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [form, setForm] = useState<Form>(() => aForm(atencion));
  const [dx, setDx] = useState<DiagnosticoForm[]>(() => (atencion.diagnosticos ?? []).map((d) => ({ ...d }) as DiagnosticoForm));
  const [version, setVersion] = useState(atencion.version ?? 0);
  const [confirmando, setConfirmando] = useState(false);
  const [panel, setPanel] = useState<"ia" | "formula" | "documentos" | null>(null);
  const [guardadoEn, setGuardadoEn] = useState<Date | null>(null);

  const original = useMemo(() => JSON.stringify({ f: aForm(atencion), d: atencion.diagnosticos ?? [] }), [atencion]);
  const [base, setBase] = useState(original);
  const sucio = JSON.stringify({ f: form, d: dx }) !== base;

  useEffect(() => {
    if (!sucio) return;
    const aviso = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", aviso);
    return () => window.removeEventListener("beforeunload", aviso);
  }, [sucio]);

  const guardar = useMutation({
    mutationFn: async () =>
      unwrap(await api.PUT("/api/v1/historia/atenciones/{id}", {
        params: { path: { id: atencion.id! } },
        body: {
          version,
          motivoConsulta: txt(form.motivoConsulta), enfermedadActual: txt(form.enfermedadActual),
          antecedentes: txt(form.antecedentes), revisionSistemas: txt(form.revisionSistemas),
          taSistolica: num(form.taSistolica), taDiastolica: num(form.taDiastolica),
          frecuenciaCardiaca: num(form.frecuenciaCardiaca), frecuenciaRespiratoria: num(form.frecuenciaRespiratoria),
          temperatura: num(form.temperatura), saturacionO2: num(form.saturacionO2),
          pesoKg: num(form.pesoKg), tallaCm: num(form.tallaCm),
          examenFisico: txt(form.examenFisico), analisis: txt(form.analisis), plan: txt(form.plan),
          diagnosticos: dx,
        },
      })),
    onSuccess: (a) => {
      setVersion(a.version ?? version);
      setBase(JSON.stringify({ f: form, d: dx }));
      setGuardadoEn(new Date());
    },
  });

  const firmar = useMutation({
    mutationFn: async () => {
      if (sucio) await guardar.mutateAsync();
      return unwrap(await api.POST("/api/v1/historia/atenciones/{id}/firma", { params: { path: { id: atencion.id! } } }));
    },
    onSuccess: (a) => {
      queryClient.setQueryData(["atencion", atencion.id], a);
      queryClient.invalidateQueries({ queryKey: ["historia"] });
      queryClient.invalidateQueries({ queryKey: ["citas"] });
    },
  });

  const eliminar = useMutation({
    mutationFn: async () => expectOk(await api.DELETE("/api/v1/historia/atenciones/{id}", { params: { path: { id: atencion.id! } } })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["historia"] });
      navigate(`/pacientes/${atencion.pacienteId}`);
    },
  });

  const set = (k: Campo) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });
  const imc = (() => {
    const peso = num(form.pesoKg), talla = num(form.tallaCm);
    return peso && talla ? (peso / (talla / 100) ** 2).toFixed(1) : null;
  })();
  const principal = dx.find((d) => d.principal);
  const signosTexto = [
    form.taSistolica && form.taDiastolica && `TA ${form.taSistolica}/${form.taDiastolica}`,
    form.frecuenciaCardiaca && `FC ${form.frecuenciaCardiaca}`, form.frecuenciaRespiratoria && `FR ${form.frecuenciaRespiratoria}`,
    form.temperatura && `T ${form.temperatura} °C`, form.saturacionO2 && `SatO2 ${form.saturacionO2}%`,
  ].filter(Boolean).join(", ");
  const error = guardar.error ?? firmar.error ?? eliminar.error;

  return (
    <div className="stack">
      <div className="row between">
        <h1>Atención <span className="badge badge-warn">Borrador</span></h1>
        <span className="muted small">{guardadoEn ? `Guardado a las ${guardadoEn.toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" })}` : sucio ? "Cambios sin guardar" : ""}</span>
      </div>

      <div className="card stack">
        {TEXTOS.map(([k, label]) => (
          <label key={k}>{label}
            {k === "motivoConsulta" ? <input value={form[k]} onChange={set(k)} /> : <textarea rows={k === "enfermedadActual" ? 4 : 2} value={form[k]} onChange={set(k)} />}
          </label>
        ))}
      </div>

      <div className="card stack">
        <h3>Signos vitales {imc && <span className="muted small">· IMC {imc}</span>}</h3>
        <div className="signos">
          {SIGNOS.map(([k, label]) => (
            <label key={k}>{label}<input inputMode="decimal" value={form[k]} onChange={set(k)} /></label>
          ))}
        </div>
      </div>

      <div className="card stack">
        <label>Examen físico<textarea rows={4} value={form.examenFisico} onChange={set("examenFisico")} /></label>
        <h3>Diagnósticos</h3>
        <DiagnosticosEditor value={dx} onChange={setDx} />
        <label>Análisis<textarea rows={3} value={form.analisis} onChange={set("analisis")} /></label>
        <label>Plan<textarea rows={3} value={form.plan} onChange={set("plan")} /></label>
      </div>

      {error && <p className="alert alert-error">{error.message}</p>}
      <div className="card row between acciones-atencion">
        <div className="row">
          <button className="btn btn-secondary" disabled={!sucio || guardar.isPending} onClick={() => guardar.mutate()}>Guardar borrador</button>
          {!confirmando
            ? <button className="btn" onClick={() => setConfirmando(true)}>Firmar atención</button>
            : (
              <span className="row confirmar">
                <span>Al firmar ya no podrá editarla (solo agregar notas aclaratorias).</span>
                <button className="btn" disabled={firmar.isPending} onClick={() => firmar.mutate()}>{firmar.isPending ? "Firmando…" : "Sí, firmar"}</button>
                <button className="btn-link" onClick={() => setConfirmando(false)}>Cancelar</button>
              </span>
            )}
        </div>
        <button className="btn-link danger" disabled={eliminar.isPending} onClick={() => eliminar.mutate()}>Eliminar borrador</button>
      </div>

      <nav className="tabs">
        <button className={panel === "ia" ? "active" : ""} onClick={() => setPanel(panel === "ia" ? null : "ia")}>Apoyo diagnóstico con IA</button>
        <button className={panel === "formula" ? "active" : ""} onClick={() => setPanel(panel === "formula" ? null : "formula")}>Fórmula</button>
        <button className={panel === "documentos" ? "active" : ""} onClick={() => setPanel(panel === "documentos" ? null : "documentos")}>Documentos ({atencion.documentos?.length ?? 0})</button>
      </nav>
      {panel === "ia" && (
        <ApoyoDiagnostico
          pacienteId={atencion.pacienteId!}
          inicial={{ motivoConsulta: form.motivoConsulta, enfermedadActual: form.enfermedadActual, signosVitales: signosTexto, examenFisico: form.examenFisico }}
          onUsarDiagnostico={(d) => {
            // La IA puede escribir "J02.9"; el catálogo y RIPS usan "J029"
            const codigo = (d.codigoCie10 ?? "").toUpperCase().replace(/[.\s]/g, "");
            if (!codigo || dx.some((x) => x.codigoCie10 === codigo)) return;
            setDx([...dx, { codigoCie10: codigo, descripcion: d.descripcion ?? "", principal: dx.length === 0, tipo: "IMPRESION_DIAGNOSTICA" }]);
          }}
        />
      )}
      {panel === "formula" && (
        <RedactorFormula pacienteId={atencion.pacienteId!} atencionId={atencion.id}
          diagnosticoInicial={principal ? `${principal.codigoCie10} ${principal.descripcion}` : undefined}
          onFirmada={() => queryClient.invalidateQueries({ queryKey: ["formulas", atencion.pacienteId] })} />
      )}
      {panel === "documentos" && <DocumentosAtencion atencion={atencion} editable />}
    </div>
  );
}

function LecturaAtencion({ atencion: a }: { atencion: Atencion }) {
  const { me, puede } = useAuth();
  const zona = me?.zonaHoraria ?? "America/Bogota";
  const queryClient = useQueryClient();
  const [nota, setNota] = useState("");
  const formulas = useQuery({
    queryKey: ["formulas", a.pacienteId],
    queryFn: async () => unwrap(await api.GET("/api/v1/formulacion/formulas", { params: { query: { pacienteId: a.pacienteId! } } })),
    enabled: puede("FORMULA_REDACTAR"),
  });
  const agregarNota = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/historia/atenciones/{id}/notas", { params: { path: { id: a.id! } }, body: { texto: nota } })),
    onSuccess: (actualizada) => {
      queryClient.setQueryData(["atencion", a.id], actualizada);
      setNota("");
    },
  });
  const signos = [
    a.taSistolica && a.taDiastolica && `TA ${a.taSistolica}/${a.taDiastolica} mmHg`,
    a.frecuenciaCardiaca && `FC ${a.frecuenciaCardiaca} lpm`, a.frecuenciaRespiratoria && `FR ${a.frecuenciaRespiratoria} rpm`,
    a.temperatura && `T ${a.temperatura} °C`, a.saturacionO2 && `SatO₂ ${a.saturacionO2}%`,
    a.pesoKg && `Peso ${a.pesoKg} kg`, a.tallaCm && `Talla ${a.tallaCm} cm`,
  ].filter(Boolean);
  const ligadas = (formulas.data ?? []).filter((f) => f.atencionId === a.id);

  return (
    <div className="stack">
      <div className="row between">
        <h1>Atención {a.estado === "FIRMADA" ? <span className="badge badge-ok">Firmada</span> : <span className="badge badge-warn">Borrador</span>}</h1>
        <span className="muted small">
          {a.medicoNombre}{a.registroMedico && ` · Reg. médico ${a.registroMedico}`} · {fechaHoraCorta(a.fechaAtencion!, zona)}
          {a.firmadaEn && ` · firmada ${fechaHoraCorta(a.firmadaEn, zona)}`}
        </span>
      </div>
      <div className="card stack lectura">
        <Seccion titulo="Motivo de consulta" texto={a.motivoConsulta} />
        <Seccion titulo="Enfermedad actual" texto={a.enfermedadActual} />
        <Seccion titulo="Antecedentes" texto={a.antecedentes} />
        <Seccion titulo="Revisión por sistemas" texto={a.revisionSistemas} />
        {!!signos.length && <Seccion titulo="Signos vitales" texto={signos.join(" · ")} />}
        <Seccion titulo="Examen físico" texto={a.examenFisico} />
        {!!a.diagnosticos?.length && (
          <div>
            <h3>Diagnósticos</h3>
            <ul>
              {a.diagnosticos.map((d) => (
                <li key={d.codigoCie10}><strong>{d.codigoCie10}</strong> {d.descripcion} · {TIPO_DX[d.tipo!]}{d.principal && <span className="badge">Principal</span>}</li>
              ))}
            </ul>
          </div>
        )}
        <Seccion titulo="Análisis" texto={a.analisis} />
        <Seccion titulo="Plan" texto={a.plan} />
      </div>

      {!!ligadas.length && (
        <div className="card">
          <h3>Fórmulas de esta atención</h3>
          <ul>{ligadas.map((f) => <li key={f.id}>{f.items?.map((i) => `${i.medicamento} ${i.concentracion ?? ""} — ${i.dosis} ${i.frecuencia} por ${i.duracion}`).join("; ")}</li>)}</ul>
        </div>
      )}

      <DocumentosAtencion atencion={a} editable={false} />

      {a.estado === "FIRMADA" && (
        <div className="card stack">
          <h3>Notas aclaratorias</h3>
          {!a.notas?.length && <p className="muted small">Sin notas. Una atención firmada no se edita: las correcciones se registran aquí.</p>}
          {a.notas?.map((n) => (
            <div key={n.id} className="nota">
              <div className="muted small">{n.autorNombre} · {fechaHoraCorta(n.creadaEn!, zona)}</div>
              <p>{n.texto}</p>
            </div>
          ))}
          {puede("HISTORIA_REGISTRAR") && (
            <>
              <textarea rows={2} value={nota} onChange={(e) => setNota(e.target.value)} placeholder="Escriba la aclaración o corrección…" />
              {agregarNota.error && <p className="alert alert-error">{agregarNota.error.message}</p>}
              <div><button className="btn btn-secondary" disabled={!nota.trim() || agregarNota.isPending} onClick={() => agregarNota.mutate()}>Agregar nota aclaratoria</button></div>
            </>
          )}
        </div>
      )}
    </div>
  );
}

function Seccion({ titulo, texto }: { titulo: string; texto?: string | null }) {
  if (!texto) return null;
  return (
    <div>
      <h3>{titulo}</h3>
      <p className="texto-clinico">{texto}</p>
    </div>
  );
}

function DocumentosAtencion({ atencion, editable }: { atencion: Atencion; editable: boolean }) {
  const docs = atencion.documentos ?? [];
  if (!editable && !docs.length) return null;
  return (
    <div className="card stack">
      <h3>Documentos de la atención</h3>
      {docs.map((d) => (
        <div key={d.id} className="row between">
          <span>{d.descripcion ?? d.nombreArchivo} <span className="muted small">· {TIPO_DOCUMENTO[d.tipo!]} · {tamano(d.tamanoBytes)}</span></span>
          <BotonDescargar id={d.id!} nombre={d.nombreArchivo!} />
        </div>
      ))}
      {editable && <SubirDocumento pacienteId={atencion.pacienteId!} atencionId={atencion.id} />}
    </div>
  );
}
