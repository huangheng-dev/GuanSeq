import { runPilotSmoke } from "./pilot-validation.mjs";

const apiBaseValue = process.env.GUANSEQ_API_BASE_URL;
const accessToken = process.env.GUANSEQ_PILOT_ACCESS_TOKEN;
if (!apiBaseValue || !accessToken) {
  console.error("[FAIL] 必须设置 GUANSEQ_API_BASE_URL 和 GUANSEQ_PILOT_ACCESS_TOKEN");
  process.exitCode = 1;
} else {
  const results = await runPilotSmoke({
    apiBase: new URL(apiBaseValue),
    accessToken,
    expectedUsername: process.env.GUANSEQ_PILOT_EXPECTED_USERNAME,
  });
  console.log("GuanSeq 试点主闭环只读烟雾验收");
  for (const result of results) {
    console.log(`[${result.passed ? "PASS" : "FAIL"}] ${result.name} · ${result.detail} · ${result.durationMs}ms`);
  }
  const failures = results.filter((result) => !result.passed).length;
  console.log(`结果：${results.length - failures}/${results.length} 通过`);
  process.exitCode = failures === 0 ? 0 : 1;
}
