import type { ManufacturingSnapshot } from "./contracts";
import type { ResolvedProductRoute } from "./product-navigation";
import { getBusinessPageSpecialization } from "./business-page-specializations";
import { getCatalogPageSpecialization } from "./business-page-catalog";

export type BusinessTone = "good" | "warn" | "risk" | "info";

export type BusinessLayout = "work" | "relationship" | "document" | "catalog" | "planning" | "execution" | "inventory" | "quality" | "equipment" | "finance" | "analytics" | "settings";

export type BusinessFormField = {
  name: string;
  label: string;
  type: "text" | "number" | "date" | "select" | "textarea";
  placeholder?: string;
  required?: boolean;
  options?: string[];
  span?: "full";
};

export type BusinessRow = {
  id: string;
  entityId?: string;
  version?: number;
  dataSource?: "mock" | "backend";
  formValues?: Record<string, string>;
  cells: string[];
  status: string;
  tone?: BusinessTone;
  owner: string;
  description: string;
  /** Mock 数据距当前日期的天数，用于验证时间范围筛选。 */
  ageInDays?: number;
};

export type BusinessPageModel = {
  definitionId: string;
  pathname: string;
  eyebrow: string;
  title: string;
  description: string;
  icon: string;
  recordNoun: string;
  primaryAction: string;
  primaryActionMode: "form" | "refresh" | "query" | "export" | "feedback";
  planned: boolean;
  dataSource?: "mock" | "backend";
  layout: BusinessLayout;
  context: {
    kicker: string;
    title: string;
    summary: string;
    items: Array<{ label: string; value: string; note: string; progress: number; tone: BusinessTone }>;
  };
  metrics: Array<{ label: string; value: string; note: string; tone?: BusinessTone }>;
  views: string[];
  filters: Array<{ label: string; options: string[] }>;
  columns: string[];
  rows: BusinessRow[];
  attentionTitle: string;
  attentionItems: Array<{ title: string; detail: string; owner: string; tone: BusinessTone }>;
  formFields: BusinessFormField[];
  cellFields: string[];
  workflow: Array<{ label: string; detail: string }>;
};

export type BusinessPageSpecialization = Partial<Omit<BusinessPageModel, "pathname" | "eyebrow">>;

type ProfileId = "overview" | "crm" | "document" | "master" | "planning" | "execution" | "inventory" | "quality" | "equipment" | "finance" | "analytics" | "settings";

type Profile = {
  layout: BusinessLayout;
  icon: string;
  prefix: string;
  columns: string[];
  objects: string[];
  owners: string[];
  values: string[];
  formFields: BusinessFormField[];
  views: string[];
};

const commonFields = (objectLabel: string): BusinessFormField[] => [
  { name: "name", label: objectLabel, type: "text", required: true, placeholder: `请输入${objectLabel}` },
  { name: "owner", label: "负责人", type: "select", required: true, options: ["林浩", "周洁", "王峻", "许雯", "陈琪"] },
  { name: "date", label: "计划日期", type: "date", required: true },
  { name: "priority", label: "优先级", type: "select", options: ["普通", "紧急", "关键"] },
  { name: "remark", label: "业务说明", type: "textarea", placeholder: "补充业务背景、交付要求或注意事项", span: "full" },
];

