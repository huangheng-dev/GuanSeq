import "server-only";

import { randomUUID } from "node:crypto";
import { operationLaborEntryPageSchema, operationLaborEntrySchema, operationTaskPageSchema, operationTaskRecordSchema, type OperationLaborEntry, type OperationTaskRecord } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

const OPERATION_TASK_PATHS = new Set(["/production/work-orders/operations"]);

export type OperationTaskPageData = {
  source: "backend" | "unavailable";
  tasks: OperationTaskRecord[];
  laborEntries: OperationLaborEntry[];
  error?: string;
};

export type OperationLaborCreateInput = {
  kind: "CREATE";
  taskId: string;
  workDate: string;
  shiftName: string;
  operatorName: string;
  actualMinutes: number;
  note?: string | null;
};

export type OperationLaborActionInput = {
  kind: "ACTION";
  id: string;
  action: "APPROVE" | "VOID";
  expectedVersion: number;
  reason?: string | null;
};

export type OperationTaskActionInput = {
  id: string;
  action: "START" | "COMPLETE";
  expectedVersion: number;
  shiftName?: string | null;
  operatorName?: string | null;
  completedQuantity?: number | null;
  note?: string | null;
};

function unavailable(response?: Response | null): OperationTaskPageData {
  const error = response?.status === 401 || response?.status === 403
    ? "当前账号无权查看车间工序任务，请联系管理员授权。"
    : "车间工序执行服务暂时不可用，请稍后刷新重试。";
  return { source: "unavailable", tasks: [], laborEntries: [], error };
}

export async function fetchOperationTaskPageData(): Promise<OperationTaskPageData> {
  const requestId = `web-operation-task-page-${randomUUID()}`;
  const [taskResponse, laborResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/production/operation-tasks?page=0&size=100&status=ALL", requestId, undefined, 10000),
    requestGuanSeqApi("/api/v1/production/operation-labor-entries?page=0&size=100&status=ALL", `${requestId}-labor`, undefined, 10000),
  ]);
  if (!taskResponse?.ok) return unavailable(taskResponse);
  if (!laborResponse?.ok) return unavailable(laborResponse);
  return {
    source: "backend",
    tasks: operationTaskPageSchema.parse(await taskResponse.json()).items,
    laborEntries: operationLaborEntryPageSchema.parse(await laborResponse.json()).items,
  };
}

export async function getOperationTaskPageData(pathname: string): Promise<OperationTaskPageData | null> {
  if (!OPERATION_TASK_PATHS.has(pathname)) return null;
  return fetchOperationTaskPageData();
}

export async function actOnOperationTask(input: OperationTaskActionInput, requestId: string) {
  const response = await requestGuanSeqApi(`/api/v1/production/operation-tasks/${input.id}/actions`, requestId, {
    method: "POST",
    body: JSON.stringify({
      action: input.action,
      expectedVersion: input.expectedVersion,
      shiftName: input.shiftName ?? null,
      operatorName: input.operatorName ?? null,
      completedQuantity: input.completedQuantity ?? null,
      note: input.note ?? null,
    }),
  }, 10000);
  if (!response) throw new GuanSeqApiError("车间工序执行服务暂时不可用，未保存工序动作", 503);
  if (!response.ok) await readApiError(response, input.action === "START" ? "工序开工失败" : "工序完工登记失败");
  return {
    task: operationTaskRecordSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}

export async function mutateOperationLabor(input: OperationLaborCreateInput | OperationLaborActionInput, requestId: string) {
  const path = input.kind === "CREATE"
    ? "/api/v1/production/operation-labor-entries"
    : `/api/v1/production/operation-labor-entries/${input.id}/actions`;
  const body = input.kind === "CREATE"
    ? { taskId: input.taskId, workDate: input.workDate, shiftName: input.shiftName, operatorName: input.operatorName, actualMinutes: input.actualMinutes, note: input.note ?? null }
    : { action: input.action, expectedVersion: input.expectedVersion, reason: input.reason ?? null };
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) }, 10000);
  if (!response) throw new GuanSeqApiError("实际人工工时服务暂时不可用，本次操作未保存", 503);
  if (!response.ok) await readApiError(response, input.kind === "CREATE" ? "实际人工工时登记失败" : input.action === "APPROVE" ? "实际人工工时审核失败" : "实际人工工时冲销失败");
  return {
    laborEntry: operationLaborEntrySchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}

