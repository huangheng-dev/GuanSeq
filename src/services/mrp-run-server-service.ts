import "server-only";

import { randomUUID } from "node:crypto";

import { mrpRunPageSchema, mrpRunRecordSchema, type MrpRunRecord } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type MrpRunPageData = {
  source: "backend";
  runs: MrpRunRecord[];
};

export async function getMrpRunPageData(pathname: string): Promise<MrpRunPageData | null> {
  if (pathname !== "/planning/mrp/runs") return null;
  const requestId = `web-mrp-runs-${randomUUID()}`;
  const response = await requestGuanSeqApi("/api/v1/planning/mrp-runs?page=0&size=100&status=ALL", requestId);
  if (!response?.ok) return null;
  return { source: "backend", runs: mrpRunPageSchema.parse(await response.json()).items };
}

export type CreateMrpRunInput = {
  name: string;
  horizonStart: string;
  horizonEnd: string;
};

export async function createMrpRun(input: CreateMrpRunInput, requestId: string) {
  const response = await requestGuanSeqApi("/api/v1/planning/mrp-runs", requestId, {
    method: "POST",
    body: JSON.stringify(input),
  }, 10000);
  if (!response) throw new GuanSeqApiError("MRP 运算服务暂时不可用，未创建任何记录", 503);
  if (!response.ok) await readApiError(response, "MRP 运算失败");
  return {
    run: mrpRunRecordSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}
