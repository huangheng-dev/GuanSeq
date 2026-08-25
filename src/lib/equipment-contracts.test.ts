import { describe, expect, it } from "vitest";

import { equipmentAssetPageSchema, equipmentAssetSchema } from "./contracts";

const asset = {
  id: "a1000000-0000-4000-8000-000000000001",
  assetCode: "EQ-CNC-001",
  assetName: "一号精密加工中心",
  category: "PRODUCTION",
  manufacturer: null,
  model: "VMC-850",
  serialNumber: null,
  workCenterCode: "WC-CNC-01",
  workCenterName: "数控加工中心",
  location: "机加车间 A-01",
  responsiblePerson: "周凯",
  commissioningDate: "2025-03-18",
  operatingStatus: "RUNNING",
  statusChangedAt: "2026-08-25T01:10:00Z",
  version: 1,
  createdAt: "2026-08-20T01:00:00Z",
  updatedAt: "2026-08-25T01:10:00Z",
  events: [{
    id: "a2000000-0000-4000-8000-000000000001",
    actorUserId: "20000000-0000-4000-8000-000000000001",
    action: "STARTED",
    fromStatus: "IDLE",
    toStatus: "RUNNING",
    reason: "白班生产任务开机",
    requestId: "equipment-start-001",
    details: { statusSource: "MANUAL" },
    occurredAt: "2026-08-25T01:10:00Z",
  }],
};

describe("equipment contracts", () => {
  it("parses manual status evidence and the maintenance permission hint", () => {
    expect(equipmentAssetSchema.parse(asset).events[0].details.statusSource).toBe("MANUAL");
    expect(equipmentAssetPageSchema.parse({ items: [asset], totalElements: 1, page: 0, size: 200, totalPages: 1, canMaintain: true }).canMaintain).toBe(true);
  });

  it("rejects telemetry-like unknown status and missing concurrency version", () => {
    expect(() => equipmentAssetSchema.parse({ ...asset, operatingStatus: "ONLINE" })).toThrow();
    const withoutVersion: Record<string, unknown> = { ...asset };
    delete withoutVersion.version;
    expect(() => equipmentAssetSchema.parse(withoutVersion)).toThrow();
  });
});
