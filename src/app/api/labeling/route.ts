import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { fetchLabelingPageData, prepareLabelPrintRequest } from "@/services/labeling-server-service";

const mutation = z.object({ objectType: z.enum(["OPERATION_TASK", "EMPLOYEE", "STOCK_BALANCE"]), objectId: z.string().uuid(),
  expectedObjectVersion: z.number().int().nonnegative(), mode: z.enum(["INITIAL", "REPRINT"]), copies: z.number().int().min(1).max(10),
  reason: z.string().max(300).nullable() });

export async function GET() {
  const data = await fetchLabelingPageData();
  return Response.json(data, { status: data.source === "backend" ? 200 : 503 });
}

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutation.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "标签打印准备参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await prepareLabelPrintRequest(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : error instanceof Error && error.message.includes("无响应") ? 503 : 500;
    return Response.json({ message: error instanceof Error ? error.message : "标签服务发生未预期错误", requestId },
      { status, headers: { "X-Request-Id": requestId } });
  }
}

