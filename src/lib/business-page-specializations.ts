import type { BusinessFormField, BusinessPageSpecialization, BusinessRow, BusinessTone } from "./business-page-data";

type Specialization = BusinessPageSpecialization;
type RowSource = [string[], string[], string[], string[]];

function makeRows(
  prefix: string,
  source: RowSource,
  statuses: Array<[string, BusinessTone]>,
  owners: string[],
  description: string,
): BusinessRow[] {
  return Array.from({ length: 24 }, (_, index) => {
    const status = statuses[index % statuses.length];
    // 同一行始终使用同一组样本序号，避免角色、范围、金额等字段被交叉错配。
    const cells = source.map((values) => values[index % values.length]);
    return {
      id: `${prefix}-${String(index + 1).padStart(3, "0")}`,
      cells,
      status: status[0],
      tone: status[1],
      owner: owners[index % owners.length],
      description: `${cells[0]}：${description} 当前状态为${status[0]}，责任人${owners[index % owners.length]}。`,
      ageInDays: index * 3,
    };
  });
}

function fields(items: BusinessFormField[]): BusinessFormField[] {
  return [...items, { name: "owner", label: "负责人", type: "select", required: true, options: ["林浩", "周洁", "王峻", "许雯", "孙琳", "蒋宁"] }, { name: "remark", label: "业务说明", type: "textarea", span: "full", placeholder: "补充来源、约束条件与处理说明" }];
}

const salesOrders: Specialization = {
  description: "管理客户订单、产品与数量、含税金额、要求交期和承诺交期，并持续跟踪审核、下达、发货与回款状态。",
  recordNoun: "销售订单",
  primaryAction: "新建销售订单",
  icon: "receipt_long",
  views: ["全部订单", "待审核", "待交付", "交期风险"],
  columns: ["订单号", "客户 / 产品", "金额 / 币种", "要求 / 承诺交期", "履约进度"],
  rows: makeRows("SO-260814", [
    ["恒锐自动化 · GS-800", "创驰装备 · PM-45", "东岳电气 · QC-20", "明川机器人 · SR-12", "北辰机电 · GS-600"],
    ["¥864,000 · CNY", "¥386,400 · CNY", "$72,800 · USD", "¥198,600 · CNY"],
    ["要求 08-20 · 承诺 08-19", "要求 08-22 · 承诺 08-24", "要求 08-28 · 承诺 08-28", "要求 09-02 · 待承诺"],
    ["已下达 24 / 24", "已生产 72%", "待排产 0 / 12", "已发货 18 / 20", "待审核"],
  ], [["执行中", "info"], ["交期风险", "risk"], ["待审核", "warn"], ["已完成", "good"]], ["沈妍", "赵辰", "周洁"], "订单已经关联客户需求、产品版本、税率和交付承诺。"),
  metrics: [
    { label: "本月订单额", value: "¥628.4万", note: "较上月 +8.6%", tone: "good" },
    { label: "待审核", value: "6 单", note: "最早等待 3.2 小时", tone: "warn" },
    { label: "准时承诺率", value: "94.8%", note: "2 单尚未确认承诺日", tone: "good" },
    { label: "交期风险", value: "3 单", note: "涉及 ¥126.8万", tone: "risk" },
  ],
  context: { kicker: "销售订单履约", title: "订单履约漏斗", summary: "从审核、计划、生产到发货观察客户订单，交期偏差直接回到责任人与业务节点。", items: [
    { label: "等待审核", value: "6 单", note: "其中 1 单超过 4 小时", progress: 34, tone: "warn" },
    { label: "等待计划", value: "9 单", note: "2 单存在物料缺口", progress: 52, tone: "risk" },
    { label: "生产执行", value: "21 单", note: "平均进度 74%", progress: 74, tone: "info" },
    { label: "待发与在途", value: "13 单", note: "本周应交付 8 单", progress: 88, tone: "good" },
  ] },
  formFields: fields([
    { name: "subject", label: "客户 / 产品", type: "text", required: true, placeholder: "选择客户并填写产品" },
    { name: "amount", label: "含税金额 / 币种", type: "text", required: true, placeholder: "例如 ¥864,000 · CNY" },
    { name: "delivery", label: "要求 / 承诺交期", type: "text", required: true, placeholder: "例如 要求 08-20 · 承诺 08-19" },
    { name: "fulfillment", label: "初始履约状态", type: "select", required: true, options: ["待审核", "待承诺", "待排产", "已下达"] },
  ]),
  cellFields: ["subject", "amount", "delivery", "fulfillment"],
  attentionTitle: "订单履约风险",
  attentionItems: [
    { title: "SO-260814-002 承诺日晚于客户要求", detail: "关键外购件预计晚到 2 天，需要销售与计划共同确认替代方案。", owner: "赵辰", tone: "risk" },
    { title: "两张订单尚未完成信用检查", detail: "订单金额已接近客户可用额度，审核前需完成额度释放。", owner: "沈妍", tone: "warn" },
  ],
  workflow: [{ label: "录入与信用检查", detail: "校验客户、产品、价格和信用额度" }, { label: "审核与交期承诺", detail: "确认金额、需求日期与承诺日期" }, { label: "下达与履约", detail: "联动计划、生产、仓储与发货" }, { label: "签收与关闭", detail: "收集签收证据并进入结算" }],
};

