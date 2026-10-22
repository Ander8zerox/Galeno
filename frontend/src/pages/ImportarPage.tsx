import { Fragment, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, expectOk, subirArchivo, unwrap, type Schemas } from "../api/client";
import { SubirListado } from "../features/importacion/SubirListado";

type Lote = Schemas["LoteVista"];
type Registro = Schemas["RegistroVista"];
type EstadoRegistro = NonNullable<Registro["estado"]>;

const fechaHora = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium", timeStyle: "short" });
const TAMANO = 50;

const ESTADO_LOTE: Record<NonNullable<Lote["estado"]>, [string, string]> = {
  EN_REVISION: ["En revisión", "badge-warn"],
  CONFIRMADO: ["Importado", "badge-ok"],
  DESHECHO: ["Deshecho", ""],
  DESCARTADO: ["Descartado", ""],
};

const ESTADO_REGISTRO: Record<EstadoRegistro, [string, string]> = {
  NUEVO: ["Nuevo", "badge-ok"],
  EXISTENTE: ["Ya existe", ""],
  ERROR: ["Con error", "badge-danger"],
};

const ORIGEN: Record<NonNullable<Lote["origen"]>, string> = {
  RIPS_JSON: "RIPS",
  PACIENTES_LISTADO: "Listado de pacientes",
};

const CAMPO_CAMBIO: Record<string, string> = {
  nombres: "Nombres", apellidos: "Apellidos", telefono: "Teléfono", email: "Correo", datosIncompletos: "Datos incompletos",
};

const TIPO_SERVICIO: Record<string, string> = {
  consultas: "Consulta",
  procedimientos: "Procedimiento",
  urgencias: "Urgencias",
  hospitalizacion: "Hospitalización",
};

