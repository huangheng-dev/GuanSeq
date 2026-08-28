import { inventoryControlReferenceDataSchema, stockCountTaskSchema, transferTaskSchema } from "@/lib/inventory-control-contracts";

export async function loadInventoryControlData() {
  const response = await fetch("/api/warehouse/inventory-control", { cache: "no-store" });
  const body = await response.json().catch(() => null); if (!response.ok) throw new Error(body?.message ?? "调拨与盘点数据刷新失败");
  return { references: inventoryControlReferenceDataSchema.parse(body.references), transfers: transferTaskSchema.array().parse(body.transfers),
    counts: stockCountTaskSchema.array().parse(body.counts) };
}
export async function submitInventoryControl(input: Record<string, unknown>, requestId: string) {
  const response = await fetch("/api/warehouse/inventory-control", { method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": requestId }, body: JSON.stringify(input) });
  const body = await response.json().catch(() => null); if (!response.ok) throw new Error(body?.message ?? "库存控制操作失败；未形成业务事实");
  return input.action?.toString().startsWith("TRANSFER_") ? transferTaskSchema.parse(body) : stockCountTaskSchema.parse(body);
}
