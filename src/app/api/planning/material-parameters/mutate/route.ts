import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { updatePlanningParameter } from "@/services/planning-parameter-server-service";
const schema = z.object({ materialId: z.string().uuid(), leadTimeDays: z.number().int().min(1).max(3650), expectedVersion: z.number().int().nonnegative() });
export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID(); const parsed = schema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "计划参数无效", requestId }, { status: 400 });
  try { const result = await updatePlanningParameter(parsed.data.materialId, parsed.data.leadTimeDays, parsed.data.expectedVersion, requestId); return Response.json(result, { headers: { "X-Request-Id": result.requestId } }); }
  catch (error) { return Response.json({ message: error instanceof Error ? error.message : "计划参数保存失败", requestId }, { status: error instanceof GuanSeqApiError ? error.status : 500 }); }
}