const bomList: Specialization = {
  description: "维护父项物料、工厂用途、受控版本、生效日期和行项目结构，确保计划、采购与生产引用同一版 BOM。",
  recordNoun: "BOM版本", primaryAction: "新建BOM版本", icon: "account_tree", views: ["有效版本", "修订中", "待发布", "已失效"],
  columns: ["BOM编号", "父项物料", "工厂 / 用途", "版本 / 生效", "行项目"],
  rows: makeRows("BOM", [["GS-800 伺服驱动控制柜", "PM-45 精密传动模组", "QC-20 智能检测工作站", "SR-12 机器人底座"], ["华东工厂 · 生产", "华南工厂 · 生产", "全工厂 · 报价", "华东工厂 · 返修"], ["V3.2 · 08-01生效", "V2.6 · 08-18待生效", "V1.8 · 修订中", "V4.0 · 待发布"], ["68项 · 2替代料", "42项 · 无缺失", "96项 · 1项缺图", "31项 · 3项变更"]], [["已发布", "good"], ["审核中", "warn"], ["修订中", "info"], ["变更风险", "risk"]], ["何工", "顾工", "唐工"], "结构已关联工艺路线、替代料和工程变更证据。"),
  metrics: [{ label: "有效BOM", value: "128", note: "生产引用 76 个", tone: "good" }, { label: "修订中", value: "7", note: "3 个等待工程确认", tone: "warn" }, { label: "待发布", value: "4", note: "影响 11 张未下达工单", tone: "risk" }, { label: "结构完整率", value: "97.2%", note: "缺项集中在辅料", tone: "good" }],
  context: { kicker: "受控产品结构", title: "BOM版本控制", summary: "围绕引用范围、版本状态与生效窗口管理产品结构，避免现场使用未受控结构。", items: [{ label: "生产有效", value: "76", note: "覆盖 3 个制造基地", progress: 94, tone: "good" }, { label: "报价专用", value: "18", note: "不参与生产领料", progress: 72, tone: "info" }, { label: "变更待评估", value: "5", note: "影响采购与在制工单", progress: 38, tone: "risk" }, { label: "缺少关键属性", value: "3", note: "单位或损耗率待补齐", progress: 24, tone: "warn" }] },
  formFields: fields([{ name: "parent", label: "父项物料", type: "text", required: true }, { name: "plantUse", label: "工厂 / 用途", type: "text", required: true }, { name: "version", label: "版本 / 生效", type: "text", required: true, placeholder: "例如 V3.3 · 09-01生效" }, { name: "items", label: "行项目概况", type: "text", required: true, placeholder: "例如 68项 · 2替代料" }]),
  cellFields: ["parent", "plantUse", "version", "items"],
  attentionTitle: "结构与版本风险", attentionItems: [{ title: "BOM-004 变更影响 6 张采购订单", detail: "替换的关键器件已形成在途数量，需要工程、采购共同评估切换点。", owner: "何工", tone: "risk" }, { title: "3 个修订版本缺少生效范围", detail: "未明确工厂和用途，暂不能进入发布审批。", owner: "顾工", tone: "warn" }],
  workflow: [{ label: "创建结构", detail: "选择父项并维护组件、数量和损耗" }, { label: "工程校验", detail: "检查替代料、工艺与文档引用" }, { label: "审核发布", detail: "锁定版本、生效日期和适用范围" }, { label: "变更与追溯", detail: "通过工程变更形成新受控版本" }],
};

const mrpRuns: Specialization = {
  description: "按工厂、计划范围和时间围栏发起净需求运算，保留需求、库存、在途、提前期、异常与建议单据的完整快照。",
  recordNoun: "MRP运算", primaryAction: "发起MRP运算", icon: "calculate", views: ["全部运算", "运行中", "已完成", "有异常"],
  columns: ["运算编号", "工厂 / 范围", "计划期间", "耗时 / 异常", "完成时间"],
  rows: makeRows("MRP-260814", [["华东工厂 · 全量", "华东工厂 · 净变化", "华南工厂 · 全量", "全工厂 · 关键物料"], ["08-14 至 09-30", "08-14 至 10-31", "08-18 至 11-30", "08-14 至 08-31"], ["04:36 · 3项异常", "01:18 · 无异常", "运行 62%", "02:42 · 1项异常"], ["今天 09:36", "今天 07:18", "预计 11:20", "昨天 18:42"]], [["已完成", "good"], ["有异常", "risk"], ["运行中", "info"], ["待确认", "warn"]], ["系统", "林浩", "宋可"], "运算快照已冻结需求、库存、在途与计划参数。"),
  metrics: [{ label: "今日运算", value: "8 次", note: "7 次成功完成", tone: "good" }, { label: "供需建议", value: "126 条", note: "采购 82 · 生产 44", tone: "info" }, { label: "例外消息", value: "9 条", note: "3 条影响客户交期", tone: "risk" }, { label: "平均耗时", value: "3分24秒", note: "较上周缩短 12%", tone: "good" }],
  context: { kicker: "物料需求计划", title: "本次运算供需结构", summary: "把净需求结果按紧迫性和来源拆解，优先处理会穿透客户承诺的供需例外。", items: [{ label: "建议采购", value: "82 条", note: "其中 14 条需要加急", progress: 76, tone: "warn" }, { label: "建议生产", value: "44 条", note: "6 条受产能约束", progress: 61, tone: "info" }, { label: "建议取消 / 延后", value: "11 条", note: "避免过量库存", progress: 34, tone: "good" }, { label: "交期例外", value: "3 条", note: "关联 2 张销售订单", progress: 22, tone: "risk" }] },
  formFields: fields([{ name: "scope", label: "工厂 / 运算范围", type: "text", required: true }, { name: "period", label: "计划期间", type: "text", required: true }, { name: "parameters", label: "运算参数", type: "select", required: true, options: ["全量运算 · 标准围栏", "净变化运算", "关键物料模拟"] }, { name: "schedule", label: "计划执行时间", type: "text", required: true, placeholder: "立即执行或计划时间" }]),
  cellFields: ["scope", "period", "parameters", "schedule"],
  attentionTitle: "计划例外", attentionItems: [{ title: "轴承 BR-6204 将造成两张订单短缺", detail: "现存与在途合计仍缺 280 件，最早缺料日期为 08-20。", owner: "林浩", tone: "risk" }, { title: "11 条建议单据等待计划员确认", detail: "其中 4 条已接近采购提前期边界。", owner: "宋可", tone: "warn" }],
  workflow: [{ label: "冻结运算快照", detail: "记录需求、库存、在途与参数版本" }, { label: "执行净需求运算", detail: "逐级展开BOM并计算供需缺口" }, { label: "处理例外消息", detail: "确认加急、延后、取消与替代方案" }, { label: "转为业务单据", detail: "生成采购申请或生产订单建议" }],
};