const profiles: Record<ProfileId, Profile> = {
  overview: { layout: "work", icon: "space_dashboard", prefix: "OV", columns: ["事项编号", "工作事项", "来源流程", "截止时间", "更新时间"], objects: ["本周重点经营任务", "订单到交付主流程", "跨部门风险闭环"], owners: ["经营办", "林浩", "王峻"], values: ["今日 16:00", "明日 10:00", "本周五"], formFields: commonFields("事项名称"), views: ["我的工作", "今日到期", "我关注的"] },
  crm: { layout: "relationship", icon: "groups", prefix: "CRM", columns: ["档案编号", "名称", "分类 / 角色", "交易概况", "更新时间"], objects: ["恒锐自动化", "创驰装备", "东岳电气"], owners: ["沈妍", "赵辰", "沈妍"], values: ["年交易 ¥286万", "合作 4 年", "信用 A 级"], formFields: commonFields("名称"), views: ["全部关系", "我负责的", "重点对象"] },
  document: { layout: "document", icon: "description", prefix: "DOC", columns: ["单据编号", "业务对象", "关联来源", "金额 / 数量", "计划日期"], objects: ["恒锐自动化 · GS-800", "创驰装备 · PM-45", "东岳电气 · QC-20"], owners: ["沈妍", "赵辰", "周洁"], values: ["¥86.4万", "120套", "¥72.0万"], formFields: [...commonFields("业务对象"), { name: "amount", label: "金额 / 数量", type: "text", required: true, placeholder: "例如 ¥86.4万 或 120套" }], views: ["全部单据", "待处理", "本月到期"] },
  master: { layout: "catalog", icon: "schema", prefix: "MD", columns: ["对象编码", "名称", "分类 / 版本", "关键属性", "更新时间"], objects: ["伺服驱动控制柜 GS-800", "精密传动模组 PM-45", "智能检测工作站 QC-20"], owners: ["何工", "顾工", "唐工"], values: ["V3.2 · 68项", "R2.6 · 42项", "V1.8 · 96项"], formFields: [...commonFields("名称"), { name: "version", label: "版本 / 规格", type: "text", required: true, placeholder: "例如 V1.0" }], views: ["有效版本", "修订中", "待发布"] },
  planning: { layout: "planning", icon: "event_note", prefix: "PLN", columns: ["计划编号", "计划对象", "周期 / 日期", "完成度", "负责人"], objects: ["GS-800 本周交付需求", "PM-45 插单需求", "全工厂日运算方案"], owners: ["林浩", "宋可", "系统"], values: ["92%", "68%", "已完成"], formFields: [...commonFields("计划对象"), { name: "quantity", label: "计划数量", type: "number", required: true, placeholder: "0" }], views: ["计划看板", "异常建议", "已下达"] },
  execution: { layout: "execution", icon: "precision_manufacturing", prefix: "MO", columns: ["任务编号", "生产对象", "车间 / 工位", "进度 / 数量", "计划时间"], objects: ["伺服驱动控制柜 GS-800", "精密传动模组 PM-45", "智能检测工作站 QC-20"], owners: ["王峻", "陈磊", "刘鹏"], values: ["72% · 24台", "48% · 120套", "88% · 6套"], formFields: [...commonFields("生产对象"), { name: "workshop", label: "车间 / 工位", type: "select", required: true, options: ["机加车间", "总装一车间", "总装二车间", "电子车间"] }], views: ["执行看板", "待处理", "异常任务"] },
  inventory: { layout: "inventory", icon: "inventory_2", prefix: "STK", columns: ["业务编号", "物料 / 作业", "仓库库位", "数量", "发生时间"], objects: ["轴承 BR-6204", "GS-800 生产备料", "铝合金型材"], owners: ["徐峰", "吴倩", "方敏"], values: ["420件 / 可用340件", "6类 / 齐套92%", "1,200kg / 待检"], formFields: [...commonFields("物料 / 作业"), { name: "warehouse", label: "仓库 / 库位", type: "select", required: true, options: ["原材料仓 A区", "线边仓 L区", "成品仓 F区", "待检区 IQC"] }, { name: "quantity", label: "数量", type: "number", required: true, placeholder: "0" }], views: ["库存视图", "今日作业", "异常库存"] },
  quality: { layout: "quality", icon: "verified", prefix: "QC", columns: ["记录编号", "检验对象", "检验类型", "抽样 / 数量", "检验员"], objects: ["轴承 BR-6204", "控制柜门板 GS-800", "智能检测工作站 QC-20"], owners: ["许雯", "王敏", "陈琪"], values: ["抽样32件", "2件 / 尺寸超差", "6套 / 完成4套"], formFields: [...commonFields("检验对象"), { name: "standard", label: "检验标准", type: "select", required: true, options: ["IQC-AQL 1.0", "IPQC-尺寸标准", "FQC-整机标准"] }, { name: "sample", label: "抽样数量", type: "number", required: true, placeholder: "0" }], views: ["检验任务", "待判定", "已完成"] },
  equipment: { layout: "equipment", icon: "manufacturing", prefix: "EAM", columns: ["设备 / 工单编号", "对象名称", "位置 / 周期", "状态数据", "责任人"], objects: ["立式加工中心 CNC-07", "电气装配台 ASM-12", "综合测试台 TEST-04"], owners: ["赵凯", "陈磊", "刘鹏"], values: ["停机 46分钟", "运行 98.2%", "待点检"], formFields: [...commonFields("设备 / 作业名称"), { name: "asset", label: "设备", type: "select", required: true, options: ["CNC-07", "ASM-12", "TEST-04"] }, { name: "cycle", label: "周期 / 工时", type: "text", placeholder: "例如 30天" }], views: ["设备视图", "今日任务", "异常设备"] },
  finance: { layout: "finance", icon: "account_balance_wallet", prefix: "FIN", columns: ["凭证 / 对象编号", "往来 / 订单对象", "期间", "金额", "负责人"], objects: ["恒锐自动化应收", "华轴精工应付", "SO-260814-036 订单利润"], owners: ["孙琳", "魏铭", "孙琳"], values: ["¥86.4万", "¥38.6万", "毛利 24.8%"], formFields: [...commonFields("往来 / 订单对象"), { name: "amount", label: "金额", type: "number", required: true, placeholder: "0.00" }, { name: "account", label: "会计科目", type: "select", options: ["应收账款", "应付账款", "主营业务成本", "制造费用"] }], views: ["本期数据", "待核算", "异常差异"] },
  analytics: { layout: "analytics", icon: "monitoring", prefix: "BI", columns: ["指标 / 报表编号", "分析主题", "统计周期", "当前值", "更新时间"], objects: ["订单交付健康度", "车间计划达成率", "一次检验合格率"], owners: ["经营办", "生产部", "质量部"], values: ["94.2%", "93.4%", "97.6%"], formFields: [...commonFields("分析主题"), { name: "dimension", label: "分析维度", type: "select", required: true, options: ["按工厂", "按车间", "按产品", "按客户"] }, { name: "cycle", label: "统计周期", type: "select", options: ["本日", "本周", "本月", "本季度"] }], views: ["核心指标", "趋势分析", "异常下钻"] },
  settings: { layout: "settings", icon: "settings", prefix: "SYS", columns: ["对象编号", "名称", "类型", "配置 / 权限", "更新时间"], objects: ["华东制造中心", "生产计划员", "采购订单审批"], owners: ["系统管理员", "蒋宁", "蒋宁"], values: ["已启用", "12项权限", "V2.1"], formFields: [...commonFields("配置名称"), { name: "scope", label: "适用范围", type: "select", required: true, options: ["全公司", "华东制造中心", "指定部门", "指定用户"] }], views: ["全部配置", "已启用", "待发布"] },
};

