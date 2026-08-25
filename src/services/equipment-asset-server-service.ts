import "server-only";

import { randomUUID } from "node:crypto";

import {
  equipmentAssetPageSchema,
  equipmentAssetSchema,
  type EquipmentAsset,
  type EquipmentAssetAction,
  type EquipmentAssetCategory,
  type EquipmentAssetPage,
} from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type EquipmentAssetWritePayload = {
  assetName: string;
  category: EquipmentAssetCategory;
  manufacturer?: string | null;
  model?: string | null;
  serialNumber?: string | null;
  workCenterCode?: string | null;
  workCenterName?: string | null;
  location: string;
  responsiblePerson: string;
  commissioningDate?: string | null;
  reason: string;
};

export type EquipmentAssetMutation =
  | { operation: "create"; assetCode: string; payload: EquipmentAssetWritePayload }
  | { operation: "update"; id: string; payload: EquipmentAssetWritePayload & { expectedVersion: number } }
  | { operation: "action"; id: string; action: EquipmentAssetAction; reason: string; expectedVersion: number };

export type EquipmentAssetPageData =
  | { source: "backend"; page: EquipmentAssetPage; requestId: string }
  | { source: "unavailable"; page: null; status: number; message: string; requestId: string };

async function responseMessage(response: Response, fallback: string) {
  try { return ((await response.json()) as { message?: string }).message ?? fallback; }
  catch { return fallback; }
}

export async function loadEquipmentAssetPage(requestId: string): Promise<EquipmentAssetPageData> {
  const response = await requestGuanSeqApi("/api/v1/equipment/assets?page=0&size=200&status=ALL&category=ALL", requestId);
  if (!response) return { source: "unavailable", page: null, status: 503, message: "设备台账服务暂时不可用", requestId };
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  if (!response.ok) return {
    source: "unavailable",
    page: null,
    status: response.status,
    message: await responseMessage(response, response.status === 403 ? "当前角色无权读取设备台账" : "设备台账加载失败"),
    requestId: responseRequestId,
  };
  return { source: "backend", page: equipmentAssetPageSchema.parse(await response.json()), requestId: responseRequestId };
}

export async function loadEquipmentAsset(id: string, requestId: string): Promise<{ asset: EquipmentAsset; requestId: string }> {
  const response = await requestGuanSeqApi(`/api/v1/equipment/assets/${id}`, requestId);
  if (!response) throw new GuanSeqApiError("设备台账服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "设备详情加载失败");
  return { asset: equipmentAssetSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
export async function getEquipmentAssetPageData(pathname: string): Promise<EquipmentAssetPageData | null> {
  if (!new Set(["/equipment/assets", "/equipment/status"]).has(pathname)) return null;
  return loadEquipmentAssetPage(`web-equipment-assets-${randomUUID()}`);
}

export async function mutateEquipmentAsset(input: EquipmentAssetMutation, requestId: string) {
  let path = "/api/v1/equipment/assets";
  let method = "POST";
  let body: unknown;
  if (input.operation === "create") body = { assetCode: input.assetCode, ...input.payload };
  else if (input.operation === "update") { path += `/${input.id}`; method = "PUT"; body = input.payload; }
  else { path += `/${input.id}/actions`; body = { action: input.action, reason: input.reason, expectedVersion: input.expectedVersion }; }
  const response = await requestGuanSeqApi(path, requestId, { method, body: JSON.stringify(body) }, 10000);
  if (!response) throw new GuanSeqApiError("设备台账服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "设备操作失败");
  return { asset: equipmentAssetSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
