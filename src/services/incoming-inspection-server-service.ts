import "server-only";

import { randomUUID } from "node:crypto";
import { incomingInspectionPageSchema, incomingInspectionRecordSchema, type IncomingInspectionRecord } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type IncomingInspectionPageData = { source: "backend"; inspections: IncomingInspectionRecord[] };
export type CompleteIncomingInspectionPayload = {
  id: string;
  acceptedQuantity: number;
  rejectedQuantity: number;
  inspector: string;
  defectDescription: string | null;
  conclusion: string;
  expectedVersion: number;
};

export async function getIncomingInspectionPageData(pathname: string): Promise<IncomingInspectionPageData | null> {
  if (pathname !== "/quality/incoming") return null;
  const requestId = `web-incoming-list-${randomUUID()}`;
  const response = await requestGuanSeqApi("/api/v1/quality/incoming-inspections?page=0&size=100&status=ALL", requestId);
  if (!response) return null;
  if (!response.ok) await readApiError(response, "来料检验台账加载失败");
  return { source: "backend", inspections: incomingInspectionPageSchema.parse(await response.json()).items };
}

export async function completeIncomingInspection(payload: CompleteIncomingInspectionPayload, requestId: string) {
  const response = await requestGuanSeqApi(`/api/v1/quality/incoming-inspections/${payload.id}/complete`, requestId, {
    method: "POST",
    body: JSON.stringify({
      acceptedQuantity: payload.acceptedQuantity,
      rejectedQuantity: payload.rejectedQuantity,
      inspector: payload.inspector,
      defectDescription: payload.defectDescription,
      conclusion: payload.conclusion,
      expectedVersion: payload.expectedVersion,
    }),
  });
  if (!response) throw new GuanSeqApiError("来料检验服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "来料检验判定无法保存");
  return { inspection: incomingInspectionRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
