import { readFileSync } from "node:fs";

import { describe, expect, it } from "vitest";

const rootFile = (name: string) =>
  readFileSync(new URL(`../../${name}`, import.meta.url), "utf8");

describe("open-source governance baseline", () => {
  it("keeps the required community and release boundaries in the repository", () => {
    for (const file of [
      "LICENSE",
      "NOTICE",
      "CONTRIBUTING.md",
      "SECURITY.md",
      "SUPPORT.md",
      "CODE_OF_CONDUCT.md",
      "CHANGELOG.md",
      "docs/版本与发布策略.md",
    ]) {
      expect(rootFile(file).trim().length, file).toBeGreaterThan(100);
    }
  });

  it("does not present the development snapshot as a stable release", () => {
    expect(rootFile("LICENSE")).toContain("Apache License");
    expect(rootFile("SECURITY.md")).toContain("开发快照");
    expect(rootFile("SUPPORT.md")).toContain("尚无稳定支持版本");
    expect(rootFile("docs/版本与发布策略.md")).toContain("不是稳定生产发行版");
  });
});