const areaProfiles: Record<string, ProfileId> = {
  overview: "overview", sales: "document", procurement: "document", product: "master", planning: "planning", production: "execution",
  warehouse: "inventory", quality: "quality", equipment: "equipment", finance: "finance", analytics: "analytics", settings: "settings",
};

const crmPages = new Set(["客户", "客户列表", "联系人", "信用资料", "供应商"]);

const actionLabels: Record<string, string> = {
  "我的待办": "处理待办", "我的审批": "批量审批", "我的发起": "发起业务流程", "我的关注": "添加关注",
  "业务流程": "发起流程", "风险中心": "登记风险", "客户列表": "新建客户", "联系人": "新建联系人", "信用资料": "维护信用资料",
  "订单审核": "发起审核", "变更记录": "申请订单变更", "待发货": "创建发货单", "交付跟踪": "更新交付节点", "采购申请": "新建采购申请",
  "到货协同": "登记到货", "采购退货": "新建采购退货", "供应商绩效": "发起绩效评估", "版本比较": "选择比较版本", "替代料": "维护替代关系", "运算方案": "新建运算方案", "运算记录": "发起 MRP 运算",
  "受控图纸": "上传受控图纸", "技术文件": "新建技术文件", "文档发布": "发起文档发布",
  "供需建议": "生成业务单据", "物料齐套": "执行齐套检查", "产能负荷": "测算产能", "高级排程": "新建排程方案", "工单看板": "新建车间工单",
  "派工队列": "创建派工", "生产报工": "新建报工", "报工审核": "发起报工审核", "批次追溯": "查询批次", "序列号追溯": "查询序列号",
  "异常记录": "登记生产异常", "返工返修": "创建返工单", "报废处置": "发起报废审批", "扫码领料": "开始扫码领料", "扫码报工": "开始扫码报工", "标签补打": "申请标签补打",
  "现存量": "库存调整", "可用量": "执行可用量检查", "库存台账": "导入库存流水", "收货上架": "创建上架任务",
  "待拣货": "创建拣货任务", "复核装箱": "开始复核装箱", "出库交接": "确认出库交接", "签收回单": "登记签收回单",
  "生产备料": "创建备料任务", "领退料": "新建领退料单", "成品入库": "创建入库单", "盘点作业": "发起盘点", "待检任务": "创建检验任务",
  "库存调拨": "新建调拨单", "库存调整": "新建调整单", "冻结解冻": "发起冻结操作", "其他出入库": "新建出入库单",
  "标签模板": "新建标签模板", "标签打印": "创建打印任务", "扫码作业": "开始扫码作业",
  "检验记录": "录入检验结果", "评审处置": "发起评审", "纠正措施": "创建纠正措施", "质量追溯": "发起质量追溯", "量检具台账": "新增量检具", "校准计划": "新建校准计划", "客诉记录": "登记客户投诉", "8D 改进": "发起 8D 改进", "点检计划": "新建点检计划",
  "保养计划": "新建保养计划", "维修工单": "创建维修工单", "工装模具": "新增工装模具", "备件管理": "新增备件", "设备采集": "接入设备",
  "销售开票": "申请销售开票", "收款核销": "登记收款核销", "客户对账": "生成客户对账单", "采购发票": "登记采购发票", "付款核销": "登记付款核销", "供应商对账": "生成供应商对账单",
  "材料成本": "执行材料核算", "制造成本": "执行成本结转",
  "订单利润": "重新测算利润", "总账税务": "创建记账凭证", "自定义报表": "新建报表", "组织架构": "新建组织单元", "用户管理": "邀请用户",
  "工厂与车间": "新建工厂或车间", "工作中心": "新建工作中心", "班次与日历": "新建生产日历", "仓库与库位": "新建仓库或库位", "业务原因码": "新建原因码",
  "岗位管理": "新建岗位", "角色权限": "新建角色", "审批流程": "新建审批流程", "编号规则": "新建编号规则", "操作审计": "导出审计记录", "开放接口": "创建应用凭证",
  "消息中心": "新建消息规则", "批量任务": "新建批量任务", "导入任务": "创建导入任务", "导出任务": "创建导出任务", "后台任务": "新建后台任务",
  "价目表": "新建价目表", "折扣政策": "新建折扣政策", "销售合同": "新建销售合同", "销售预测": "新建销售预测",
  "询价单": "新建询价单", "比价决策": "发起比价决策", "采购合同": "新建采购合同", "采购价格": "维护采购价格", "供应商协同": "发起协同事项",
  "独立需求": "新建独立需求", "需求合并": "执行需求合并", "可承诺量": "执行ATP检查", "计划例外": "处理计划例外", "计划参数": "新建参数方案",
  "工序执行台": "开始工序作业", "电子作业指导": "发布作业指导", "班组与人员": "新建班组", "生产资源": "新增生产资源",
  "批次与序列号": "新建批次规则", "库存占用": "新建库存占用", "库存策略": "新建库存策略", "运输交接": "创建运输交接",
  "质量计划": "新建质量计划", "抽样方案": "新建抽样方案", "缺陷代码": "新建缺陷代码", "供应商质量": "发起供应商质量评估", "SPC 分析": "新建SPC分析",
  "设备状态": "更新设备状态", "计量抄表": "录入计量读数", "OEE 与停机": "登记停机事件", "故障知识库": "新增故障知识",
  "成本期间": "新建成本期间", "标准成本": "发布标准成本", "实际成本": "执行实际成本核算", "成本差异": "分析成本差异",
  "设备分析": "刷新设备分析", "库存分析": "刷新库存分析", "成本分析": "刷新成本分析", "指标定义": "新建指标",
  "系统参数": "新建系统参数", "消息模板": "新建消息模板", "打印模板": "新建打印模板", "数据归档": "新建归档策略", "API 应用": "创建API应用", "Webhook": "新建Webhook", "外部编码映射": "新建编码映射", "集成日志": "导出集成日志",
};

