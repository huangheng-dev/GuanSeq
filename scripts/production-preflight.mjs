import { spawnSync } from "node:child_process";
import { resolve } from "node:path";
import { loadEnvFile } from "node:process";
import { fileURLToPath } from "node:url";

import { validateProductionEnvironment } from "./production-config.mjs";

const envFileIndex = process.argv.indexOf("--env-file");
const envFile = envFileIndex >= 0 && process.argv[envFileIndex + 1]
  ? resolve(process.argv[envFileIndex + 1])
  : null;
if (envFileIndex >= 0 && !envFile) throw new Error("--env-file 后必须提供环境文件路径");
if (envFile) loadEnvFile(envFile);

const result = validateProductionEnvironment(process.env);
console.log("GuanSeq 生产部署离线预检");
for (const warning of result.warnings) console.log(`[WARN] ${warning}`);
for (const error of result.errors) console.error(`[FAIL] ${error}`);

if (result.errors.length === 0) {
  const composeFile = fileURLToPath(new URL("../compose.production.yaml", import.meta.url));
  const docker = spawnSync(
    "docker",
    ["compose", ...(envFile ? ["--env-file", envFile] : []), "-f", composeFile, "config", "--quiet"],
    { env: process.env, encoding: "utf8", windowsHide: true },
  );
  if (docker.error) {
    console.error("[FAIL] Docker Compose 不可用，无法验证生产编排");
    result.errors.push("docker-unavailable");
  } else if (docker.status !== 0) {
    console.error("[FAIL] 生产 Compose 配置解析失败；请检查必需变量和编排语法");
    result.errors.push("compose-invalid");
  } else {
    console.log("[PASS] 生产配置边界和 Compose 解析通过");
    console.log("[NEXT] 在受控网络节点继续运行 pnpm pilot:preflight 验证 OIDC discovery 与 JWK");
  }
}

process.exitCode = result.errors.length === 0 ? 0 : 1;
