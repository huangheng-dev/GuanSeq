import { NextResponse } from "next/server";

import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { createMrpRun, type CreateMrpRunInput } from "@/services/mrp-run-server-service";

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? `web-mrp-run-${crypto.randomUUID()}`;
  try {
    const input = await request.json() as CreateMrpRunInput;
    const result = await createMrpRun(input, requestId);
    return NextResponse.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    const message = error instanceof Error ? error.message : "MRP 运算准备检查失败";
    return NextResponse.json({ message, requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