const descriptions: Record<string, string> = {
  "我的工作": "汇总个人待办、审批、发起事项与关注对象，让跨部门任务有统一入口和处理时限。",
  "客户": "统一维护客户、联系人、信用与交易关系，支撑从商机到回款的全过程协同。",
  "销售订单": "管理客户订单、交付承诺、审核与变更，向计划端传递可信需求。",
  "图纸与文档": "统一控制图纸、技术文件和发布版本，确保现场始终使用有效资料。",
  "MRP 运算": "基于需求、库存、在途和提前期计算净需求，并保留每次运算证据。",
  "车间工单": "把生产订单拆解为可执行的工序任务，明确人员、工位、物料和时间。",
  "异常管理": "从异常登记到返工、返修和报废审批，保留完整处置责任与证据。",
  "车间移动作业": "面向现场扫码领料、报工与标签补打，减少手工录入和对象识别差错。",
  "库存总览": "从现存、可用、批次与流水四个维度掌握库存事实和占用关系。",
  "销售发货": "衔接销售交付承诺与仓库执行，完成拣货、复核、装箱、交接和签收闭环。",
  "库存作业": "统一处理调拨、调整、冻结解冻和其他出入库，确保每次库存变化均可追溯。",
  "条码与标签": "集中维护标签模板、打印任务与扫码作业，统一现场对象身份和作业证据。",
  "不合格品": "记录不合格事实，完成评审、处置和纠正措施闭环。",
  "量检具管理": "管理量检具台账、状态和校准计划，避免使用超期或失准的检验工具。",
  "客诉与改进": "从客户投诉登记到 8D 改进验证，形成责任明确的外部质量闭环。",
  "销售开票与收款": "连接销售订单、发货与应收，完成开票、收款核销和客户对账。",
  "采购发票与付款": "连接采购订单、收货与应付，完成发票匹配、付款核销和供应商对账。",
  "基础资料": "统一维护工厂、车间、工作中心、生产日历、仓库库位和业务原因码。",
  "组织与用户": "统一管理组织、用户和岗位归属，为权限与审批提供基础。",
  "消息中心": "集中查看业务提醒、系统消息和异常通知，支持已读、归档和按业务对象下钻。",
  "任务中心": "统一承载批量、导入、导出和后台任务，展示进度、结果、失败原因与重试证据。",
  "价格与折扣": "受控管理客户价目表、币种、税率和折扣政策，确保报价与订单采用有效价格。",
  "询价比价": "从供应商询价、报价回收、价格比较到采购决策形成可追溯的寻源证据。",
  "需求管理": "统一维护预测、独立需求与客户订单需求，为主计划和MRP提供可信输入。",
  "批次与序列号": "集中管理批次和序列号规则、状态、库存位置及其完整业务追溯链。",
  "质量计划": "按产品、供应商和工艺阶段定义检验关口、特性、抽样与判定要求。",
  "OEE 与停机": "按设备和班次分解可用率、性能率、质量率与停机损失，为改善提供证据。",
  "成本期间": "控制成本核算期间的打开、结算、重开和锁定，确保成本口径稳定可追溯。",
  "指标定义": "统一定义指标口径、维度、刷新频率和责任部门，避免分析页面口径不一致。",
  "系统参数": "按全公司、工厂和组织范围管理平台参数、字典和生效版本。",
};

