import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { listAdvances } from "@/services/advance-server-service";

export async function GET(request: Request) {
  const requestId =
    request.headers.get("X-Request-Id") ?? `web-adv-list-${crypto.randomUUID()}`;
  const url = new URL(request.url);
  const type = url.searchParams.get("type") ?? "ALL";
  const status = url.searchParams.get("status") ?? "ALL";
  const partyId = url.searchParams.get("partyId") ?? undefined;
  const query = url.searchParams.get("query") ?? undefined;
  const page = Number(url.searchParams.get("page") ?? "0");
  const size = Number(url.searchParams.get("size") ?? "20");
  if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) {
    return Response.json(
      { message: "分页参数无效", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } },
    );
  }
  try {
    const pageData = await listAdvances({ type, status, partyId, query, page, size });
    return Response.json(pageData, { headers: { "X-Request-Id": requestId } });
  } catch (error) {
    const statusCode = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      { message: error instanceof Error ? error.message : "预收预付台账加载失败", requestId },
      { status: statusCode, headers: { "X-Request-Id": requestId } },
    );
  }
}
