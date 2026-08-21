import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { completeFinalInspection } from "@/services/production-execution-server-service";

const mutation = z.object({ id: z.string().uuid(), acceptedQuantity: z.number().nonnegative(), rejectedQuantity: z.number().nonnegative(), inspector: z.string().min(1).max(80), defectDescription: z.string().max(500).nullable(), conclusion: z.string().min(1).max(500), expectedVersion: z.number().int().nonnegative() });

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutation.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "完工检验参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try { const result = await completeFinalInspection(parsed.data, requestId); return Response.json(result, { headers: { "X-Request-Id": result.requestId } }); }
  catch (error) { const status = error instanceof GuanSeqApiError ? error.status : 500; return Response.json({ message: error instanceof Error ? error.message : "质量检验服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } }); }
}
