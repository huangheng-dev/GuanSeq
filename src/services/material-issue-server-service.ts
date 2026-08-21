import "server-only";

import { randomUUID } from "node:crypto";
import {
  materialIssuePageSchema,
  materialIssueRecordSchema,
  materialIssueReferenceDataSchema,
  type MaterialIssueRecord,
  type MaterialIssueReferenceData,
} from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

const MATERIAL_ISSUE_PATHS = new Set(["/warehouse/staging", "/warehouse/material-issues"]);
const emptyReferences: MaterialIssueReferenceData = { productionOrders: [], warehouses: [], locations: [] };

export type MaterialIssuePageData = {
  source: "backend" | "unavailable";
  issues: MaterialIssueRecord[];
  reference: MaterialIssueReferenceData;
  error?: string;
};

export type CreateMaterialIssueInput = {
  productionOrderId: string;
  warehouseId: string;
};

export type MaterialIssueActionInput = {
  id: string;
  action: "ISSUE" | "CANCEL";
  expectedVersion: number;
  comment?: string | null;
  lines?: { lineId: string; quantity: number; expectedLineVersion: number }[];
};

export type MaterialIssueReturnInput = {
  id: string;
  locationId: string;
  reason: string;
  lines: { lineId: string; quantity: number; expectedLineVersion: number; reason?: string | null }[];
};

function unavailable(response?: Response | null): MaterialIssuePageData {
  const error = response?.status === 401 || response?.status === 403
    ? "当前账号无权查看生产备料与领退料，请联系管理员授权。"
    : "生产备料服务暂时不可用，请稍后刷新重试。";
  return { source: "unavailable", issues: [], reference: emptyReferences, error };
}

export async function fetchMaterialIssuePageData(): Promise<MaterialIssuePageData> {
  const requestId = `web-material-issue-page-${randomUUID()}`;
  const [listResponse, referenceResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/production/material-issues?page=0&size=100&status=ALL", requestId, undefined, 10000),
    requestGuanSeqApi("/api/v1/production/material-issue-reference-data", requestId, undefined, 10000),
  ]);
  if (!listResponse?.ok || !referenceResponse?.ok) return unavailable(listResponse && !listResponse.ok ? listResponse : referenceResponse);
  const [listPayload, referencePayload] = await Promise.all([listResponse.json(), referenceResponse.json()]);
  return {
    source: "backend",
    issues: materialIssuePageSchema.parse(listPayload).items,
    reference: materialIssueReferenceDataSchema.parse(referencePayload),
  };
}

export async function getMaterialIssuePageData(pathname: string): Promise<MaterialIssuePageData | null> {
  if (!MATERIAL_ISSUE_PATHS.has(pathname)) return null;
  return fetchMaterialIssuePageData();
}

export async function createMaterialIssue(input: CreateMaterialIssueInput, requestId: string) {
  const response = await requestGuanSeqApi("/api/v1/production/material-issues", requestId, {
    method: "POST",
    body: JSON.stringify(input),
  }, 10000);
  if (!response) throw new GuanSeqApiError("生产备料服务暂时不可用，未生成领料单", 503);
  if (!response.ok) await readApiError(response, "生产领料单生成失败");
  return { issue: materialIssueRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function actOnMaterialIssue(input: MaterialIssueActionInput, requestId: string) {
  const response = await requestGuanSeqApi(`/api/v1/production/material-issues/${input.id}/actions`, requestId, {
    method: "POST",
    body: JSON.stringify({ action: input.action, expectedVersion: input.expectedVersion, comment: input.comment ?? null, lines: input.lines ?? [] }),
  }, 10000);
  if (!response) throw new GuanSeqApiError("生产备料服务暂时不可用，未保存发料动作", 503);
  if (!response.ok) await readApiError(response, "生产领料动作无法完成");
  return { issue: materialIssueRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function createMaterialReturn(input: MaterialIssueReturnInput, requestId: string) {
  const response = await requestGuanSeqApi(`/api/v1/production/material-issues/${input.id}/returns`, requestId, {
    method: "POST",
    body: JSON.stringify({ locationId: input.locationId, reason: input.reason, lines: input.lines }),
  }, 10000);
  if (!response) throw new GuanSeqApiError("生产备料服务暂时不可用，未保存退料", 503);
  if (!response.ok) await readApiError(response, "组件退料无法完成");
  return { issue: materialIssueRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
