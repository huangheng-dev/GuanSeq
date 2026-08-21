import type { MaterialIssueRecord, MaterialIssueReferenceData } from "@/lib/contracts";
import type {
  CreateMaterialIssueInput,
  MaterialIssueActionInput,
  MaterialIssuePageData,
  MaterialIssueReturnInput,
} from "@/services/material-issue-server-service";

async function parseJson<T>(response: Response): Promise<T> {
  return response.json() as Promise<T>;
}

export async function fetchMaterialIssuePage(): Promise<MaterialIssuePageData> {
  const response = await fetch("/api/production/material-issues", { cache: "no-store" });
  const payload = await parseJson<MaterialIssuePageData | { message?: string }>(response);
  if (!response.ok || !("source" in payload)) throw new Error("message" in payload && payload.message ? payload.message : "生产备料数据加载失败");
  return payload;
}

export async function createMaterialIssue(input: CreateMaterialIssueInput, requestId: string): Promise<MaterialIssueRecord> {
  const response = await fetch("/api/production/material-issues", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": requestId },
    body: JSON.stringify(input),
  });
  const payload = await parseJson<{ issue?: MaterialIssueRecord; message?: string }>(response);
  if (!response.ok || !payload.issue) throw new Error(payload.message ?? "生产领料单生成失败");
  return payload.issue;
}

export async function submitMaterialIssueAction(input: MaterialIssueActionInput, requestId: string): Promise<MaterialIssueRecord> {
  const response = await fetch("/api/production/material-issues/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": requestId },
    body: JSON.stringify({ operation: "action", ...input }),
  });
  const payload = await parseJson<{ issue?: MaterialIssueRecord; message?: string }>(response);
  if (!response.ok || !payload.issue) throw new Error(payload.message ?? "生产领料动作失败");
  return payload.issue;
}

export async function submitMaterialIssueReturn(input: MaterialIssueReturnInput, requestId: string): Promise<MaterialIssueRecord> {
  const response = await fetch("/api/production/material-issues/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": requestId },
    body: JSON.stringify({ operation: "return", ...input }),
  });
  const payload = await parseJson<{ issue?: MaterialIssueRecord; message?: string }>(response);
  if (!response.ok || !payload.issue) throw new Error(payload.message ?? "组件退料失败");
  return payload.issue;
}

export type { MaterialIssuePageData, MaterialIssueReferenceData };
