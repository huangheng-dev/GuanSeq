import "server-only";

import { randomUUID } from "node:crypto";

import { equipmentAssetPageSchema, equipmentOeePageSchema, equipmentOeeRecordSchema,
  type EquipmentAssetPage, type EquipmentOeeAction, type EquipmentOeeDowntimeCategory,
  type EquipmentOeePage, type EquipmentOeeRecord } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type EquipmentOeeMutation =
  | { operation: "create"; assetId: string; windowStart: string; windowEnd: string; plannedProductionMinutes: number;
      idealCycleSeconds: number; totalCount: number; goodCount: number; shiftName: string;
      productionReference?: string | null; sourceReference?: string | null; reason: string }
  | { operation: "act"; id: string; action: EquipmentOeeAction; reason: string; expectedVersion: number;
      windowStart?: string; windowEnd?: string; plannedProductionMinutes?: number; idealCycleSeconds?: number;
      totalCount?: number; goodCount?: number; shiftName?: string; productionReference?: string | null;
      sourceReference?: string | null; downtimeId?: string; downtimeStartedAt?: string; downtimeEndedAt?: string;
      reasonCategory?: EquipmentOeeDowntimeCategory; responsibleParty?: string; description?: string };

export type EquipmentOeePageData =
  | { source: "backend"; page: EquipmentOeePage; assets: EquipmentAssetPage; requestId: string }
  | { source: "unavailable"; page: null; assets: null; status: number; message: string; requestId: string };

async function message(response: Response | null, fallback: string) {
  if (!response) return fallback;
  try { return ((await response.json()) as { message?: string }).message ?? fallback; } catch { return fallback; }
}

export async function loadEquipmentOeePage(requestId: string): Promise<EquipmentOeePageData> {
  const [records, assets] = await Promise.all([
    requestGuanSeqApi("/api/v1/equipment/oee-records?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/equipment/assets?page=0&size=100&status=ALL&category=ALL", `${requestId}-assets`),
  ]);
  const failed = !records?.ok ? records : !assets?.ok ? assets : null;
  if (failed || !records || !assets) return { source: "unavailable", page: null, assets: null,
    status: failed?.status ?? 503, message: await message(failed, "OEE 服务暂时不可用"),
    requestId: failed?.headers.get("X-Request-Id") ?? requestId };
  return { source: "backend", page: equipmentOeePageSchema.parse(await records.json()),
    assets: equipmentAssetPageSchema.parse(await assets.json()), requestId: records.headers.get("X-Request-Id") ?? requestId };
}

export async function getEquipmentOeePageData(pathname: string): Promise<EquipmentOeePageData | null> {
  if (pathname !== "/equipment/oee") return null;
  return loadEquipmentOeePage(`web-equipment-oee-${randomUUID()}`);
}

export async function loadEquipmentOeeRecord(id: string, requestId: string): Promise<{ record: EquipmentOeeRecord; requestId: string }> {
  const response = await requestGuanSeqApi(`/api/v1/equipment/oee-records/${id}`, requestId);
  if (!response) throw new GuanSeqApiError("OEE 服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "OEE 详情加载失败");
  return { record: equipmentOeeRecordSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function mutateEquipmentOee(input: EquipmentOeeMutation, requestId: string): Promise<{
  record: EquipmentOeeRecord; requestId: string;
}> {
  const create = input.operation === "create";
  const path = create ? "/api/v1/equipment/oee-records" : `/api/v1/equipment/oee-records/${input.id}/actions`;
  const body = create ? { assetId: input.assetId, windowStart: input.windowStart, windowEnd: input.windowEnd,
    plannedProductionMinutes: input.plannedProductionMinutes, idealCycleSeconds: input.idealCycleSeconds,
    totalCount: input.totalCount, goodCount: input.goodCount, shiftName: input.shiftName,
    productionReference: input.productionReference ?? null, sourceReference: input.sourceReference ?? null, reason: input.reason }
    : { action: input.action, reason: input.reason, expectedVersion: input.expectedVersion,
      windowStart: input.windowStart, windowEnd: input.windowEnd, plannedProductionMinutes: input.plannedProductionMinutes,
      idealCycleSeconds: input.idealCycleSeconds, totalCount: input.totalCount, goodCount: input.goodCount,
      shiftName: input.shiftName, productionReference: input.productionReference ?? null,
      sourceReference: input.sourceReference ?? null, downtimeId: input.downtimeId,
      downtimeStartedAt: input.downtimeStartedAt, downtimeEndedAt: input.downtimeEndedAt,
      reasonCategory: input.reasonCategory, responsibleParty: input.responsibleParty, description: input.description };
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) });
  if (!response) throw new GuanSeqApiError("OEE 服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "OEE 操作失败");
  return { record: equipmentOeeRecordSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId };
}
