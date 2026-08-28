import { describe, expect, it } from "vitest";
import { normalizeLocationScan, normalizeStockScan, resolvePutawaySource, resolvePutawayTarget } from "./putaway-scan";
import type { PutawayReferenceData } from "./putaway-contracts";

const references = { sourceBalances: [{ id: "73000000-0000-4000-8000-000000000011", version: 0, warehouseId: "71000000-0000-4000-8000-000000000001",
  warehouseCode: "WH-RM", warehouseName: "原材料仓", locationId: "72000000-0000-4000-8000-000000000002", locationCode: "IQC-06", locationName: "待检区",
  materialCode: "BR-6204", materialName: "轴承", materialSpecification: null, lotNumber: "LOT-1", unit: "件", availableQuantity: 12, reservedOpenQuantity: 0 }],
  targetLocations: [{ id: "72000000-0000-4000-8000-000000000001", warehouseId: "71000000-0000-4000-8000-000000000001", warehouseCode: "WH-RM", code: "A-01-03", name: "正式库位", scanCode: "LOC:A-01-03" }] } satisfies PutawayReferenceData;

describe("putaway scan resolution", () => {
  it("only accepts a full STOCK token", () => { expect(normalizeStockScan(" STOCK:73000000-0000-4000-8000-000000000011 ")).toContain("73000000"); expect(normalizeStockScan("73000000")).toBe(""); });
  it("accepts exact location code or LOC token within the source warehouse", () => { expect(normalizeLocationScan("loc:a-01-03")).toBe("A-01-03"); expect(resolvePutawayTarget("A-01-03", references.sourceBalances[0].warehouseId, references)?.name).toBe("正式库位"); });
  it("does not guess unknown objects", () => { expect(resolvePutawaySource("STOCK:73000000-0000-4000-8000-000000000099", references)).toBeNull(); expect(resolvePutawayTarget("A-01", references.sourceBalances[0].warehouseId, references)).toBeNull(); });
});

