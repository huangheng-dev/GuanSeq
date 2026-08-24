import "server-only";

const backendBaseUrl = process.env.GUANSEQ_API_BASE_URL ?? "http://localhost:8080";
const developmentIdentityEnabled = process.env.GUANSEQ_DEV_IDENTITY_ENABLED === "true";
const developmentUsername = process.env.GUANSEQ_DEV_USERNAME ?? "lin.hao";
const developmentPassword = process.env.GUANSEQ_DEV_PASSWORD ?? "guanseq_dev";

export async function requestGuanSeqApi(
  path: string,
  requestId: string,
  init?: RequestInit,
  timeoutMs = 3000,
): Promise<Response | null> {
  const headers = new Headers(init?.headers);
  if (developmentIdentityEnabled && !headers.has("Authorization")) {
    const credentials = Buffer.from(`${developmentUsername}:${developmentPassword}`).toString("base64");
    headers.set("Authorization", `Basic ${credentials}`);
  }
  if (!headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (!headers.has("X-Request-Id")) headers.set("X-Request-Id", requestId);

  try {
    return await fetch(`${backendBaseUrl}${path}`, {
      ...init,
      cache: "no-store",
      headers,
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch {
    return null;
  }
}

export async function readApiError(response: Response, fallback: string): Promise<never> {
  let message = fallback;
  try {
    const problem = await response.json() as { detail?: string; message?: string };
    message = problem.detail ?? problem.message ?? fallback;
  } catch {
    // 保留业务友好的默认错误；状态码仍继续向调用方传递。
  }
  throw new GuanSeqApiError(message, response.status);
}

export class GuanSeqApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}
