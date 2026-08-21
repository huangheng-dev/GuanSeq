import type { RoutingRecord } from "@/lib/contracts";
import type { RoutingMutation } from "@/services/routing-server-service";

export async function submitRoutingMutation(input: RoutingMutation): Promise<RoutingRecord> {
  const response = await fetch("/api/product/routings", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-routing-${crypto.randomUUID()}` },
    body: JSON.stringify(input),
  });
  const payload = await response.json().catch(() => null) as { routing?: RoutingRecord; message?: string } | null;
  if (!response.ok || !payload?.routing) throw new Error(payload?.message ?? "工艺路线操作失败，请重试");
  return payload.routing;
}
