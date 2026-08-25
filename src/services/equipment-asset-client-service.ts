import {
  equipmentAssetPageSchema,
  equipmentAssetSchema,
  type EquipmentAsset,
  type EquipmentAssetPage,
} from "@/lib/contracts";
import type { EquipmentAssetMutation } from "@/services/equipment-asset-server-service";

export class EquipmentAssetClientError extends Error {
  constructor(message: string, readonly status: number, readonly requestId?: string) { super(message); }
}

async function payload(response: Response) {
  return await response.json().catch(() => null) as Record<string, unknown> | null;
}

function failure(response: Response, body: Record<string, unknown> | null, fallback: string) {
  return new EquipmentAssetClientError(
    typeof body?.message === "string" ? body.message : fallback,
    response.status,
    typeof body?.requestId === "string" ? body.requestId : response.headers.get("X-Request-Id") ?? undefined,
  );
}

export async function refreshEquipmentAssets(): Promise<EquipmentAssetPage> {
  const response = await fetch("/api/equipment/assets", { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.page) throw failure(response, body, "设备台账刷新失败");
  return equipmentAssetPageSchema.parse(body.page);
}

export async function loadEquipmentAssetDetail(id: string): Promise<EquipmentAsset> {
  const response = await fetch(`/api/equipment/assets?id=${encodeURIComponent(id)}`, { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.asset) throw failure(response, body, "设备详情加载失败");
  return equipmentAssetSchema.parse(body.asset);
}
export async function submitEquipmentAssetMutation(input: EquipmentAssetMutation): Promise<EquipmentAsset> {
  const response = await fetch("/api/equipment/assets", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-equipment-${crypto.randomUUID()}` },
    body: JSON.stringify(input),
  });
  const body = await payload(response);
  if (!response.ok || !body?.asset) throw failure(response, body, "设备操作失败");
  return equipmentAssetSchema.parse(body.asset);
}