const operationTasks: Specialization = {
  description: "面向车间管理工单工序任务、工作中心、设备、人员、计划数量、完工与不良数量，以及实际开工完工时间。",
  recordNoun: "工序任务", primaryAction: "创建工序任务", icon: "precision_manufacturing", views: ["待开工", "执行中", "暂停 / 异常", "已完工"],
  columns: ["任务编号", "工单 / 工序", "工作中心 / 设备", "计划 / 完工 / 不良", "计划 / 实际时间"],
  rows: makeRows("OP-260814", [["MO-260814-036 · 30 装配", "MO-260814-041 · 20 精加工", "MO-260814-052 · 40 电气接线", "MO-260813-028 · 50 整机测试"], ["总装一车间 · ASM-12", "机加车间 · CNC-07", "电子车间 · ELE-03", "测试区 · TEST-04"], ["24 / 18 / 0", "120 / 76 / 2", "12 / 0 / 0", "20 / 20 / 1"], ["08:00–16:00 · 08:12开工", "10:00–18:00 · 暂停46分", "14:00–20:00 · 待开工", "昨日 · 17:36完工"]], [["执行中", "info"], ["设备异常", "risk"], ["待开工", "warn"], ["已完工", "good"]], ["王峻", "陈磊", "刘鹏"], "任务已绑定工艺版本、物料齐套状态、作业指导书和报工证据。"),
  metrics: [{ label: "今日任务", value: "48", note: "已开工 31", tone: "info" }, { label: "计划完成率", value: "93.4%", note: "较昨日 +1.8%", tone: "good" }, { label: "暂停 / 异常", value: "4", note: "设备 1 · 质量 2 · 缺料 1", tone: "risk" }, { label: "一次合格率", value: "97.6%", note: "不良 7 件", tone: "good" }],
  context: { kicker: "车间生产执行", title: "车间实时执行", summary: "按工作中心观察节拍、在制和异常，使派工、开工、报工、检验与完工在同一任务上闭环。", items: [{ label: "机加车间", value: "84%", note: "CNC-07 异常停机", progress: 84, tone: "risk" }, { label: "总装一车间", value: "76%", note: "节拍稳定", progress: 76, tone: "good" }, { label: "总装二车间", value: "68%", note: "等待齐套 3 项", progress: 68, tone: "warn" }, { label: "电子车间", value: "91%", note: "今日计划即将完成", progress: 91, tone: "good" }] },
  formFields: fields([{ name: "operation", label: "工单 / 工序", type: "text", required: true }, { name: "resource", label: "工作中心 / 设备", type: "text", required: true }, { name: "quantity", label: "计划 / 完工 / 不良", type: "text", required: true }, { name: "time", label: "计划 / 实际时间", type: "text", required: true }]),
  cellFields: ["operation", "resource", "quantity", "time"],
  attentionTitle: "现场异常", attentionItems: [{ title: "CNC-07 已停机 46 分钟", detail: "OP-260814-002 暂停，预计影响后续装配 2.5 小时。", owner: "陈磊", tone: "risk" }, { title: "三项任务尚未完成上岗校验", detail: "当前操作人员缺少对应工序资质确认。", owner: "王峻", tone: "warn" }],
  workflow: [{ label: "派工与校验", detail: "确认人员、设备、物料与指导书" }, { label: "开工与采集", detail: "记录实际开工和设备运行事实" }, { label: "报工与检验", detail: "上报完成、不良和质量结果" }, { label: "完工与转序", detail: "形成转序或完工入库凭证" }],
};

const inventoryOnHand: Specialization = {
  description: "按物料、质量状态、仓库库位、批次或序列号展示现存、占用和可用数量，作为计划与仓储作业的库存事实。",
  recordNoun: "库存记录", primaryAction: "库存调整", icon: "inventory_2", views: ["全部库存", "可用库存", "受限库存", "零 / 负库存"],
  columns: ["库存键", "物料 / 质量状态", "仓库 / 库位", "批次 / 序列号", "现存 / 占用 / 可用"],
  rows: makeRows("STK", [["BR-6204 轴承 · 合格", "AL-6061 型材 · 待检", "GS-800 控制柜 · 合格", "DRV-02 驱动器 · 冻结"], ["原材料仓 · A-01-03", "待检区 · IQC-06", "成品仓 · F-02-08", "线边仓 · L-12"], ["LOT-260731-08", "LOT-260812-03", "SN GS8-2608-018", "LOT-260701-12"], ["420 / 80 / 340件", "1,200 / 1,200 / 0kg", "18 / 12 / 6台", "36 / 0 / 0件"]], [["可用", "good"], ["待检", "warn"], ["已占用", "info"], ["冻结", "risk"]], ["徐峰", "吴倩", "方敏"], "库存数量已关联最近事务、占用来源、质量状态和盘点证据。"),
  metrics: [{ label: "库存金额", value: "¥1,286万", note: "较上月 +2.4%", tone: "info" }, { label: "可用库存", value: "78.6%", note: "占现存总量", tone: "good" }, { label: "受限 / 冻结", value: "23 批", note: "质量原因 18 批", tone: "warn" }, { label: "负库存", value: "2 项", note: "需要立即核对事务", tone: "risk" }],
  context: { kicker: "库存业务事实", title: "库存状态结构", summary: "区分现存、占用、待检与冻结事实，避免把物理数量误当成可承诺数量。", items: [{ label: "合格可用", value: "78.6%", note: "可参与计划与承诺", progress: 79, tone: "good" }, { label: "订单占用", value: "12.4%", note: "关联 31 张需求单", progress: 54, tone: "info" }, { label: "待检库存", value: "6.8%", note: "最早等待 9.5 小时", progress: 38, tone: "warn" }, { label: "冻结库存", value: "2.2%", note: "18 批等待质量处置", progress: 22, tone: "risk" }] },
  formFields: fields([{ name: "material", label: "物料 / 质量状态", type: "text", required: true }, { name: "location", label: "仓库 / 库位", type: "text", required: true }, { name: "lot", label: "批次 / 序列号", type: "text", required: true }, { name: "stock", label: "现存 / 占用 / 可用", type: "text", required: true }]),
  cellFields: ["material", "location", "lot", "stock"],
  attentionTitle: "库存事实异常", attentionItems: [{ title: "两项物料出现负可用量", detail: "领料事务早于入库过账，需要核对时间顺序与库位。", owner: "徐峰", tone: "risk" }, { title: "IQC-06 容量接近预警线", detail: "6 托来料等待检验，预计今日 16:00 后到货 4 托。", owner: "吴倩", tone: "warn" }],
  workflow: [{ label: "形成库存事实", detail: "收货、入库或生产完工增加现存" }, { label: "占用与释放", detail: "订单、备料和发货任务形成占用" }, { label: "移动与状态转换", detail: "调拨、检验、冻结均保留事务" }, { label: "盘点与调整", detail: "差异审批后形成可追溯调整" }],
};

