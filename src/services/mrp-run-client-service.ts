import type { MrpRunRecord } from "@/lib/contracts";
import type { CreateMrpRunInput } from "@/services/mrp-run-server-service";

export async function submitMrpRun(input: CreateMrpRunInput, requestId: string): Promise<{ run: MrpRunRecord; requestId: string }> {
  const response = await fetch("/api/planning/mrp-runs", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": requestId },
    body: JSON.stringify(input),
  });
  const body = await response.json().catch(() => null) as { run?: MrpRunRecord; requestId?: string; message?: string } | null;
  if (!response.ok || !body?.run) throw new Error(body?.message ?? "MRP 运算失败，请稍后重试");
  return { run: body.run, requestId: body.requestId ?? "" };
}