function resolveProfile(areaId: string, title: string): ProfileId {
  if (crmPages.has(title)) return "crm";
  return areaProfiles[areaId] ?? "overview";
}

function statusSet(profile: ProfileId) {
  if (profile === "master" || profile === "settings") return [{ label: "已发布", tone: "good" as const }, { label: "审核中", tone: "warn" as const }, { label: "草稿", tone: "info" as const }];
  if (profile === "quality") return [{ label: "待检验", tone: "info" as const }, { label: "执行中", tone: "warn" as const }, { label: "已判定", tone: "good" as const }];
  if (profile === "finance") return [{ label: "待核算", tone: "warn" as const }, { label: "已确认", tone: "good" as const }, { label: "有差异", tone: "risk" as const }];
  return [{ label: "执行中", tone: "info" as const }, { label: "有风险", tone: "warn" as const }, { label: "已完成", tone: "good" as const }];
}

function stableCode(value: string) {
  return [...value].reduce((total, char) => total + char.charCodeAt(0), 0) % 900 + 100;
}

function recordCount(title: string) {
  return 28 + stableCode(title) % 19;
}

function makeRows(title: string, profileId: ProfileId, profile: Profile, snapshot: ManufacturingSnapshot): BusinessRow[] {
  if (title === "生产订单" || title === "订单列表" && profileId === "execution") {
    return Array.from({ length: recordCount(title) }, (_, index) => {
      const order = snapshot.workOrders[index % snapshot.workOrders.length];
      const batch = Math.floor(index / snapshot.workOrders.length);
      return {
      id: batch === 0 ? order.id : `${order.id}-${String(batch + 1).padStart(2, "0")}`,
      cells: [batch === 0 ? order.product : `${order.product} · 批次${batch + 1}`, order.workshop, `${Math.max(16, order.progress - batch * 3)}% · ${order.quantity}`, `2026-${order.dueDate}`],
      status: order.status,
      tone: order.status === "有风险" ? "warn" : order.status === "待检验" ? "good" : "info",
      owner: "王峻",
      description: `${order.customer}的生产需求，当前在${order.workshop}执行。`,
      ageInDays: index * 2,
      };
    });
  }
  const statuses = statusSet(profileId);
  const code = stableCode(title);
  const dates = ["今天 10:42", "今天 09:18", "昨天 16:20", "08-13 14:36", "08-12 09:18", "08-11 17:05"];
  const qualifiers = ["标准", "重点", "例行", "加急", "月度", "临时"];
  return Array.from({ length: recordCount(title) }, (_, index) => {
    const baseIndex = index % profile.objects.length;
    const batch = Math.floor(index / profile.objects.length);
    const status = statuses[index % statuses.length];
    const object = profile.objects[baseIndex];
    return {
    id: `${profile.prefix}-${String(code + index).padStart(4, "0")}`,
    cells: [batch === 0 ? object : `${object} · ${String(batch + 1).padStart(2, "0")}`, `${title} · ${qualifiers[index % qualifiers.length]}`, profile.values[baseIndex], dates[index % dates.length]],
    status: status.label,
    tone: status.tone,
    owner: profile.owners[baseIndex],
    description: `${object}的${title}记录，责任人${profile.owners[baseIndex]}，相关业务证据已归档。`,
    ageInDays: index * 3,
    };
  });
}

