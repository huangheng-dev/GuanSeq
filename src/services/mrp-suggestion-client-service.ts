import type { MrpSuggestion } from "@/lib/contracts";
import type { MrpSuggestionMutation } from "@/services/mrp-suggestion-server-service";

export async function submitMrpSuggestionMutation(input: MrpSuggestionMutation, requestId: string): Promise<MrpSuggestion> {
  const response = await fetch("/api/planning/mrp-suggestions/mutate", { method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": requestId }, body: JSON.stringify(input) });
  const result = await response.json().catch(() => null) as { suggestion?: MrpSuggestion; message?: string } | null;
  if (!response.ok || !result?.suggestion) throw new Error(result?.message ?? "MRP 建议操作失败，请重试");
  return result.suggestion;
}
