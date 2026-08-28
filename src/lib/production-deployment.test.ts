import { readFileSync } from "node:fs";

import { describe, expect, it } from "vitest";

const rootFile = (name: string) =>
  readFileSync(new URL(`../../${name}`, import.meta.url), "utf8");

describe("production deployment release gates", () => {
  it("keeps the minimum runtime isolated and fail-closed", () => {
    const compose = rootFile("compose.production.yaml");
    expect(compose).toContain("GUANSEQ_DB_PASSWORD:?");
    expect(compose).toContain("GUANSEQ_SECURITY_MODE: oidc");
    expect(compose).toContain("127.0.0.1");
    expect(compose).toContain("read_only: true");
    expect(compose).toContain("no-new-privileges:true");
    expect(compose).not.toMatch(/redis:|rabbitmq:|emqx:|minio:/i);
  });

  it("builds non-root images with real readiness probes", () => {
    const web = rootFile("Dockerfile");
    const server = rootFile("guanseq-server/Dockerfile");
    expect(web).toContain("USER guanseq");
    expect(web).toContain("/api/health");
    expect(server).toContain("USER guanseq");
    expect(server).toContain("/api/v1/platform/status");
  });

  it("generates SBOMs and blocks severe source and image findings", () => {
    const workflow = rootFile(".github/workflows/release-security.yml");
    expect(workflow.match(/anchore\/sbom-action@v0/g)).toHaveLength(2);
    expect(workflow.match(/aquasecurity\/trivy-action@v0\.36\.0/g)).toHaveLength(3);
    expect(workflow).toContain("severity: HIGH,CRITICAL");
    expect(workflow).toContain('exit-code: "1"');
  });
});
