import {
  manufacturingSnapshotSchema,
  globalSearchItemSchema,
  type GlobalSearchItem,
  type ManufacturingSnapshot,
} from "@/lib/contracts";
import { createBusinessPageModel, type BusinessPageModel, type BusinessRow } from "@/lib/business-page-data";
import { resolveProductRoute } from "@/lib/product-navigation";
import { isBackendMasterDataPath, submitMasterDataMutation } from "@/services/master-data-client-service";

// 当前唯一的数据入口。接入后端时以 OpenAPI client 替换此适配器，页面无需改写。
const mockSnapshot = {
  workspace: { name: "华东制造中心", company: "示例精工制造有限公司", date: "2026年8月14日" },
  metrics: [
    { label: "本月订单额", value: "¥ 328.6万", change: "较计划 +6.8%", tone: "positive" },
    { label: "准时交付率", value: "94.2%", change: "目标 96.0%", tone: "warning" },
    { label: "在制工单", value: "36", change: "7项需关注", tone: "neutral" },
    { label: "一次合格率", value: "97.6%", change: "较上周 +0.9%", tone: "positive" },
  ],
  flow: [
    { id: "order", label: "销售订单", owner: "销售部", status: "done", count: 18 },
    { id: "plan", label: "计划 / MRP", owner: "计划部", status: "done", count: 14 },
    { id: "supply", label: "采购与备料", owner: "供应链", status: "warning", count: 5 },
    { id: "execute", label: "生产执行", owner: "生产部", status: "active", count: 36 },
    { id: "quality", label: "质量检验", owner: "质量部", status: "active", count: 8 },
    { id: "delivery", label: "入库与交付", owner: "仓储部", status: "pending", count: 12 },
  ],
  workOrders: [
    { id: "MO-260814-018", product: "伺服驱动控制柜 GS-800", customer: "恒锐自动化", workshop: "总装一车间", progress: 72, status: "执行中", dueDate: "08-16", quantity: "24 台" },
    { id: "MO-260814-012", product: "精密传动模组 PM-45", customer: "创驰装备", workshop: "机加车间", progress: 48, status: "有风险", dueDate: "08-15", quantity: "120 套" },
    { id: "MO-260813-027", product: "智能检测工作站 QC-20", customer: "东岳电气", workshop: "总装二车间", progress: 88, status: "待检验", dueDate: "08-14", quantity: "6 套" },
    { id: "MO-260814-021", product: "工业通讯模块 IC-4", customer: "海铭智造", workshop: "电子车间", progress: 0, status: "待开工", dueDate: "08-20", quantity: "300 件" },
  ],
  capacity: [
    { name: "机加车间", load: 92, note: "未来7天 · 接近上限" },
    { name: "总装一车间", load: 78, note: "未来7天 · 可控" },
    { name: "总装二车间", load: 64, note: "未来7天 · 有余量" },
    { name: "电子车间", load: 86, note: "未来7天 · 需平衡" },
  ],
  alerts: [
    { id: "A-018", level: "高", title: "传动模组关键物料预计晚到", detail: "轴承 BR-6204 缺口 80 件，影响工单 MO-260814-012。", owner: "采购部 · 周洁" },
    { id: "A-014", level: "中", title: "机加车间负荷超过预警线", detail: "未来三日负荷 92%，两张插单尚未确认产能。", owner: "计划部 · 林浩" },
    { id: "A-009", level: "中", title: "完工检验存在待判记录", detail: "检测工作站 2 项尺寸记录等待质量工程师判定。", owner: "质量部 · 许雯" },
  ],
} satisfies ManufacturingSnapshot;

