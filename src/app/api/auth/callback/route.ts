import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { authorizationCodeGrant } from "openid-client";

import { getOidcRuntimeConfiguration } from "@/services/oidc-config-server";
import {
  decodeOidcFlow,
  encodeOidcSession,
  oidcFlowCookieName,
  oidcSessionCookieName,
  protectedCookieOptions,
} from "@/services/oidc-session-server";

function failedAuthenticationRedirect(redirectUri: string) {
  const target = new URL("/login", redirectUri);
  target.searchParams.set("error", "authentication_failed");
  return target;
}

export async function GET(request: Request) {
  const runtime = await getOidcRuntimeConfiguration();
  const cookieStore = await cookies();
  const flow = decodeOidcFlow(cookieStore.get(oidcFlowCookieName())?.value);
  if (!flow) {
    return NextResponse.redirect(failedAuthenticationRedirect(runtime.redirectUri));
  }

  try {
    const currentUrl = new URL(runtime.redirectUri);
    currentUrl.search = new URL(request.url).search;
    const tokenParameters: Record<string, string> = { redirect_uri: runtime.redirectUri };
    if (runtime.resource) tokenParameters.resource = runtime.resource;
    const tokens = await authorizationCodeGrant(
      runtime.client,
      currentUrl,
      {
        expectedState: flow.state,
        expectedNonce: flow.nonce,
        pkceCodeVerifier: flow.codeVerifier,
        idTokenExpected: true,
      },
      tokenParameters,
    );
    const expiresIn = tokens.expiresIn();
    if (!tokens.access_token || !expiresIn || expiresIn <= 0 || tokens.token_type.toLowerCase() !== "bearer") {
      throw new Error("身份提供者没有返回可用的 Bearer 访问令牌");
    }

    const response = NextResponse.redirect(new URL(flow.returnTo, runtime.redirectUri));
    response.cookies.set(
      oidcSessionCookieName(),
      encodeOidcSession({
        accessToken: tokens.access_token,
        expiresAt: Date.now() + expiresIn * 1000,
      }),
      protectedCookieOptions(expiresIn),
    );
    response.cookies.delete(oidcFlowCookieName());
    return response;
  } catch {
    const response = NextResponse.redirect(failedAuthenticationRedirect(runtime.redirectUri));
    response.cookies.delete(oidcFlowCookieName());
    response.cookies.delete(oidcSessionCookieName());
    return response;
  }
}
