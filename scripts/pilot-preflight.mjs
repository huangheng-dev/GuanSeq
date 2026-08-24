import { inspectOidcProvider, validatePilotEnvironment } from "./pilot-validation.mjs";

const validation = validatePilotEnvironment(process.env);
const network = validation.errors.length === 0
  ? await inspectOidcProvider(validation.config)
  : { errors: [], warnings: [] };
const errors = [...validation.errors, ...network.errors];
const warnings = [...validation.warnings, ...network.warnings];

console.log("GuanSeq 试点生产配置预检");
for (const warning of warnings) console.log(`[WARN] ${warning}`);
for (const error of errors) console.error(`[FAIL] ${error}`);
if (errors.length === 0) console.log("[PASS] OIDC 配置、发现文档和公开签名密钥检查通过");
process.exitCode = errors.length === 0 ? 0 : 1;