const nonconformanceReviews: Specialization = {
  description: "对来料、过程、终检和客诉不合格进行跨部门评审，确定隔离、返工、让步、退货或报废处置及完成期限。",
  recordNoun: "不合格评审", primaryAction: "发起不合格评审", icon: "gavel", views: ["待评审", "评审中", "待执行", "已关闭"],
  columns: ["不合格编号", "来源 / 对象", "缺陷 / 严重度", "数量 / 隔离", "处置 / 期限"],
  rows: makeRows("NCR-260814", [["过程检验 · GS-800门板", "来料检验 · BR-6204", "完工检验 · QC-20", "客户投诉 · PM-45"], ["尺寸超差 · 主要", "异响 · 严重", "标签错误 · 次要", "表面划伤 · 主要"], ["2件 · 已隔离", "32件 · 待隔离", "6套 · 已冻结", "1套 · 已追回"], ["返工 · 08-15", "退货 · 08-16", "让步接收 · 待审批", "8D分析 · 08-20"]], [["待评审", "warn"], ["重大风险", "risk"], ["待执行", "info"], ["已关闭", "good"]], ["许雯", "王敏", "陈琪"], "不合格事实已关联检验结果、批次、隔离位置与责任部门。"),
  metrics: [{ label: "待评审", value: "7 项", note: "2 项超过 8 小时", tone: "warn" }, { label: "隔离数量", value: "86 件", note: "分布在 4 个库位", tone: "info" }, { label: "重大不合格", value: "2 项", note: "均已冻结关联批次", tone: "risk" }, { label: "本月按期关闭", value: "91.8%", note: "较上月 +3.1%", tone: "good" }],
  context: { kicker: "不合格评审", title: "不合格处置结构", summary: "先锁定不合格事实和影响范围，再由质量、工程、生产与供应链共同决定处置。", items: [{ label: "等待评审", value: "7", note: "重大 2 · 主要 4 · 次要 1", progress: 44, tone: "warn" }, { label: "返工 / 返修", value: "11", note: "3 项等待工艺方案", progress: 63, tone: "info" }, { label: "退货 / 报废", value: "4", note: "预计损失 ¥3.6万", progress: 28, tone: "risk" }, { label: "让步接收", value: "3", note: "均已获得技术批准", progress: 72, tone: "good" }] },
  formFields: fields([{ name: "source", label: "来源 / 对象", type: "text", required: true }, { name: "defect", label: "缺陷 / 严重度", type: "text", required: true }, { name: "isolation", label: "数量 / 隔离", type: "text", required: true }, { name: "disposition", label: "处置 / 期限", type: "text", required: true }]),
  cellFields: ["source", "defect", "isolation", "disposition"],
  attentionTitle: "评审与处置风险", attentionItems: [{ title: "BR-6204 异响可能影响三张工单", detail: "同批次仍有 280 件在线边仓，需要扩大隔离范围。", owner: "许雯", tone: "risk" }, { title: "两项返工处置尚未发布工艺", detail: "生产已预留返工时段，等待工程确认作业指导。", owner: "王敏", tone: "warn" }],
  workflow: [{ label: "登记与隔离", detail: "记录缺陷事实并冻结影响对象" }, { label: "跨部门评审", detail: "确认影响范围、责任和处置方式" }, { label: "执行与验证", detail: "完成返工、退货、报废或让步" }, { label: "关闭与纠正", detail: "验证效果并按需发起CAPA" }],
};

const equipmentTelemetry: Specialization = {
  description: "定义设备、网关、协议、点位、数据质量和告警的前端契约，为后续 MQTT / OPC UA 与时序数据平台接入预留边界。",
  recordNoun: "设备接入", primaryAction: "配置设备接入", icon: "sensors", planned: true, views: ["设备连接", "网关", "采集点位", "告警"],
  columns: ["设备编号", "设备 / 网关", "协议 / 在线", "最新数据 / 质量", "告警 / 点位"],
  rows: makeRows("IOT", [["CNC-07 · GW-EAST-01", "ASM-12 · GW-EAST-02", "TEST-04 · GW-QA-01", "AIR-01 · GW-UTILITY-01"], ["OPC UA · 在线", "Modbus TCP · 在线", "MQTT · 离线", "Modbus RTU · 在线"], ["主轴负载 68% · 良好", "节拍 52秒 · 良好", "最后数据 32分钟前 · 中断", "压力 0.68MPa · 可疑"], ["1条 · 26点", "无告警 · 12点", "通信中断 · 18点", "越限预警 · 9点"]], [["在线", "good"], ["数据延迟", "warn"], ["离线", "risk"], ["调试中", "info"]], ["赵凯", "陈磊", "平台管理员"], "当前仅验证前端模型与异步契约，未声称已建立真实设备连接。"),
  metrics: [{ label: "已建模设备", value: "46 台", note: "正式接入需后端确认", tone: "info" }, { label: "在线模型", value: "42 台", note: "前端模拟在线率 91.3%", tone: "good" }, { label: "采集点位", value: "684", note: "关键点位 126 个", tone: "info" }, { label: "数据异常", value: "5 项", note: "中断 2 · 可疑 3", tone: "risk" }],
  context: { kicker: "工业连接模型", title: "设备数据接入规划", summary: "产品层只定义设备、连接、点位、质量与告警语义；真实采集由边缘网关、消息平台与时序存储实现。", items: [{ label: "设备模型", value: "46", note: "编码与资产台账一致", progress: 92, tone: "good" }, { label: "连接配置", value: "4类", note: "OPC UA、Modbus、MQTT", progress: 68, tone: "info" }, { label: "点位映射", value: "684", note: "126 个关键生产点", progress: 76, tone: "warn" }, { label: "告警规则", value: "38", note: "仍需后端事件引擎", progress: 42, tone: "risk" }] },
  formFields: fields([{ name: "device", label: "设备 / 网关", type: "text", required: true }, { name: "connection", label: "协议 / 在线策略", type: "select", required: true, options: ["OPC UA", "Modbus TCP", "Modbus RTU", "MQTT"] }, { name: "data", label: "关键数据 / 质量规则", type: "text", required: true }, { name: "points", label: "告警 / 点位概况", type: "text", required: true }]),
  cellFields: ["device", "connection", "data", "points"],
  attentionTitle: "接入规划风险", attentionItems: [{ title: "TEST-04 的连接模型缺少断线补传策略", detail: "正式接入前需确认边缘缓存容量与数据重放顺序。", owner: "平台管理员", tone: "risk" }, { title: "18 个点位尚未定义工程单位", detail: "量纲缺失会影响趋势分析与越限告警。", owner: "赵凯", tone: "warn" }],
  workflow: [{ label: "设备与网关建模", detail: "保持设备编码与资产台账一致" }, { label: "协议与点位配置", detail: "定义地址、单位、频率和质量规则" }, { label: "联调与数据验证", detail: "验证断线、补传、时钟与幂等" }, { label: "告警与业务联动", detail: "将事件关联设备、工单和责任人" }],
};

