import { describe, expect, it } from "vitest";

import { openCookieValue, sanitizeReturnTo, sealCookieValue } from "./secure-cookie";

const secret = Buffer.alloc(32, 17).toString("base64");

describe("secure identity cookies", () => {
  it("encrypts and authenticates a payload for one purpose", () => {
    const value = sealCookieValue({ accessToken: "sensitive-token", expiresAt: 42 }, "session", secret);

    expect(value).not.toContain("sensitive-token");
    expect(openCookieValue(value, "session", secret)).toEqual({
      accessToken: "sensitive-token",
      expiresAt: 42,
    });
    expect(openCookieValue(value, "different-purpose", secret)).toBeNull();
  });

  it("rejects tampering and invalid keys", () => {
    const value = sealCookieValue({ state: "state-value" }, "flow", secret);
    const parts = value.split(".");
    parts[2] = `${parts[2].startsWith("A") ? "B" : "A"}${parts[2].slice(1)}`;
    const tampered = parts.join(".");

    expect(openCookieValue(tampered, "flow", secret)).toBeNull();
    expect(() => sealCookieValue({}, "flow", "not-a-key")).toThrow(/32 字节/);
  });
});

describe("OIDC return address", () => {
  it("keeps only local paths and query strings", () => {
    expect(sanitizeReturnTo("/sales/orders/list?page=2#ignored")).toBe("/sales/orders/list?page=2");
    expect(sanitizeReturnTo("https://evil.example/steal")).toBe("/");
    expect(sanitizeReturnTo("//evil.example/steal")).toBe("/");
    expect(sanitizeReturnTo("/\\evil.example/steal")).toBe("/");
  });
});
