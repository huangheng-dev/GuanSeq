import type { FinalInspectionRecord, ProductionWorkReportRecord } from "@/lib/contracts";
import type { FinalInspectionMutation, ProductionExecutionMutation } from "@/services/production-execution-server-service";

export async function submitProductionExecutionMutation(input: ProductionExecutionMutation): Promise<ProductionWorkReportRecord> {
  const response = await fetch("/api/production/execution/mutate", { method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": `web-execution-${crypto.randomUUID()}` }, body: JSON.stringify(input) });
  const payload = await response.json().catch(() => null) as { report?: ProductionWorkReportRecord; message?: string } | null;
  if (!response.ok || !payload?.report) throw new Error(payload?.message ?? "生产执行操作失败，请重试");
  return payload.report;
}

export async function submitFinalInspection(input: FinalInspectionMutation): Promise<FinalInspectionRecord> {
  const response = await fetch("/api/quality/final-inspections/mutate", { method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": `web-quality-${crypto.randomUUID()}` }, body: JSON.stringify(input) });
  const payload = await response.json().catch(() => null) as { inspection?: FinalInspectionRecord; message?: string } | null;
  if (!response.ok || !payload?.inspection) throw new Error(payload?.message ?? "检验判定提交失败，请重试");
  return payload.inspection;
}
