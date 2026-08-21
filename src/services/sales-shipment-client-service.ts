import type { SalesShipmentRecord } from "@/lib/contracts";
import type { CreateSalesShipmentPayload } from "@/services/sales-shipment-server-service";

export async function submitCreateSalesShipment(payload: CreateSalesShipmentPayload): Promise<SalesShipmentRecord> {
  const response = await fetch("/api/sales/shipments/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-sales-shipment-${crypto.randomUUID()}` },
    body: JSON.stringify(payload),
  });
  const data = await response.json().catch(() => null) as { shipment?: SalesShipmentRecord; message?: string } | null;
  if (!response.ok || !data?.shipment) throw new Error(data?.message ?? "销售发货失败，请重试");
  return data.shipment;
}