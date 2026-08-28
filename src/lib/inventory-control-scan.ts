import type { InventoryControlReferenceData } from "./inventory-control-contracts";

export function resolveInventoryControlBalance(scan: string, references: InventoryControlReferenceData) {
  const value = scan.trim();
  if (!value.toUpperCase().startsWith("STOCK:")) return null;
  const id = value.slice(6).trim().toLowerCase();
  return references.balances.find((item) => item.id.toLowerCase() === id) ?? null;
}

export function resolveInventoryControlTarget(scan: string, warehouseId: string, references: InventoryControlReferenceData) {
  const value = scan.trim();
  const code = (value.toUpperCase().startsWith("LOC:") ? value.slice(4) : value).trim().toUpperCase();
  return references.targetLocations.find((item) => item.warehouseId === warehouseId && item.code.toUpperCase() === code) ?? null;
}
