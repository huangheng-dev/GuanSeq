import "server-only";

import { randomUUID } from "node:crypto";
import { operationTaskPageSchema, productionOrderPageSchema, workspaceSessionSchema,
  type OperationTaskRecord, type ProductionOrderRecord } from "@/lib/contracts";
import { requestGuanSeqApi } from "@/services/guanseq-api-server";

const MOBILE_REPORTING_PATH = "/production/mobile-operations/reporting-scan";

export type MobileProductionReportingPageData = {
  source: "backend" | "unavailable";
  canControl: boolean;
  tasks: OperationTaskRecord[];
  orders: ProductionOrderRecord[];
  operator: { username: string; displayName: string } | null;
  error?: string;
};

export async function fetchMobileProductionReportingPageData(): Promise<MobileProductionReportingPageData> {
  const requestId = `web-mobile-production-reporting-${randomUUID()}`;
  const [tasksResponse, ordersResponse, sessionResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/production/operation-tasks?page=0&size=100&status=ALL", requestId, undefined, 10000),
    requestGuanSeqApi("/api/v1/production/orders?page=0&size=100&status=ALL", `${requestId}-orders`, undefined, 10000),
    requestGuanSeqApi("/api/v1/me/workspaces", `${requestId}-operator`, undefined, 10000),
  ]);
  if (!tasksResponse || !ordersResponse || !sessionResponse) return { source: "unavailable", canControl: false, tasks: [], orders: [], operator: null,
    error: "生产扫码报工服务暂时不可用，请稍后刷新重试。" };
  if (!tasksResponse.ok || !ordersResponse.ok || !sessionResponse.ok) {
    const denied = [tasksResponse.status, ordersResponse.status].some((status) => status === 401 || status === 403);
    return { source: "unavailable", canControl: false, tasks: [], orders: [], operator: null,
      error: denied ? "当前账号无权执行生产扫码报工，请联系管理员授权。" : "生产扫码报工参考数据加载失败，请稍后刷新重试。" };
  }
  const session = workspaceSessionSchema.parse(await sessionResponse.json());
  return { source: "backend", canControl: true,
    tasks: operationTaskPageSchema.parse(await tasksResponse.json()).items,
    orders: productionOrderPageSchema.parse(await ordersResponse.json()).items,
    operator: { username: session.username, displayName: session.displayName } };
}

export async function getMobileProductionReportingPageData(pathname: string) {
  return pathname === MOBILE_REPORTING_PATH ? fetchMobileProductionReportingPageData() : null;
}