const orderProfit: Specialization = {
  description: "按销售订单归集收入、材料、人工、制造费用和交付费用，展示毛利额、毛利率、核算完整度与差异来源。",
  recordNoun: "订单利润", primaryAction: "重新测算利润", icon: "finance", views: ["全部订单", "已核算", "待补成本", "低毛利"],
  columns: ["订单号", "客户 / 产品", "收入 / 成本", "毛利额 / 毛利率", "核算状态 / 时间"],
  rows: makeRows("SO-2608", [["恒锐自动化 · GS-800", "创驰装备 · PM-45", "东岳电气 · QC-20", "明川机器人 · SR-12"], ["¥864,000 / ¥649,728", "¥386,400 / ¥315,432", "¥728,000 / ¥517,152", "¥198,600 / ¥172,782"], ["¥214,272 / 24.8%", "¥70,968 / 18.4%", "¥210,848 / 29.0%", "¥25,818 / 13.0%"], ["已核算 · 今天09:30", "待补外协成本", "已核算 · 昨天18:20", "低毛利预警"]], [["已确认", "good"], ["待补成本", "warn"], ["有差异", "risk"], ["测算中", "info"]], ["孙琳", "魏铭"], "利润已穿透材料领用、报工工时、费用分摊、发货和开票证据。"),
  metrics: [{ label: "本月确认收入", value: "¥628.4万", note: "已交付订单口径", tone: "good" }, { label: "订单综合毛利率", value: "23.6%", note: "较预算 -1.2%", tone: "warn" }, { label: "待补成本", value: "5 单", note: "主要为外协与物流", tone: "warn" }, { label: "低毛利订单", value: "3 单", note: "低于 15% 预警线", tone: "risk" }],
  context: { kicker: "订单盈利能力", title: "订单利润构成", summary: "从收入到材料、人工、制造费用和履约费用解释利润，而不是用应收账龄替代经营结果。", items: [{ label: "材料成本", value: "56.8%", note: "价格差异 +¥2.8万", progress: 57, tone: "warn" }, { label: "直接人工", value: "8.6%", note: "工时完整率 97%", progress: 36, tone: "good" }, { label: "制造费用", value: "7.4%", note: "按机器工时分摊", progress: 31, tone: "info" }, { label: "销售毛利", value: "23.6%", note: "目标值 24.8%", progress: 78, tone: "risk" }] },
  formFields: fields([{ name: "subject", label: "客户 / 产品", type: "text", required: true }, { name: "revenueCost", label: "收入 / 成本", type: "text", required: true }, { name: "profit", label: "毛利额 / 毛利率", type: "text", required: true }, { name: "calculation", label: "核算状态 / 时间", type: "text", required: true }]),
  cellFields: ["subject", "revenueCost", "profit", "calculation"],
  attentionTitle: "利润差异", attentionItems: [{ title: "SO-2608-004 毛利率低于预警线", detail: "采购价格上涨与返工工时共同造成成本超预算 ¥18,600。", owner: "孙琳", tone: "risk" }, { title: "五张订单尚未归集外协费用", detail: "当前利润为暂估口径，费用入账后需要重新测算。", owner: "魏铭", tone: "warn" }],
  workflow: [{ label: "确认收入范围", detail: "按发货、签收或结算政策确认收入" }, { label: "归集实际成本", detail: "汇总材料、人工、制造与履约费用" }, { label: "分摊与差异分析", detail: "解释标准、实际与预算差异" }, { label: "确认与经营复盘", detail: "锁定口径并下钻业务证据" }],
};

const roles: Specialization = {
  description: "以角色为权限载体，分离功能权限、数据范围和审批授权，明确成员、组织边界、敏感操作与最近变更记录。",
  recordNoun: "角色", primaryAction: "新建角色", icon: "admin_panel_settings", views: ["全部角色", "业务角色", "平台角色", "待复核"],
  columns: ["角色编码", "角色名称", "成员", "功能 / 数据范围", "复核策略"],
  rows: makeRows("ROLE", [["生产计划员", "采购主管", "质量工程师", "仓库操作员", "系统管理员"], ["8 人", "3 人", "6 人", "12 人", "2 人"], ["计划与MRP · 华东工厂", "采购全流程 · 全公司", "质量处置 · 华东/华南", "库存作业 · 指定仓库", "平台配置 · 全公司"], ["每季度复核", "每半年复核", "变更时复核", "每季度复核", "敏感操作双人复核"]], [["已启用", "good"], ["待复核", "warn"], ["权限变更", "info"], ["敏感角色", "risk"]], ["蒋宁", "系统管理员"], "角色已分别记录菜单、操作、字段、数据范围和审批授权。"),
  metrics: [{ label: "有效角色", value: "28", note: "业务角色 23 个", tone: "good" }, { label: "已授权用户", value: "126", note: "覆盖率 98.4%", tone: "good" }, { label: "待复核角色", value: "4", note: "季度权限复核", tone: "warn" }, { label: "敏感权限", value: "7 项", note: "分布在 3 个角色", tone: "risk" }],
  context: { kicker: "基于角色的访问控制", title: "权限覆盖与风险", summary: "角色只承载完成工作所需的最小权限，功能范围和数据范围分别配置并完整审计。", items: [{ label: "业务操作角色", value: "18", note: "面向日常业务处理", progress: 82, tone: "good" }, { label: "审批角色", value: "5", note: "按组织与金额分级", progress: 64, tone: "info" }, { label: "平台管理角色", value: "5", note: "需要双人复核", progress: 42, tone: "warn" }, { label: "越权风险", value: "2", note: "数据范围重叠待处理", progress: 18, tone: "risk" }] },
  formFields: fields([{ name: "roleName", label: "角色名称", type: "text", required: true }, { name: "members", label: "初始成员", type: "text", required: true, placeholder: "例如 林浩、宋可（共 2 人）" }, { name: "scope", label: "功能 / 数据范围", type: "select", required: true, options: ["计划与MRP · 华东工厂", "采购全流程 · 全公司", "质量处置 · 华东/华南", "库存作业 · 指定仓库", "平台配置 · 全公司"] }, { name: "review", label: "权限复核策略", type: "select", required: true, options: ["每季度复核", "每半年复核", "变更时复核", "敏感操作双人复核"] }]),
  cellFields: ["roleName", "members", "scope", "review"],
  attentionTitle: "权限治理风险", attentionItems: [{ title: "系统管理员角色包含两项过度授权", detail: "数据导出与接口密钥权限应拆分至独立安全管理员。", owner: "蒋宁", tone: "risk" }, { title: "四个角色到达季度复核日期", detail: "需由角色所有者确认成员、功能权限和数据范围。", owner: "系统管理员", tone: "warn" }],
  workflow: [{ label: "定义角色职责", detail: "从工作任务确定最小权限集合" }, { label: "配置功能与数据", detail: "分别配置操作能力和组织数据范围" }, { label: "审批与授权", detail: "敏感角色经过双人复核后分配" }, { label: "审计与定期复核", detail: "保留变更记录并回收不再需要的权限" }],
};

