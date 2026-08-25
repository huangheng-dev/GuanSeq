import { afterEach, describe, expect, it, vi } from "vitest";

import type { EquipmentAsset } from "@/lib/contracts";
import {
  EquipmentAssetClientError,
  loadEquipmentAssetDetail,
  refreshEquipmentAssets,
  submitEquipmentAssetMutation,
} from "./equipment-asset-client-service";

const asset: EquipmentAsset = {
  id: "a1000000-0000-4000-8000-000000000001", assetCode: "EQ-CNC-001", assetName: "加工中心",
  category: "PRODUCTION", manufacturer: null, model: null, serialNumber: null, workCenterCode: null,
  workCenterName: null, location: "机加车间", responsiblePerson: "周凯", commissioningDate: null,
  operatingStatus: "IDLE", statusChangedAt: "2026-08-25T01:00:00Z", version: 0,
  createdAt: "2026-08-25T01:00:00Z", updatedAt: "2026-08-25T01:00:00Z", events: [],
};

afterEach(() => vi.unstubAllGlobals());

describe("equipment asset client service", () => {
  it("parses page and detail responses", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ page: { items: [asset], totalElements: 1, page: 0, size: 200, totalPages: 1, canMaintain: true } }))
      .mockResolvedValueOnce(Response.json({ asset }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(refreshEquipmentAssets()).resolves.toEqual(expect.objectContaining({ canMaintain: true }));
    await expect(loadEquipmentAssetDetail(asset.id)).resolves.toEqual(asset);
    expect(fetchMock.mock.calls[1][0]).toContain(encodeURIComponent(asset.id));
  });

  it("sends reason and optimistic version for a controlled status action", async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ asset: { ...asset, operatingStatus: "RUNNING", version: 1 } }));
    vi.stubGlobal("fetch", fetchMock);
    await submitEquipmentAssetMutation({ operation: "action", id: asset.id, action: "START", reason: "白班任务开机", expectedVersion: 0 });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ operation: "action", id: asset.id, action: "START", reason: "白班任务开机", expectedVersion: 0 });
  });

  it("retains conflict status and backend request id", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ message: "设备状态已经变化", requestId: "req-equipment-conflict" }, { status: 409 })));
    await expect(submitEquipmentAssetMutation({ operation: "action", id: asset.id, action: "START", reason: "白班任务开机", expectedVersion: 0 })).rejects.toEqual(
      expect.objectContaining<Partial<EquipmentAssetClientError>>({ message: "设备状态已经变化", status: 409, requestId: "req-equipment-conflict" }),
    );
  });
});
