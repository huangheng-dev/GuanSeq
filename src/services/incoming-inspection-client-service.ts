import type { IncomingInspectionRecord } from "@/lib/contracts";
import type { CompleteIncomingInspectionPayload } from "@/services/incoming-inspection-server-service";

export async function submitCompleteIncomingInspection(payload: CompleteIncomingInspectionPayload): Promise<IncomingInspectionRecord> {
  const response = await fetch("/api/quality/incoming-inspections/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-iqc-${crypto.randomUUID()}` },
    body: JSON.stringify(payload),
  });
  const data = await response.json().catch(() => null) as { inspection?: IncomingInspectionRecord; message?: string } | null;
  if (!response.ok || !data?.inspection) throw new Error(data?.message ?? "来料检验判定失败，请重试");
  return data.inspection;
}
