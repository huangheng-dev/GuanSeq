import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { createMaterialIssue, fetchMaterialIssuePageData } from "@/services/material-issue-server-service";

const createSchema = z.object({
  productionOrderId: z.string().uuid(),
  warehouseId: z.string().uuid(),
});

export async function GET() {
  const data = await fetchMaterialIssuePageData();
  return Response.json(data, { headers: { "X-Request-Id": crypto.randomUUID() } });
}

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = createSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "生成领料单参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await createMaterialIssue(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json({ message: error instanceof Error ? error.message : "生产备料服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
