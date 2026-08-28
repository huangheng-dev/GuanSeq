import { labelPrintRequestSchema, labelReferenceDataSchema, labelPrintRequestPageSchema,
  type LabelMode, type LabelObjectType } from "@/lib/labeling-contracts";

export async function loadLabelingData() {
  const response = await fetch("/api/labeling", { cache: "no-store" });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.message ?? "标签数据刷新失败");
  return { references: labelReferenceDataSchema.parse(body.references),
    requests: labelPrintRequestPageSchema.parse({ items: body.requests, total: body.requests.length, page: 0,
      size: Math.max(1, body.requests.length), totalPages: body.requests.length ? 1 : 0 }).items };
}

export async function submitLabelPrintRequest(input: { objectType: LabelObjectType; objectId: string;
  expectedObjectVersion: number; mode: LabelMode; copies: number; reason: string | null }, requestId: string) {
  const response = await fetch("/api/labeling", { method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": requestId },
    body: JSON.stringify(input) });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.message ?? "标签打印准备失败；未形成业务事实");
  return labelPrintRequestSchema.parse(body);
}

