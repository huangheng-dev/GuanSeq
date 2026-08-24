import { NextResponse } from "next/server";
import {
  buildAuthorizationUrl,
  calculatePKCECodeChallenge,
  randomNonce,
  randomPKCECodeVerifier,
  randomState,
} from "openid-client";

import { sanitizeReturnTo } from "@/lib/secure-cookie";
import { getSecurityMode } from "@/lib/security-mode";
import { getOidcRuntimeConfiguration } from "@/services/oidc-config-server";
import {
  encodeOidcFlow,
  oidcFlowCookieName,
  protectedCookieOptions,
} from "@/services/oidc-session-server";

export async function GET(request: Request) {
  const returnTo = sanitizeReturnTo(new URL(request.url).searchParams.get("returnTo"));
  const mode = getSecurityMode();
  if (mode === "development") return NextResponse.redirect(new URL(returnTo, request.url));
  if (mode !== "oidc") {
    return Response.json({ message: "贯序身份认证尚未配置" }, { status: 503 });
  }

  const { client, redirectUri, scope, resource } = await getOidcRuntimeConfiguration();
  const state = randomState();
  const nonce = randomNonce();
  const codeVerifier = randomPKCECodeVerifier();
  const codeChallenge = await calculatePKCECodeChallenge(codeVerifier);
  const parameters: Record<string, string> = {
    redirect_uri: redirectUri,
    scope,
    state,
    nonce,
    code_challenge: codeChallenge,
    code_challenge_method: "S256",
  };
  if (resource) parameters.resource = resource;

  const response = NextResponse.redirect(buildAuthorizationUrl(client, parameters));
  response.cookies.set(
    oidcFlowCookieName(),
    encodeOidcFlow({ state, nonce, codeVerifier, returnTo, createdAt: Date.now() }),
    protectedCookieOptions(10 * 60),
  );
  return response;
}
