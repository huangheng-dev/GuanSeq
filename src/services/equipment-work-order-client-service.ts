import { z } from "zod";

import { equipmentAssetSchema, equipmentMaintenanceCostMutationResultSchema, equipmentWorkOrderPageSchema, equipmentWorkOrderSchema,
  type EquipmentAsset, type EquipmentMaintenanceCostMutationResult, type EquipmentWorkOrder, type EquipmentWorkOrderPage } from "@/lib/contracts";
import type { EquipmentMaintenanceCostMutation, EquipmentWorkOrderMutation } from "@/services/equipment-work-order-server-service";

export class EquipmentWorkOrderClientError extends Error {
  constructor(message: string, readonly status: number, readonly requestId?: string) { super(message); }
}

async function payload(response: Response) { return await response.json().catch(() => null) as Record<string, unknown> | null; }
function failure(response: Response, body: Record<string, unknown> | null, fallback: string) {
  return new EquipmentWorkOrderClientError(typeof body?.message === "string" ? body.message : fallback, response.status,
    typeof body?.requestId === "string" ? body.requestId : response.headers.get("X-Request-Id") ?? undefined);
}

export async function refreshEquipmentWorkOrders(): Promise<{ page: EquipmentWorkOrderPage; assets: EquipmentAsset[] }> {
  const response = await fetch("/api/equipment/work-orders", { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.page || !Array.isArray(body.assets)) throw failure(response, body, "设备运维工单刷新失败");
  return { page: equipmentWorkOrderPageSchema.parse(body.page), assets: z.array(equipmentAssetSchema).parse(body.assets) };
}

export async function loadEquipmentWorkOrderDetail(id: string): Promise<EquipmentWorkOrder> {
  const response = await fetch(`/api/equipment/work-orders?id=${encodeURIComponent(id)}`, { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.workOrder) throw failure(response, body, "设备运维工单详情加载失败");
  return equipmentWorkOrderSchema.parse(body.workOrder);
}

export async function submitEquipmentWorkOrderMutation(input: EquipmentWorkOrderMutation): Promise<EquipmentWorkOrder> {
  const response = await fetch("/api/equipment/work-orders", { method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-equipment-work-order-${crypto.randomUUID()}` },
    body: JSON.stringify(input) });
  const body = await payload(response);
  if (!response.ok || !body?.workOrder) throw failure(response, body, "设备运维操作失败");
  return equipmentWorkOrderSchema.parse(body.workOrder);
}

export async function submitEquipmentMaintenanceCostMutation(input: EquipmentMaintenanceCostMutation): Promise<EquipmentMaintenanceCostMutationResult> {
  const response = await fetch("/api/equipment/work-orders", { method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-equipment-cost-${crypto.randomUUID()}` },
    body: JSON.stringify(input) });
  const body = await payload(response);
  if (!response.ok || !body?.result) throw failure(response, body, "维修备件或人工成本操作失败");
  return equipmentMaintenanceCostMutationResultSchema.parse(body.result);
}
