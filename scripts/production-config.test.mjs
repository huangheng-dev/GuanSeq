import { randomBytes } from "node:crypto";

import { describe, expect, it } from "vitest";

import { validateProductionEnvironment } from "./production-config.mjs";

function validEnvironment() {
  return {
    GUANSEQ_VERSION: "0.1.0-rc.1",
    GUANSEQ_PUBLIC_ORIGIN: "https://guanseq.factory.test",
    GUANSEQ_HTTP_BIND_ADDRESS: "127.0.0.1",
    GUANSEQ_TLS_TERMINATION_CONFIRMED: "true",
    GUANSEQ_POSTGRES_DB: "guanseq",
    GUANSEQ_POSTGRES_USER: "guanseq",
    GUANSEQ_POSTGRES_VOLUME: "guanseq-postgres-data",
    GUANSEQ_DB_PASSWORD: "db-secret-abcdefghijklmnopqrstuvwxyz",
    GUANSEQ_OIDC_ISSUER_URI: "https://id.factory.test/realms/guanseq",
    GUANSEQ_OIDC_JWK_SET_URI: "https://id.factory.test/realms/guanseq/certs",
    GUANSEQ_OIDC_AUDIENCE: "guanseq-api",
    GUANSEQ_OIDC_CLIENT_ID: "guanseq-web",
    GUANSEQ_OIDC_CLIENT_SECRET: "oidc-secret-abcdefghijklmnopqrstuvwxyz",
    GUANSEQ_OIDC_REDIRECT_URI: "https://guanseq.factory.test/api/auth/callback",
    GUANSEQ_OIDC_POST_LOGOUT_REDIRECT_URI: "https://guanseq.factory.test/login?signedOut=1",
    GUANSEQ_SESSION_SECRET: randomBytes(32).toString("base64"),
    GUANSEQ_BACKUP_RETENTION_DAYS: "30",
    GUANSEQ_RPO_HOURS: "24",
    GUANSEQ_RTO_HOURS: "4",
    GUANSEQ_BOOTSTRAP_ENABLED: "false",
    GUANSEQ_BOOTSTRAP_TOKEN: "disabled",
  };
}

describe("production deployment configuration", () => {
  it("accepts an explicit OIDC, TLS, backup and recovery boundary", () => {
    expect(validateProductionEnvironment(validEnvironment())).toEqual({ errors: [], warnings: [] });
  });

  it("rejects placeholders, development exposure and reused secrets", () => {
    const environment = validEnvironment();
    environment.GUANSEQ_SECURITY_MODE = "development";
    environment.GUANSEQ_HTTP_BIND_ADDRESS = "0.0.0.0";
    environment.GUANSEQ_TLS_TERMINATION_CONFIRMED = "false";
    environment.GUANSEQ_DB_PASSWORD = "replace-with-password";
    environment.GUANSEQ_OIDC_CLIENT_SECRET = environment.GUANSEQ_SESSION_SECRET;

    const result = validateProductionEnvironment(environment);
    expect(result.errors).toEqual(expect.arrayContaining([
      "正式编排只允许 GUANSEQ_SECURITY_MODE=oidc",
      "GUANSEQ_DB_PASSWORD 长度至少为 24 个字符",
      "GUANSEQ_DB_PASSWORD 仍是示例占位值",
      "数据库、OIDC 客户端和会话密钥必须彼此独立",
      "非回环监听必须设置 GUANSEQ_TLS_TERMINATION_CONFIRMED=true 并由外部 TLS 入口保护",
      "必须确认外部 TLS 终止边界：GUANSEQ_TLS_TERMINATION_CONFIRMED=true",
    ]));
  });

  it("rejects an unsafe bootstrap token and mismatched public callbacks", () => {
    const environment = validEnvironment();
    environment.GUANSEQ_BOOTSTRAP_ENABLED = "true";
    environment.GUANSEQ_BOOTSTRAP_TOKEN = "short";
    environment.GUANSEQ_OIDC_REDIRECT_URI = "https://wrong.factory.test/api/auth/callback";

    const result = validateProductionEnvironment(environment);
    expect(result.errors).toContain("OIDC 回调来源必须与 GUANSEQ_PUBLIC_ORIGIN 一致");
    expect(result.errors).toContain("GUANSEQ_BOOTSTRAP_TOKEN 长度至少为 32 个字符");
  });
});
