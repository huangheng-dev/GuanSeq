import { z } from "zod";
import {
  refundAdvance,
  registerAdvance,
} from "@/services/advance-server-service";
import { GuanSeqApiError } from "@/services/guanseq-api-server";

const registerSchema = z.object({
  type: z.enum(["RECEIVABLE", "PAYABLE"]),
  partyId: z.string().uuid(),
  advanceDate: z.string().date(),
  totalAmount: z.number().positive(),
  note: z.string().max(500).optional(),
});

const refundSchema = z.object({
  refundAmount: z.number().positive(),
  refundDate: z.string().date(),
  reason: z.string().min(4).max(500),
});

const requestSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("register"), payload: registerSchema }),
  z.object({
    operation: z.literal("refund"),
    id: z.string().uuid(),
    payload: refundSchema,
  }),
]);

export async function POST(request: Request) {
  const requestId =
    request.headers.get("X-Request-Id") ?? `web-adv-mutate-${crypto.randomUUID()}`;
  const parsed = requestSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json(
      { message: "预收预付操作参数无效", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } },
    );
  }
  try {
    switch (parsed.data.operation) {
      case "register": {
        const result = await registerAdvance(parsed.data.payload, requestId);
        return Response.json(
          { advance: result.advance },
          { headers: { "X-Request-Id": result.requestId } },
        );
      }
      case "refund": {
        const { id, payload } = parsed.data;
        const result = await refundAdvance(id, payload, requestId);
        return Response.json(
          { advance: result.advance },
          { headers: { "X-Request-Id": result.requestId } },
        );
      }
    }
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      {
        message: error instanceof Error ? error.message : "预收预付业务处理失败",
        requestId,
      },
      { status, headers: { "X-Request-Id": requestId } },
    );
  }
}
