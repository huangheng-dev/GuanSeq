import { spawnSync } from "node:child_process";
import { resolve } from "node:path";
import { loadEnvFile } from "node:process";
import { fileURLToPath } from "node:url";

export const productionComposeFile = fileURLToPath(
  new URL("../compose.production.yaml", import.meta.url),
);

const SAFE_IDENTIFIER = /^[a-zA-Z][a-zA-Z0-9_]{0,62}$/;
let requestedEnvFile = null;

export function loadRequestedEnvFile(argumentsList = process.argv) {
  const index = argumentsList.indexOf("--env-file");
  if (index < 0) return null;
  if (!argumentsList[index + 1]) throw new Error("--env-file 后必须提供环境文件路径");
  requestedEnvFile = resolve(argumentsList[index + 1]);
  loadEnvFile(requestedEnvFile);
  return requestedEnvFile;
}

export function requireDatabaseIdentity(environment = process.env) {
  const database = environment.GUANSEQ_POSTGRES_DB || "guanseq";
  const username = environment.GUANSEQ_POSTGRES_USER || "guanseq";
  if (!SAFE_IDENTIFIER.test(database) || !SAFE_IDENTIFIER.test(username)) {
    throw new Error("数据库名称或账号不是安全标识符");
  }
  return { database, username };
}

export function runPostgresCommand(argumentsList, options = {}) {
  const result = spawnSync(
    "docker",
    [
      "compose",
      ...(requestedEnvFile ? ["--env-file", requestedEnvFile] : []),
      "-f", productionComposeFile,
      "exec", "-T", "postgres",
      ...argumentsList,
    ],
    {
      env: process.env,
      encoding: options.binary ? null : "utf8",
      input: options.input,
      maxBuffer: 1024 * 1024 * 1024,
      windowsHide: true,
    },
  );
  if (result.error || result.status !== 0) {
    throw new Error(options.failureMessage || "PostgreSQL 容器命令执行失败");
  }
  return result.stdout;
}

export function readSuccessfulMigrationCount(database, username) {
  const value = runPostgresCommand([
    "psql",
    "--username", username,
    "--dbname", database,
    "--tuples-only",
    "--no-align",
    "--command", "SELECT count(*) FROM flyway_schema_history WHERE success = true",
  ], { failureMessage: "无法读取 Flyway 迁移事实" });
  const count = Number(String(value).trim());
  if (!Number.isInteger(count) || count < 1) throw new Error("Flyway 迁移事实无效");
  return count;
}
