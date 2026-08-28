import { z } from "zod";
import { operationTaskRecordSchema, productionOrderRecordSchema, productionWorkReportRecordSchema,
  type OperationTaskRecord, type ProductionWorkReportRecord } from "@/lib/contracts";

const pageDataSchema = z.object({
  source: z.enum(["backend", "unavailable"]), canControl: z.boolean(),
  tasks: z.array(operationTaskRecordSchema), orders: z.array(productionOrderRecordSchema),
  operator: z.object({ username: z.string(), displayName: z.string() }).nullable(), error: z.string().optional(),
});

export type MobileProductionReportingMutation =
  | { kind: "TASK_ACTION"; id: string; action: "START" | "COMPLETE"; expectedVersion: number; shiftName: string;
      completedQuantity: number | null; note: string | null; operatorBadge: string }
  | { kind: "WORK_REPORT"; orderId: string; operationTaskId: string; quantity: number; shiftName: string;
      note: string | null; expectedOrderVersion: number; operatorBadge: string };

export async function loadMobileProductionReportingData() {
  const response = await fetch("/api/production/mobile-reporting", { cache: "no-store" });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.message ?? "生产扫码报工参考数据刷新失败");
  return pageDataSchema.parse(body);
}

export async function submitMobileProductionReportingMutation(input: MobileProductionReportingMutation, requestId: string): Promise<
  { kind: "TASK_ACTION"; task: OperationTaskRecord; requestId: string }
  | { kind: "WORK_REPORT"; report: ProductionWorkReportRecord; requestId: string }
> {
  const response = await fetch("/api/production/mobile-reporting", {
    method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": requestId }, body: JSON.stringify(input),
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.message ?? "生产扫码报工失败；业务事实未保存");
  return body.kind === "TASK_ACTION"
    ? { kind: "TASK_ACTION", task: operationTaskRecordSchema.parse(body.task), requestId: body.requestId }
    : { kind: "WORK_REPORT", report: productionWorkReportRecordSchema.parse(body.report), requestId: body.requestId };
}
