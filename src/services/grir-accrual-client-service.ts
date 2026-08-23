import type {
  GrirAccrual,
  GrirAccrualPreview,
  ReverseGrirAccrualPayload,
  RunGrirAccrualPayload,
} from "@/lib/contracts";

export type GrirAccrualPage = {
  items: GrirAccrual[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
};

type MutateResponse = {
  accrual?: GrirAccrual;
  message?: string;
};

export async function fetchGrirAccruals(params: {
  year?: number;
  status?: string;
  page?: number;
  size?: number;
}): Promise<GrirAccrualPage> {
  const search = new URLSearchParams();
  if (params.year) search.set("year", String(params.year));
  if (params.status && params.status !== "ALL") search.set("status", params.status);
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 20));
  const response = await fetch(`/api/finance/grir-accruals?${search.toString()}`, {
    headers: { "X-Request-Id": `web-grir-list-${crypto.randomUUID()}` },
  });
  const data = (await response.json().catch(() => null)) as GrirAccrualPage | { message?: string } | null;
  if (!response.ok || !data || !("items" in data)) {
    throw new Error((data && "message" in data && data.message) || "暂估应付台账加载失败");
  }
  return data;
}

export async function fetchGrirAccrualPreview(year: number, period: number): Promise<GrirAccrualPreview> {
  const response = await fetch(
    `/api/finance/grir-accruals/preview?year=${year}&period=${period}`,
    { headers: { "X-Request-Id": `web-grir-preview-${crypto.randomUUID()}` } },
  );
  const data = (await response.json().catch(() => null)) as
    | { preview?: GrirAccrualPreview; message?: string }
    | null;
  if (!response.ok || !data?.preview) {
    throw new Error(data?.message ?? "暂估预览加载失败");
  }
  return data.preview;
}

export async function submitRunGrirAccrual(payload: RunGrirAccrualPayload): Promise<GrirAccrual> {
  const response = await fetch("/api/finance/grir-accruals/mutate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Request-Id": `web-grir-run-${crypto.randomUUID()}`,
    },
    body: JSON.stringify({ operation: "run", payload }),
  });
  const data = (await response.json().catch(() => null)) as MutateResponse | null;
  if (!response.ok || !data?.accrual) {
    throw new Error(data?.message ?? "暂估运行失败，请重试");
  }
  return data.accrual;
}

export async function submitReverseGrirAccrual(
  id: string,
  payload: Omit<ReverseGrirAccrualPayload, never> & { reversalDate: string; reason: string },
): Promise<GrirAccrual> {
  const response = await fetch("/api/finance/grir-accruals/mutate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Request-Id": `web-grir-reverse-${crypto.randomUUID()}`,
    },
    body: JSON.stringify({ operation: "reverse", payload: { id, ...payload } }),
  });
  const data = (await response.json().catch(() => null)) as MutateResponse | null;
  if (!response.ok || !data?.accrual) {
    throw new Error(data?.message ?? "暂估冲回失败，请重试");
  }
  return data.accrual;
}
