export type ProductChild = {
  label: string;
  slug: string;
};

export type ProductModule = {
  label: string;
  slug: string;
  status?: "已启用" | "已规划";
  children?: ProductChild[];
};

export type ProductArea = {
  id: string;
  slug: string;
  label: string;
  icon: string;
  description: string;
  capability: string;
  modules: ProductModule[];
};

const child = (label: string, slug: string): ProductChild => ({ label, slug });
const moduleItem = (
  label: string,
  slug: string,
  children?: ProductChild[],
  status?: ProductModule["status"],
): ProductModule => ({ label, slug, children, status });

export const productAreas: ProductArea[] = [
  {
    id: "overview", slug: "", label: "经营工作台", icon: "space_dashboard", capability: "CORE",
    description: "订单、交付、生产与风险的统一经营视图",
    modules: [
      moduleItem("经营总览", "overview", undefined, "已启用"),
      moduleItem("我的工作", "my-work", [child("我的待办", "todos"), child("我的审批", "approvals"), child("我的发起", "requests"), child("我的关注", "following")], "已启用"),
      moduleItem("消息中心", "notifications", undefined, "已启用"),
      moduleItem("风险中心", "risks", undefined, "已启用"),
      moduleItem("业务流程", "process", undefined, "已启用"),
      moduleItem("任务中心", "jobs", [child("批量任务", "batch"), child("导入任务", "imports"), child("导出任务", "exports")], "已启用"),
    ],
  },
  {
    id: "sales", slug: "sales", label: "销售管理", icon: "request_quote", capability: "ERP",
    description: "从客户需求、报价到销售订单与交付承诺",
    modules: [
      moduleItem("客户", "customers", [child("客户列表", "list"), child("联系人", "contacts"), child("信用资料", "credit")]),
      moduleItem("销售预测", "forecasts"),
      moduleItem("价格与折扣", "pricing", [child("价目表", "price-lists"), child("折扣政策", "discounts")]),
      moduleItem("报价", "quotes"),
      moduleItem("销售合同", "contracts"),
      moduleItem("销售订单", "orders", [child("订单列表", "list"), child("订单审核", "approvals"), child("变更记录", "changes")]),
      moduleItem("发货计划", "deliveries", [child("待发货", "pending"), child("交付跟踪", "tracking")]),
      moduleItem("销售退货", "returns"),
    ],
  },
  {
    id: "product", slug: "product", label: "产品与工艺", icon: "schema", capability: "PLM",
    description: "物料、BOM、图纸、工艺路线与受控版本",
    modules: [
      moduleItem("物料档案", "materials", [child("物料列表", "list"), child("分类与属性", "categories"), child("计量单位", "units")]),
      moduleItem("产品结构视图", "structures"),
      moduleItem("BOM 版本", "boms", [child("BOM 列表", "list"), child("版本比较", "compare"), child("替代料", "substitutes")]),
      moduleItem("工艺路线", "routings", [child("路线列表", "list"), child("工序库", "operations"), child("作业指导书", "instructions")]),
      moduleItem("图纸与文档", "documents", [child("受控图纸", "drawings"), child("技术文件", "technical-files"), child("文档发布", "releases")]),
      moduleItem("工程变更", "changes", undefined, "已规划"),
    ],
  },
  {
    id: "planning", slug: "planning", label: "计划管理", icon: "event_note", capability: "APS",
    description: "需求、MRP、齐套、产能与生产排程",
    modules: [moduleItem("需求管理", "demand", [child("独立需求", "independent"), child("需求合并", "consolidation")]), moduleItem("可承诺量", "atp"), moduleItem("主生产计划", "master-plans"), moduleItem("MRP 运算", "mrp", [child("运算方案", "plans"), child("运算记录", "runs"), child("供需建议", "recommendations")]), moduleItem("物料齐套", "readiness"), moduleItem("产能负荷", "capacity"), moduleItem("高级排程", "scheduling", undefined, "已规划"), moduleItem("计划例外", "exceptions"), moduleItem("计划参数", "parameters")],
  },
  {
    id: "procurement", slug: "procurement", label: "采购与供应", icon: "local_shipping", capability: "SCM",
    description: "供应商、采购需求、订单、到货与委外协同",
    modules: [moduleItem("供应商", "suppliers"), moduleItem("采购申请", "requisitions"), moduleItem("询价比价", "sourcing", [child("询价单", "inquiries"), child("比价决策", "comparisons")]), moduleItem("采购合同", "contracts"), moduleItem("采购价格", "pricing"), moduleItem("采购订单", "orders"), moduleItem("委外加工", "subcontracts"), moduleItem("到货协同", "receipts"), moduleItem("采购退货", "returns"), moduleItem("供应商协同", "supplier-collaboration", undefined, "已规划"), moduleItem("供应商绩效", "supplier-performance")],
  },
  {
    id: "warehouse", slug: "warehouse", label: "仓储物流", icon: "inventory_2", capability: "WMS",
    description: "仓库、库位、收发、生产物流与批次库存",
    modules: [
      moduleItem("库存总览", "inventory", [child("现存量", "on-hand"), child("可用量", "available"), child("库存台账", "ledger")]),
      moduleItem("收货上架", "receiving"),
      moduleItem("生产备料", "staging"),
      moduleItem("领退料", "material-issues"),
      moduleItem("成品入库", "finished-goods"),
      moduleItem("销售发货", "sales-shipping", [child("待拣货", "picking"), child("复核装箱", "packing"), child("出库交接", "handover"), child("签收回单", "proof-of-delivery")]),
      moduleItem("库存作业", "inventory-operations", [child("库存调拨", "transfers"), child("库存调整", "adjustments"), child("冻结解冻", "holds"), child("其他出入库", "other-movements")]),
      moduleItem("盘点作业", "counts"),
      moduleItem("批次与序列号", "lot-serial"),
      moduleItem("库存占用", "reservations"),
      moduleItem("条码与标签", "barcodes", [child("标签模板", "templates"), child("标签打印", "printing"), child("扫码作业", "scanning")]),
      moduleItem("库存策略", "strategies"),
      moduleItem("运输交接", "transport-handover", undefined, "已规划"),
    ],
  },
  {
    id: "production", slug: "production", label: "生产管理", icon: "precision_manufacturing", capability: "MES",
    description: "生产工单、工序执行、报工、在制与追溯",
    modules: [
      moduleItem("生产订单", "orders", [child("订单列表", "list"), child("下达记录", "releases")]),
      moduleItem("车间工单", "work-orders", [child("工单看板", "board"), child("工序任务", "operations"), child("工序执行台", "execution"), child("电子作业指导", "instructions")]),
      moduleItem("派工与报工", "reporting", [child("派工队列", "dispatch"), child("生产报工", "reports"), child("报工审核", "approvals")]),
      moduleItem("在制品", "wip"),
      moduleItem("异常管理", "exceptions", [child("异常记录", "records"), child("返工返修", "rework"), child("报废处置", "scrap")]),
      moduleItem("生产追溯", "traceability", [child("批次追溯", "lots"), child("序列号追溯", "serials")]),
      moduleItem("车间移动作业", "mobile-operations", [child("扫码领料", "material-scan"), child("扫码报工", "reporting-scan"), child("标签补打", "label-reprint")]),
      moduleItem("班组与人员", "workforce"),
      moduleItem("生产资源", "resources"),
    ],
  },
  {
    id: "quality", slug: "quality", label: "质量管理", icon: "verified", capability: "QMS",
    description: "检验标准、来料、过程、完工与不合格闭环",
    modules: [moduleItem("质量计划", "plans"), moduleItem("质量标准", "standards"), moduleItem("抽样方案", "sampling"), moduleItem("缺陷代码", "defect-codes"), moduleItem("来料检验", "incoming"), moduleItem("过程检验", "process"), moduleItem("完工检验", "final"), moduleItem("不合格品", "nonconformance", [child("不合格记录", "records"), child("评审处置", "reviews"), child("纠正措施", "actions")]), moduleItem("质量追溯", "traceability"), moduleItem("供应商质量", "supplier-quality"), moduleItem("客诉与改进", "customer-quality", [child("客诉记录", "complaints"), child("8D 改进", "eight-d")]), moduleItem("量检具管理", "gauges", [child("量检具台账", "assets"), child("校准计划", "calibration")]), moduleItem("SPC 分析", "spc", undefined, "已规划")],
  },
  {
    id: "equipment", slug: "equipment", label: "设备与资产", icon: "manufacturing", capability: "EAM",
    description: "设备台账、点检、保养、维修与工装模具",
    modules: [moduleItem("设备台账", "assets"), moduleItem("设备状态", "status"), moduleItem("点检计划", "inspections"), moduleItem("保养计划", "maintenance"), moduleItem("维修工单", "work-orders"), moduleItem("备件管理", "spare-parts"), moduleItem("工装模具", "tooling"), moduleItem("计量抄表", "meters"), moduleItem("OEE 与停机", "oee"), moduleItem("故障知识库", "fault-knowledge"), moduleItem("设备采集", "telemetry", undefined, "已规划")],
  },
  {
    id: "finance", slug: "finance", label: "成本与结算", icon: "account_balance_wallet", capability: "ERP",
    description: "销售与采购结算、应收应付、材料与制造成本、订单利润",
    modules: [moduleItem("应收管理", "receivables"), moduleItem("销售开票与收款", "sales-settlement", [child("销售开票", "invoicing"), child("收款核销", "receipts"), child("客户对账", "reconciliation")]), moduleItem("应付管理", "payables"), moduleItem("采购发票与付款", "purchase-settlement", [child("采购发票", "invoices"), child("付款核销", "payments"), child("供应商对账", "reconciliation"), child("暂估应付", "grir-accruals")]), moduleItem("会计期间", "accounting-periods", undefined, "已启用"), moduleItem("标准成本", "standard-costs"), moduleItem("材料成本", "material-costs"), moduleItem("制造成本", "manufacturing-costs"), moduleItem("实际成本", "actual-costs"), moduleItem("成本差异", "cost-variances"), moduleItem("订单利润", "order-profit"), moduleItem("总账税务", "general-ledger", undefined, "已规划")],
  },
  {
    id: "analytics", slug: "analytics", label: "数据分析", icon: "monitoring", capability: "BI",
    description: "经营、交付、生产、质量与设备指标",
    modules: [moduleItem("经营分析", "operations"), moduleItem("交付分析", "delivery"), moduleItem("生产看板", "production"), moduleItem("质量分析", "quality"), moduleItem("库存分析", "inventory"), moduleItem("设备分析", "equipment"), moduleItem("成本分析", "cost"), moduleItem("指标定义", "metrics"), moduleItem("自定义报表", "reports", undefined, "已规划")],
  },
  {
    id: "settings", slug: "settings", label: "系统管理", icon: "settings", capability: "SYS",
    description: "组织、权限、审批、消息、审计与集成配置",
    modules: [moduleItem("组织与用户", "organization", [child("组织架构", "structure"), child("岗位管理", "positions"), child("用户管理", "users")]), moduleItem("角色权限", "roles"), moduleItem("审批流程", "workflows"), moduleItem("基础资料", "master-data", [child("工厂与车间", "plants"), child("工作中心", "work-centers"), child("班次与日历", "calendars"), child("仓库与库位", "warehouses"), child("业务原因码", "reason-codes")]), moduleItem("编号规则", "numbering"), moduleItem("系统参数", "parameters"), moduleItem("消息模板", "notification-templates"), moduleItem("打印模板", "print-templates"), moduleItem("AI 管理中心", "ai", [child("助手配置", "assistants"), child("知识范围", "knowledge"), child("技能与场景", "skills"), child("权限与审计", "governance")], "已启用"), moduleItem("开放接口", "integrations", [child("API 应用", "api-clients"), child("Webhook", "webhooks"), child("外部编码映射", "external-codes"), child("集成日志", "logs")], "已规划"), moduleItem("操作审计", "audit"), moduleItem("数据归档", "retention"), moduleItem("后台任务监控", "jobs", [child("导入任务", "imports"), child("导出任务", "exports"), child("调度任务", "background")])],
  },
];

