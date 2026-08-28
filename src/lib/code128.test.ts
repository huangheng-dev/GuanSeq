import { describe, expect, it } from "vitest";
import { encodeCode128B } from "./code128";

describe("Code 128B label encoder", () => {
  it("encodes all controlled GuanSeq payload shapes with quiet zones", () => {
    for (const payload of ["OT:OT-260815-900001", "EMP:lin.hao", "STOCK:73000000-0000-4000-8000-000000000001"]) {
      const result = encodeCode128B(payload);
      expect(result.bars.length).toBeGreaterThan(payload.length * 2);
      expect(result.bars[0].x).toBe(10);
      expect(result.width - Math.max(...result.bars.map((bar) => bar.x + bar.width))).toBeGreaterThanOrEqual(10);
    }
  });

  it("rejects empty and non-ASCII payloads instead of emitting an invalid barcode", () => {
    expect(() => encodeCode128B("")).toThrow();
    expect(() => encodeCode128B("EMP:林浩")).toThrow();
  });
});

