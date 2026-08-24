import "server-only";

import { randomUUID } from "node:crypto";
import {
  advancePageSchema,
  advanceSchema,
  createAdvanceSchema,
  refundAdvanceSchema,
  type Advance,
  type AdvancePage,
  type CreateAdvancePayload,
  type RefundAdvancePayload,
} from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export async function listAdvances(options: {
  type?: string;
  status?: string;
  partyId?: string;
  query?: string;
  page?: number;
  size?: number;
}): Promise<AdvancePage> {
  const params = new URLSearchParams();
  if (options.type && options.type !== "ALL") params.set("type", options.type);
  if (options.status && options.status !== "ALL") params.set("status", options.status);
  if (options.partyId) params.set("partyId", options.partyId);
  if (options.query) params.set("query", options.query);
  params.set("page", String(options.page ?? 0));
  params.set("size", String(options.size ?? 20));
  const requestId = `web-adv-list-${randomUUID()}`;
  const response = await requestGuanSeqApi(`/api/v1/finance/advances?${params}`, requestId);
  if (!response) throw new GuanSeqApiError("预收预付服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "预收预付台账暂时无法加载");
  return advancePageSchema.parse(await response.json());
}

export async function getAdvance(id: string): Promise<Advance> {
  const requestId = `web-adv-get-${randomUUID()}`;
  const response = await requestGuanSeqApi(`/api/v1/finance/advances/${id}`, requestId);
  if (!response) throw new GuanSeqApiError("预收预付服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "预收预付详情暂时无法加载");
  return advanceSchema.parse(await response.json());
}

export async function registerAdvance(
  payload: CreateAdvancePayload,
  requestId: string,
): Promise<{ advance: Advance; requestId: string }> {
  const parsed = createAdvanceSchema.parse(payload);
  const response = await requestGuanSeqApi("/api/v1/finance/advances", requestId, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(parsed),
  });
  if (!response) throw new GuanSeqApiError("登记服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "预收预付登记失败");
  return {
    advance: advanceSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}

export async function refundAdvance(
  id: string,
  payload: RefundAdvancePayload,
  requestId: string,
): Promise<{ advance: Advance; requestId: string }> {
  const parsed = refundAdvanceSchema.parse(payload);
  const response = await requestGuanSeqApi(`/api/v1/finance/advances/${id}/refund`, requestId, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(parsed),
  });
  if (!response) throw new GuanSeqApiError("退款服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "预收预付退款失败");
  return {
    advance: advanceSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}