function aiSettingsPage(config: {
  prefix: string;
  title: string;
  description: string;
  recordNoun: string;
  action: string;
  icon: string;
  columns: string[];
  samples: RowSource;
  views: string[];
  metrics: Specialization["metrics"];
  contextTitle: string;
  contextSummary: string;
  contextItems: NonNullable<Specialization["context"]>["items"];
  attentionTitle: string;
  attentionItems: NonNullable<Specialization["attentionItems"]>;
  workflow: NonNullable<Specialization["workflow"]>;
  fields: BusinessFormField[];
}): Specialization {
  return {
    definitionId: `ai:${config.prefix.toLowerCase()}`,
    title: config.title,
    description: config.description,
    recordNoun: config.recordNoun,
    primaryAction: config.action,
    icon: config.icon,
    views: config.views,
    columns: config.columns,
    rows: makeRows(config.prefix, config.samples, [["已启用", "good"], ["待评审", "warn"], ["受控", "info"], ["已停用", "risk"]], ["AI 管理员", "业务负责人", "数据治理组"], "该配置只作用于前端产品原型，真实模型、知识库与权限服务接入前仍需完成安全评审。"),
    metrics: config.metrics,
    context: { kicker: "AI 治理与制造场景", title: config.contextTitle, summary: config.contextSummary, items: config.contextItems },
    formFields: fields(config.fields),
    cellFields: ["name", "scope", "policy", "review"],
    attentionTitle: config.attentionTitle,
    attentionItems: config.attentionItems,
    workflow: config.workflow,
  };
}

const aiOverview = aiSettingsPage({
  prefix: "AI-GOV", title: "AI 管理中心", description: "统一管理制造助手、知识来源、业务技能、权限边界与使用审计，让 AI 有据可查、可控可停，并始终由人完成关键业务确认。", recordNoun: "AI治理项", action: "新建治理策略", icon: "auto_awesome",
  columns: ["治理编号", "能力 / 场景", "适用范围", "控制策略", "最近复核"], views: ["治理总览", "待评审", "风险项", "最近变更"],
  samples: [["制造运营助手", "订单风险解读", "设备异常诊断", "质量问题归因"], ["全公司 · 只读", "销售与计划 · 华东", "设备管理 · 指定工厂", "质量管理 · 全公司"], ["引用必显 · 禁止自动执行", "订单下达需人工确认", "设备控制完全禁用", "质量判定仅提供建议"], ["今天 10:20", "昨天 16:40", "08-12 09:30", "08-10 14:18"]],
  metrics: [{ label: "受控助手", value: "3 个", note: "全部绑定业务范围", tone: "good" }, { label: "已登记技能", value: "12 项", note: "8 项已通过评审", tone: "info" }, { label: "待复核策略", value: "2 项", note: "涉及导出与敏感成本", tone: "warn" }, { label: "越权事件", value: "0", note: "最近 30 天", tone: "good" }],
  contextTitle: "AI 控制面", contextSummary: "从身份、知识、技能、动作和审计五个层面约束 AI，避免助手绕过业务权限或把建议伪装成已执行事实。", contextItems: [{ label: "身份与角色", value: "100%", note: "继承当前用户权限", progress: 100, tone: "good" }, { label: "知识可见范围", value: "8 个域", note: "按组织与密级过滤", progress: 82, tone: "info" }, { label: "关键动作确认", value: "全部", note: "下达、过账、判定均需人工", progress: 100, tone: "good" }, { label: "审计留痕", value: "30 天", note: "问答、引用与动作建议", progress: 76, tone: "warn" }],
  attentionTitle: "AI 治理待办", attentionItems: [{ title: "成本分析技能需要补充字段脱敏规则", detail: "客户价格和订单毛利属于敏感经营数据，应按角色控制引用与导出。", owner: "数据治理组", tone: "risk" }, { title: "两项技能将在本周到达复核日期", detail: "需重新确认知识范围、输出模板和人工确认节点。", owner: "AI 管理员", tone: "warn" }],
  workflow: [{ label: "登记业务场景", detail: "说明目标、使用者和禁止事项" }, { label: "绑定知识与权限", detail: "继承用户身份并限制数据范围" }, { label: "评测与发布", detail: "通过事实性、安全性与权限评测" }, { label: "监控与复核", detail: "审计使用记录并定期复核策略" }],
  fields: [{ name: "name", label: "能力 / 场景", type: "text", required: true }, { name: "scope", label: "适用范围", type: "select", required: true, options: ["全公司 · 只读", "指定业务域", "指定工厂", "指定角色"] }, { name: "policy", label: "控制策略", type: "select", required: true, options: ["仅建议", "允许生成草稿", "关键动作人工确认", "完全停用"] }, { name: "review", label: "复核周期", type: "select", options: ["每月", "每季度", "变更时复核"] }],
});

