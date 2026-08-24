import { randomUUID } from "node:crypto";

const REQUIRED_OIDC_VARIABLES = [
  "GUANSEQ_API_BASE_URL",
  "GUANSEQ_OIDC_ISSUER_URI",
  "GUANSEQ_OIDC_CLIENT_ID",
  "GUANSEQ_OIDC_CLIENT_SECRET",
  "GUANSEQ_OIDC_REDIRECT_URI",
  "GUANSEQ_OIDC_JWK_SET_URI",
  "GUANSEQ_OIDC_AUDIENCE",
  "GUANSEQ_SESSION_SECRET",
];

function stripTrailingSlash(value) {
  return value.replace(/\/+$/, "");
}

function safeUrl(value, variable, errors, { https = false } = {}) {
  try {
    const parsed = new URL(value);
    if (!["http:", "https:"].includes(parsed.protocol)) {
      errors.push(`${variable} 必须使用 http 或 https URL`);
    } else if (https && parsed.protocol !== "https:") {
      errors.push(`${variable} 正式环境必须使用 https`);
    }
    return parsed;
  } catch {
    errors.push(`${variable} 不是有效 URL`);
    return null;
  }
}

export function validatePilotEnvironment(environment) {
  const errors = [];
  const warnings = [];
  if (environment.GUANSEQ_SECURITY_MODE !== "oidc") {
    errors.push("GUANSEQ_SECURITY_MODE 必须为 oidc");
  }
  for (const variable of REQUIRED_OIDC_VARIABLES) {
    if (!environment[variable]?.trim()) errors.push(`缺少 ${variable}`);
  }

  const issuer = environment.GUANSEQ_OIDC_ISSUER_URI
    ? safeUrl(environment.GUANSEQ_OIDC_ISSUER_URI, "GUANSEQ_OIDC_ISSUER_URI", errors, { https: true })
    : null;
  const redirect = environment.GUANSEQ_OIDC_REDIRECT_URI
    ? safeUrl(environment.GUANSEQ_OIDC_REDIRECT_URI, "GUANSEQ_OIDC_REDIRECT_URI", errors, { https: true })
    : null;
  const jwkSet = environment.GUANSEQ_OIDC_JWK_SET_URI
    ? safeUrl(environment.GUANSEQ_OIDC_JWK_SET_URI, "GUANSEQ_OIDC_JWK_SET_URI", errors, { https: true })
    : null;
  const apiBase = environment.GUANSEQ_API_BASE_URL
    ? safeUrl(environment.GUANSEQ_API_BASE_URL, "GUANSEQ_API_BASE_URL", errors)
    : null;
  if (redirect && redirect.pathname !== "/api/auth/callback") {
    errors.push("GUANSEQ_OIDC_REDIRECT_URI 路径必须是 /api/auth/callback");
  }
  const postLogout = environment.GUANSEQ_OIDC_POST_LOGOUT_REDIRECT_URI
    ? safeUrl(environment.GUANSEQ_OIDC_POST_LOGOUT_REDIRECT_URI, "GUANSEQ_OIDC_POST_LOGOUT_REDIRECT_URI", errors, { https: true })
    : null;
  if (redirect && postLogout && redirect.origin !== postLogout.origin) {
    warnings.push("OIDC 回调与退出回调不在同一来源，请确认身份提供者已分别登记");
  }
  const scope = environment.GUANSEQ_OIDC_SCOPE?.trim() || "openid profile";
  if (!scope.split(/\s+/).includes("openid")) errors.push("GUANSEQ_OIDC_SCOPE 必须包含 openid");
  const authenticationMethod = environment.GUANSEQ_OIDC_CLIENT_AUTH_METHOD || "client_secret_basic";
  if (!["client_secret_basic", "client_secret_post"].includes(authenticationMethod)) {
    errors.push("GUANSEQ_OIDC_CLIENT_AUTH_METHOD 只能是 client_secret_basic 或 client_secret_post");
  }
  if (environment.GUANSEQ_SESSION_SECRET) {
    const secret = Buffer.from(environment.GUANSEQ_SESSION_SECRET, "base64");
    if (secret.length !== 32) errors.push("GUANSEQ_SESSION_SECRET 必须是 Base64 编码的 32 字节随机密钥");
  }
  if ((environment.GUANSEQ_OIDC_CLIENT_SECRET?.length ?? 0) < 24) {
    warnings.push("OIDC 客户端密钥短于 24 字符，请确认身份提供者的密钥强度策略");
  }
  if (!environment.GUANSEQ_OIDC_RESOURCE) {
    warnings.push("未配置 GUANSEQ_OIDC_RESOURCE；必须确认身份提供者仍会签发 GUANSEQ_OIDC_AUDIENCE 对应受众");
  }
  if (environment.GUANSEQ_BOOTSTRAP_ENABLED === "true") {
    const bootstrapToken = environment.GUANSEQ_BOOTSTRAP_TOKEN ?? "";
    if (bootstrapToken.length < 32) errors.push("启用首次初始化时 GUANSEQ_BOOTSTRAP_TOKEN 至少需要 32 字符");
    if (bootstrapToken === environment.GUANSEQ_OIDC_CLIENT_SECRET
      || bootstrapToken === environment.GUANSEQ_SESSION_SECRET) {
      errors.push("首次初始化令牌不能复用 OIDC 客户端密钥或会话密钥");
    }
    warnings.push("首次初始化入口处于开启状态；成功后必须立即关闭并删除令牌");
  }
  if (apiBase?.protocol === "http:" && !["localhost", "127.0.0.1"].includes(apiBase.hostname)) {
    warnings.push("BFF 到后端使用明文 HTTP；仅允许在受控内部网络中使用");
  }

  return {
    errors,
    warnings,
    config: {
      issuer,
      redirect,
      jwkSet,
      apiBase,
      authenticationMethod,
      clientId: environment.GUANSEQ_OIDC_CLIENT_ID,
    },
  };
}

