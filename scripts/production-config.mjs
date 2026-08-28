const REQUIRED_VARIABLES = [
  "GUANSEQ_VERSION",
  "GUANSEQ_PUBLIC_ORIGIN",
  "GUANSEQ_DB_PASSWORD",
  "GUANSEQ_OIDC_ISSUER_URI",
  "GUANSEQ_OIDC_JWK_SET_URI",
  "GUANSEQ_OIDC_AUDIENCE",
  "GUANSEQ_OIDC_CLIENT_ID",
  "GUANSEQ_OIDC_CLIENT_SECRET",
  "GUANSEQ_OIDC_REDIRECT_URI",
  "GUANSEQ_OIDC_POST_LOGOUT_REDIRECT_URI",
  "GUANSEQ_SESSION_SECRET",
  "GUANSEQ_RPO_HOURS",
  "GUANSEQ_RTO_HOURS",
];

const PLACEHOLDER_PATTERN = /(replace|change[-_ ]?me|example\.com|your[-_ ]|<[^>]+>)/i;
const SAFE_IDENTIFIER = /^[a-zA-Z][a-zA-Z0-9_]{0,62}$/;
const SAFE_VOLUME = /^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$/;

function parsePositiveNumber(environment, name, errors) {
  const value = Number(environment[name]);
  if (!Number.isFinite(value) || value <= 0) {
    errors.push(`${name} 必须是大于 0 的数字`);
  }
  return value;
}

function parseUrl(environment, name, errors, { https = true, originOnly = false } = {}) {
  const value = environment[name];
  if (!value) return null;
  try {
    const url = new URL(value);
    if (https && url.protocol !== "https:") errors.push(`${name} 必须使用 https`);
    if (originOnly && (url.pathname !== "/" || url.search || url.hash)) {
      errors.push(`${name} 只能包含协议、主机和端口`);
    }
    return url;
  } catch {
    errors.push(`${name} 不是有效 URL`);
    return null;
  }
}

function validateSecret(environment, name, errors, minimumLength) {
  const value = environment[name] ?? "";
  if (value.length < minimumLength) errors.push(`${name} 长度至少为 ${minimumLength} 个字符`);
  if (PLACEHOLDER_PATTERN.test(value)) errors.push(`${name} 仍是示例占位值`);
}

