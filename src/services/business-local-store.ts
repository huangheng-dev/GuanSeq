import type { BusinessRow } from "@/lib/business-page-data";

const storagePrefix = "guanseq-business-rows:";

function isBusinessRow(value: unknown): value is BusinessRow {
  if (!value || typeof value !== "object") return false;
  const row = value as Partial<BusinessRow>;
  return typeof row.id === "string"
    && Array.isArray(row.cells)
    && row.cells.every((cell) => typeof cell === "string")
    && typeof row.status === "string"
    && typeof row.owner === "string"
    && typeof row.description === "string";
}

export function readStoredBusinessRows(pathname: string): BusinessRow[] | null {
  if (typeof window === "undefined") return null;
  try {
    const value = JSON.parse(window.localStorage.getItem(`${storagePrefix}${pathname}`) ?? "null") as unknown;
    return Array.isArray(value) && value.every(isBusinessRow) ? value : null;
  } catch {
    return null;
  }
}

export function writeStoredBusinessRows(pathname: string, rows: BusinessRow[]) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(`${storagePrefix}${pathname}`, JSON.stringify(rows));
  } catch {
    // 禁用本地存储时保留当前会话状态，不阻断业务操作。
  }
}
