import "server-only";

import { randomUUID } from "node:crypto";
import { inventoryControlReferenceDataSchema, stockCountPageSchema, stockCountTaskSchema, transferPageSchema, transferTaskSchema,
  type InventoryControlReferenceData, type StockCountTask, type TransferTask } from "@/lib/inventory-control-contracts";
import { readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

const PATHS = new Set(["/warehouse/inventory-operations/transfers", "/warehouse/counts"]);
export type InventoryControlPageData = { source: "backend" | "unavailable"; references: InventoryControlReferenceData;
  transfers: TransferTask[]; counts: StockCountTask[]; error?: string };
const emptyReferences: InventoryControlReferenceData = { balances: [], targetLocations: [] };

export async function fetchInventoryControlPageData(): Promise<InventoryControlPageData> {
  const requestId = `web-inventory-control-${randomUUID()}`;
  const [referenceResponse, transferResponse, countResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/warehouse/inventory-control-reference-data", requestId, undefined, 10000),
    requestGuanSeqApi("/api/v1/warehouse/transfer-tasks?page=0&size=100&status=ALL", `${requestId}-transfers`, undefined, 10000),
    requestGuanSeqApi("/api/v1/warehouse/stock-count-tasks?page=0&size=100&status=ALL", `${requestId}-counts`, undefined, 10000),
  ]);
  if (!referenceResponse || !transferResponse || !countResponse) return { source: "unavailable", references: emptyReferences, transfers: [], counts: [], error: "库存控制服务暂时不可用，请确认后端连接后重试。" };
  if (!referenceResponse.ok || !transferResponse.ok || !countResponse.ok) {
    const denied = [referenceResponse.status, transferResponse.status, countResponse.status].some((status) => status === 401 || status === 403);
    return { source: "unavailable", references: emptyReferences, transfers: [], counts: [], error: denied ? "当前账号无权执行调拨与盘点。" : "调拨与盘点数据加载失败，请稍后刷新。" };
  }
  return { source: "backend", references: inventoryControlReferenceDataSchema.parse(await referenceResponse.json()),
    transfers: transferPageSchema.parse(await transferResponse.json()).items, counts: stockCountPageSchema.parse(await countResponse.json()).items };
}

export async function mutateInventoryControl(input: Record<string, unknown>, requestId: string) {
  const action = String(input.action ?? ""); const id = typeof input.id === "string" ? input.id : "";
  let path: string; let schema: typeof transferTaskSchema | typeof stockCountTaskSchema;
  if (action === "TRANSFER_CREATE") { path = "/api/v1/warehouse/transfer-tasks"; schema = transferTaskSchema; }
  else if (action.startsWith("TRANSFER_")) { path = `/api/v1/warehouse/transfer-tasks/${id}/${action.slice(9).toLowerCase()}`; schema = transferTaskSchema; }
  else if (action === "COUNT_CREATE") { path = "/api/v1/warehouse/stock-count-tasks"; schema = stockCountTaskSchema; }
  else { const operation = action === "COUNT_RECORD" ? "record-count" : action.slice(6).toLowerCase(); path = `/api/v1/warehouse/stock-count-tasks/${id}/${operation}`; schema = stockCountTaskSchema; }
  const { action: _action, id: _id, ...body } = input; void _action; void _id;
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) }, 10000);
  if (!response) throw new Error("库存控制服务当前无响应，未形成业务事实");
  if (!response.ok) await readApiError(response, "库存控制操作失败");
  return schema.parse(await response.json());
}

export async function getInventoryControlPageData(pathname: string) { return PATHS.has(pathname) ? fetchInventoryControlPageData() : null; }
