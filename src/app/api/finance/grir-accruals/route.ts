import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { listGrirAccruals } from "@/services/grir-accrual-server-service";

export async function GET(request: Request) {
  const requestId =
    request.headers.get("X-Request-Id") ?? `web-grir-list-${crypto.randomUUID()}`;
  const url = new URL(request.url);
  const yearParam = url.searchParams.get("year");
  const status = url.searchParams.get("status") ?? "ALL";
  const pageParam = url.searchParams.get("page") ?? "0";
  const sizeParam = url.searchParams.get("size") ?? "20";
  const year = yearParam ? Number(yearParam) : undefined;
  const page = Number(pageParam);
  const size = Number(sizeParam);
  if (yearParam && (!Number.isInteger(year) || year! < 2000 || year! > 2100)) {
    return Response.json(
      { message: "会计年度参数无效", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } },
    );
  }
  if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) {
    return Response.json(
      { message: "分页参数无效", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } },
    );
  }
  try {
    const pageData = await listGrirAccruals({ year, status, page, size });
    return Response.json(pageData, { headers: { "X-Request-Id": requestId } });
  } catch (error) {
    const statusCode = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      { message: error instanceof Error ? error.message : "暂估应付台账加载失败", requestId },
      { status: statusCode, headers: { "X-Request-Id": requestId } },
    );
  }
}
