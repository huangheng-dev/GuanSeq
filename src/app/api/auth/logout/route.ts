import { NextResponse } from "next/server";
import { buildEndSessionUrl } from "openid-client";

import { getSecurityMode } from "@/lib/security-mode";
import { getOidcRuntimeConfiguration } from "@/services/oidc-config-server";
import { oidcSessionCookieName } from "@/services/oidc-session-server";

export async function POST(request: Request) {
  if (getSecurityMode() !== "oidc") {
    return NextResponse.redirect(new URL("/login?signedOut=1", request.url), { status: 303 });
  }

  const redirectUri = process.env.GUANSEQ_OIDC_REDIRECT_URI ?? request.url;
  const fallback = new URL("/login?signedOut=1", redirectUri);
  let target = fallback;
  try {
    const runtime = await getOidcRuntimeConfiguration();
    const configuredPostLogout = process.env.GUANSEQ_OIDC_POST_LOGOUT_REDIRECT_URI;
    const postLogoutRedirect = configuredPostLogout ? new URL(configuredPostLogout) : fallback;
    if (runtime.client.serverMetadata().end_session_endpoint) {
      target = buildEndSessionUrl(runtime.client, {
        post_logout_redirect_uri: postLogoutRedirect.href,
      });
    }
  } catch {
    // 身份提供者不可用时仍必须优先删除本地受保护会话。
  }
  const response = NextResponse.redirect(target, { status: 303 });
  response.cookies.delete(oidcSessionCookieName());
  return response;
}
