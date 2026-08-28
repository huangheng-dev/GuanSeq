import { createHash, randomBytes } from "node:crypto";
import { readFileSync } from "node:fs";
import { basename, resolve } from "node:path";

import {
  loadRequestedEnvFile,
  readSuccessfulMigrationCount,
  requireDatabaseIdentity,
  runPostgresCommand,
} from "./production-database-tools.mjs";

loadRequestedEnvFile();
const backupArgumentIndex = process.argv.indexOf("--backup");
if (backupArgumentIndex < 0 || !process.argv[backupArgumentIndex + 1]) {
  throw new Error("用法：pnpm production:restore-drill -- --backup <备份.dump>");
}

const dumpPath = resolve(process.argv[backupArgumentIndex + 1]);
if (!dumpPath.endsWith(".dump")) throw new Error("恢复演练只接受 .dump 备份文件");
const manifestPath = dumpPath.replace(/\.dump$/, ".manifest.json");
const archive = readFileSync(dumpPath);
const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
if (manifest.dumpFile !== basename(dumpPath)) throw new Error("备份清单与文件名不一致");
if (manifest.bytes !== archive.length) throw new Error("备份长度与清单不一致");
const sha256 = createHash("sha256").update(archive).digest("hex");
if (manifest.sha256 !== sha256) throw new Error("备份 SHA-256 校验失败");

const { username } = requireDatabaseIdentity();
const targetDatabase = `guanseq_restore_${Date.now()}_${randomBytes(3).toString("hex")}`;
let created = false;
try {
  runPostgresCommand([
    "createdb",
    "--username", username,
    "--template", "template0",
    "--encoding", "UTF8",
    targetDatabase,
  ], { failureMessage: "无法创建隔离恢复演练数据库" });
  created = true;
  runPostgresCommand([
    "pg_restore",
    "--exit-on-error",
    "--no-owner",
    "--no-acl",
    "--username", username,
    "--dbname", targetDatabase,
  ], { input: archive, binary: true, failureMessage: "隔离恢复演练失败" });
  const restoredMigrations = readSuccessfulMigrationCount(targetDatabase, username);
  if (restoredMigrations !== manifest.flywaySuccessfulMigrations) {
    throw new Error("恢复后的 Flyway 迁移数量与备份清单不一致");
  }
  console.log(`[PASS] 隔离恢复演练通过：${manifest.dumpFile}，Flyway ${restoredMigrations} 条`);
  console.log("[INFO] 原数据库未被覆盖；演练数据库正在清理");
} finally {
  if (created) {
    runPostgresCommand([
      "dropdb",
      "--force",
      "--if-exists",
      "--username", username,
      targetDatabase,
    ], { failureMessage: "演练数据库清理失败，请由数据库负责人核查" });
  }
}
