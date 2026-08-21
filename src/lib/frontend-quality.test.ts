import { readFileSync, readdirSync } from "node:fs";
import { extname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { describe, expect, it } from "vitest";

const sourceRoot = fileURLToPath(new URL("..", import.meta.url));
const globalsCss = readFileSync(join(sourceRoot, "app", "globals.css"), "utf8");

function collectFiles(directory: string, extension: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const pathname = join(directory, entry.name);
    if (entry.isDirectory()) return collectFiles(pathname, extension);
    return extname(entry.name) === extension ? [pathname] : [];
  });
}

describe("frontend quality gates", () => {
  it("keeps explicit interface text at the audited readability baseline", () => {
    const fontSizes = [...globalsCss.matchAll(/font-size:\s*(\d+)px/g)].map((match) => Number(match[1]));
    const shorthandFontSizes = [...globalsCss.matchAll(/font:\s*(\d+)px(?:\/[^\s]+)?\s/g)].map((match) => Number(match[1]));

    expect(fontSizes.length).toBeGreaterThan(0);
    expect(Math.min(...fontSizes, ...shorthandFontSizes)).toBeGreaterThanOrEqual(11);
  });

  it("preserves complete mobile row details and coarse-pointer target sizes", () => {
    expect(globalsCss).toContain("@media (pointer: coarse)");
    expect(globalsCss).toMatch(/\.businessRowActions > button[\s\S]*?min-height:\s*44px/);
    expect(globalsCss).toMatch(/@media \(max-width: 720px\)[\s\S]*?\.businessTableRow > span\[role="cell"\]:not\(\.businessRowActions\):not\(\.businessSelectionCell\)[\s\S]*?display:\s*grid !important/);
    expect(globalsCss).toMatch(/\.businessTableRow > span\[role="cell"\][\s\S]*?content:\s*attr\(data-label\)/);
  });

  it("uses the Ant-backed shared selector instead of native select controls", () => {
    const componentSource = collectFiles(join(sourceRoot, "components"), ".tsx")
      .map((pathname) => readFileSync(pathname, "utf8"))
      .join("\n");
    const roundedSelectSource = readFileSync(join(sourceRoot, "components", "rounded-select.tsx"), "utf8");

    expect(componentSource).not.toMatch(/<select\b/);
    expect(componentSource).toContain("RoundedSelect");
    expect(roundedSelectSource).toContain('import { Select } from "antd"');
    expect(roundedSelectSource).toContain("<Select<string>");
  });

  it("keeps every business workspace on the GuanSeq UI layer", () => {
    const workspaceFiles = collectFiles(join(sourceRoot, "components"), ".tsx")
      .filter((pathname) => pathname.endsWith("-workspace.tsx"));

    expect(workspaceFiles.length).toBeGreaterThan(15);
    for (const pathname of workspaceFiles) {
      const filename = pathname.split(/[\\/]/).at(-1) ?? pathname;
      const source = readFileSync(pathname, "utf8");

      expect(source, filename).not.toMatch(/<button\b|<input\b|<textarea\b|dialogBackdrop|drawerBackdrop|paginationJump/);
    }
  });

  it("keeps stateful independent ledgers on the complete shared control set", () => {
    for (const filename of [
      "sales-order-workspace.tsx",
      "bom-workspace.tsx",
      "routing-workspace.tsx",
      "planning-demand-workspace.tsx",
      "mrp-run-workspace.tsx",
      "mrp-suggestion-workspace.tsx",
      "inventory-workspace.tsx",
      "procurement-order-workspace.tsx",
      "production-order-workspace.tsx",
      "production-execution-workspace.tsx",
      "final-inspection-workspace.tsx",
      "receivable-workspace.tsx",
    ]) {
      const source = readFileSync(join(sourceRoot, "components", filename), "utf8");

      expect(source, filename).toMatch(/GsModal|GsModalHost/);
      expect(source, filename).toMatch(/GsDrawer|GsDrawerHost/);
      expect(source, filename).toContain("GsPagination");
      expect(source, filename).toContain("GsCheckbox");
      expect(source, filename).not.toMatch(/<input\b|<textarea\b|dialogBackdrop|drawerBackdrop|paginationJump|useOverlayFocus/);
    }
  });

  it("removes the retired handcrafted overlay and pagination paths", () => {
    expect(globalsCss).not.toMatch(/dialogBackdrop|drawerBackdrop|paginationJump|businessPageSize|aiAssistantBackdrop/);
  });
});
