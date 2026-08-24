import "server-only";

import { randomUUID } from "node:crypto";
import { mrpSuggestionPageSchema, mrpSuggestionSchema, purchaseOrderReferenceDataSchema, type MrpSuggestion, type PurchaseOrderReferenceData } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type MrpSuggestionPageData = { source: "backend"; suggestions: MrpSuggestion[]; references: PurchaseOrderReferenceData };
export type MrpSuggestionMutation =
  | { operation: "action"; id: string; action: "APPROVE" | "REJECT"; expectedVersion: number; comment?: string }
  | { operation: "convert"; id: string; expectedVersion: number; supplierId?: string; currency?: "CNY" | "USD" | "EUR"; taxRate?: number; unitPrice?: number; requestedReceiptDate?: string; buyer?: string; plannedStartDate?: string; plannedReceiptDate?: string; workshop?: string; owner?: string };

export async function getMrpSuggestionPageData(pathname: string): Promise<MrpSuggestionPageData | null> {
  if (pathname !== "/planning/mrp/recommendations") return null;
  const requestId = `web-mrp-suggestions-${randomUUID()}`;
  const [suggestionsResponse, referencesResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/planning/mrp-suggestions?page=0&size=100&status=ALL&type=ALL", requestId),
    requestGuanSeqApi("/api/v1/procurement/order-reference-data", requestId),
  ]);
  if (!suggestionsResponse) return null;
  if (suggestionsResponse && !suggestionsResponse.ok) await readApiError(suggestionsResponse, "MRP 建议加载失败");
  if (!referencesResponse) return null;
  if (referencesResponse && !referencesResponse.ok) await readApiError(referencesResponse, "MRP 参考数据加载失败");
  return {
    source: "backend",
    suggestions: mrpSuggestionPageSchema.parse(await suggestionsResponse.json()).items,
    references: purchaseOrderReferenceDataSchema.parse(await referencesResponse.json()),
  };
}

export async function mutateMrpSuggestion(input: MrpSuggestionMutation, requestId: string) {
  const path = input.operation === "action" ? `/api/v1/planning/mrp-suggestions/${input.id}/actions` : `/api/v1/planning/mrp-suggestions/${input.id}/convert`;
  const body = input.operation === "action"
    ? { action: input.action, expectedVersion: input.expectedVersion, comment: input.comment || null }
    : { ...input, operation: undefined, id: undefined };
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) });
  if (!response) throw new GuanSeqApiError("MRP 建议服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "MRP 建议操作失败");
  return { suggestion: mrpSuggestionSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
