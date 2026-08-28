import "server-only";

import { randomUUID } from "node:crypto";
import { labelPrintRequestPageSchema, labelPrintRequestSchema, labelReferenceDataSchema,
  type LabelPrintRequest, type LabelReferenceData } from "@/lib/labeling-contracts";
import { readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

const LABELING_PATH = "/production/mobile-operations/label-reprint";

export type LabelingPageData = {
  source: "backend" | "unavailable";
  references: LabelReferenceData;
  requests: LabelPrintRequest[];
  error?: string;
};

const emptyReferences: LabelReferenceData = { allowedObjectTypes: [], templates: [], candidates: [] };

export async function fetchLabelingPageData(): Promise<LabelingPageData> {
  const requestId = `web-labeling-${randomUUID()}`;
  const [referenceResponse, requestsResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/labeling/reference-data", requestId, undefined, 10000),
    requestGuanSeqApi("/api/v1/labeling/print-requests?page=0&size=100&objectType=ALL", `${requestId}-requests`, undefined, 10000),
  ]);
  if (!referenceResponse || !requestsResponse) return { source: "unavailable", references: emptyReferences, requests: [],
    error: "标签服务暂时不可用，请确认后端连接后重试。" };
  if (!referenceResponse.ok || !requestsResponse.ok) {
    const denied = [referenceResponse.status, requestsResponse.status].some((status) => status === 401 || status === 403);
    return { source: "unavailable", references: emptyReferences, requests: [],
      error: denied ? "当前账号无权生成或补打现场标签。" : "标签参考数据加载失败，请稍后刷新。" };
  }
  return { source: "backend", references: labelReferenceDataSchema.parse(await referenceResponse.json()),
    requests: labelPrintRequestPageSchema.parse(await requestsResponse.json()).items };
}

export async function prepareLabelPrintRequest(input: {
  objectType: "OPERATION_TASK" | "EMPLOYEE" | "STOCK_BALANCE";
  objectId: string;
  expectedObjectVersion: number;
  mode: "INITIAL" | "REPRINT";
  copies: number;
  reason: string | null;
}, requestId: string) {
  const response = await requestGuanSeqApi("/api/v1/labeling/print-requests", requestId,
    { method: "POST", body: JSON.stringify(input) }, 10000);
  if (!response) throw new Error("标签服务当前无响应，未生成打印准备事实");
  if (!response.ok) await readApiError(response, "标签打印准备失败");
  return labelPrintRequestSchema.parse(await response.json());
}

export async function getLabelingPageData(pathname: string) {
  return pathname === LABELING_PATH ? fetchLabelingPageData() : null;
}

