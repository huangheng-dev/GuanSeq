import { z } from "zod";
import {
  reverseGrirAccrual,
  runGrirAccrual,
} from "@/services/grir-accrual-server-service";
import { GuanSeqApiError } from "@/services/guanseq-api-server";

const runSchema = z.object({
  fiscalYear: z.number().int().min(2000).max(2100),
  fiscalPeriod: z.number().int().min(1).max(12),
  accrualDate: z.string().date().optional(),
  note: z.string().max(500).optional(),
});

const reverseSchema = z.object({
  id: z.string().uuid(),
  reversalDate: z.string().date(),
  reason: z.string().min(4).max(500),
});

const requestSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("run"), payload: runSchema }),
  z.object({ operation: z.literal("reverse"), payload: reverseSchema }),
]);

export async function POST(request: Request) {
  const requestId =
    request.headers.get("X-Request-Id") ?? `web-grir-mutate-${crypto.randomUUID()}`;
  const parsed = requestSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json(
      { message: "暂估操作参数无效", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } },
    );
  }
  try {
    switch (parsed.data.operation) {
      case "run": {
        const result = await runGrirAccrual(parsed.data.payload, requestId);
        return Response.json(
          { accrual: result.accrual },
          { headers: { "X-Request-Id": result.requestId } },
        );
      }
      case "reverse": {
        const { id, ...payload } = parsed.data.payload;
        const result = await reverseGrirAccrual(id, payload, requestId);
        return Response.json(
          { accrual: result.accrual },
          { headers: { "X-Request-Id": result.requestId } },
        );
      }
    }
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      {
        message: error instanceof Error ? error.message : "暂估应付业务处理失败",
        requestId,
      },
      { status, headers: { "X-Request-Id": requestId } },
    );
  }
}
