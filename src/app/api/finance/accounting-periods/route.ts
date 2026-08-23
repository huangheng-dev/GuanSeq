import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { getAccountingPeriods } from "@/services/accounting-period-server-service";

export async function GET(request: Request) {
  const requestId =
    request.headers.get("X-Request-Id") ?? `web-accounting-period-${crypto.randomUUID()}`;
  const url = new URL(request.url);
  const yearParam = url.searchParams.get("year");
  const year = yearParam ? Number(yearParam) : undefined;
  if (yearParam && (!Number.isInteger(year) || year! < 2020 || year! > 2099)) {
    return Response.json(
      { message: "会计年度参数无效", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } },
    );
  }
  try {
    const periods = await getAccountingPeriods(year);
    return Response.json(
      { periods },
      { headers: { "X-Request-Id": requestId } },
    );
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      {
        message: error instanceof Error ? error.message : "会计期间加载失败",
        requestId,
      },
      { status, headers: { "X-Request-Id": requestId } },
    );
  }
}
