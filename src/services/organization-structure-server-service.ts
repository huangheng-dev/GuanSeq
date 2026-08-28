import "server-only";
import { randomUUID } from "node:crypto";
import { organizationStructurePageSchema, type OrganizationStructurePage } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type OrganizationStructurePageData =
  | { source: "backend"; page: OrganizationStructurePage; requestId: string }
  | { source: "unavailable"; page: null; status: number; message: string; requestId: string };

async function message(response: Response, fallback: string) {
  try { return ((await response.json()) as { message?: string }).message ?? fallback; } catch { return fallback; }
}
export async function loadOrganizationStructurePage(requestId: string): Promise<OrganizationStructurePageData> {
  const response = await requestGuanSeqApi("/api/v1/identity/organization-structure", requestId);
  if (!response) return { source: "unavailable", page: null, status: 503, message: "组织治理服务暂时不可用", requestId };
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  if (!response.ok) return { source: "unavailable", page: null, status: response.status,
    message: await message(response, response.status === 403 ? "当前角色无权治理组织" : "组织治理服务加载失败"), requestId: responseRequestId };
  return { source: "backend", page: organizationStructurePageSchema.parse(await response.json()), requestId: responseRequestId };
}
export async function getOrganizationStructurePageData(pathname: string) {
  if (pathname !== "/settings/organization/structure") return null;
  return loadOrganizationStructurePage(`web-organization-${randomUUID()}`);
}

export type OrganizationMutation =
  | { action: "createSite"; code: string; name: string; responsibleUserId: string | null }
  | { action: "updateUnit"; unitId: string; name: string; responsibleUserId: string | null; expectedVersion: number }
  | { action: "changeUnitStatus"; unitId: string; nextStatus: "ACTIVE" | "INACTIVE"; expectedVersion: number; reason: string }
  | { action: "updateWorkspace"; name: string; responsibleUserId: string | null; expectedVersion: number }
  | { action: "assignMember"; userId: string; organizationUnitId: string; expectedMembershipVersion: number; reason: string };

export async function mutateOrganization(input: OrganizationMutation, requestId: string) {
  let path = "/api/v1/identity/organization-structure/units"; let method = "POST"; let body: Record<string, unknown>;
  if (input.action === "createSite") body = { code: input.code, name: input.name, responsibleUserId: input.responsibleUserId };
  else if (input.action === "updateUnit") { path += `/${input.unitId}`; method = "PUT"; body = { name: input.name, responsibleUserId: input.responsibleUserId, expectedVersion: input.expectedVersion }; }
  else if (input.action === "changeUnitStatus") { path += `/${input.unitId}/actions`; body = { action: input.nextStatus === "ACTIVE" ? "ACTIVATE" : "DEACTIVATE", expectedVersion: input.expectedVersion, reason: input.reason }; }
  else if (input.action === "updateWorkspace") { path = "/api/v1/identity/organization-structure/workspace"; method = "PUT"; body = { name: input.name, responsibleUserId: input.responsibleUserId, expectedVersion: input.expectedVersion }; }
  else { path = `/api/v1/identity/organization-structure/members/${input.userId}`; method = "PUT"; body = { organizationUnitId: input.organizationUnitId, expectedMembershipVersion: input.expectedMembershipVersion, reason: input.reason }; }
  const response = await requestGuanSeqApi(path, requestId, { method, body: JSON.stringify(body) });
  if (!response) throw new GuanSeqApiError("组织治理服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "组织操作失败");
  return { page: organizationStructurePageSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