/** Lista de importaciones y carga de archivos RIPS o de listados de pacientes. */
export function ImportarPage() {
  const lotes = useQuery({
    queryKey: ["importaciones"],
    queryFn: async () => unwrap(await api.GET("/api/v1/importaciones", { params: { query: { size: 50 } } })),
  });

  return (
    <section className="stack">
      <div className="page-header"><h1>Importar historias</h1></div>
      <SubirRips />
      <SubirListado />
      <div className="stack">
        <h3>Importaciones</h3>
        {lotes.isLoading && <p className="muted">Cargando…</p>}
        {lotes.error && <p className="alert alert-error">{lotes.error.message}</p>}
        {lotes.data && !lotes.data.items?.length && <p className="muted">Todavía no hay importaciones.</p>}
        {!!lotes.data?.items?.length && (
          <div className="card table-wrap">
            <table>
              <thead><tr><th>Fecha</th><th>Origen</th><th>Estado</th><th>Pacientes</th><th>Atenciones</th><th>Errores</th><th /></tr></thead>
              <tbody>
                {lotes.data.items.map((l) => (
                  <tr key={l.id}>
                    <td>{fechaHora.format(new Date(l.creadoEn!))}</td>
                    <td>{ORIGEN[l.origen!]}</td>
                    <td><EstadoLote estado={l.estado!} /></td>
                    <td>{l.pacientes}</td>
                    <td>{l.origen === "RIPS_JSON" ? l.atenciones : "—"}</td>
                    <td>{l.errores ? <span className="badge badge-danger">{l.errores}</span> : 0}</td>
                    <td><Link to={`/importar/${l.id}`}>{l.estado === "EN_REVISION" ? "Revisar" : "Ver"}</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}

function EstadoLote({ estado }: { estado: NonNullable<Lote["estado"]> }) {
  const [texto, clase] = ESTADO_LOTE[estado];
  return <span className={`badge ${clase}`}>{texto}</span>;
}

function SubirRips() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [archivos, setArchivos] = useState<File[]>([]);

  const subir = useMutation({
    mutationFn: async () => {
      const datos = new FormData();
      archivos.forEach((a) => datos.append("archivos", a));
      return subirArchivo<Lote>("/api/v1/importaciones/rips", datos);
    },
    onSuccess: (lote) => {
      queryClient.invalidateQueries({ queryKey: ["importaciones"] });
      navigate(`/importar/${lote.id}`);
    },
  });

  return (
    <div className="card stack">
      <h3>Subir RIPS</h3>
      <p className="muted small">
        Los archivos JSON de RIPS que su sistema anterior generó con cada factura electrónica: varios a la vez o un ZIP
        con todos (hasta 50 MB). Antes de importar podrá revisar cada paciente; nada entra a la historia hasta que confirme.
      </p>
      <div className="row">
        <input ref={input} type="file" accept=".json,.zip,application/json,application/zip" multiple
          onChange={(e) => setArchivos(Array.from(e.target.files ?? []))} />
        <button className="btn" disabled={!archivos.length || subir.isPending} onClick={() => subir.mutate()}>
          {subir.isPending ? "Leyendo archivos…" : "Revisar archivos"}
        </button>
      </div>
      {archivos.length > 1 && <small className="muted">{archivos.length} archivos seleccionados</small>}
      {subir.error && <p className="alert alert-error">{subir.error.message}</p>}
    </div>
  );
}

/** Revisión de un lote: qué pacientes y atenciones trae, cuáles incluir, y confirmar o deshacer. */
export function LoteImportacionPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [estado, setEstado] = useState<EstadoRegistro | undefined>();
  const [page, setPage] = useState(0);

  const clave = ["importacion", id, estado, page];
  const detalle = useQuery({
    queryKey: clave,
    queryFn: async () => unwrap(await api.GET("/api/v1/importaciones/{id}", {
      params: { path: { id: id! }, query: { estado, page, size: TAMANO } },
    })),
  });
  const refrescar = () => {
    queryClient.invalidateQueries({ queryKey: ["importacion", id] });
    queryClient.invalidateQueries({ queryKey: ["importaciones"] });
  };

  const incluirTodos = useMutation({
    mutationFn: async (incluido: boolean) =>
      unwrap(await api.PUT("/api/v1/importaciones/{id}/registros", { params: { path: { id: id! } }, body: { incluido } })),
    onSuccess: refrescar,
  });
  const confirmar = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/importaciones/{id}/confirmar", { params: { path: { id: id! } } })),
    onSuccess: () => {
      refrescar();
      queryClient.invalidateQueries({ queryKey: ["pacientes"] });
    },
  });
  const deshacer = useMutation({
    mutationFn: async () => unwrap(await api.POST("/api/v1/importaciones/{id}/deshacer", { params: { path: { id: id! } } })),
    onSuccess: () => {
      refrescar();
      queryClient.invalidateQueries({ queryKey: ["pacientes"] });
    },
  });
  const descartar = useMutation({
    mutationFn: async () => expectOk(await api.DELETE("/api/v1/importaciones/{id}", { params: { path: { id: id! } } })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["importaciones"] });
      navigate("/importar");
    },
  });

  if (detalle.isLoading) return <p className="muted">Cargando…</p>;
  if (detalle.error) return <p className="alert alert-error">{detalle.error.message}</p>;
  const lote = detalle.data!.lote!;
  const registros = detalle.data!.registros!;
  const enRevision = lote.estado === "EN_REVISION";
  const listado = lote.origen === "PACIENTES_LISTADO";
  const error = incluirTodos.error ?? confirmar.error ?? deshacer.error ?? descartar.error;
  const total = registros.total ?? 0;

  return (
    <section className="stack">
      <Link to="/importar" className="back">← Importaciones</Link>
      <div className="page-header between">
        <h1>{ORIGEN[lote.origen!]} del {fechaHora.format(new Date(lote.creadoEn!))}</h1>
        <EstadoLote estado={lote.estado!} />
      </div>

      <div className="card stack">
        <div className="row">
          {listado ? <Cifra valor={lote.pacientes} texto="pacientes en el archivo" /> : (
            <>
              <Cifra valor={lote.archivos} texto="archivos" />
              <Cifra valor={lote.facturas} texto="facturas" />
              <Cifra valor={lote.pacientes} texto="pacientes" />
              <Cifra valor={lote.atenciones} texto="atenciones" />
            </>
          )}
          {!!lote.errores && <Cifra valor={lote.errores} texto="con error" />}
        </div>
        {!!lote.prestadores?.length && <small className="muted">Prestador (NIT): {lote.prestadores.join(", ")}</small>}
        {!!lote.omitidos && listado && <small className="muted">{lote.omitidos} filas vacías no se tienen en cuenta.</small>}
        {!!lote.omitidos && !listado && (
          <small className="muted">
            {lote.omitidos === 1 ? "1 servicio" : `${lote.omitidos} servicios`} de medicamentos, recién nacidos u otros
            no se importan en esta versión.
          </small>
        )}
        {!!lote.erroresArchivo?.length && (
          <div className="alert alert-error">
            <strong>Archivos que no se pudieron leer:</strong>
            <ul>{lote.erroresArchivo.map((e) => <li key={e}>{e}</li>)}</ul>
          </div>
        )}
        <Resultado lote={lote} />
        {error && <p className="alert alert-error">{error.message}</p>}

        {enRevision && (
          <div className="row">
            <button className="btn" disabled={!lote.incluidos || confirmar.isPending} onClick={() => {
              if (confirm(listado ? `Se completarán o crearán ${lote.incluidos} pacientes. ¿Continuar?`
                : `Se importarán ${lote.incluidos} pacientes a la historia clínica. ¿Continuar?`)) confirmar.mutate();
            }}>
              {confirmar.isPending ? "Importando…" : `Importar ${lote.incluidos} pacientes`}
            </button>
            <button className="btn btn-secondary" disabled={incluirTodos.isPending} onClick={() => incluirTodos.mutate(true)}>Incluir todos</button>
            <button className="btn btn-secondary" disabled={incluirTodos.isPending} onClick={() => incluirTodos.mutate(false)}>Quitar todos</button>
            <span className="spacer" />
            <button className="btn-link danger" disabled={descartar.isPending} onClick={() => {
              if (confirm("¿Descartar esta importación? No se importará nada.")) descartar.mutate();
            }}>Descartar</button>
          </div>
        )}
        {lote.estado === "CONFIRMADO" && (
          <div className="row">
            <button className="btn btn-secondary" disabled={deshacer.isPending} onClick={() => {
              if (confirm(listado
                ? "Los pacientes completados vuelven a sus datos anteriores y se borran los creados que no tengan otra actividad. ¿Deshacer?"
                : "Se quitarán las atenciones importadas y los pacientes creados que no tengan otra actividad. ¿Deshacer?")) deshacer.mutate();
            }}>{deshacer.isPending ? "Deshaciendo…" : "Deshacer importación"}</button>
            <small className="muted">{listado
              ? "Los datos que alguien haya editado después de importar se conservan."
              : "Solo es posible mientras nadie haya agregado notas o fórmulas a lo importado."}</small>
          </div>
        )}
      </div>

      <nav className="tabs">
        {([undefined, "NUEVO", "EXISTENTE", "ERROR"] as const).map((e) => (
          <button key={e ?? "todos"} className={estado === e ? "active" : ""}
            onClick={() => { setEstado(e); setPage(0); }}>
            {e ? ESTADO_REGISTRO[e][0] : "Todos"}
          </button>
        ))}
      </nav>

      {lote.estado === "DESCARTADO" ? <p className="muted">Los registros de una importación descartada se eliminan.</p> : (
        <div className="card table-wrap">
          <table>
            {listado ? (
              <>
                <thead><tr>{enRevision && <th>Importar</th>}<th>Fila</th><th>Documento</th><th>Estado</th><th>En el listado</th><th>{enRevision ? "Qué cambia" : "Qué cambió"}</th><th>Observaciones</th></tr></thead>
                <tbody>
                  {registros.items?.map((r) => <FilaListado key={r.id} loteId={id!} registro={r} editable={enRevision} onCambio={refrescar} />)}
                </tbody>
              </>
            ) : (
              <>
                <thead><tr>{enRevision && <th>Importar</th>}<th>Documento</th><th>Estado</th><th>Nacimiento</th><th>Sexo</th><th>Atenciones</th><th>Observaciones</th></tr></thead>
                <tbody>
                  {registros.items?.map((r) => <FilaRegistro key={r.id} loteId={id!} registro={r} editable={enRevision} onCambio={refrescar} />)}
                </tbody>
              </>
            )}
          </table>
          {!registros.items?.length && <p className="muted">No hay registros con ese estado.</p>}
        </div>
      )}

      {total > TAMANO && (
        <div className="row">
          <button className="btn btn-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button>
          <span className="muted">Página {page + 1} de {Math.ceil(total / TAMANO)}</span>
          <button className="btn btn-secondary" disabled={(page + 1) * TAMANO >= total} onClick={() => setPage(page + 1)}>Siguiente</button>
        </div>
      )}
    </section>
  );
}

