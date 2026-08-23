import type { AccountingPeriod, CreateAccountingPeriodPayload } from "@/lib/contracts";
import type { ReopenAccountingPeriodPayload } from "@/services/accounting-period-server-service";

type MutateResponse = {
  period?: AccountingPeriod;
  message?: string;
};

async function postMutation(
  operation: string,
  payload: unknown,
): Promise<MutateResponse> {
  const response = await fetch("/api/finance/accounting-periods/mutate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Request-Id": `web-accounting-period-${crypto.randomUUID()}`,
    },
    body: JSON.stringify({ operation, payload }),
  });
  const data = (await response.json().catch(() => null)) as MutateResponse | null;
  if (!response.ok || !data?.period) {
    throw new Error(data?.message ?? "会计期间业务处理失败，请重试");
  }
  return data;
}

export async function fetchAccountingPeriods(year?: number): Promise<AccountingPeriod[]> {
  const query = year ? `?year=${year}` : "";
  const response = await fetch(`/api/finance/accounting-periods${query}`, {
    headers: { "X-Request-Id": `web-accounting-period-${crypto.randomUUID()}` },
  });
  const data = (await response.json().catch(() => null)) as
    | { periods?: AccountingPeriod[]; message?: string }
    | null;
  if (!response.ok || !data?.periods) {
    throw new Error(data?.message ?? "会计期间加载失败");
  }
  return data.periods;
}

export async function submitCreateAccountingPeriod(
  payload: CreateAccountingPeriodPayload,
): Promise<AccountingPeriod> {
  const data = await postMutation("create", payload);
  return data.period!;
}

export async function submitCloseAccountingPeriod(id: string): Promise<AccountingPeriod> {
  const data = await postMutation("close", { id });
  return data.period!;
}

export async function submitReopenAccountingPeriod(
  id: string,
  payload: ReopenAccountingPeriodPayload,
): Promise<AccountingPeriod> {
  const data = await postMutation("reopen", { id, ...payload });
  return data.period!;
}
