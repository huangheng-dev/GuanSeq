import type { OperationLaborEntry, OperationTaskRecord } from "@/lib/contracts";
import type { OperationLaborActionInput, OperationLaborCreateInput, OperationTaskActionInput, OperationTaskPageData } from "@/services/operation-task-server-service";

async function parseJson<T>(response: Response): Promise<T> {
  return response.json() as Promise<T>;
}

export async function fetchOperationTaskPage(): Promise<OperationTaskPageData> {
  const response = await fetch("/api/production/operation-tasks", { cache: "no-store" });
  const payload = await parseJson<OperationTaskPageData | { message?: string }>(response);
  if (!response.ok || !("source" in payload)) {
    throw new Error("message" in payload && payload.message ? payload.message : "车间工序任务加载失败");
  }
  return payload;
}

export async function submitOperationTaskAction(input: OperationTaskActionInput, requestId: string): Promise<OperationTaskRecord> {
  const response = await fetch("/api/production/operation-tasks/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": requestId },
    body: JSON.stringify(input),
  });
  const payload = await parseJson<{ task?: OperationTaskRecord; message?: string }>(response);
  if (!response.ok || !payload.task) throw new Error(payload.message ?? "车间工序动作失败");
  return payload.task;
}

export async function submitOperationLaborMutation(input: OperationLaborCreateInput | OperationLaborActionInput, requestId: string): Promise<OperationLaborEntry> {
  const response = await fetch("/api/production/operation-labor-entries/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": requestId },
    body: JSON.stringify(input),
  });
  const payload = await parseJson<{ laborEntry?: OperationLaborEntry; message?: string }>(response);
  if (!response.ok || !payload.laborEntry) throw new Error(payload.message ?? "实际人工工时操作失败");
  return payload.laborEntry;
}