async function fetchJson(fetchImplementation, url, headers = {}) {
  const response = await fetchImplementation(url, {
    headers: { Accept: "application/json", ...headers },
    redirect: "error",
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) throw new Error(`${url.origin}${url.pathname} 返回 HTTP ${response.status}`);
  return response.json();
}

export async function inspectOidcProvider(config, fetchImplementation = fetch) {
  const errors = [];
  const warnings = [];
  if (!config.issuer || !config.jwkSet) return { errors: ["OIDC URL 配置无效，跳过连通性检查"], warnings };
  try {
    const discoveryUrl = new URL(`${stripTrailingSlash(config.issuer.href)}/.well-known/openid-configuration`);
    const metadata = await fetchJson(fetchImplementation, discoveryUrl);
    if (stripTrailingSlash(metadata.issuer ?? "") !== stripTrailingSlash(config.issuer.href)) {
      errors.push("OIDC discovery issuer 与 GUANSEQ_OIDC_ISSUER_URI 不一致");
    }
    for (const field of ["authorization_endpoint", "token_endpoint", "jwks_uri"]) {
      if (!metadata[field]) {
        errors.push(`OIDC discovery 缺少 ${field}`);
      } else {
        safeUrl(metadata[field], `OIDC ${field}`, errors, { https: true });
      }
    }
    if (metadata.response_types_supported && !metadata.response_types_supported.includes("code")) {
      errors.push("身份提供者不支持 authorization code response type");
    }
    if (metadata.grant_types_supported && !metadata.grant_types_supported.includes("authorization_code")) {
      errors.push("身份提供者不支持 authorization_code grant");
    }
    const pkceMethods = metadata.code_challenge_methods_supported;
    if (Array.isArray(pkceMethods) && !pkceMethods.includes("S256")) {
      errors.push("身份提供者不支持 PKCE S256");
    } else if (!Array.isArray(pkceMethods)) {
      warnings.push("OIDC discovery 未声明 PKCE 方法，需要在真实登录验收中确认 S256");
    }
    const authMethods = metadata.token_endpoint_auth_methods_supported;
    if (Array.isArray(authMethods) && !authMethods.includes(config.authenticationMethod)) {
      errors.push(`身份提供者不支持配置的 ${config.authenticationMethod}`);
    }
    if (metadata.jwks_uri && stripTrailingSlash(metadata.jwks_uri) !== stripTrailingSlash(config.jwkSet.href)) {
      warnings.push("后端 JWK 地址与 OIDC discovery 的 jwks_uri 不一致");
    }

    const jwkSet = await fetchJson(fetchImplementation, config.jwkSet);
    const signingKeys = Array.isArray(jwkSet.keys)
      ? jwkSet.keys.filter((key) => (!key.use || key.use === "sig") && ["RSA", "EC", "OKP"].includes(key.kty) && !key.d)
      : [];
    if (signingKeys.length === 0) errors.push("JWK 集中没有可用的公开签名密钥");
  } catch (error) {
    errors.push(error instanceof Error ? error.message : "OIDC 连通性检查失败");
  }
  return { errors, warnings };
}

export const PILOT_SMOKE_ENDPOINTS = [
  ["平台状态", "/api/v1/platform/status", false],
  ["当前工作区", "/api/v1/me/workspaces", true],
  ["工作区成员", "/api/v1/identity/workspace-users?page=0&size=1&status=ALL", true],
  ["销售订单", "/api/v1/sales/orders?page=0&size=1&status=ALL", true],
  ["独立需求", "/api/v1/planning/independent-demands?page=0&size=1&status=ALL&sourceType=ALL", true],
  ["MRP 运算", "/api/v1/planning/mrp-runs?page=0&size=1&status=ALL", true],
  ["采购订单", "/api/v1/procurement/orders?page=0&size=1&status=ALL", true],
  ["生产订单", "/api/v1/production/orders?page=0&size=1&status=ALL", true],
  ["工序任务", "/api/v1/production/operation-tasks?page=0&size=1&status=ALL", true],
  ["完工检验", "/api/v1/quality/final-inspections?page=0&size=1&status=ALL", true],
  ["库存余额", "/api/v1/warehouse/inventory-balances?page=0&size=1&qualityStatus=ALL&warehouseCode=ALL", true],
  ["销售发货", "/api/v1/sales/shipments?page=0&size=1&status=ALL", true],
  ["订单利润", "/api/v1/finance/order-profits?page=0&size=1&costStatus=ALL", true],
];

export async function runPilotSmoke({ apiBase, accessToken, expectedUsername, fetchImplementation = fetch }) {
  const results = [];
  for (const [name, path, authenticated] of PILOT_SMOKE_ENDPOINTS) {
    const requestId = `pilot-smoke-${randomUUID()}`;
    const startedAt = performance.now();
    try {
      const headers = { Accept: "application/json", "X-Request-Id": requestId };
      if (authenticated) headers.Authorization = `Bearer ${accessToken}`;
      const response = await fetchImplementation(new URL(path, apiBase), {
        headers,
        redirect: "error",
        signal: AbortSignal.timeout(10_000),
      });
      let body;
      try {
        body = await response.json();
      } catch {
        body = null;
      }
      let detail = response.ok ? "HTTP 200" : `HTTP ${response.status}`;
      if (response.headers.get("X-Request-Id") !== requestId) detail += "，请求编号未贯穿";
      if (!body) detail += "，响应不是 JSON";
      if (name === "当前工作区" && expectedUsername && body?.username !== expectedUsername) {
        detail += "，内部用户名映射不一致";
      }
      const passed = response.status === 200
        && response.headers.get("X-Request-Id") === requestId
        && body !== null
        && (name !== "当前工作区" || !expectedUsername || body.username === expectedUsername);
      results.push({ name, passed, detail, durationMs: Math.round(performance.now() - startedAt) });
    } catch (error) {
      results.push({
        name,
        passed: false,
        detail: error instanceof Error ? error.message : "请求失败",
        durationMs: Math.round(performance.now() - startedAt),
      });
    }
  }
  return results;
}
