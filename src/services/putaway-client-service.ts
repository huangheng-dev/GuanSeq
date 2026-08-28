import { putawayReferenceDataSchema, putawayTaskSchema } from "@/lib/putaway-contracts";

export async function loadPutawayData() {
  const response = await fetch("/api/warehouse/putaway", { cache: "no-store" }); const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.message ?? "上架数据刷新失败");
  return { references: putawayReferenceDataSchema.parse(body.references), tasks: putawayTaskSchema.array().parse(body.tasks) };
}
export async function submitPutaway(input: Record<string, unknown>, requestId: string) {
  const response = await fetch("/api/warehouse/putaway", { method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": requestId }, body: JSON.stringify(input) });
  const body = await response.json().catch(() => null); if (!response.ok) throw new Error(body?.message ?? "仓储上架操作失败；未形成业务事实"); return putawayTaskSchema.parse(body);
}

