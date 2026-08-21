import { z } from "zod";
import { inventoryMovementTypeSchema } from "@/lib/contracts";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { postInventoryMovement } from "@/services/inventory-server-service";

const movementSchema = z.object({ id: z.string().uuid(), movementType: inventoryMovementTypeSchema, quantity: z.number().positive(), reason: z.string().trim().min(1).max(500), expectedVersion: z.number().int().nonnegative() });

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = movementSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "库存事务参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await postInventoryMovement(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    const message = error instanceof Error ? error.message : "库存服务发生未预期错误";
    return Response.json({ message, requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
