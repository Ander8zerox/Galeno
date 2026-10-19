import { useQuery } from "@tanstack/react-query";
import { api, unwrap } from "../api/client";

const TERMINAL = new Set(["COMPLETADO", "FALLIDO"]);

/** Consulta el trabajo de IA cada 1,5 s hasta que termine. Con SSE o WebSockets solo cambiaría este hook. */
export function useAiJob<T>(jobId: string | undefined) {
  const query = useQuery({
    queryKey: ["ai-job", jobId],
    enabled: !!jobId,
    queryFn: async () => unwrap(await api.GET("/api/v1/ai-jobs/{id}", { params: { path: { id: jobId! } } })),
    refetchInterval: (q) => (TERMINAL.has(q.state.data?.estado ?? "") ? false : 1500),
  });
  const estado = query.data?.estado;
  return {
    enCurso: !!jobId && !TERMINAL.has(estado ?? ""),
    resultado: estado === "COMPLETADO" ? (query.data?.resultado as T) : undefined,
    error: estado === "FALLIDO" ? (query.data?.error ?? "La IA no pudo completar la solicitud") : query.error?.message,
  };
}