function Cifra({ valor, texto }: { valor?: number; texto: string }) {
  return <div className="cifra"><strong>{valor ?? 0}</strong><small className="muted">{texto}</small></div>;
}

function Resultado({ lote }: { lote: Lote }) {
  const r = lote.resultado;
  if (!r) return null;
  if (lote.origen === "PACIENTES_LISTADO") {
    return lote.estado === "DESHECHO" ? (
      <p className="alert">
        Deshecha el {fechaHora.format(new Date(lote.deshechoEn!))}: {r.pacientesRestaurados} pacientes volvieron a sus datos
        anteriores y se borraron {r.pacientesEliminados} creados por la importación
        {!!r.pacientesConservados && ` (${r.pacientesConservados} se conservaron porque ya tienen otra actividad)`}.
      </p>
    ) : (
      <p className="alert">
        Importada el {fechaHora.format(new Date(lote.confirmadoEn!))}: {r.pacientesActualizados} pacientes completados
        y {r.pacientesCreados} nuevos.
      </p>
    );
  }
  if (lote.estado === "DESHECHO") {
    return (
      <p className="alert">
        Deshecha el {fechaHora.format(new Date(lote.deshechoEn!))}: se quitaron {r.atencionesEliminadas} atenciones y{" "}
        {r.pacientesEliminados} pacientes creados por la importación
        {!!r.pacientesConservados && ` (${r.pacientesConservados} se conservaron porque ya tienen otra actividad)`}.
      </p>
    );
  }
  return (
    <p className="alert">
      Importada el {fechaHora.format(new Date(lote.confirmadoEn!))}: {r.atencionesCreadas} atenciones, {r.pacientesCreados} pacientes
      nuevos y {r.pacientesActualizados} existentes.
      {!!r.atencionesYaImportadas && ` ${r.atencionesYaImportadas} atenciones ya se habían importado antes y no se duplicaron.`}
      {!!r.pacientesCreados && " Los pacientes nuevos quedan con datos incompletos: el RIPS no trae nombres."}
    </p>
  );
}

