import type { PurchaseReceiptReferenceData } from "@/lib/contracts";

export type MobileReceivingOrder = PurchaseReceiptReferenceData["releasedOrders"][number];
export type MobileReceivingLine = MobileReceivingOrder["lines"][number];

function canonicalScan(value: string, prefix: "PO" | "MAT") {
  const normalized = value.trim();
  const tagged = new RegExp(`^${prefix}[:|]`, "i");
  return normalized.replace(tagged, "").trim().toLocaleUpperCase("en-US");
}

export function resolvePurchaseOrderScan(
  orders: PurchaseReceiptReferenceData["releasedOrders"],
  scannedValue: string,
): { order: MobileReceivingOrder | null; error: string } {
  const code = canonicalScan(scannedValue, "PO");
  if (!code) return { order: null, error: "请扫描或输入采购单号。" };
  const order = orders.find((item) => item.orderNumber.toLocaleUpperCase("en-US") === code) ?? null;
  return order
    ? { order, error: "" }
    : { order: null, error: "未找到当前工作区已下达且仍有未收数量的采购订单。" };
}

export function resolveMaterialScan(
  order: MobileReceivingOrder,
  scannedValue: string,
): { line: MobileReceivingLine | null; error: string } {
  const code = canonicalScan(scannedValue, "MAT");
  if (!code) return { line: null, error: "请扫描或输入物料编码。" };
  const line = order.lines.find((item) => item.materialCode.toLocaleUpperCase("en-US") === code) ?? null;
  return line
    ? { line, error: "" }
    : { line: null, error: "该物料不属于所选采购订单，或对应订单行已经收完。" };
}
