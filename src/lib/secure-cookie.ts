import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";

const COOKIE_VERSION = "v1";
const MAX_COOKIE_VALUE_LENGTH = 3800;

function decodeSecret(encodedSecret: string): Buffer {
  const secret = Buffer.from(encodedSecret, "base64");
  if (secret.length !== 32) {
    throw new Error("GUANSEQ_SESSION_SECRET 必须是 Base64 编码的 32 字节随机密钥。");
  }
  return secret;
}

export function sealCookieValue(
  payload: unknown,
  purpose: string,
  encodedSecret: string,
): string {
  const initializationVector = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", decodeSecret(encodedSecret), initializationVector);
  cipher.setAAD(Buffer.from(`${COOKIE_VERSION}:${purpose}`, "utf8"));
  const ciphertext = Buffer.concat([
    cipher.update(JSON.stringify(payload), "utf8"),
    cipher.final(),
  ]);
  const value = [
    COOKIE_VERSION,
    initializationVector.toString("base64url"),
    cipher.getAuthTag().toString("base64url"),
    ciphertext.toString("base64url"),
  ].join(".");
  if (value.length > MAX_COOKIE_VALUE_LENGTH) {
    throw new Error("身份会话超过浏览器 Cookie 安全容量，无法建立会话。");
  }
  return value;
}

export function openCookieValue(
  value: string | undefined,
  purpose: string,
  encodedSecret: string,
): unknown | null {
  if (!value) return null;
  const secret = decodeSecret(encodedSecret);
  try {
    const [version, encodedIv, encodedTag, encodedCiphertext, extra] = value.split(".");
    if (version !== COOKIE_VERSION || !encodedIv || !encodedTag || !encodedCiphertext || extra) {
      return null;
    }
    const decipher = createDecipheriv(
      "aes-256-gcm",
      secret,
      Buffer.from(encodedIv, "base64url"),
    );
    decipher.setAAD(Buffer.from(`${COOKIE_VERSION}:${purpose}`, "utf8"));
    decipher.setAuthTag(Buffer.from(encodedTag, "base64url"));
    const plaintext = Buffer.concat([
      decipher.update(Buffer.from(encodedCiphertext, "base64url")),
      decipher.final(),
    ]).toString("utf8");
    return JSON.parse(plaintext) as unknown;
  } catch {
    return null;
  }
}

export function sanitizeReturnTo(value: string | null | undefined): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.includes("\\")) {
    return "/";
  }
  try {
    const base = new URL("https://guanseq.invalid");
    const candidate = new URL(value, base);
    if (candidate.origin !== base.origin) return "/";
    return `${candidate.pathname}${candidate.search}`;
  } catch {
    return "/";
  }
}
