import "server-only";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { z } from "zod";

import { openCookieValue, sanitizeReturnTo, sealCookieValue } from "@/lib/secure-cookie";
import { getSecurityMode } from "@/lib/security-mode";

const SESSION_PURPOSE = "oidc-session";
const FLOW_PURPOSE = "oidc-flow";
const CLOCK_SKEW_MS = 30_000;

const sessionSchema = z.object({
  accessToken: z.string().min(1),
  expiresAt: z.number().int().positive(),
});

const flowSchema = z.object({
  state: z.string().min(1),
  nonce: z.string().min(1),
  codeVerifier: z.string().min(1),
  returnTo: z.string().min(1),
  createdAt: z.number().int().positive(),
});

export type OidcSession = z.infer<typeof sessionSchema>;
export type OidcAuthorizationFlow = z.infer<typeof flowSchema>;

function sessionSecret(): string {
  const secret = process.env.GUANSEQ_SESSION_SECRET;
  if (!secret) throw new Error("OIDC 正式身份缺少配置：GUANSEQ_SESSION_SECRET");
  return secret;
}

function productionCookies(): boolean {
  return process.env.NODE_ENV === "production";
}

export function oidcSessionCookieName(): string {
  return productionCookies() ? "__Host-guanseq-session" : "guanseq-session";
}

export function oidcFlowCookieName(): string {
  return productionCookies() ? "__Host-guanseq-oidc-flow" : "guanseq-oidc-flow";
}

export function protectedCookieOptions(maxAge: number) {
  return {
    httpOnly: true,
    secure: productionCookies(),
    sameSite: "lax" as const,
    path: "/",
    maxAge,
  };
}

export function encodeOidcSession(session: OidcSession): string {
  return sealCookieValue(sessionSchema.parse(session), SESSION_PURPOSE, sessionSecret());
}

export function decodeOidcSession(value: string | undefined, now = Date.now()): OidcSession | null {
  const parsed = sessionSchema.safeParse(openCookieValue(value, SESSION_PURPOSE, sessionSecret()));
  if (!parsed.success || parsed.data.expiresAt <= now + CLOCK_SKEW_MS) return null;
  return parsed.data;
}

export function encodeOidcFlow(flow: OidcAuthorizationFlow): string {
  return sealCookieValue(flowSchema.parse(flow), FLOW_PURPOSE, sessionSecret());
}

export function decodeOidcFlow(value: string | undefined, now = Date.now()): OidcAuthorizationFlow | null {
  const parsed = flowSchema.safeParse(openCookieValue(value, FLOW_PURPOSE, sessionSecret()));
  if (!parsed.success || parsed.data.createdAt < now - 10 * 60_000) return null;
  return parsed.data;
}

export async function readOidcSession(): Promise<OidcSession | null> {
  if (getSecurityMode() !== "oidc") return null;
  const cookieStore = await cookies();
  return decodeOidcSession(cookieStore.get(oidcSessionCookieName())?.value);
}

export async function readOidcAccessToken(): Promise<string | null> {
  return (await readOidcSession())?.accessToken ?? null;
}

export async function requireFrontendSession(returnTo: string): Promise<void> {
  const mode = getSecurityMode();
  if (mode === "development") return;
  if (mode === "disabled") {
    throw new Error("贯序身份认证已禁用；请启用 development 或 oidc 安全模式。");
  }
  if (!await readOidcSession()) {
    redirect(`/login?returnTo=${encodeURIComponent(sanitizeReturnTo(returnTo))}`);
  }
}
