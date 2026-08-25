import { afterEach, describe, expect, it, vi } from "vitest";

import { EquipmentSparePartClientError, refreshEquipmentSpareParts, submitEquipmentSparePart } from "./equipment-spare-part-client-service";

const sparePart = { id: "c1000000-0000-4000-8000-000000000001", materialId: "42000000-0000-4000-8000-000000000003",
  materialCode: "BR-6204", materialName: "深沟球轴承", materialSpecification: "6204-2RS", unit: "件",
  preferredWarehouseId: "71000000-0000-4000-8000-000000000001", preferredWarehouseCode: "WH-RM", preferredWarehouseName: "原材料仓",
  reorderPoint: 120, availableQuantity: 400, standardUnitCost: 80, currency: "CNY", costEffectiveDate: "2026-08-15",
  costStatus: "READY", stockStatus: "SUFFICIENT", status: "ACTIVE", version: 0, updatedAt: "2026-08-25T00:45:00Z" };
const page = { items: [sparePart], totalElements: 1, page: 0, size: 200, totalPages: 1, canMaintain: true };
const references = { materials: [], warehouses: [], locations: [] };

afterEach(() => vi.unstubAllGlobals());

describe("equipment spare part client service", () => {
  it("parses live stock, cost and references", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ page, references })));
    await expect(refreshEquipmentSpareParts()).resolves.toEqual({ page, references });
  });

  it("sends create reason and keeps backend conflict evidence", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(Response.json({ sparePart }, { status: 201 }))
      .mockResolvedValueOnce(Response.json({ message: "该物料已建立备件台账", requestId: "req-spare-conflict" }, { status: 409 }));
    vi.stubGlobal("fetch", fetchMock);
    const input = { materialId: sparePart.materialId, preferredWarehouseId: sparePart.preferredWarehouseId, reorderPoint: 120, reason: "建立轴承备件用途台账" };
    await expect(submitEquipmentSparePart(input)).resolves.toEqual(sparePart);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(input);
    await expect(submitEquipmentSparePart(input)).rejects.toEqual(expect.objectContaining<Partial<EquipmentSparePartClientError>>({ status: 409, requestId: "req-spare-conflict" }));
  });
});
