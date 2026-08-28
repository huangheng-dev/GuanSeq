import { equipmentAssetPageSchema, equipmentOeePageSchema, equipmentOeeRecordSchema,
  type EquipmentOeeRecord } from "@/lib/contracts";
import type { EquipmentOeeMutation, EquipmentOeePageData } from "@/services/equipment-oee-server-service";

export class EquipmentOeeClientError extends Error {
  constructor(message: string, readonly status: number, readonly requestId?: string) { super(message); }
}

async function payload(response: Response) { return await response.json().catch(() => null) as Record<string, unknown> | null; }
function failure(response: Response, body: Record<string, unknown> | null, fallback: string) {
  return new EquipmentOeeClientError(typeof body?.message === "string" ? body.message : fallback, response.status,
    typeof body?.requestId === "string" ? body.requestId : response.headers.get("X-Request-Id") ?? undefined);
}

export async function refreshEquipmentOeePage(): Promise<Extract<EquipmentOeePageData, { source: "backend" }>> {
  const response = await fetch("/api/equipment/oee", { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.data || typeof body.data !== "object") throw failure(response, body, "OEE 刷新失败");
  const data = body.data as Record<string, unknown>;
  return { source: "backend", page: equipmentOeePageSchema.parse(data.page), assets: equipmentAssetPageSchema.parse(data.assets),
    requestId: typeof data.requestId === "string" ? data.requestId : "web-equipment-oee" };
}

export async function loadEquipmentOeeDetail(id: string): Promise<EquipmentOeeRecord> {
  const response = await fetch(`/api/equipment/oee?id=${encodeURIComponent(id)}`, { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.record) throw failure(response, body, "OEE 详情加载失败");
  return equipmentOeeRecordSchema.parse(body.record);
}

export async function submitEquipmentOeeMutation(input: EquipmentOeeMutation): Promise<EquipmentOeeRecord> {
  const response = await fetch("/api/equipment/oee", { method: "POST", headers: {
    "Content-Type": "application/json", "X-Request-Id": `web-equipment-oee-${crypto.randomUUID()}` }, body: JSON.stringify(input) });
  const body = await payload(response);
  if (!response.ok || !body?.record) throw failure(response, body, "OEE 操作失败");
  return equipmentOeeRecordSchema.parse(body.record);
}
