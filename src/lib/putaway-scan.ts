import type { PutawayReferenceData } from "./putaway-contracts";

export function normalizeStockScan(value: string) {
  const normalized = value.trim();
  return normalized.toUpperCase().startsWith("STOCK:") ? normalized.slice(6).trim().toLowerCase() : "";
}

export function normalizeLocationScan(value: string) {
  const normalized = value.trim();
  return (normalized.toUpperCase().startsWith("LOC:") ? normalized.slice(4) : normalized).trim().toUpperCase();
}

export function resolvePutawaySource(value: string, references: PutawayReferenceData) {
  const id = normalizeStockScan(value);
  return id ? references.sourceBalances.find((item) => item.id.toLowerCase() === id) ?? null : null;
}

export function resolvePutawayTarget(value: string, warehouseId: string, references: PutawayReferenceData) {
  const code = normalizeLocationScan(value);
  return code ? references.targetLocations.find((item) => item.warehouseId === warehouseId && item.code.toUpperCase() === code) ?? null : null;
}

