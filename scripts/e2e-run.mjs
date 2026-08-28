import { spawnSync } from "node:child_process";
import { resolve } from "node:path";
import process from "node:process";

const composeFile = "compose.e2e.yaml";
const useExistingServer = process.env.GUANSEQ_E2E_USE_EXISTING_SERVER === "true";
const docker = process.platform === "win32" ? "docker.exe" : "docker";
const playwrightCli = resolve("node_modules/@playwright/test/cli.js");

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: process.cwd(),
    env: process.env,
    stdio: "inherit",
    ...options,
  });
  if (result.error) throw result.error;
  return result.status ?? 1;
}

let exitCode = 1;
const environmentManaged = !useExistingServer;

try {
  if (!useExistingServer) {
    const upStatus = run(docker, [
      "compose", "--file", composeFile, "up", "--detach", "--build", "--wait", "--wait-timeout", "420",
    ]);
    if (upStatus !== 0) throw new Error(`E2E 环境启动失败，退出码 ${upStatus}`);
  }

  exitCode = run(process.execPath, [playwrightCli, "test"], {
    env: {
      ...process.env,
      PLAYWRIGHT_BASE_URL: process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3100",
    },
  });
} catch (error) {
  console.error(error instanceof Error ? error.message : error);
  exitCode = 1;
} finally {
  if (environmentManaged) {
    const downStatus = run(docker, ["compose", "--file", composeFile, "down", "--remove-orphans"]);
    if (downStatus !== 0 && exitCode === 0) exitCode = downStatus;
  }
}

process.exitCode = exitCode;
