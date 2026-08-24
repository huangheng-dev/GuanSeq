"use client";

import {
  advancePageSchema,
  createAdvanceSchema,
  refundAdvanceSchema,
  type Advance,
  type AdvancePage,
  type CreateAdvancePayload,
  type RefundAdvancePayload,
} from "@/lib/contracts";

export type { Advance, AdvancePage, CreateAdvancePayload, RefundAdvancePayload };

type MutateResponse = { advance?: Advance; message?: string };

export async function fetchAdvances(options: {
  type?: string;
  status?: string;
  query?: string;
  page?: number;
  size?: number;
  signal?: AbortSignal;
}): Promise<AdvancePage> {
  const params = new URLSearchParams();
  if (options.type && options.type !== "ALL") params.set("type", options.type);
  if (options.status && options.status !== "ALL") params.set("status", options.status);
  if (options.query) params.set("query", options.query);
  params.set("page", String(options.page ?? 0));
  params.set("size", String(options.size ?? 50));
  const response = await fetch(`/api/finance/advances?${params.toString()}`, {
    signal: options.signal,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message ?? "预收预付台账加载失败");
  }
  return advancePageSchema.parse(await response.json());
}

export async function submitRegisterAdvance(
  payload: CreateAdvancePayload,
): Promise<Advance> {
  const parsed = createAdvanceSchema.parse(payload);
  const response = await fetch("/api/finance/advances/mutate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Request-Id": `web-adv-reg-${crypto.randomUUID()}`,
    },
    body: JSON.stringify({ operation: "register", payload: parsed }),
  });
  const data = (await response.json().catch(() => null)) as MutateResponse | null;
  if (!response.ok || !data?.advance) {
    throw new Error(data?.message ?? "登记失败，请重试");
  }
  return data.advance;
}

export async function submitRefundAdvance(
  id: string,
  payload: RefundAdvancePayload,
): Promise<Advance> {
  const parsed = refundAdvanceSchema.parse(payload);
  const response = await fetch("/api/finance/advances/mutate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Request-Id": `web-adv-ref-${crypto.randomUUID()}`,
    },
    body: JSON.stringify({ operation: "refund", id, payload: parsed }),
  });
  const data = (await response.json().catch(() => null)) as MutateResponse | null;
  if (!response.ok || !data?.advance) {
    throw new Error(data?.message ?? "退款失败，请重试");
  }
  return data.advance;
}
