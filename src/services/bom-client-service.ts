import type { BomRecord } from "@/lib/contracts";
import type { BomMutation } from "@/services/bom-server-service";

export async function submitBomMutation(input: BomMutation): Promise<BomRecord> {
  const response = await fetch("/api/product/boms", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-bom-${crypto.randomUUID()}` },
    body: JSON.stringify(input),
  });
  const payload = await response.json().catch(() => null) as { bom?: BomRecord; message?: string } | null;
  if (!response.ok || !payload?.bom) throw new Error(payload?.message ?? "BOM 操作失败，请重试");
  return payload.bom;
}
