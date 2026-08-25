import { equipmentSparePartPageSchema, equipmentSparePartReferenceSchema, equipmentSparePartSchema,
  type EquipmentSparePart, type EquipmentSparePartPage, type EquipmentSparePartReference } from "@/lib/contracts";
import type { EquipmentSparePartMutation } from "@/services/equipment-spare-part-server-service";

export class EquipmentSparePartClientError extends Error {
  constructor(message: string, readonly status: number, readonly requestId?: string) { super(message); }
}
async function payload(response: Response) { return await response.json().catch(() => null) as Record<string, unknown> | null; }
function failure(response: Response, body: Record<string, unknown> | null, fallback: string) {
  return new EquipmentSparePartClientError(typeof body?.message === "string" ? body.message : fallback, response.status,
    typeof body?.requestId === "string" ? body.requestId : response.headers.get("X-Request-Id") ?? undefined);
}
export async function refreshEquipmentSpareParts(): Promise<{ page: EquipmentSparePartPage; references: EquipmentSparePartReference }> {
  const response = await fetch("/api/equipment/spare-parts", { cache: "no-store" }); const body = await payload(response);
  if (!response.ok || !body?.page || !body.references) throw failure(response, body, "设备备件台账刷新失败");
  return { page: equipmentSparePartPageSchema.parse(body.page), references: equipmentSparePartReferenceSchema.parse(body.references) };
}
export async function submitEquipmentSparePart(input: EquipmentSparePartMutation): Promise<EquipmentSparePart> {
  const response = await fetch("/api/equipment/spare-parts", { method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-equipment-spare-${crypto.randomUUID()}` },
    body: JSON.stringify(input) }); const body = await payload(response);
  if (!response.ok || !body?.sparePart) throw failure(response, body, "备件台账建立失败");
  return equipmentSparePartSchema.parse(body.sparePart);
}
