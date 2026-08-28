import "server-only";

import type { BackendHealthStatus } from "@/lib/health-status";
import { getSecurityMode } from "@/lib/security-mode";
import { readOidcAccessToken } from "@/services/oidc-session-server";

const backendBaseUrl = process.env.GUANSEQ_API_BASE_URL ?? "http://localhost:8080";
const developmentUsername = process.env.GUANSEQ_DEV_USERNAME ?? "lin.hao";
const developmentPassword = process.env.GUANSEQ_DEV_PASSWORD ?? "guanseq_dev";

export async function checkGuanSeqApiHealth(
  timeoutMs = 2000,
): Promise<BackendHealthStatus | null> {
  try {
    const response = await fetch(`${backendBaseUrl}/api/v1/platform/status`, {
      cache: "no-store",
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (!response.ok) return null;
    const payload = (await response.json()) as Partial<BackendHealthStatus>;
    if (
      payload.service !== "guanseq-server" ||
      payload.status !== "UP" ||
      typeof payload.version !== "string"
    ) {
      return null;
    }
    return payload as BackendHealthStatus;
  } catch {
    return null;
  }
}

export async function requestGuanSeqApi(
  path: string,
  requestId: string,
  init?: RequestInit,
  timeoutMs = 3000,
): Promise<Response | null> {
  const headers = new Headers(init?.headers);
  headers.delete("Authorization");
  const securityMode = getSecurityMode();
  if (securityMode === "development") {
    const credentials = Buffer.from(`${developmentUsername}:${developmentPassword}`).toString("base64");
    headers.set("Authorization", `Basic ${credentials}`);
  } else if (securityMode === "oidc") {
    const accessToken = await readOidcAccessToken();
    if (!accessToken) {
      return Response.json(
        { message: "身份会话不存在或已经过期", requestId },
        { status: 401, headers: { "X-Request-Id": requestId } },
      );
    }
    headers.set("Authorization", `Bearer ${accessToken}`);
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