export type ResolvedProductRoute = {
  area: ProductArea;
  module?: ProductModule;
  child?: ProductChild;
  pathname: string;
};

export function areaPath(area: ProductArea) {
  return area.slug ? `/${area.slug}` : "/";
}

export function modulePath(area: ProductArea, item: ProductModule) {
  return `${areaPath(area).replace(/\/$/, "")}/${item.slug}`;
}

export function childPath(area: ProductArea, item: ProductModule, itemChild: ProductChild) {
  return `${modulePath(area, item)}/${itemChild.slug}`;
}

export function resolveProductRoute(pathname: string): ResolvedProductRoute | null {
  const segments = pathname.split("/").filter(Boolean);
  if (segments.length === 0) return { area: productAreas[0], module: productAreas[0].modules[0], pathname: "/" };
  const overviewArea = productAreas[0];
  const overviewModule = overviewArea.modules.find((item) => item.slug === segments[0]);
  if (overviewModule) {
    if (segments.length === 1) return { area: overviewArea, module: overviewModule, pathname };
    const overviewChild = overviewModule.children?.find((candidate) => candidate.slug === segments[1]);
    if (overviewChild && segments.length === 2) return { area: overviewArea, module: overviewModule, child: overviewChild, pathname };
    return null;
  }
  const area = productAreas.find((item) => item.slug === segments[0]);
  if (!area) return null;
  if (segments.length === 1) return { area, pathname };
  const item = area.modules.find((candidate) => candidate.slug === segments[1]);
  if (!item) return null;
  if (segments.length === 2) return { area, module: item, pathname };
  const itemChild = item.children?.find((candidate) => candidate.slug === segments[2]);
  if (!itemChild || segments.length > 3) return null;
  return { area, module: item, child: itemChild, pathname };
}

export function allProductPaths() {
  return productAreas.flatMap((area) => [
    areaPath(area),
    ...area.modules.flatMap((item) => [
      modulePath(area, item),
      ...(item.children?.map((itemChild) => childPath(area, item, itemChild)) ?? []),
    ]),
  ]);
}

