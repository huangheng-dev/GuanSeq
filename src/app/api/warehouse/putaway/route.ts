import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { fetchPutawayPageData, mutatePutaway } from "@/services/putaway-server-service";

const mutation = z.discriminatedUnion("action", [
  z.object({ action: z.literal("CREATE"), sourceBalanceId: z.string().uuid(), targetLocationId: z.string().uuid(), quantity: z.number().positive(), expectedSourceBalanceVersion: z.number().int().nonnegative() }),
  z.object({ action: z.literal("COMPLETE"), id: z.string().uuid(), expectedVersion: z.number().int().nonnegative(), expectedSourceBalanceVersion: z.number().int().nonnegative() }),
  z.object({ action: z.literal("CANCEL"), id: z.string().uuid(), expectedVersion: z.number().int().nonnegative(), reason: z.string().trim().min(4).max(300) }),
  z.object({ action: z.literal("REVERSE"), id: z.string().uuid(), expectedVersion: z.number().int().nonnegative(), expectedTargetBalanceVersion: z.number().int().nonnegative(), reason: z.string().trim().min(4).max(300) }),
]);
export async function GET() { const data = await fetchPutawayPageData(); return Response.json(data, { status: data.source === "backend" ? 200 : 503 }); }
export async function POST(request: Request) {
  const requestId=request.headers.get("X-Request-Id")??crypto.randomUUID(); const parsed=mutation.safeParse(await request.json().catch(()=>null));
  if(!parsed.success)return Response.json({message:"仓储上架参数无效",requestId},{status:400,headers:{"X-Request-Id":requestId}});
  try { const result=await mutatePutaway(parsed.data,requestId); return Response.json(result,{headers:{"X-Request-Id":requestId}}); }
  catch(error){const status=error instanceof GuanSeqApiError?error.status:error instanceof Error&&error.message.includes("无响应")?503:500;
    return Response.json({message:error instanceof Error?error.message:"仓储上架服务发生未预期错误",requestId},{status,headers:{"X-Request-Id":requestId}});}
}
