import { createHash } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

import {
  loadRequestedEnvFile,
  readSuccessfulMigrationCount,
  requireDatabaseIdentity,
  runPostgresCommand,
} from "./production-database-tools.mjs";

loadRequestedEnvFile();
const { database, username } = requireDatabaseIdentity();
const backupDirectory = resolve(process.env.GUANSEQ_BACKUP_DIRECTORY || "backups");
mkdirSync(backupDirectory, { recursive: true, mode: 0o700 });

const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
const baseName = `guanseq-${database}-${timestamp}`;
const dumpPath = resolve(backupDirectory, `${baseName}.dump`);
const manifestPath = resolve(backupDirectory, `${baseName}.manifest.json`);
const migrationCount = readSuccessfulMigrationCount(database, username);
const archive = runPostgresCommand([
  "pg_dump",
  "--format=custom",
  "--compress=6",
  "--no-owner",
  "--no-acl",
  "--username", username,
  "--dbname", database,
], { binary: true, failureMessage: "数据库备份失败" });

if (!Buffer.isBuffer(archive) || archive.length < 1024) {
  throw new Error("数据库备份产物异常，拒绝写入清单");
}
const sha256 = createHash("sha256").update(archive).digest("hex");
writeFileSync(dumpPath, archive, { flag: "wx", mode: 0o600 });
writeFileSync(manifestPath, `${JSON.stringify({
  schemaVersion: 1,
  service: "guanseq-server",
  applicationVersion: process.env.GUANSEQ_VERSION || "unknown",
  database,
  createdAt: new Date().toISOString(),
  flywaySuccessfulMigrations: migrationCount,
  bytes: archive.length,
  sha256,
  dumpFile: `${baseName}.dump`,
}, null, 2)}\n`, { flag: "wx", mode: 0o600 });

console.log(`[PASS] 备份与校验清单已生成：${dumpPath}`);
console.log(`[NEXT] 将 .dump 与 .manifest.json 加密复制到独立故障域，并运行 production:restore-drill`);
