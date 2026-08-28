import { getBackendPageDataKey } from "./backend-page-registry";
import { allProductPaths, resolveProductRoute } from "./product-navigation";

export type CapabilityMaturity = "backend" | "mock" | "planned";

export const capabilityMaturityMeta: Record<CapabilityMaturity, {
  label: string;
  pageLabel: string;
  description: string;
}> = {
  backend: {
    label: "正式后端",
    pageLabel: "正式后端",
    description: "本页通过受控服务访问正式 API；业务权限、状态与审计事实以 GuanSeq Server 为准。",
  },
  mock: {
    label: "Mock",
    pageLabel: "Mock 原型",
    description: "本页使用前端 Mock 或原型数据，仅用于产品评审；不得作为正式业务记录。",
  },
  planned: {
    label: "规划",
    pageLabel: "规划能力",
    description: "本页仅表达产品边界、依赖与验收条件，尚未接入正式业务服务。",
  },
};

export function getCapabilityMaturity(pathname: string): CapabilityMaturity | null {
  if (getBackendPageDataKey(pathname)) return "backend";
  const route = resolveProductRoute(pathname);
  if (!route) return null;
  return route.module?.status === "已规划" ? "planned" : "mock";
}

export function getCapabilityMaturitySummary(paths = allProductPaths()) {
  return paths.reduce<Record<CapabilityMaturity, number>>((summary, pathname) => {
    const maturity = getCapabilityMaturity(pathname);
    if (maturity) summary[maturity] += 1;
    return summary;
  }, { backend: 0, mock: 0, planned: 0 });
}