/** Un paciente de un listado: lo que trae el archivo y qué cambiaría en Galeno. */
function FilaListado({ loteId, registro: r, editable, onCambio }: { loteId: string; registro: Registro; editable: boolean; onCambio: () => void }) {
  const incluir = useMutation({
    mutationFn: async (incluido: boolean) => unwrap(await api.PUT("/api/v1/importaciones/{id}/registros/{registroId}", {
      params: { path: { id: loteId, registroId: r.id! } }, body: { incluido },
    })),
    onSuccess: onCambio,
  });
  const [texto, clase] = ESTADO_REGISTRO[r.estado!];
  const p = r.persona;
  const cambios = (r.cambios ?? []).filter((c) => c.campo !== "datosIncompletos");

  return (
    <tr>
      {editable && (
        <td><input type="checkbox" aria-label={`Importar fila ${r.fila}`}
          checked={!!r.incluido} disabled={r.estado === "ERROR" || incluir.isPending}
          onChange={(e) => incluir.mutate(e.target.checked)} /></td>
      )}
      <td>{r.fila}</td>
      <td>
        {r.numeroDocumento?.startsWith("fila ") ? "—" : `${r.tipoDocumento} ${r.numeroDocumento}`}
        {r.pacienteId && <><br /><Link to={`/pacientes/${r.pacienteId}`} className="small">{r.nombreActual ?? "Ver paciente"}</Link></>}
      </td>
      <td><span className={`badge ${clase}`}>{texto}</span></td>
      <td>
        {p && <>
          <div>{[p.nombres, p.apellidos].filter(Boolean).join(" ") || <span className="muted">Sin nombre</span>}</div>
          <div className="muted small">{[p.fechaNacimiento, p.sexo, p.telefono, p.email].filter(Boolean).join(" · ")}</div>
        </>}
      </td>
      <td>
        {r.estado === "NUEVO" && <span className="small">Se crea el paciente</span>}
        {cambios.map((c) => (
          <div key={c.campo} className="small">
            {CAMPO_CAMBIO[c.campo!] ?? c.campo}: {c.antes ? <><s className="muted">{c.antes}</s> → </> : ""}{c.despues}
          </div>
        ))}
      </td>
      <td>
        {r.mensajes?.map((m, i) => (
          <div key={i} className={m.nivel === "ERROR" ? "field-error" : "muted small"}>{m.texto}</div>
        ))}
        {incluir.error && <div className="field-error">{incluir.error.message}</div>}
      </td>
    </tr>
  );
}

