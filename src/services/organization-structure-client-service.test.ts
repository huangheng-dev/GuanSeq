import { afterEach, describe, expect, it, vi } from "vitest";
import { mutateOrganizationStructure, OrganizationClientError, refreshOrganizationStructure } from "./organization-structure-client-service";

const unit = { id: "00000000-0000-4000-8000-000000000101", code: "EAST-MFG", name: "华东制造中心", unitType: "PLANT" as const,
  parentId: "00000000-0000-4000-8000-000000000001", status: "ACTIVE" as const, responsibleUserId: null, responsibleUserName: null,
  version: 0, createdAt: "2026-08-28T00:00:00Z", updatedAt: "2026-08-28T00:00:00Z" };
const page = { currentUserId: "20000000-0000-4000-8000-000000000001",
  company: { ...unit, id: "00000000-0000-4000-8000-000000000001", code: "GS", name: "示例公司", unitType: "COMPANY" as const, parentId: null },
  operatingUnit: unit, siteUnits: [], workspace: { id: "10000000-0000-4000-8000-000000000101", code: "EAST-MFG", name: "华东制造中心",
    status: "ACTIVE" as const, operatingOrganizationId: unit.id, responsibleUserId: null, responsibleUserName: null, version: 0,
    createdAt: "2026-08-28T00:00:00Z", updatedAt: "2026-08-28T00:00:00Z" }, members: [], scopeDescription: "当前工作区范围" };

afterEach(() => vi.unstubAllGlobals());
describe("organization structure client service", () => {
  it("parses the current-workspace organization facts", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ page })));
    await expect(refreshOrganizationStructure()).resolves.toEqual(page);
  });
  it("sends an audited member assignment mutation", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ page })); vi.stubGlobal("fetch", fetch);
    await mutateOrganizationStructure({ action: "assignMember", userId: page.currentUserId, organizationUnitId: unit.id, expectedMembershipVersion: 0, reason: "调整现场责任归属" });
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toMatchObject({ action: "assignMember", reason: "调整现场责任归属" });
  });
  it("keeps request ids on business conflicts", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ message: "仍有启用成员", requestId: "org-conflict-1" }, { status: 409 })));
    await expect(refreshOrganizationStructure()).rejects.toEqual(expect.objectContaining<Partial<OrganizationClientError>>({ message: "仍有启用成员", requestId: "org-conflict-1" }));
  });
});
