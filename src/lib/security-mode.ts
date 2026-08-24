export type GuanSeqSecurityMode = "disabled" | "development" | "oidc";

export function getSecurityMode(environment: NodeJS.ProcessEnv = process.env): GuanSeqSecurityMode {
  const configured = environment.GUANSEQ_SECURITY_MODE ?? "disabled";
  if (configured === "disabled" || configured === "development" || configured === "oidc") {
    return configured;
  }
  throw new Error(
    `不支持的 GUANSEQ_SECURITY_MODE=${configured}，只能使用 disabled、development 或 oidc。`,
  );
}