const mockGlobalSearchIndex: GlobalSearchItem[] = [
  { type: "销售订单", title: "SO-260814-001", detail: "恒锐自动化 · GS-800 · 执行中", keywords: ["订单", "恒锐自动化", "GS-800"], href: "/sales/orders/list" },
  { type: "物料", title: "BR-6204 轴承", detail: "原材料仓 A-01-03 · 可用 340件", keywords: ["BR-6204", "轴承", "短缺"], href: "/warehouse/inventory/on-hand" },
  { type: "客户", title: "恒锐自动化", detail: "A级客户 · 当前订单 ¥864,000", keywords: ["客户", "恒锐", "GS-800"], href: "/sales/customers/list" },
  { type: "供应商", title: "华轴精工", detail: "轴承类核心供应商 · 交付风险待确认", keywords: ["供应商", "华轴", "BR-6204"], href: "/procurement/suppliers/list" },
  { type: "BOM", title: "BOM-GS800-V3.2", detail: "GS-800 伺服驱动控制柜 · 68项", keywords: ["BOM", "GS-800", "V3.2"], href: "/product/boms/list" },
  { type: "采购订单", title: "PO-260814-026", detail: "华轴精工 · BR-6204 · 待交付", keywords: ["采购", "华轴", "BR-6204"], href: "/procurement/orders" },
  { type: "生产工单", title: "MO-260814-012", detail: "精密传动模组 PM-45 · 有风险", keywords: ["工单", "PM-45", "创驰装备"], href: "/production/orders/list" },
  { type: "库存批次", title: "LOT-260731-08", detail: "BR-6204 · 原材料仓 · 合格", keywords: ["批次", "BR-6204", "库存"], href: "/warehouse/inventory/lots-serials" },
  { type: "质量问题", title: "NCR-260814-002", detail: "BR-6204 异响 · 32件待隔离", keywords: ["不合格", "异响", "BR-6204"], href: "/quality/nonconformance/reviews" },
  { type: "设备", title: "CNC-07", detail: "机加车间 · 设备异常", keywords: ["设备", "CNC", "机加"], href: "/equipment/assets" },
];

export async function getManufacturingSnapshot(): Promise<ManufacturingSnapshot> {
  await Promise.resolve();
  return manufacturingSnapshotSchema.parse(mockSnapshot);
}

export async function getGlobalSearchIndex(): Promise<GlobalSearchItem[]> {
  await Promise.resolve();
  return mockGlobalSearchIndex.map((item) => globalSearchItemSchema.parse(item));
}

export async function getBusinessPage(pathname: string): Promise<BusinessPageModel | null> {
  const route = resolveProductRoute(pathname);
  if (!route) return null;
  const snapshot = await getManufacturingSnapshot();
  return createBusinessPageModel(route, snapshot);
}

export type BusinessMutationInput = {
  pathname: string;
  action: "create" | "update" | "workflow" | "delete" | "restore" | "batch";
  values: Record<string, string>;
  records?: Array<{ id: string; version: number }>;
};

export type BusinessMutationResult = {
  requestId: string;
  savedAt: string;
  source?: "backend" | "mock";
  row?: BusinessRow;
  rows?: BusinessRow[];
};

export async function submitBusinessMutation(input: BusinessMutationInput): Promise<BusinessMutationResult> {
  if (isBackendMasterDataPath(input.pathname)) {
    if (input.action === "workflow") throw new Error("主数据审批流程将在权限与审批切片中接入。当前可执行保存、批量修改、停用和恢复。");
    return submitMasterDataMutation({
      ...input,
      action: input.action as Exclude<BusinessMutationInput["action"], "workflow">,
    });
  }
  await new Promise((resolve) => setTimeout(resolve, 520));
  const primaryValue = Object.entries(input.values).find(([key, value]) => !["owner", "remark"].includes(key) && value.trim())?.[1];
  if ((input.action === "create" || input.action === "update") && !primaryValue) {
    throw new Error("请填写必填信息后再提交。");
  }
  return {
    source: "mock" as const,
    requestId: `REQ-${Date.now().toString().slice(-8)}`,
    savedAt: new Date().toISOString(),
  };
}
