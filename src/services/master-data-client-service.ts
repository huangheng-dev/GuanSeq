import type { BusinessRow } from "@/lib/business-page-data";

export type MasterDataMutationInput = {
  pathname: string;
  action: "create" | "update" | "delete" | "restore" | "batch";
  values: Record<string, string>;
  records?: Array<{ id: string; version: number }>;
};

export type MasterDataMutationResult = {
  source: "backend" | "mock";
  requestId: string;
  savedAt: string;
  row?: BusinessRow;
  rows?: BusinessRow[];
};

export function isBackendMasterDataPath(pathname: string) {
  return pathname === "/sales/customers/list" || pathname === "/product/materials/list";
}

export async function submitMasterDataMutation(input: MasterDataMutationInput): Promise<MasterDataMutationResult> {
  const response = await fetch("/api/masterdata/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-mutation-${crypto.randomUUID()}` },
    body: JSON.stringify(input),
  });
  const payload = await response.json().catch(() => null) as MasterDataMutationResult | { message?: string } | null;
  if (!response.ok) throw new Error(payload && "message" in payload ? payload.message ?? "主数据操作失败" : "主数据操作失败");
  return payload as MasterDataMutationResult;
}
