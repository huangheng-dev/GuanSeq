import "server-only";

import { randomUUID } from "node:crypto";
import {
  purchaseOrderReferenceDataSchema,
  salesOrderReferenceDataSchema,
  type PurchaseOrderReferenceData,
  type SalesOrderReferenceData,
} from "@/lib/contracts";
import { readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";
import { listAdvances } from "./advance-server-service";

export type AdvancePageData = {
  source: "backend";
  page: Awaited<ReturnType<typeof listAdvances>>;
  references: {
    customers: SalesOrderReferenceData["customers"];
    suppliers: PurchaseOrderReferenceData["suppliers"];
  };
};

export async function getAdvancePageData(pathname: string): Promise<AdvancePageData | null> {
  if (pathname !== "/finance/advances") return null;
  const requestId = `web-adv-page-${randomUUID()}`;
  const [pageResponse, salesRefResponse, procurementRefResponse] = await Promise.all([
    listAdvances({ type: "ALL", status: "ALL", page: 0, size: 50 }),
    requestGuanSeqApi("/api/v1/sales/order-reference-data", requestId),
    requestGuanSeqApi("/api/v1/procurement/order-reference-data", requestId),
  ]);
  if (!salesRefResponse) return null;
  if (salesRefResponse && !salesRefResponse.ok) await readApiError(salesRefResponse, "销售参考数据加载失败");
  if (!procurementRefResponse) return null;
  if (procurementRefResponse && !procurementRefResponse.ok) await readApiError(procurementRefResponse, "采购参考数据加载失败");
  const salesRef = salesOrderReferenceDataSchema.parse(await salesRefResponse.json());
  const procurementRef = purchaseOrderReferenceDataSchema.parse(await procurementRefResponse.json());
  return {
    source: "backend",
    page: pageResponse,
    references: {
      customers: salesRef.customers,
      suppliers: procurementRef.suppliers,
    },
  };
}
