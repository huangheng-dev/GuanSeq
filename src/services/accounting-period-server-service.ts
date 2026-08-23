import "server-only";

import { randomUUID } from "node:crypto";
import {
  accountingPeriodSchema,
  createAccountingPeriodSchema,
  reopenAccountingPeriodSchema,
  type AccountingPeriod,
  type CreateAccountingPeriodPayload,
} from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type ReopenAccountingPeriodPayload = {
  reason: string;
  expectedVersion?: number;
};

export async function getAccountingPeriods(year?: number): Promise<AccountingPeriod[]> {
  const requestId = `web-accounting-period-list-${randomUUID()}`;
  const query = year ? `?year=${year}` : "";
  const response = await requestGuanSeqApi(`/api/v1/finance/accounting-periods${query}`, requestId);
  if (!response) throw new GuanSeqApiError("会计期间服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "会计期间暂时无法加载");
  return accountingPeriodSchema.array().parse(await response.json());
}

export async function createAccountingPeriod(
  payload: CreateAccountingPeriodPayload,
  requestId: string,
): Promise<{ period: AccountingPeriod; requestId: string }> {
  const parsed = createAccountingPeriodSchema.parse(payload);
  const response = await requestGuanSeqApi("/api/v1/finance/accounting-periods", requestId, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(parsed),
  });
  if (!response) throw new GuanSeqApiError("会计期间服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "会计期间暂时无法创建");
  return {
    period: accountingPeriodSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}

export async function closeAccountingPeriod(
  id: string,
  requestId: string,
): Promise<{ period: AccountingPeriod; requestId: string }> {
  const response = await requestGuanSeqApi(`/api/v1/finance/accounting-periods/${id}/close`, requestId, {
    method: "POST",
  });
  if (!response) throw new GuanSeqApiError("关账服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "会计期间暂时无法关账");
  return {
    period: accountingPeriodSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}

export async function reopenAccountingPeriod(
  id: string,
  payload: ReopenAccountingPeriodPayload,
  requestId: string,
): Promise<{ period: AccountingPeriod; requestId: string }> {
  const parsed = reopenAccountingPeriodSchema.parse(payload);
  const response = await requestGuanSeqApi(`/api/v1/finance/accounting-periods/${id}/reopen`, requestId, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(parsed),
  });
  if (!response) throw new GuanSeqApiError("重开服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "会计期间暂时无法重开");
  return {
    period: accountingPeriodSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}
