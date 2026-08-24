import { describe, expect, it, vi } from "vitest";

import { inspectOidcProvider, PILOT_SMOKE_ENDPOINTS, runPilotSmoke, validatePilotEnvironment } from "./pilot-validation.mjs";

const validEnvironment = {
  GUANSEQ_SECURITY_MODE: "oidc",
  GUANSEQ_API_BASE_URL: "http://guanseq-server:8080",
  GUANSEQ_OIDC_ISSUER_URI: "https://identity.example.com/realms/guanseq",
  GUANSEQ_OIDC_CLIENT_ID: "guanseq-web",
  GUANSEQ_OIDC_CLIENT_SECRET: "client-secret-with-sufficient-entropy",
  GUANSEQ_OIDC_CLIENT_AUTH_METHOD: "client_secret_basic",
  GUANSEQ_OIDC_REDIRECT_URI: "https://guanseq.example.com/api/auth/callback",
  GUANSEQ_OIDC_POST_LOGOUT_REDIRECT_URI: "https://guanseq.example.com/login?signedOut=1",
  GUANSEQ_OIDC_JWK_SET_URI: "https://identity.example.com/realms/guanseq/certs",
  GUANSEQ_OIDC_AUDIENCE: "guanseq-api",
  GUANSEQ_OIDC_RESOURCE: "guanseq-api",
  GUANSEQ_SESSION_SECRET: Buffer.alloc(32, 9).toString("base64"),
};

describe("pilot environment validation", () => {
  it("accepts a complete OIDC production contract without exposing secrets", () => {
    const result = validatePilotEnvironment(validEnvironment);
    expect(result.errors).toEqual([]);
    expect(JSON.stringify(result)).not.toContain(validEnvironment.GUANSEQ_OIDC_CLIENT_SECRET);
    expect(JSON.stringify(result)).not.toContain(validEnvironment.GUANSEQ_SESSION_SECRET);
  });

  it("rejects unsafe identity, callback, session and bootstrap settings", () => {
    const result = validatePilotEnvironment({
      ...validEnvironment,
      GUANSEQ_SECURITY_MODE: "development",
      GUANSEQ_OIDC_ISSUER_URI: "http://identity.example.com",
      GUANSEQ_OIDC_REDIRECT_URI: "https://guanseq.example.com/wrong-callback",
      GUANSEQ_SESSION_SECRET: "short",
      GUANSEQ_BOOTSTRAP_ENABLED: "true",
      GUANSEQ_BOOTSTRAP_TOKEN: validEnvironment.GUANSEQ_OIDC_CLIENT_SECRET,
    });
    expect(result.errors).toEqual(expect.arrayContaining([
      expect.stringContaining("GUANSEQ_SECURITY_MODE"),
      expect.stringContaining("GUANSEQ_OIDC_ISSUER_URI"),
      expect.stringContaining("/api/auth/callback"),
      expect.stringContaining("32 字节"),
      expect.stringContaining("不能复用"),
    ]));
  });
});

describe("pilot connectivity checks", () => {
  it("validates OIDC discovery and signing keys", async () => {
    const fetchImplementation = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        issuer: validEnvironment.GUANSEQ_OIDC_ISSUER_URI,
        authorization_endpoint: "https://identity.example.com/authorize",
        token_endpoint: "https://identity.example.com/token",
        jwks_uri: validEnvironment.GUANSEQ_OIDC_JWK_SET_URI,
        response_types_supported: ["code"],
        grant_types_supported: ["authorization_code"],
        code_challenge_methods_supported: ["S256"],
        token_endpoint_auth_methods_supported: ["client_secret_basic"],
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ keys: [{ kty: "RSA", use: "sig", kid: "key-1", n: "x", e: "AQAB" }] }), { status: 200 }));
    const validation = validatePilotEnvironment(validEnvironment);

    expect(await inspectOidcProvider(validation.config, fetchImplementation)).toEqual({ errors: [], warnings: [] });
  });

  it("runs authenticated read-only smoke checks without logging the token", async () => {
    const fetchImplementation = vi.fn(async (url, init) => {
      const requestId = init.headers["X-Request-Id"];
      const body = url.pathname === "/api/v1/me/workspaces" ? { username: "pilot.admin" } : {};
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { "Content-Type": "application/json", "X-Request-Id": requestId },
      });
    });
    const results = await runPilotSmoke({
      apiBase: new URL("https://api.example.com"),
      accessToken: "sensitive-access-token",
      expectedUsername: "pilot.admin",
      fetchImplementation,
    });

    expect(results.every((result) => result.passed)).toBe(true);
    expect(PILOT_SMOKE_ENDPOINTS).toHaveLength(13);
    expect(PILOT_SMOKE_ENDPOINTS).toContainEqual([
      "工作区成员",
      "/api/v1/identity/workspace-users?page=0&size=1&status=ALL",
      true,
    ]);
    expect(JSON.stringify(results)).not.toContain("sensitive-access-token");
  });
});
