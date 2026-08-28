import type { SalesReturnRecord } from "@/lib/contracts";
import type { SalesReturnMutation } from "@/services/sales-return-server-service";

export async function submitSalesReturn(input: SalesReturnMutation): Promise<SalesReturnRecord> {
  const response = await fetch("/api/sales/returns/mutate", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) });
  const data = await response.json().catch(() => null) as { record?: SalesReturnRecord; message?: string } | null;
  if (!response.ok || !data?.record) throw new Error(data?.message ?? "销售退货动作失败，请重试");
  return data.record;
}
