import { organizationStructurePageSchema, type OrganizationStructurePage } from "@/lib/contracts";
import type { OrganizationMutation } from "@/services/organization-structure-server-service";

export class OrganizationClientError extends Error { constructor(message: string, readonly requestId?: string) { super(message); } }
async function parse(response: Response): Promise<OrganizationStructurePage> {
  const payload = await response.json().catch(() => null) as Record<string, unknown> | null;
  if (!response.ok || !payload) throw new OrganizationClientError(typeof payload?.message === "string" ? payload.message : "组织操作失败",
    typeof payload?.requestId === "string" ? payload.requestId : response.headers.get("X-Request-Id") ?? undefined);
  return organizationStructurePageSchema.parse(payload.page);
}
export async function refreshOrganizationStructure() { return parse(await fetch("/api/identity/organization-structure", { cache: "no-store" })); }
export async function mutateOrganizationStructure(input: OrganizationMutation) {
  return parse(await fetch("/api/identity/organization-structure", { method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-org-${crypto.randomUUID()}` }, body: JSON.stringify(input) }));
}
