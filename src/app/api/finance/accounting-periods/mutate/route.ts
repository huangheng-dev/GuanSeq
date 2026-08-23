import { z } from "zod";
import {
  closeAccountingPeriod,
  createAccountingPeriod,
  reopenAccountingPeriod,
} from "@/services/accounting-period-server-service";
import { GuanSeqApiError } from "@/services/guanseq-api-server";

const createSchema = z.object({
  fiscalYear: z.number().int().min(2020).max(2099),
  fiscalPeriod: z.number().int().min(1).max(12),
});

const closeSchema = z.object({
  id: z.string().uuid(),
});

const reopenSchema = z.object({
  id: z.string().uuid(),
  reason: z.string().min(4).max(500),
  expectedVersion: z.number().int().nonnegative().optional(),
});

const requestSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("create"), payload: createSchema }),
  z.object({ operation: z.literal("close"), payload: closeSchema }),
  z.object({ operation: z.literal("reopen"), payload: reopenSchema }),
]);

export async function POST(request: Request) {
  const requestId =
    request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = requestSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json(
      { message: "会计期间操作参数无效", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } },
    );
  }
  try {
    let result;
    switch (parsed.data.operation) {
      case "create":
        result = await createAccountingPeriod(parsed.data.payload, requestId);
        return Response.json(
          { period: result.period },
          { headers: { "X-Request-Id": result.requestId } },
        );
      case "close":
        result = await closeAccountingPeriod(parsed.data.payload.id, requestId);
        return Response.json(
          { period: result.period },
          { headers: { "X-Request-Id": result.requestId } },
        );
      case "reopen": {
        const { id, ...payload } = parsed.data.payload;
        result = await reopenAccountingPeriod(id, payload, requestId);
        return Response.json(
          { period: result.period },
          { headers: { "X-Request-Id": result.requestId } },
        );
      }
    }
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      {
        message: error instanceof Error ? error.message : "会计期间服务发生未预期错误",
        requestId,
      },
      { status, headers: { "X-Request-Id": requestId } },
    );
  }
}
