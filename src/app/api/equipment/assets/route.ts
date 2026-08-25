import { z } from "zod";

import { equipmentAssetActionSchema, equipmentAssetCategorySchema } from "@/lib/contracts";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import {
  loadEquipmentAsset,
  loadEquipmentAssetPage,
  mutateEquipmentAsset,
} from "@/services/equipment-asset-server-service";

const writePayload = z.object({
  assetName: z.string().trim().min(1).max(120),
  category: equipmentAssetCategorySchema,
  manufacturer: z.string().trim().max(120).nullable().optional(),
  model: z.string().trim().max(120).nullable().optional(),
  serialNumber: z.string().trim().max(120).nullable().optional(),
  workCenterCode: z.string().trim().max(40).nullable().optional(),
  workCenterName: z.string().trim().max(120).nullable().optional(),
  location: z.string().trim().min(1).max(160),
  responsiblePerson: z.string().trim().min(1).max(80),
  commissioningDate: z.string().date().nullable().optional(),
  reason: z.string().trim().min(4).max(500),
});
const mutationSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("create"), assetCode: z.string().trim().min(1).max(40), payload: writePayload }),
  z.object({ operation: z.literal("update"), id: z.string().uuid(), payload: writePayload.extend({ expectedVersion: z.number().int().nonnegative() }) }),
  z.object({ operation: z.literal("action"), id: z.string().uuid(), action: equipmentAssetActionSchema, reason: z.string().trim().min(4).max(500), expectedVersion: z.number().int().nonnegative() }),
]);

function requestIdFrom(request: Request) { return request.headers.get("X-Request-Id") ?? crypto.randomUUID(); }

function errorResponse(error: unknown, requestId: string, fallback: string) {
  const status = error instanceof GuanSeqApiError ? error.status : 500;
  return Response.json({ message: error instanceof Error ? error.message : fallback, requestId },
    { status, headers: { "X-Request-Id": requestId } });
}

export async function GET(request: Request) {
  const requestId = requestIdFrom(request);
  const id = new URL(request.url).searchParams.get("id");
  try {
    if (id) {
      const parsedId = z.string().uuid().safeParse(id);
      if (!parsedId.success) return Response.json({ message: "设备编号参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
      const result = await loadEquipmentAsset(parsedId.data, requestId);
      return Response.json({ asset: result.asset }, { headers: { "X-Request-Id": result.requestId } });
    }
    const result = await loadEquipmentAssetPage(requestId);
    if (result.source === "unavailable") return Response.json({ message: result.message, requestId: result.requestId }, { status: result.status, headers: { "X-Request-Id": result.requestId } });
    return Response.json({ page: result.page }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "设备台账服务发生未预期错误"); }
}

export async function POST(request: Request) {
  const requestId = requestIdFrom(request);
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "设备操作参数无效，请检查必填项、原因和版本", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateEquipmentAsset(parsed.data, requestId);
    return Response.json({ asset: result.asset }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "设备台账服务发生未预期错误"); }
}