function makeMetrics(title: string, profileId: ProfileId, snapshot: ManufacturingSnapshot): BusinessPageModel["metrics"] {
  const count = recordCount(title);
  const areaMetric = snapshot.metrics[profileId === "quality" ? 3 : profileId === "execution" ? 2 : 0];
  return [
    { label: `${title}总量`, value: String(count), note: "较上期 +6.8%", tone: "good" },
    { label: "待处理", value: String(count % 9 + 3), note: "最早等待 2 小时", tone: "warn" },
    { label: "本周完成", value: String(count % 24 + 12), note: areaMetric?.change ?? "按计划推进", tone: "good" },
    { label: "风险 / 异常", value: String(count % 4 + 1), note: "需要责任人关注", tone: "risk" },
  ];
}

const contextBlueprints: Record<BusinessLayout, Omit<BusinessPageModel["context"], "title">> = {
  work: { kicker: "个人工作节奏", summary: "按照截止时间和业务影响组织个人工作，不让跨部门事项停在无人处理的节点。", items: [
    { label: "今日必须完成", value: "6 项", note: "2 项将在 2 小时内到期", progress: 76, tone: "warn" },
    { label: "等待我审批", value: "4 项", note: "采购与生产变更为主", progress: 54, tone: "info" },
    { label: "他人协同中", value: "9 项", note: "3 项需要今日跟进", progress: 68, tone: "good" },
    { label: "本周已完成", value: "31 项", note: "平均处理时长 3.2 小时", progress: 88, tone: "good" },
  ] },
  relationship: { kicker: "业务关系健康度", summary: "把档案、信用、联系人和交易事实放在同一视图，优先处理关系风险。", items: [
    { label: "战略合作", value: "8 家", note: "贡献本年收入 62%", progress: 82, tone: "good" },
    { label: "信用正常", value: "93%", note: "2 家接近额度上限", progress: 93, tone: "good" },
    { label: "待维护档案", value: "5 项", note: "联系人或开票资料缺失", progress: 42, tone: "warn" },
    { label: "近期无互动", value: "3 家", note: "超过 45 天未发生业务", progress: 28, tone: "risk" },
  ] },
  document: { kicker: "业务单据履约", summary: "按来源、审核、执行和关闭节点观察单据流转，及时识别金额与交期风险。", items: [
    { label: "已创建", value: "46", note: "来源关系完整", progress: 100, tone: "good" },
    { label: "审核流转", value: "12", note: "最早等待 3.6 小时", progress: 72, tone: "warn" },
    { label: "执行协同", value: "21", note: "4 项存在交付偏差", progress: 58, tone: "info" },
    { label: "本月关闭", value: "35", note: "关闭及时率 94.6%", progress: 95, tone: "good" },
  ] },
  catalog: { kicker: "受控主数据", summary: "用受控版本组织物料、结构、工艺与技术文件，保证下游引用的是同一事实。", items: [
    { label: "有效版本", value: "128", note: "已被生产与采购引用", progress: 92, tone: "good" },
    { label: "修订中", value: "7", note: "3 项等待工程确认", progress: 46, tone: "warn" },
    { label: "待发布", value: "4", note: "影响 11 张未下达工单", progress: 31, tone: "risk" },
    { label: "完整性", value: "97.2%", note: "缺失属性集中在辅料", progress: 97, tone: "good" },
  ] },
  planning: { kicker: "未来七日计划窗口", summary: "对齐需求、物料和产能，在未来七日窗口内持续消解计划冲突。", items: [
    { label: "今天", value: "92%", note: "计划达成 23 / 25", progress: 92, tone: "good" },
    { label: "明天", value: "86%", note: "机加能力接近预警线", progress: 86, tone: "warn" },
    { label: "后天", value: "74%", note: "关键物料仍有 2 项缺口", progress: 74, tone: "risk" },
    { label: "未来七日", value: "81%", note: "可承诺产能保持稳定", progress: 81, tone: "info" },
  ] },
  execution: { kicker: "车间执行脉搏", summary: "以车间、工位和工序节拍观察执行现场，异常直接关联责任人与业务证据。", items: [
    { label: "机加车间", value: "84%", note: "在制 18 项 · 异常 1 项", progress: 84, tone: "warn" },
    { label: "总装一车间", value: "76%", note: "在制 12 项 · 节拍正常", progress: 76, tone: "good" },
    { label: "总装二车间", value: "68%", note: "等待齐套 3 项", progress: 68, tone: "info" },
    { label: "电子车间", value: "91%", note: "今日计划即将完成", progress: 91, tone: "good" },
  ] },
  inventory: { kicker: "仓储作业态势", summary: "从库区、库位与作业队列观察库存变化，让每一次移动都有来源和去向。", items: [
    { label: "原材料仓 A 区", value: "78%", note: "容量正常 · 待上架 6 托", progress: 78, tone: "good" },
    { label: "线边仓 L 区", value: "66%", note: "待补料 4 项", progress: 66, tone: "info" },
    { label: "待检区 IQC", value: "88%", note: "接近容量预警线", progress: 88, tone: "warn" },
    { label: "成品仓 F 区", value: "57%", note: "今日待发 8 单", progress: 57, tone: "good" },
  ] },
  quality: { kicker: "质量控制关口", summary: "围绕来料、过程、完工和问题闭环设置质量关口，突出待判定与重复缺陷。", items: [
    { label: "来料关口", value: "98.1%", note: "待判 3 批", progress: 98, tone: "good" },
    { label: "过程关口", value: "96.4%", note: "尺寸类缺陷上升", progress: 82, tone: "warn" },
    { label: "完工关口", value: "99.2%", note: "6 套等待终检", progress: 99, tone: "good" },
    { label: "问题闭环", value: "87%", note: "2 项纠正措施逾期", progress: 87, tone: "risk" },
  ] },
  equipment: { kicker: "设备资产健康度", summary: "把设备状态、点检保养与维修工单放在同一节奏中，减少非计划停机。", items: [
    { label: "健康运行", value: "42 台", note: "综合可用率 96.8%", progress: 97, tone: "good" },
    { label: "今日待点检", value: "6 台", note: "完成 4 / 6", progress: 67, tone: "info" },
    { label: "计划保养", value: "3 台", note: "均已锁定停机窗口", progress: 74, tone: "warn" },
    { label: "维修中", value: "1 台", note: "CNC-07 已停机 46 分钟", progress: 34, tone: "risk" },
  ] },
  finance: { kicker: "结算与账龄", summary: "以业务单据为依据完成开票、核销和对账，持续关注账龄与成本差异。", items: [
    { label: "30 天以内", value: "¥128.6万", note: "占未结金额 68%", progress: 68, tone: "good" },
    { label: "31–60 天", value: "¥42.8万", note: "5 笔等待回款", progress: 42, tone: "info" },
    { label: "61–90 天", value: "¥13.2万", note: "2 笔需要业务跟进", progress: 24, tone: "warn" },
    { label: "90 天以上", value: "¥5.6万", note: "已进入重点催收", progress: 12, tone: "risk" },
  ] },
  analytics: { kicker: "指标趋势分析", summary: "从经营指标下钻到订单、工单和检验记录，确保每个数字都有事实来源。", items: [
    { label: "第 1 周", value: "88.2%", note: "基准周", progress: 72, tone: "info" },
    { label: "第 2 周", value: "91.4%", note: "环比 +3.2%", progress: 81, tone: "good" },
    { label: "第 3 周", value: "90.6%", note: "受插单影响", progress: 78, tone: "warn" },
    { label: "本周", value: "94.2%", note: "当前最优表现", progress: 94, tone: "good" },
  ] },
  settings: { kicker: "平台配置覆盖", summary: "通过适用范围、版本与启用状态管理平台配置，避免规则在组织间失配。", items: [
    { label: "全公司生效", value: "18 项", note: "核心平台规则", progress: 100, tone: "good" },
    { label: "工厂级配置", value: "27 项", note: "覆盖 3 个制造基地", progress: 86, tone: "good" },
    { label: "待发布变更", value: "5 项", note: "2 项影响生产流程", progress: 45, tone: "warn" },
    { label: "配置冲突", value: "1 项", note: "班次日历存在重叠", progress: 18, tone: "risk" },
  ] },
};

