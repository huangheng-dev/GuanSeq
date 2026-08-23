import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { previewGrirAccrual } from "@/services/grir-accrual-server-service";

export async function GET(request: Request) {
  const requestId =
    request.headers.get("X-Request-Id") ?? `web-grir-preview-${crypto.randomUUID()}`;
  const url = new URL(request.url);
  const year = Number(url.searchParams.get("year"));
  const period = Number(url.searchParams.get("period"));
  if (!Number.isInteger(year) || year < 2000 || year > 2100) {
    return Response.json(
      { message: "会计年度参数无效", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } },
    );
  }
  if (!Number.isInteger(period) || period < 1 || period > 12) {
    return Response.json(
      { message: "会计期间参数无效", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } },
    );
  }
  try {
    const preview = await previewGrirAccrual(year, period);
    return Response.json({ preview }, { headers: { "X-Request-Id": requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      { message: error instanceof Error ? error.message : "暂估预览加载失败", requestId },
      { status, headers: { "X-Request-Id": requestId } },
    );
  }
}
