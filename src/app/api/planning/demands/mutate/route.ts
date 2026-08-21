import { NextResponse } from "next/server";

import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { mutatePlanningDemand, type PlanningDemandMutation } from "@/services/planning-demand-server-service";

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? `web-planning-demand-${crypto.randomUUID()}`;
  try {
    const input = await request.json() as PlanningDemandMutation;
    const result = await mutatePlanningDemand(input, requestId);
    return NextResponse.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    const message = error instanceof Error ? error.message : "计划需求操作失败";
    return NextResponse.json({ message, requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
