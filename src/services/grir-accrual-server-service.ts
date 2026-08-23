import "server-only";

import { randomUUID } from "node:crypto";
import {
  grirAccrualPageSchema,
  grirAccrualPreviewSchema,
  grirAccrualSchema,
  reverseGrirAccrualSchema,
  runGrirAccrualSchema,
  type GrirAccrual,
  type GrirAccrualPreview,
  type ReverseGrirAccrualPayload,
  type RunGrirAccrualPayload,
} from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type GrirAccrualPage = {
  items: GrirAccrual[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
};

export async function listGrirAccruals(options: {
  year?: number;
  status?: string;
  page?: number;
  size?: number;
}): Promise<GrirAccrualPage> {
  const params = new URLSearchParams();
  if (options.year) params.set("year", String(options.year));
  if (options.status && options.status !== "ALL") params.set("status", options.status);
  params.set("page", String(options.page ?? 0));
  params.set("size", String(options.size ?? 20));
  const requestId = `web-grir-list-${randomUUID()}`;
  const response = await requestGuanSeqApi(`/api/v1/finance/grir-accruals?${params}`, requestId);
  if (!response) throw new GuanSeqApiError("暂估应付服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "暂估应付台账暂时无法加载");
  return grirAccrualPageSchema.parse(await response.json());
}

export async function getGrirAccrual(id: string): Promise<GrirAccrual> {
  const requestId = `web-grir-get-${randomUUID()}`;
  const response = await requestGuanSeqApi(`/api/v1/finance/grir-accruals/${id}`, requestId);
  if (!response) throw new GuanSeqApiError("暂估应付服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "暂估应付详情暂时无法加载");
  return grirAccrualSchema.parse(await response.json());
}

export async function previewGrirAccrual(year: number, period: number): Promise<GrirAccrualPreview> {
  const requestId = `web-grir-preview-${randomUUID()}`;
  const response = await requestGuanSeqApi(
    `/api/v1/finance/grir-accruals/preview?year=${year}&period=${period}`,
    requestId,
  );
  if (!response) throw new GuanSeqApiError("暂估预览服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "暂估预览暂时无法加载");
  return grirAccrualPreviewSchema.parse(await response.json());
}

export async function runGrirAccrual(
  payload: RunGrirAccrualPayload,
  requestId: string,
): Promise<{ accrual: GrirAccrual; requestId: string }> {
  const parsed = runGrirAccrualSchema.parse(payload);
  const response = await requestGuanSeqApi("/api/v1/finance/grir-accruals/run", requestId, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(parsed),
  });
  if (!response) throw new GuanSeqApiError("暂估运行服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "暂估运行失败");
  return {
    accrual: grirAccrualSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}

export async function reverseGrirAccrual(
  id: string,
  payload: ReverseGrirAccrualPayload,
  requestId: string,
): Promise<{ accrual: GrirAccrual; requestId: string }> {
  const parsed = reverseGrirAccrualSchema.parse(payload);
  const response = await requestGuanSeqApi(`/api/v1/finance/grir-accruals/${id}/reverse`, requestId, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(parsed),
  });
  if (!response) throw new GuanSeqApiError("冲回服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "暂估冲回失败");
  return {
    accrual: grirAccrualSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}
