import { z } from "zod";

import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { mutateMasterData } from "@/services/master-data-server-service";

const mutationSchema = z.object({
  pathname: z.enum(["/sales/customers/list", "/product/materials/list", "/procurement/suppliers/list"]),
  action: z.enum(["create", "update", "delete", "restore", "batch"]),
  values: z.record(z.string(), z.string()),
  records: z.array(z.object({ id: z.string().uuid(), version: z.number().int().nonnegative().optional() })).optional(),
});

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json({ message: "主数据操作参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  }
  try {
    const result = await mutateMasterData({ ...parsed.data, records: parsed.data.records?.map((r) => ({ id: r.id, version: r.version ?? 0 })) }, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    const message = error instanceof Error ? error.message : "主数据服务发生未预期错误";
    return Response.json({ message, requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