export function validateProductionEnvironment(environment) {
  const errors = [];
  const warnings = [];

  for (const name of REQUIRED_VARIABLES) {
    if (!environment[name]?.trim()) errors.push(`缺少 ${name}`);
  }
  if (environment.GUANSEQ_SECURITY_MODE && environment.GUANSEQ_SECURITY_MODE !== "oidc") {
    errors.push("正式编排只允许 GUANSEQ_SECURITY_MODE=oidc");
  }
  if (environment.GUANSEQ_VERSION && !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(environment.GUANSEQ_VERSION)) {
    errors.push("GUANSEQ_VERSION 必须是明确的语义化版本，禁止 latest");
  }

  const publicOrigin = parseUrl(environment, "GUANSEQ_PUBLIC_ORIGIN", errors, { originOnly: true });
  const issuer = parseUrl(environment, "GUANSEQ_OIDC_ISSUER_URI", errors);
  const jwkSet = parseUrl(environment, "GUANSEQ_OIDC_JWK_SET_URI", errors);
  const redirect = parseUrl(environment, "GUANSEQ_OIDC_REDIRECT_URI", errors);
  const logout = parseUrl(environment, "GUANSEQ_OIDC_POST_LOGOUT_REDIRECT_URI", errors);
  if (redirect?.pathname !== "/api/auth/callback") {
    errors.push("GUANSEQ_OIDC_REDIRECT_URI 路径必须是 /api/auth/callback");
  }
  if (publicOrigin && redirect && publicOrigin.origin !== redirect.origin) {
    errors.push("OIDC 回调来源必须与 GUANSEQ_PUBLIC_ORIGIN 一致");
  }
  if (publicOrigin && logout && publicOrigin.origin !== logout.origin) {
    errors.push("OIDC 退出回调来源必须与 GUANSEQ_PUBLIC_ORIGIN 一致");
  }
  if (issuer && jwkSet && issuer.origin !== jwkSet.origin) {
    warnings.push("OIDC issuer 与 JWK 地址不在同一来源，必须由身份负责人确认");
  }

  validateSecret(environment, "GUANSEQ_DB_PASSWORD", errors, 24);
  validateSecret(environment, "GUANSEQ_OIDC_CLIENT_SECRET", errors, 24);
  if (environment.GUANSEQ_SESSION_SECRET) {
    if (PLACEHOLDER_PATTERN.test(environment.GUANSEQ_SESSION_SECRET)) {
      errors.push("GUANSEQ_SESSION_SECRET 仍是示例占位值");
    } else {
      const decoded = Buffer.from(environment.GUANSEQ_SESSION_SECRET, "base64");
      if (decoded.length !== 32 || decoded.toString("base64") !== environment.GUANSEQ_SESSION_SECRET) {
        errors.push("GUANSEQ_SESSION_SECRET 必须是标准 Base64 编码的 32 字节随机密钥");
      }
    }
  }
  const distinctSecrets = [
    environment.GUANSEQ_DB_PASSWORD,
    environment.GUANSEQ_OIDC_CLIENT_SECRET,
    environment.GUANSEQ_SESSION_SECRET,
  ].filter(Boolean);
  if (new Set(distinctSecrets).size !== distinctSecrets.length) {
    errors.push("数据库、OIDC 客户端和会话密钥必须彼此独立");
  }

  const bindAddress = environment.GUANSEQ_HTTP_BIND_ADDRESS || "127.0.0.1";
  if (!["127.0.0.1", "::1", "localhost"].includes(bindAddress)) {
    if (environment.GUANSEQ_TLS_TERMINATION_CONFIRMED !== "true") {
      errors.push("非回环监听必须设置 GUANSEQ_TLS_TERMINATION_CONFIRMED=true 并由外部 TLS 入口保护");
    }
    warnings.push("Web 端口将监听非回环地址；必须用防火墙限制为受控反向代理来源");
  }
  if (environment.GUANSEQ_TLS_TERMINATION_CONFIRMED !== "true") {
    errors.push("必须确认外部 TLS 终止边界：GUANSEQ_TLS_TERMINATION_CONFIRMED=true");
  }

  const database = environment.GUANSEQ_POSTGRES_DB || "guanseq";
  const databaseUser = environment.GUANSEQ_POSTGRES_USER || "guanseq";
  const volume = environment.GUANSEQ_POSTGRES_VOLUME || "guanseq-postgres-data";
  if (!SAFE_IDENTIFIER.test(database)) errors.push("GUANSEQ_POSTGRES_DB 不是安全数据库标识符");
  if (!SAFE_IDENTIFIER.test(databaseUser)) errors.push("GUANSEQ_POSTGRES_USER 不是安全数据库标识符");
  if (!SAFE_VOLUME.test(volume)) errors.push("GUANSEQ_POSTGRES_VOLUME 不是安全卷名称");

  const retentionDays = Number(environment.GUANSEQ_BACKUP_RETENTION_DAYS || 30);
  if (!Number.isInteger(retentionDays) || retentionDays < 1) {
    errors.push("GUANSEQ_BACKUP_RETENTION_DAYS 必须是正整数");
  }
  parsePositiveNumber(environment, "GUANSEQ_RPO_HOURS", errors);
  parsePositiveNumber(environment, "GUANSEQ_RTO_HOURS", errors);

  if (environment.GUANSEQ_BOOTSTRAP_ENABLED === "true") {
    validateSecret(environment, "GUANSEQ_BOOTSTRAP_TOKEN", errors, 32);
    if (distinctSecrets.includes(environment.GUANSEQ_BOOTSTRAP_TOKEN)) {
      errors.push("初始化令牌不能复用数据库、OIDC 或会话密钥");
    }
    warnings.push("首次初始化入口处于开启状态；成功后必须立即关闭并删除令牌");
  } else if (environment.GUANSEQ_BOOTSTRAP_TOKEN && environment.GUANSEQ_BOOTSTRAP_TOKEN !== "disabled") {
    warnings.push("初始化入口已关闭但令牌仍存在，应从部署环境和密钥系统删除");
  }

  return { errors, warnings };
}
