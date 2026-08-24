import "server-only";

import {
  ClientSecretBasic,
  ClientSecretPost,
  discovery,
  type Configuration,
} from "openid-client";

type OidcRuntimeConfiguration = {
  client: Configuration;
  redirectUri: string;
  scope: string;
  resource?: string;
};

let configurationPromise: Promise<OidcRuntimeConfiguration> | undefined;

function requiredEnvironment(name: string): string {
  const value = process.env[name];
  if (!value?.trim()) throw new Error(`OIDC 正式身份缺少配置：${name}`);
  return value.trim();
}

export function getOidcRuntimeConfiguration(): Promise<OidcRuntimeConfiguration> {
  configurationPromise ??= createConfiguration();
  return configurationPromise;
}

async function createConfiguration(): Promise<OidcRuntimeConfiguration> {
  const issuer = new URL(requiredEnvironment("GUANSEQ_OIDC_ISSUER_URI"));
  const clientId = requiredEnvironment("GUANSEQ_OIDC_CLIENT_ID");
  const clientSecret = requiredEnvironment("GUANSEQ_OIDC_CLIENT_SECRET");
  const redirectUri = requiredEnvironment("GUANSEQ_OIDC_REDIRECT_URI");
  const authenticationMethod = process.env.GUANSEQ_OIDC_CLIENT_AUTH_METHOD ?? "client_secret_basic";
  const clientAuthentication = authenticationMethod === "client_secret_post"
    ? ClientSecretPost(clientSecret)
    : authenticationMethod === "client_secret_basic"
      ? ClientSecretBasic(clientSecret)
      : null;
  if (!clientAuthentication) {
    throw new Error(
      "GUANSEQ_OIDC_CLIENT_AUTH_METHOD 只能使用 client_secret_basic 或 client_secret_post。",
    );
  }

  const client = await discovery(
    issuer,
    clientId,
    {
      client_secret: clientSecret,
      redirect_uris: [redirectUri],
      response_types: ["code"],
    },
    clientAuthentication,
  );
  client.timeout = 10;
  return {
    client,
    redirectUri,
    scope: process.env.GUANSEQ_OIDC_SCOPE?.trim() || "openid profile",
    resource: process.env.GUANSEQ_OIDC_RESOURCE?.trim() || undefined,
  };
}