const aiAssistants = aiSettingsPage({
  prefix: "AI-AST", title: "助手配置", description: "配置助手身份、默认语言、响应边界、引用要求与业务作用域；当前仅保存前端原型配置，不连接真实模型供应商。", recordNoun: "助手", action: "新建助手", icon: "smart_toy",
  columns: ["助手编号", "助手名称", "业务范围", "响应策略", "复核策略"], views: ["全部助手", "已启用", "待评审", "已停用"],
  samples: [["制造运营助手", "计划协同助手", "质量改进助手", "设备运维助手"], ["全业务只读", "计划与供应链", "质量与追溯", "设备与维修"], ["必须引用来源 · 不执行动作", "生成排程建议 · 人工下达", "建议原因与措施 · 人工判定", "诊断线索 · 禁止设备控制"], ["每月复核", "变更时复核", "每季度复核", "每月复核"]],
  metrics: [{ label: "助手总数", value: "4", note: "3 个已启用", tone: "info" }, { label: "绑定业务域", value: "8", note: "覆盖主业务闭环", tone: "good" }, { label: "引用要求", value: "100%", note: "回答必须展示来源", tone: "good" }, { label: "待评审", value: "1", note: "设备运维助手", tone: "warn" }],
  contextTitle: "助手响应边界", contextSummary: "每个助手拥有清晰身份、可见数据、可用技能和禁止动作，避免一个万能助手跨越所有业务与权限边界。", contextItems: [{ label: "只读问答", value: "4/4", note: "默认能力", progress: 100, tone: "good" }, { label: "草稿生成", value: "3/4", note: "不直接提交", progress: 75, tone: "info" }, { label: "动作建议", value: "2/4", note: "必须人工确认", progress: 50, tone: "warn" }, { label: "设备控制", value: "0", note: "明确禁止", progress: 0, tone: "good" }],
  attentionTitle: "助手配置风险", attentionItems: [{ title: "设备运维助手尚未完成安全评测", detail: "需要验证其不会生成设备控制指令或绕过维修审批。", owner: "AI 管理员", tone: "risk" }, { title: "计划协同助手的引用模板待统一", detail: "排程建议必须同时展示需求、库存、产能和时间围栏来源。", owner: "业务负责人", tone: "warn" }],
  workflow: [{ label: "定义助手身份", detail: "确定服务对象、语气和业务目标" }, { label: "限制知识与技能", detail: "绑定允许的数据域和可调用能力" }, { label: "评测输出边界", detail: "验证引用、拒答和敏感信息处理" }, { label: "发布与复核", detail: "灰度启用并持续检查使用结果" }],
  fields: [{ name: "name", label: "助手名称", type: "text", required: true }, { name: "scope", label: "业务范围", type: "select", required: true, options: ["全业务只读", "计划与供应链", "质量与追溯", "设备与维修"] }, { name: "policy", label: "响应策略", type: "select", required: true, options: ["必须引用来源", "允许生成草稿", "只提供诊断建议", "停用"] }, { name: "review", label: "复核策略", type: "select", options: ["每月复核", "每季度复核", "变更时复核"] }],
});

const aiKnowledge = aiSettingsPage({
  prefix: "AI-KB", title: "知识范围", description: "管理助手可检索的业务页面、受控文档、主数据与历史记录范围，并明确组织、密级、有效期和引用要求。", recordNoun: "知识源", action: "登记知识源", icon: "library_books",
  columns: ["知识编号", "知识来源", "数据范围", "访问策略", "同步 / 复核"], views: ["全部知识源", "已授权", "待同步", "受限内容"],
  samples: [["受控作业指导书", "质量标准与缺陷库", "订单与交付事实", "设备维修知识库"], ["华东工厂 · 已发布版本", "全公司 · QMS", "本人可见订单", "指定设备与工厂"], ["继承文档权限 · 必须引用", "质量角色可见", "行级权限过滤", "设备角色可见"], ["今天 08:00 · 正常", "昨天 22:00 · 正常", "实时查询 · 不缓存", "08-13 · 2项待复核"]],
  metrics: [{ label: "知识源", value: "18", note: "受控文档 9 个", tone: "info" }, { label: "权限继承", value: "100%", note: "不扩大用户可见范围", tone: "good" }, { label: "待同步", value: "2", note: "版本发布后待更新", tone: "warn" }, { label: "失效引用", value: "0", note: "最近 7 天", tone: "good" }],
  contextTitle: "可检索知识边界", contextSummary: "助手只能检索当前用户本来就有权查看的事实，并在回答中返回来源、版本和更新时间。", contextItems: [{ label: "受控文档", value: "9 源", note: "只引用已发布版本", progress: 90, tone: "good" }, { label: "业务事实", value: "6 域", note: "按行级权限过滤", progress: 72, tone: "info" }, { label: "主数据", value: "3 类", note: "物料、设备、组织", progress: 64, tone: "info" }, { label: "敏感字段", value: "12 项", note: "已配置脱敏或拒答", progress: 82, tone: "warn" }],
  attentionTitle: "知识治理风险", attentionItems: [{ title: "两份作业指导书已发布新版本", detail: "需要确认旧版本从检索范围移除并重新生成引用索引。", owner: "数据治理组", tone: "warn" }, { title: "订单利润字段缺少统一脱敏策略", detail: "不同角色的价格、成本与毛利可见范围尚未完成评审。", owner: "业务负责人", tone: "risk" }],
  workflow: [{ label: "登记知识来源", detail: "说明系统、对象、版本与责任人" }, { label: "绑定访问策略", detail: "继承组织、角色和行级权限" }, { label: "同步与质量检查", detail: "验证完整性、时效性与引用定位" }, { label: "使用与失效", detail: "记录引用并及时移除失效版本" }],
  fields: [{ name: "name", label: "知识来源", type: "text", required: true }, { name: "scope", label: "数据范围", type: "select", required: true, options: ["全公司公开", "指定工厂", "指定角色", "本人业务数据"] }, { name: "policy", label: "访问策略", type: "select", required: true, options: ["继承源权限", "只读且必须引用", "敏感字段脱敏", "禁止检索"] }, { name: "review", label: "同步 / 复核", type: "select", options: ["实时查询", "每日同步", "发布时同步", "每月复核"] }],
});