function makeContext(title: string, layout: BusinessLayout): BusinessPageModel["context"] {
  const blueprint = contextBlueprints[layout];
  return { ...blueprint, title: `${title} · ${layout === "planning" ? "计划窗口" : layout === "analytics" ? "趋势观察" : layout === "finance" ? "结算结构" : "业务态势"}` };
}

export function createBusinessPageModel(route: ResolvedProductRoute, snapshot: ManufacturingSnapshot): BusinessPageModel {
  const isAreaOverview = !route.module;
  const title = route.child?.label ?? route.module?.label ?? `${route.area.label}总览`;
  const moduleTitle = route.module?.label ?? route.area.label;
  const profileId = resolveProfile(route.area.id, title);
  const profile = profiles[profileId];
  const recordNoun = title.endsWith("列表") ? title.slice(0, -2) : title;
  const planned = route.module?.status === "已规划";

  const baseModel: BusinessPageModel = {
    definitionId: "legacy-generic",
    pathname: route.pathname,
    eyebrow: `${route.area.capability} · ${moduleTitle}`,
    title,
    description: descriptions[title] ?? descriptions[moduleTitle] ?? (isAreaOverview ? route.area.description : `${route.area.description}，集中处理${title}的状态、责任、风险、交期与业务证据。`),
    icon: profile.icon,
    recordNoun,
    primaryAction: actionLabels[title] ?? `新建${recordNoun}`,
    primaryActionMode: "form",
    planned,
    layout: profile.layout,
    context: makeContext(title, profile.layout),
    metrics: makeMetrics(title, profileId, snapshot),
    views: profile.views,
    filters: [
      { label: "全部状态", options: ["全部状态", ...statusSet(profileId).map((item) => item.label)] },
      { label: "全部负责人", options: ["全部负责人", ...new Set(profile.owners)] },
      { label: "本月", options: ["本月", "本周", "今日", "本季度"] },
    ],
    columns: profile.columns,
    rows: makeRows(title, profileId, profile, snapshot),
    attentionTitle: `${title}关注`,
    attentionItems: [
      { title: `${title}中有一项即将到期`, detail: "当前节点尚未提交完整证据，建议责任人在今日 16:00 前处理。", owner: profile.owners[0], tone: "warn" },
      { title: `一项${recordNoun}存在跨部门依赖`, detail: "上游数据已更新，需重新确认数量、交期和责任归属。", owner: profile.owners[1], tone: "risk" },
    ],
    formFields: profile.formFields,
    cellFields: ["name", "priority", "amount", "date"],
    workflow: [
      { label: "创建与校验", detail: "补齐必填信息并校验主数据" },
      { label: "审核与下达", detail: "按权限流转并形成受控版本" },
      { label: "执行与留痕", detail: "记录状态、责任人与业务证据" },
      { label: "完成与复盘", detail: "关闭事项并沉淀分析数据" },
    ],
  };

  const exactSpecialization = getBusinessPageSpecialization(route.pathname);
  const specialization = exactSpecialization ?? getCatalogPageSpecialization(route);
  if (!specialization) return baseModel;

  const specializedRows = specialization.rows ?? baseModel.rows;
  const specializedModel: BusinessPageModel = {
    ...baseModel,
    ...specialization,
    definitionId: specialization.definitionId ?? `special:${route.pathname}`,
    filters: specialization.filters ?? [
      { label: "全部状态", options: ["全部状态", ...new Set(specializedRows.map((row) => row.status))] },
      { label: "全部负责人", options: ["全部负责人", ...new Set(specializedRows.map((row) => row.owner))] },
      { label: "本月", options: ["本月", "本周", "今日", "本季度"] },
    ],
  };

  if (!specializedModel.planned) return specializedModel;

  const isEquipmentTelemetry = specializedModel.pathname === "/equipment/telemetry";

  return {
    ...specializedModel,
    primaryAction: isEquipmentTelemetry ? "提交现场需求" : "提交规划反馈",
    primaryActionMode: "feedback",
    metrics: [
      { label: "当前阶段", value: "能力定义", note: "尚未进入实施", tone: "info" },
      { label: "业务规则", value: "待确认", note: "需要业务负责人评审", tone: "warn" },
      { label: "接口状态", value: "未接入", note: "当前没有真实后端连接", tone: "warn" },
      { label: "运行验证", value: "未开始", note: "不展示模拟运行率", tone: "info" },
    ],
    rows: specializedModel.rows.map((row, index) => {
      const stages = ["业务边界", "权限边界", "接口契约", "验收标准"];
      const states: Array<[string, BusinessTone]> = [["待定义", "info"], ["待评审", "warn"], ["已规划", "good"], ["暂缓", "risk"]];
      const state = states[index % states.length];
      return {
        ...row,
        cells: [`${specializedModel.title}能力项 ${String(index + 1).padStart(2, "0")}`, stages[index % stages.length], "尚未接入真实数据", "进入实施前完成评审"],
        status: state[0],
        tone: state[1],
        owner: index % 2 === 0 ? "业务负责人" : "平台架构组",
        description: `${specializedModel.title}的能力规划记录，不代表接口、设备或后端服务已经运行。`,
      };
    }),
    views: isEquipmentTelemetry ? ["现场清单", "数据质量", "验收标准"] : ["能力边界", "依赖条件", "验收标准"],
    attentionTitle: isEquipmentTelemetry ? specializedModel.attentionTitle : `${specializedModel.title}启用前置条件`,
    attentionItems: isEquipmentTelemetry ? specializedModel.attentionItems : [
      { title: "确认业务规则与数据责任", detail: "明确对象、状态、权限、审计与异常处理边界后才能进入接口设计。", owner: "业务负责人", tone: "warn" },
      { title: "完成接口与运行责任评审", detail: "确认后端服务、数据来源、监控告警和故障恢复方案。", owner: "平台架构组", tone: "info" },
    ],
  };
}
