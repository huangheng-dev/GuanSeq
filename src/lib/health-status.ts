export type BackendHealthStatus = {
  service: "guanseq-server";
  status: "UP";
  version: string;
};

export function createWebHealthStatus(
  backend: BackendHealthStatus | null,
  version: string,
) {
  const ready = backend?.status === "UP";
  return {
    ready,
    payload: {
      service: "guanseq-web",
      status: ready ? "UP" : "DEGRADED",
      version,
      backend: backend ?? { service: "guanseq-server", status: "UNAVAILABLE" },
    },
  } as const;
}