const aiSkills = aiSettingsPage({
  prefix: "AI-SKL", title: "技能与场景", description: "登记 AI 能在具体制造场景中执行的只读分析或草稿生成能力，明确输入、输出、依据、人工确认点和失败降级。", recordNoun: "AI技能", action: "新建技能", icon: "neurology",
  columns: ["技能编号", "技能 / 场景", "输入范围", "输出与动作", "评测状态"], views: ["全部技能", "已发布", "评测中", "高风险"],
  samples: [["订单交期风险解读", "MRP例外归因", "质量相似问题检索", "设备停机诊断建议"], ["订单·计划·库存", "需求·库存·在途·参数", "缺陷·批次·8D记录", "告警·维修·点检记录"], ["解释与建议 · 无自动动作", "生成处理草稿 · 人工下达", "相似案例与措施建议", "诊断线索 · 禁止设备控制"], ["已通过 42例", "评测中 28/40", "已通过 36例", "安全评测待完成"]],
  metrics: [{ label: "技能总数", value: "12", note: "8 项已发布", tone: "info" }, { label: "业务域覆盖", value: "7", note: "优先覆盖主闭环", tone: "good" }, { label: "评测通过率", value: "94.6%", note: "事实与权限综合", tone: "good" }, { label: "高风险技能", value: "1", note: "设备诊断待评审", tone: "warn" }],
  contextTitle: "制造场景技能地图", contextSummary: "技能以具体工作任务为单位，输出必须能回到订单、工单、检验、设备或文档事实，而不是生成无法核对的结论。", contextItems: [{ label: "风险解释", value: "4 项", note: "订单、供应、质量、设备", progress: 82, tone: "good" }, { label: "草稿生成", value: "3 项", note: "均需人工提交", progress: 68, tone: "info" }, { label: "知识检索", value: "4 项", note: "必须带来源", progress: 76, tone: "good" }, { label: "高风险动作", value: "0", note: "执行权限未开放", progress: 0, tone: "good" }],
  attentionTitle: "技能发布风险", attentionItems: [{ title: "设备停机诊断技能缺少拒答用例", detail: "需覆盖安全联锁、参数修改和远程控制等禁止场景。", owner: "AI 管理员", tone: "risk" }, { title: "MRP例外归因仍有 3 个低置信样本", detail: "需要补充提前期变更与替代料同时发生的复杂用例。", owner: "业务负责人", tone: "warn" }],
  workflow: [{ label: "定义输入与输出", detail: "绑定业务对象、字段和输出模板" }, { label: "设置人工确认点", detail: "明确建议、草稿和禁止动作" }, { label: "构建评测集", detail: "覆盖正确性、权限、安全与降级" }, { label: "灰度发布与监控", detail: "观察采用率、退回率和问题样本" }],
  fields: [{ name: "name", label: "技能 / 场景", type: "text", required: true }, { name: "scope", label: "输入范围", type: "select", required: true, options: ["订单与交付", "计划与供应", "质量与追溯", "设备与维修"] }, { name: "policy", label: "输出与动作", type: "select", required: true, options: ["只读解释", "生成业务草稿", "提供诊断建议", "禁止发布"] }, { name: "review", label: "评测策略", type: "select", options: ["标准评测集", "高风险安全评测", "业务负责人验收"] }],
});

const aiGovernance = aiSettingsPage({
  prefix: "AI-AUD", title: "权限与审计", description: "按用户、角色、业务域和动作类型控制 AI 能力，记录提问、引用、建议、确认与拒绝结果，支持停用、追溯和定期复核。", recordNoun: "AI审计策略", action: "新建审计策略", icon: "policy",
  columns: ["策略编号", "角色 / 场景", "允许范围", "确认与留痕", "最近审计"], views: ["权限策略", "使用审计", "拒绝记录", "待复核"],
  samples: [["生产计划员 · 排程建议", "质量工程师 · 缺陷分析", "设备管理员 · 故障诊断", "财务负责人 · 成本解读"], ["计划与库存只读", "质量记录与受控文档", "指定工厂设备只读", "本人组织成本数据"], ["下达前人工确认 · 全量留痕", "质量判定禁止 · 引用留痕", "控制指令拒绝 · 全量留痕", "导出需确认 · 敏感字段审计"], ["今天 10:42 · 正常", "今天 09:16 · 1次拒绝", "昨天 17:30 · 正常", "08-12 · 待复核"]],
  metrics: [{ label: "有效策略", value: "16", note: "覆盖 9 个业务角色", tone: "good" }, { label: "本周调用", value: "1,284", note: "引用完整率 99.2%", tone: "info" }, { label: "安全拒绝", value: "18", note: "均符合策略", tone: "good" }, { label: "待复核", value: "2", note: "涉及财务与设备", tone: "warn" }],
  contextTitle: "权限继承与审计链", contextSummary: "AI 不拥有独立的超级权限；每次检索与建议都继承当前用户身份，并记录知识引用、策略判断和人工确认。", contextItems: [{ label: "身份继承", value: "100%", note: "用户、组织、角色", progress: 100, tone: "good" }, { label: "动作确认", value: "7 类", note: "关键动作全部拦截", progress: 100, tone: "good" }, { label: "审计覆盖", value: "100%", note: "问答、引用与建议", progress: 100, tone: "good" }, { label: "策略复核", value: "87%", note: "2项将在本周到期", progress: 87, tone: "warn" }],
  attentionTitle: "权限与审计风险", attentionItems: [{ title: "财务成本解读策略即将到期", detail: "需重新确认订单毛利与客户价格的可见角色和导出条件。", owner: "数据治理组", tone: "warn" }, { title: "设备诊断拒绝规则缺少批量指令测试", detail: "应补充远程启停、参数写入和联锁旁路等明确禁止用例。", owner: "AI 管理员", tone: "risk" }],
  workflow: [{ label: "定义访问策略", detail: "按角色、组织、数据域和动作授权" }, { label: "运行时策略判断", detail: "每次检索与建议继承当前身份" }, { label: "人工确认与执行", detail: "关键动作只生成草稿或建议" }, { label: "审计、复核与停用", detail: "追溯结果并快速回收风险能力" }],
  fields: [{ name: "name", label: "角色 / 场景", type: "text", required: true }, { name: "scope", label: "允许范围", type: "select", required: true, options: ["只读业务数据", "受控文档", "本人组织数据", "禁止访问"] }, { name: "policy", label: "确认与留痕", type: "select", required: true, options: ["全量留痕", "关键动作人工确认", "敏感字段审计", "自动拒绝"] }, { name: "review", label: "复核周期", type: "select", options: ["每月", "每季度", "权限变更时"] }],
});

const specializations: Record<string, Specialization> = {
  "/sales/orders/list": salesOrders,
  "/product/boms/list": bomList,
  "/planning/mrp/runs": mrpRuns,
  "/production/work-orders/operations": operationTasks,
  "/warehouse/inventory/on-hand": inventoryOnHand,
  "/quality/nonconformance/reviews": nonconformanceReviews,
  "/equipment/telemetry": equipmentTelemetry,
  "/finance/order-profit": orderProfit,
  "/settings/roles": roles,
  "/settings/ai": aiOverview,
  "/settings/ai/assistants": aiAssistants,
  "/settings/ai/knowledge": aiKnowledge,
  "/settings/ai/skills": aiSkills,
  "/settings/ai/governance": aiGovernance,
};

export function getBusinessPageSpecialization(pathname: string): Specialization | undefined {
  return specializations[pathname];
}