function FilaRegistro({ loteId, registro: r, editable, onCambio }: { loteId: string; registro: Registro; editable: boolean; onCambio: () => void }) {
  const [abierta, setAbierta] = useState(false);
  const incluir = useMutation({
    mutationFn: async (incluido: boolean) => unwrap(await api.PUT("/api/v1/importaciones/{id}/registros/{registroId}", {
      params: { path: { id: loteId, registroId: r.id! } }, body: { incluido },
    })),
    onSuccess: onCambio,
  });
  const [texto, clase] = ESTADO_REGISTRO[r.estado!];
  const columnas = editable ? 7 : 6;

  return (
    <Fragment>
      <tr>
        {editable && (
          <td><input type="checkbox" aria-label={`Importar ${r.tipoDocumento} ${r.numeroDocumento}`}
            checked={!!r.incluido} disabled={r.estado === "ERROR" || incluir.isPending}
            onChange={(e) => incluir.mutate(e.target.checked)} /></td>
        )}
        <td>
          {r.tipoDocumento} {r.numeroDocumento}
          {r.pacienteId && <><br /><Link to={`/pacientes/${r.pacienteId}`} className="small">{r.nombreActual ?? "Ver paciente"}</Link></>}
        </td>
        <td><span className={`badge ${clase}`}>{texto}</span></td>
        <td>{r.fechaNacimiento ?? "—"}</td>
        <td>{r.sexo ?? "—"}</td>
        <td>
          {r.totalAtenciones}
          {!!r.atenciones?.length && <> · <button className="btn-link" onClick={() => setAbierta(!abierta)}>{abierta ? "ocultar" : "ver"}</button></>}
        </td>
        <td>
          {r.mensajes?.map((m, i) => (
            <div key={i} className={m.nivel === "ERROR" ? "field-error" : "muted small"}>{m.texto}</div>
          ))}
          {incluir.error && <div className="field-error">{incluir.error.message}</div>}
        </td>
      </tr>
      {abierta && (
        <tr>
          <td colSpan={columnas}>
            <ul className="stack lista-compacta">
              {r.atenciones?.map((a) => (
                <li key={a.clave}>
                  <strong>{a.fecha?.replace("T", " ").slice(0, 16)}</strong> · {TIPO_SERVICIO[a.tipo!] ?? a.tipo}
                  {a.codigoCups && <> {a.codigoCups}{a.nombreCups && ` ${a.nombreCups}`}</>}
                  {a.diagnosticos?.map((d) => (
                    <div key={d.codigo} className="small muted">
                      {d.principal ? "Dx principal" : "Relacionado"}: {d.codigo} {d.descripcion}
                    </div>
                  ))}
                </li>
              ))}
            </ul>
          </td>
        </tr>
      )}
    </Fragment>
  );
}
