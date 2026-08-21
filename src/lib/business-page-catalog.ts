import type {
  BusinessFormField,
  BusinessLayout,
  BusinessPageSpecialization,
  BusinessRow,
  BusinessTone,
} from "./business-page-data";
import type { ResolvedProductRoute } from "./product-navigation";

type CatalogField = BusinessFormField & { name: string };

type CatalogBlueprint = {
  id: string;
  layout: BusinessLayout;
  icon: string;
  prefix: string;
  purpose: string;
  actionVerb: string;
  actionMode?: "form" | "refresh" | "query" | "export" | "feedback";
  fields: [CatalogField, CatalogField, CatalogField, CatalogField];
  samples: [string[], string[], string[], string[]];
  statuses: Array<[string, BusinessTone]>;
  owners: string[];
  metricLabels: [string, string, string, string];
  workflow: Array<[string, string]>;
};

type CatalogSelection = {
  blueprint: keyof typeof blueprints;
  noun?: string;
  action?: string;
  actionMode?: "form" | "refresh" | "query" | "export" | "feedback";
};

const ownerField: BusinessFormField = {
  name: "owner",
  label: "负责人",
  type: "select",
  required: true,
  options: ["林浩", "周洁", "王峻", "许雯", "孙琳", "蒋宁", "赵凯", "徐峰"],
};

const remarkField: BusinessFormField = {
  name: "remark",
  label: "业务说明",
  type: "textarea",
  span: "full",
  placeholder: "补充来源、约束条件、风险和处理说明",
};

const textField = (name: string, label: string, placeholder?: string): CatalogField => ({ name, label, type: "text", required: true, placeholder });
const selectField = (name: string, label: string, options: string[]): CatalogField => ({ name, label, type: "select", required: true, options });

const blueprintContextLabels: Record<string, string> = {
  analytics: "经营指标分析",
  commercial: "业务单据履约",
  costing: "成本核算管理",
  dashboard: "经营态势总览",
  engineering: "产品工程数据",
  equipment: "设备资产运行",
  execution: "生产现场执行",
  finance: "业务财务协同",
  governance: "平台治理配置",
  integration: "外部系统集成",
  inventory: "库存业务事实",
  job: "后台任务执行",
  logistics: "仓储物流执行",
  maintenance: "设备维护闭环",
  "master-data": "受控基础资料",
  notification: "消息提醒管理",
  party: "业务伙伴关系",
  planning: "供需计划协同",
  pricing: "价格与有效期",
  "quality-issue": "质量问题闭环",
  quality: "质量检验控制",
  risk: "经营风险闭环",
  task: "个人业务任务",
  template: "业务模板管理",
  traceability: "全过程追溯",
};

const blueprints = {
  dashboard: {
    id: "dashboard", layout: "analytics", icon: "space_dashboard", prefix: "DB", purpose: "汇总关键指标、责任、风险与待处理任务，为管理者提供可下钻的业务入口。", actionVerb: "刷新", actionMode: "refresh",
    fields: [textField("indicator", "指标 / 主题"), textField("scope", "组织 / 范围"), textField("current", "当前值 / 目标"), textField("updated", "更新时间 / 口径")],
    samples: [["订单交付健康度", "生产计划达成率", "质量一次合格率", "库存周转效率"], ["华东制造中心", "全公司", "总装一车间", "本月经营口径"], ["94.2% / 96.0%", "93.4% / 95.0%", "97.6% / 98.0%", "6.8次 / 7.2次"], ["今天 10:42 · 已校验", "今天 09:30 · 自动刷新", "昨天 18:20 · 已确认", "本月 · 财务口径"]],
    statuses: [["正常", "good"], ["需关注", "warn"], ["有风险", "risk"], ["更新中", "info"]], owners: ["经营办", "林浩", "王峻", "许雯"], metricLabels: ["核心指标", "达到目标", "待处理任务", "风险指标"],
    workflow: [["定义业务口径", "明确指标、组织范围和数据时间"], ["汇总业务事实", "从订单、工单和检验记录形成指标"], ["识别偏差", "按责任与影响程度形成风险"], ["下钻与闭环", "进入业务对象处理并记录结果"]],
  },
  task: {
    id: "task", layout: "work", icon: "task_alt", prefix: "TASK", purpose: "按照来源、优先级和截止时间组织个人与跨部门任务，保留处理结果和协同证据。", actionVerb: "新建",
    fields: [textField("subject", "任务事项"), textField("source", "来源 / 业务对象"), selectField("priority", "优先级", ["普通", "紧急", "关键"]), textField("deadline", "截止时间")],
    samples: [["确认销售订单承诺交期", "审批采购价格调整", "复核不合格品处置", "跟进关键物料短缺"], ["SO-260814-036", "采购价格 PRC-018", "NCR-260814-007", "MRP 例外 EX-021"], ["关键", "紧急", "普通"], ["今天 16:00", "今天 18:00", "明天 10:00", "本周五"]],
    statuses: [["待处理", "warn"], ["处理中", "info"], ["已完成", "good"], ["已逾期", "risk"]], owners: ["林浩", "周洁", "许雯", "王峻"], metricLabels: ["全部任务", "今日到期", "本周完成", "逾期任务"],
    workflow: [["接收任务", "记录来源、要求与完成期限"], ["确认责任", "明确主责人与协同部门"], ["处理留痕", "提交业务结果和必要证据"], ["完成关闭", "验证结果并通知任务发起人"]],
  },
  notification: {
    id: "notification", layout: "work", icon: "notifications", prefix: "MSG", purpose: "集中管理业务提醒、系统消息和异常通知，支持已读、归档、订阅和业务对象下钻。", actionVerb: "新建消息规则",
    fields: [textField("subject", "消息主题"), selectField("channel", "渠道 / 类型", ["站内消息", "邮件", "企业微信", "系统告警"]), textField("audience", "接收对象"), textField("trigger", "触发条件 / 时间")],
    samples: [["订单交期风险提醒", "设备通信中断", "审批即将超时", "质量纠正措施到期"], ["站内消息 · 业务提醒", "系统告警 · 设备", "邮件 · 审批", "站内消息 · 质量"], ["销售与计划负责人", "设备管理员", "当前审批人", "质量责任部门"], ["承诺偏差超过1天", "离线超过5分钟", "剩余2小时", "到期前24小时"]],
    statuses: [["未读", "warn"], ["已读", "info"], ["已处理", "good"], ["升级中", "risk"]], owners: ["系统", "经营办", "平台管理员"], metricLabels: ["今日消息", "未读消息", "业务提醒", "升级告警"],
    workflow: [["定义触发条件", "选择业务事件、阈值和时间"], ["确定接收范围", "按角色、组织或责任人发送"], ["发送与确认", "记录渠道、送达和已读状态"], ["处理与归档", "关联业务结果并形成消息证据"]],
  },
  job: {
    id: "job", layout: "work", icon: "sync", prefix: "JOB", purpose: "承载导入、导出、批量处理和后台任务，透明展示进度、结果、失败原因与重试记录。", actionVerb: "创建",
    fields: [textField("job", "任务名称"), selectField("jobType", "任务类型", ["数据导入", "数据导出", "批量更新", "后台计算"]), textField("scope", "数据范围 / 文件"), textField("schedule", "执行时间 / 策略")],
    samples: [["物料主数据导入", "订单利润批量测算", "库存台账导出", "供应商编码更新"], ["数据导入", "后台计算", "数据导出", "批量更新"], ["materials-0814.xlsx · 328行", "本月已交付订单", "华东工厂 · 本月", "42家供应商"], ["立即执行", "每天 02:00", "今天 10:18", "失败后自动重试2次"]],
    statuses: [["排队中", "info"], ["执行中", "warn"], ["已完成", "good"], ["执行失败", "risk"]], owners: ["当前用户", "系统", "蒋宁"], metricLabels: ["今日任务", "执行中", "成功完成", "失败 / 重试"],
    workflow: [["创建与校验", "校验文件、参数和数据范围"], ["排队与执行", "异步运行并持续更新进度"], ["结果检查", "输出成功、失败和跳过明细"], ["重试与归档", "修复失败数据并保留任务证据"]],
  },
  risk: {
    id: "risk", layout: "work", icon: "warning", prefix: "RSK", purpose: "登记跨订单、供应、生产、质量与设备的业务风险，明确影响、责任、措施和关闭标准。", actionVerb: "登记",
    fields: [textField("risk", "风险事项"), textField("object", "来源 / 影响对象"), selectField("level", "风险等级", ["低", "中", "高", "重大"]), textField("measure", "应对措施 / 期限")],
    samples: [["关键轴承预计晚到", "机加产能超过预警线", "客户信用额度不足", "重复尺寸缺陷"], ["MO-260814-012 · 2张订单", "机加车间 · 未来3日", "恒锐自动化 · SO-036", "GS-800门板 · 3批"], ["高", "中", "重大", "中"], ["启动替代料 · 今日", "外协分流 · 明日", "申请额度释放 · 2小时", "工艺复核 · 08-16"]],
    statuses: [["已识别", "warn"], ["处理中", "info"], ["已受控", "good"], ["已升级", "risk"]], owners: ["周洁", "林浩", "沈妍", "许雯"], metricLabels: ["开放风险", "高风险", "本周关闭", "已升级"],
    workflow: [["识别与评估", "确认发生概率、影响范围和等级"], ["分派责任", "明确责任人与完成期限"], ["执行措施", "记录规避、降低、转移或接受措施"], ["验证关闭", "确认影响解除并沉淀复盘结论"]],
  },
  party: {
    id: "party", layout: "relationship", icon: "groups", prefix: "PTY", purpose: "统一维护组织或人员档案、分类、业务角色、信用/资质和最近交易事实。", actionVerb: "新建",
    fields: [textField("party", "名称 / 主体"), textField("role", "分类 / 业务角色"), textField("qualification", "信用 / 资质"), textField("activity", "交易概况 / 最近互动")],
    samples: [["恒锐自动化", "创驰装备", "华轴精工", "明川机器人"], ["战略客户 · 设备制造", "重点客户 · 自动化", "A级供应商 · 轴承", "潜在客户 · 机器人"], ["信用A · 可用额度¥186万", "信用B · 资质齐全", "ISO9001 · 有效", "资料待完善"], ["年交易¥286万 · 今天", "合作4年 · 昨天", "准交率96.8% · 08-12", "45天无互动"]],
    statuses: [["正常合作", "good"], ["待维护", "warn"], ["评估中", "info"], ["业务受限", "risk"]], owners: ["沈妍", "赵辰", "周洁"], metricLabels: ["有效主体", "重点对象", "待维护", "受限对象"],
    workflow: [["建立档案", "校验主体、联系人和业务资料"], ["分类与评估", "确定业务角色、信用或供应能力"], ["发生业务", "关联报价、订单、交付与结算"], ["持续复核", "定期更新资质、信用和绩效"]],
  },
  commercial: {
    id: "commercial", layout: "document", icon: "description", prefix: "DOC", purpose: "管理来源、交易对象、金额数量、关键日期和审核执行状态，形成完整业务单据链。", actionVerb: "新建",
    fields: [textField("subject", "交易对象 / 业务内容"), textField("reference", "来源 / 关联单据"), textField("amount", "金额 / 数量 / 币种"), textField("date", "业务日期 / 交期")],
    samples: [["恒锐自动化 · GS-800", "创驰装备 · PM-45", "华轴精工 · BR-6204", "东岳电气 · QC-20"], ["客户需求 CR-018", "报价 QT-036", "采购申请 PR-042", "销售订单 SO-052"], ["¥864,000 · CNY", "120套 · ¥386,400", "420件 · ¥38,600", "$72,800 · USD"], ["单据日08-14 · 交期08-20", "单据日08-13 · 交期08-24", "到货日08-18", "有效至09-30"]],
    statuses: [["草稿", "info"], ["待审核", "warn"], ["执行中", "info"], ["已完成", "good"], ["有风险", "risk"]], owners: ["沈妍", "赵辰", "周洁", "孙琳"], metricLabels: ["本月单据", "待审核", "执行中", "风险单据"],
    workflow: [["创建与校验", "确认业务主体、来源和明细"], ["审核与生效", "按金额和组织权限完成审批"], ["执行与协同", "联动计划、物流或结算节点"], ["完成与关闭", "核对数量金额并归档证据"]],
  },
  pricing: {
    id: "pricing", layout: "document", icon: "sell", prefix: "PRC", purpose: "受控管理价格、币种、税率、数量阶梯、有效期和适用范围，为报价与订单提供有效价格。", actionVerb: "维护",
    fields: [textField("item", "客户/供应商 · 物料"), textField("price", "价格 / 币种 / 税率"), textField("scope", "适用范围 / 数量阶梯"), textField("validity", "有效期 / 版本")],
    samples: [["恒锐自动化 · GS-800", "华轴精工 · BR-6204", "创驰装备 · PM-45", "东岳电气 · QC-20"], ["¥36,000/台 · CNY · 13%", "¥91.80/件 · CNY · 13%", "$6,066/套 · USD · 0%", "¥118,000/套 · CNY · 13%"], ["华东区 · ≥10台", "全公司 · ≥400件", "指定客户 · ≥20套", "项目报价 · 6套"], ["V2.1 · 08-01至12-31", "V3.0 · 待生效", "V1.8 · 09-30到期", "审批中"]],
    statuses: [["已生效", "good"], ["待审批", "warn"], ["待生效", "info"], ["即将到期", "risk"]], owners: ["沈妍", "周洁", "孙琳"], metricLabels: ["有效价格", "待审批", "本月生效", "即将到期"],
    workflow: [["维护价格条件", "确定对象、价格、税率和阶梯"], ["审核与发布", "检查毛利或采购价格差异"], ["业务引用", "报价和订单按生效范围取价"], ["到期与变更", "保留旧版本并发布新价格"]],
  },
  engineering: {
    id: "engineering", layout: "catalog", icon: "schema", prefix: "ENG", purpose: "以受控编码和版本管理物料、结构、工艺、图纸或技术文件，保证下游引用一致。", actionVerb: "新建",
    fields: [textField("object", "工程对象 / 编码"), textField("classification", "分类 / 用途 / 工厂"), textField("version", "版本 / 生效范围"), textField("completeness", "结构 / 文件完整性")],
    samples: [["GS-800 伺服驱动控制柜", "PM-45 精密传动模组", "BR-6204 深沟球轴承", "QC-20 智能检测工作站"], ["成品 · 华东工厂", "半成品 · 全工厂", "采购件 · A类", "报价与生产"], ["V3.2 · 08-01生效", "R2.6 · 修订中", "V1.4 · 全公司", "V1.8 · 待发布"], ["68项 · 完整", "42项 · 2项变更", "12属性 · 缺1项", "96项 · 1份图纸待签"]],
    statuses: [["已发布", "good"], ["修订中", "info"], ["待审核", "warn"], ["变更风险", "risk"]], owners: ["何工", "顾工", "唐工"], metricLabels: ["有效对象", "修订中", "待发布", "完整性风险"],
    workflow: [["建立工程对象", "维护编码、分类和关键属性"], ["设计与校验", "完成结构、工艺或文件内容"], ["审核与发布", "锁定版本、生效范围和日期"], ["引用与变更", "下游受控引用并通过变更升级"]],
  },
  planning: {
    id: "planning", layout: "planning", icon: "event_note", prefix: "PLN", purpose: "对齐需求、库存、供应和产能，形成可执行计划、例外建议和下达证据。", actionVerb: "新建",
    fields: [textField("object", "计划对象 / 范围"), textField("period", "计划期间 / 时间围栏"), textField("quantity", "需求 / 供应 / 差异"), textField("result", "计划结果 / 异常")],
    samples: [["GS-800 本周需求", "PM-45 插单需求", "BR-6204 关键物料", "华东工厂全量计划"], ["08-14至08-31 · 冻结3天", "08-18至09-30", "未来7日", "本月滚动计划"], ["需求24 / 供应20 / 缺4", "需求120 / 供应88 / 缺32", "需求420 / 在途300", "计划达成92%"], ["建议加急采购", "等待产能确认", "可承诺量不足", "已下达"]],
    statuses: [["计划中", "info"], ["待确认", "warn"], ["已下达", "good"], ["有例外", "risk"]], owners: ["林浩", "宋可", "系统"], metricLabels: ["计划对象", "待确认", "已下达", "供需例外"],
    workflow: [["汇总需求", "冻结订单、预测和独立需求快照"], ["平衡供需", "考虑库存、在途、提前期和产能"], ["处理例外", "确认加急、延后、替代或取消"], ["下达与跟踪", "转为采购或生产执行单据"]],
  },
  execution: {
    id: "execution", layout: "execution", icon: "precision_manufacturing", prefix: "MO", purpose: "把生产需求落实到工单、工序、人员、设备、数量和实际时间，支撑现场执行闭环。", actionVerb: "创建",
    fields: [textField("operation", "生产对象 / 工单工序"), textField("resource", "车间 / 工作中心 / 设备"), textField("quantity", "计划 / 完工 / 不良"), textField("time", "计划 / 实际时间")],
    samples: [["MO-260814-036 · 30装配", "MO-260814-041 · 20精加工", "MO-260814-052 · 40电气接线", "MO-260813-028 · 50整机测试"], ["总装一车间 · ASM-12", "机加车间 · CNC-07", "电子车间 · ELE-03", "测试区 · TEST-04"], ["24 / 18 / 0", "120 / 76 / 2", "12 / 0 / 0", "20 / 20 / 1"], ["08:00–16:00 · 08:12开工", "10:00–18:00 · 暂停46分", "14:00–20:00 · 待开工", "昨日 · 17:36完工"]],
    statuses: [["待开工", "warn"], ["执行中", "info"], ["已完工", "good"], ["现场异常", "risk"]], owners: ["王峻", "陈磊", "刘鹏"], metricLabels: ["今日任务", "执行中", "完成率", "暂停 / 异常"],
    workflow: [["派工与校验", "确认人员、设备、物料和指导书"], ["开工与采集", "记录实际开工和生产过程事实"], ["报工与检验", "上报完成、不良和质量结果"], ["完工与转序", "形成转序或完工入库凭证"]],
  },
  traceability: {
    id: "traceability", layout: "execution", icon: "conversion_path", prefix: "TRC", purpose: "从批次、序列号或业务对象双向追溯来源、生产过程、检验、库存与交付去向。", actionVerb: "查询", actionMode: "query",
    fields: [textField("object", "批次 / 序列号 / 对象"), textField("source", "来源单据 / 上游"), textField("process", "生产 / 检验记录"), textField("destination", "库存 / 交付去向")],
    samples: [["LOT-260731-08 · BR-6204", "SN-GS8-2608-018", "LOT-260812-03 · AL-6061", "SN-QC2-2608-006"], ["PO-260710-026 · 华轴精工", "MO-260814-036", "PO-260809-018", "MO-260813-027"], ["IQC-260731-012 · 合格", "装配/接线/测试 · 合格", "IQC待判 · 已隔离", "终检FQC-028 · 合格"], ["线边仓L-12 · 关联3工单", "成品仓F-02 · 待发", "待检区IQC-06", "已交付东岳电气"]],
    statuses: [["链路完整", "good"], ["追溯中", "info"], ["证据缺失", "warn"], ["影响扩大", "risk"]], owners: ["许雯", "徐峰", "王峻"], metricLabels: ["可追溯对象", "完整链路", "缺失证据", "风险批次"],
    workflow: [["识别追溯对象", "输入批次、序列号或业务对象"], ["向上追溯", "定位供应商、来料和投入批次"], ["过程还原", "串联工序、设备、人员和检验"], ["向下追踪", "定位库存位置、客户和召回范围"]],
  },
  inventory: {
    id: "inventory", layout: "inventory", icon: "inventory_2", prefix: "STK", purpose: "按物料、质量状态、库位、批次和占用关系管理库存事实与仓内作业。", actionVerb: "创建",
    fields: [textField("material", "物料 / 作业对象"), textField("location", "仓库 / 库位"), textField("lot", "批次 / 序列号 / 来源"), textField("quantity", "数量 / 占用 / 可用")],
    samples: [["BR-6204 轴承 · 合格", "AL-6061 型材 · 待检", "GS-800 控制柜 · 合格", "DRV-02 驱动器 · 冻结"], ["原材料仓 · A-01-03", "待检区 · IQC-06", "成品仓 · F-02-08", "线边仓 · L-12"], ["LOT-260731-08", "LOT-260812-03", "SN-GS8-2608-018", "PO-260809-018"], ["420 / 占用80 / 可用340件", "1,200kg / 待检", "18 / 待发12台", "36件 / 冻结"]],
    statuses: [["可执行", "good"], ["待处理", "warn"], ["作业中", "info"], ["库存异常", "risk"]], owners: ["徐峰", "吴倩", "方敏"], metricLabels: ["业务记录", "今日作业", "已完成", "库存异常"],
    workflow: [["创建仓储任务", "校验来源、物料、批次和数量"], ["分派与执行", "按策略生成库位和作业顺序"], ["复核与过账", "确认实物、标签和系统数量"], ["完成与追溯", "形成库存事务和完整作业证据"]],
  },
  logistics: {
    id: "logistics", layout: "inventory", icon: "local_shipping", prefix: "LOG", purpose: "衔接业务单据与仓储运输执行，管理任务、包装、交接、承运和签收证据。", actionVerb: "创建",
    fields: [textField("object", "订单 / 运输对象"), textField("route", "仓库 / 路线 / 承运方"), textField("load", "数量 / 包装 / 重量"), textField("schedule", "计划 / 实际节点")],
    samples: [["SO-260814-036 · GS-800", "PO-260810-018 · BR-6204", "MO-260814-041 · PM-45", "RTN-260814-006"], ["成品仓F区 · 顺丰重货", "待检区 · 华轴精工", "线边仓L区 · 厂内配送", "客户现场 · 德邦"], ["24台 · 6箱 · 1.8t", "420件 · 7箱", "120套 · 4托", "2套 · 返修包装"], ["计划08-16 · 待拣", "到货08-18 · 待预约", "10:30 · 配送中", "昨天 · 已签收"]],
    statuses: [["待执行", "warn"], ["运输中", "info"], ["已签收", "good"], ["交付异常", "risk"]], owners: ["徐峰", "方敏", "赵辰"], metricLabels: ["今日任务", "待执行", "运输中", "交付异常"],
    workflow: [["生成物流任务", "关联订单、库存和交付要求"], ["拣配与复核", "完成拣货、包装、标签与装载"], ["交接与运输", "记录承运、车辆、司机和节点"], ["签收与回单", "收集签收证据并关闭交付"]],
  },
  quality: {
    id: "quality", layout: "quality", icon: "verified", prefix: "QC", purpose: "按质量计划、标准和抽样要求执行检验，记录特性结果、判定与放行证据。", actionVerb: "创建",
    fields: [textField("object", "检验对象 / 来源"), textField("standard", "标准 / 特性 / 抽样"), textField("result", "数量 / 结果 / 缺陷"), textField("decision", "判定 / 放行时间")],
    samples: [["BR-6204 · PO-260710-026", "GS-800门板 · OP-036", "QC-20整机 · MO-027", "PM-45 · 客诉样件"], ["IQC-AQL1.0 · 32件", "IPQC-尺寸标准 · 全检", "FQC-整机标准 · 6套", "客户规范 CS-018"], ["32检 / 32合格", "24检 / 2超差", "6检 / 4完成", "1件 / 表面划伤"], ["合格 · 09:18放行", "待判定", "执行中", "不合格 · 已隔离"]],
    statuses: [["待检验", "warn"], ["检验中", "info"], ["已合格", "good"], ["不合格", "risk"]], owners: ["许雯", "王敏", "陈琪"], metricLabels: ["检验任务", "待检验", "一次合格率", "不合格"],
    workflow: [["生成检验任务", "引用质量计划、标准和业务来源"], ["抽样与测量", "记录样本、特性、设备和实测值"], ["判定与隔离", "形成合格、让步或不合格结论"], ["放行与归档", "更新库存/工序状态并保存证据"]],
  },
  qualityIssue: {
    id: "quality-issue", layout: "quality", icon: "gavel", prefix: "NCR", purpose: "从不合格或投诉事实出发，管理隔离、评审、根因、处置、纠正措施和效果验证。", actionVerb: "发起",
    fields: [textField("issue", "来源 / 问题对象"), textField("defect", "缺陷 / 严重度"), textField("containment", "数量 / 隔离 / 影响"), textField("disposition", "处置 / 措施 / 期限")],
    samples: [["过程检验 · GS-800门板", "来料检验 · BR-6204", "客户投诉 · PM-45", "终检 · QC-20"], ["尺寸超差 · 主要", "异响 · 严重", "表面划伤 · 主要", "标签错误 · 次要"], ["2件 · 已隔离", "32件 · 扩大隔离280件", "1套 · 已追回", "6套 · 已冻结"], ["返工 · 08-15", "退货与8D · 08-20", "客户换货 · 今天", "让步接收 · 待审批"]],
    statuses: [["待评审", "warn"], ["处理中", "info"], ["已验证", "good"], ["重大风险", "risk"]], owners: ["许雯", "王敏", "陈琪"], metricLabels: ["开放问题", "待评审", "按期关闭率", "重大问题"],
    workflow: [["登记与遏制", "锁定缺陷事实和影响范围"], ["评审与根因", "跨部门确定原因和处置方式"], ["执行与验证", "完成返工、退货、报废或纠正"], ["关闭与防再发", "验证效果并更新标准或过程"]],
  },
  equipment: {
    id: "equipment", layout: "equipment", icon: "manufacturing", prefix: "EAM", purpose: "围绕设备、位置、状态、周期、读数和责任人管理资产全生命周期与运行健康。", actionVerb: "新增",
    fields: [textField("asset", "设备 / 资产对象"), textField("location", "位置 / 类型 / 责任"), textField("health", "状态 / 周期 / 读数"), textField("schedule", "计划 / 最近执行")],
    samples: [["CNC-07 立式加工中心", "ASM-12 电气装配台", "TEST-04 综合测试台", "AIR-01 空压机"], ["机加车间 · A类设备", "总装一车间 · 生产设备", "质量中心 · 检测设备", "公用工程 · 动力设备"], ["运行 · 主轴负载68%", "待点检 · 周期8小时", "离线 · 32分钟", "压力0.68MPa · 正常"], ["保养08-20", "点检今天14:00", "维修工单WO-018", "抄表今天08:00"]],
    statuses: [["健康运行", "good"], ["待执行", "warn"], ["处理中", "info"], ["设备异常", "risk"]], owners: ["赵凯", "陈磊", "刘鹏"], metricLabels: ["设备 / 任务", "健康运行", "今日待办", "异常设备"],
    workflow: [["建立资产对象", "维护设备编码、位置和责任"], ["制定周期计划", "生成点检、保养、抄表或校准任务"], ["执行与记录", "记录人员、时间、结果、用料和读数"], ["异常与改善", "联动维修、停机、OEE和知识库"]],
  },
  maintenance: {
    id: "maintenance", layout: "equipment", icon: "build", prefix: "WO", purpose: "管理设备故障、维修工单、停机影响、备件工时、验收和故障知识沉淀。", actionVerb: "创建",
    fields: [textField("asset", "设备 / 故障对象"), textField("fault", "故障现象 / 等级"), textField("resource", "人员 / 备件 / 工时"), textField("result", "计划 / 修复 / 验收")],
    samples: [["CNC-07 · 主轴异常", "TEST-04 · 通信中断", "ASM-12 · 夹具松动", "AIR-01 · 压力波动"], ["振动超限 · 高", "网关离线 · 中", "定位偏差 · 中", "供气不足 · 高"], ["陈磊 · 轴承1件 · 2.5h", "赵凯 · 网关模块 · 1h", "刘鹏 · 无备件 · 0.5h", "外协 · 阀组1套"], ["维修中 · 预计11:30", "已修复 · 待验收", "已关闭", "等待备件 · 明日"]],
    statuses: [["待响应", "warn"], ["维修中", "info"], ["已验收", "good"], ["停机风险", "risk"]], owners: ["赵凯", "陈磊", "刘鹏"], metricLabels: ["开放工单", "维修中", "平均修复时长", "停机风险"],
    workflow: [["报修与响应", "记录故障、停机和影响范围"], ["诊断与计划", "确认原因、人员、备件和安全措施"], ["维修与试运行", "记录工时、用料和修复过程"], ["验收与沉淀", "验证能力并更新故障知识"]],
  },
  finance: {
    id: "finance", layout: "finance", icon: "account_balance_wallet", prefix: "FIN", purpose: "以业务单据为依据管理金额、期间、匹配核销、差异和责任，保持业务财务一致。", actionVerb: "新建",
    fields: [textField("object", "客户/供应商 · 业务对象"), textField("reference", "订单 / 发票 / 凭证"), textField("amount", "金额 / 币种 / 已核销"), textField("period", "期间 / 到期 / 账龄")],
    samples: [["恒锐自动化 · 应收", "华轴精工 · 应付", "创驰装备 · 销售结算", "东岳电气 · 订单利润"], ["SO-260814-036 · INV-018", "PO-260710-026 · PINV-042", "RCV-260814-012", "SO-260813-027"], ["¥864,000 / 已核销¥360,000", "¥38,600 / 待付", "¥286,400 / 已收", "收入¥728,000 / 成本¥517,152"], ["2026-08 · 08-30到期", "2026-08 · 31天", "今天09:18", "本月 · 已核算"]],
    statuses: [["待处理", "warn"], ["匹配中", "info"], ["已确认", "good"], ["有差异", "risk"]], owners: ["孙琳", "魏铭"], metricLabels: ["本期金额", "待处理", "已确认", "差异金额"],
    workflow: [["获取业务依据", "关联订单、收发、发票和付款事实"], ["匹配与校验", "核对主体、数量、税率和金额"], ["确认与核销", "按期间完成确认、分摊或核销"], ["差异与关闭", "解释差异并保留财务证据"]],
  },
  costing: {
    id: "costing", layout: "finance", icon: "calculate", prefix: "CST", purpose: "按成本期间、对象与要素管理标准成本、实际归集、分摊、差异和结算状态。", actionVerb: "执行",
    fields: [textField("object", "成本对象 / 期间"), textField("element", "成本要素 / 来源"), textField("amount", "标准 / 实际 / 差异"), textField("calculation", "核算状态 / 时间")],
    samples: [["GS-800 · 2026-08", "PM-45 · 2026-08", "华东工厂 · 制造费用", "BR-6204 · 材料价格"], ["直接材料 · 领料事务", "直接人工 · 报工工时", "机器折旧与能源", "采购价格差异"], ["标准¥26,800 / 实际¥27,420 / +¥620", "标准¥2,420 / 实际¥2,386 / -¥34", "预算¥86万 / 实际¥89.2万", "标准¥88 / 实际¥91.8"], ["已核算 · 今天09:30", "待补外协成本", "分摊中 82%", "已确认"]],
    statuses: [["待核算", "warn"], ["核算中", "info"], ["已确认", "good"], ["差异预警", "risk"]], owners: ["孙琳", "魏铭"], metricLabels: ["核算对象", "待核算", "已确认", "差异预警"],
    workflow: [["打开成本期间", "冻结范围、口径和标准版本"], ["归集与分摊", "汇总材料、人工和制造费用"], ["计算与校验", "形成实际成本和标准差异"], ["结算与锁定", "确认结果并保留可追溯证据"]],
  },
  analytics: {
    id: "analytics", layout: "analytics", icon: "monitoring", prefix: "BI", purpose: "按统一指标口径分析趋势、结构和异常，并可下钻到对应订单、工单、库存或检验事实。", actionVerb: "刷新", actionMode: "refresh",
    fields: [textField("metric", "指标 / 分析主题"), textField("dimension", "组织 / 产品 / 客户维度"), textField("value", "当前值 / 目标 / 趋势"), textField("refresh", "统计周期 / 更新时间")],
    samples: [["订单准时交付率", "车间计划达成率", "库存周转次数", "设备综合效率OEE"], ["华东工厂 · GS-800", "总装一车间 · 本周", "原材料仓 · A类物料", "机加车间 · CNC设备"], ["94.2% / 目标96% / ↑1.2%", "93.4% / 目标95%", "6.8次 / 目标7.2次", "82.6% / 目标85%"], ["本月 · 今天10:42", "本周 · 每小时", "近12月 · 昨天", "今日班次 · 5分钟前"]],
    statuses: [["达到目标", "good"], ["接近预警", "warn"], ["分析中", "info"], ["偏差异常", "risk"]], owners: ["经营办", "生产部", "质量部", "设备部"], metricLabels: ["核心指标", "达到目标", "接近预警", "异常指标"],
    workflow: [["定义指标口径", "明确公式、维度、范围和刷新频率"], ["汇总可信数据", "从业务事实和主数据形成指标"], ["识别趋势偏差", "对比目标、同期和结构变化"], ["下钻业务改善", "定位责任对象并跟踪改善结果"]],
  },
  masterData: {
    id: "master-data", layout: "settings", icon: "database", prefix: "MD", purpose: "以编码、层级、适用范围和生效状态维护平台主数据，为业务流程提供统一事实。", actionVerb: "新建",
    fields: [textField("object", "主数据对象 / 编码"), textField("hierarchy", "分类 / 上级 / 组织"), textField("configuration", "关键配置 / 属性"), textField("validity", "生效范围 / 更新时间")],
    samples: [["PLANT-EAST · 华东制造中心", "WC-ASM-01 · 总装工作中心", "WH-RM · 原材料仓", "REASON-QC-08 · 尺寸超差"], ["公司 > 工厂", "华东工厂 > 总装一车间", "华东工厂 > 仓储部", "质量原因 > 尺寸类"], ["时区UTC+8 · CNY", "能力8h/班 · 12人", "批次管理 · 库位启用", "需评审 · 可返工"], ["全公司 · 今天10:20", "华东工厂 · 08-12", "已生效", "V2.1 · 待发布"]],
    statuses: [["已启用", "good"], ["待发布", "warn"], ["配置中", "info"], ["配置冲突", "risk"]], owners: ["蒋宁", "系统管理员"], metricLabels: ["有效对象", "已启用", "待发布", "配置冲突"],
    workflow: [["建立编码对象", "校验唯一编码、名称和层级"], ["维护业务属性", "配置组织范围和关键参数"], ["审核与生效", "按版本和日期受控发布"], ["引用与停用", "检查业务引用后变更或停用"]],
  },
  governance: {
    id: "governance", layout: "settings", icon: "admin_panel_settings", prefix: "SYS", purpose: "管理组织、人员、角色、流程、规则和参数的适用范围、版本、权限与审计证据。", actionVerb: "新建",
    fields: [textField("object", "配置 / 治理对象"), textField("type", "类型 / 所属组织"), textField("scope", "权限 / 适用范围"), textField("version", "版本 / 最近更新")],
    samples: [["生产计划员", "采购订单审批", "华东制造中心", "销售订单编号规则"], ["业务角色 · 计划部", "审批流程 · 供应链", "组织单元 · 全公司", "编号规则 · 销售部"], ["计划与MRP · 华东工厂", "金额分级 · 全公司", "126名用户", "SO-{日期}-{流水号}"], ["V2.1 · 今天10:20", "已发布 · 昨天", "季度复核", "08-01生效"]],
    statuses: [["已启用", "good"], ["待复核", "warn"], ["变更中", "info"], ["治理风险", "risk"]], owners: ["蒋宁", "系统管理员"], metricLabels: ["治理对象", "已启用", "待复核", "风险配置"],
    workflow: [["定义治理对象", "明确职责、范围和业务目的"], ["配置规则权限", "坚持最小权限和范围隔离"], ["审批与发布", "敏感配置经过复核后生效"], ["审计与复核", "保留变更记录并定期确认"]],
  },
  template: {
    id: "template", layout: "settings", icon: "draft", prefix: "TPL", purpose: "受控管理消息、打印或业务模板的内容、变量、渠道、版式和适用范围。", actionVerb: "新建",
    fields: [textField("template", "模板名称 / 编码"), textField("type", "类型 / 渠道 / 版式"), textField("variables", "变量 / 数据来源"), textField("scope", "适用范围 / 版本")],
    samples: [["销售订单确认通知", "成品箱标签 100×70", "检验报告模板", "设备告警通知"], ["站内+邮件", "热敏标签 · Zebra", "A4 PDF", "企业微信+站内"], ["客户/订单/承诺交期", "物料/批次/序列号", "特性/结果/判定", "设备/告警/时间"], ["销售部 · V2.1", "华东工厂 · 已发布", "质量部 · 修订中", "全公司 · 待审核"]],
    statuses: [["已发布", "good"], ["修订中", "info"], ["待审核", "warn"], ["渲染异常", "risk"]], owners: ["蒋宁", "业务管理员"], metricLabels: ["有效模板", "已发布", "修订中", "渲染异常"],
    workflow: [["设计模板", "定义内容、变量、渠道或版式"], ["预览与校验", "使用样例数据检查渲染结果"], ["审核与发布", "锁定版本和适用范围"], ["使用与变更", "记录调用结果并受控升级"]],
  },
  integration: {
    id: "integration", layout: "settings", icon: "hub", prefix: "INT", purpose: "管理外部应用、接口、Webhook、编码映射、调用状态和集成日志，明确安全与重试边界。", actionVerb: "配置", actionMode: "feedback",
    fields: [textField("endpoint", "应用 / 接口 / 映射"), textField("protocol", "协议 / 认证 / 方向"), textField("mapping", "数据对象 / 编码映射"), textField("health", "调用状态 / 最近时间")],
    samples: [["WMS-EXT · 库存同步", "Webhook · 订单下达", "SAP供应商编码映射", "设备数据接入"], ["REST · OAuth2 · 双向", "HTTPS · HMAC · 出站", "主数据映射 · 入站", "MQTT/OPC UA · 入站"], ["物料/批次/库存事务", "销售订单/变更", "供应商/税号/组织", "设备/点位/告警"], ["成功率99.8% · 10:42", "最近失败1次 · 重试中", "42条 · 2条待确认", "前端规划 · 未真实接通"]],
    statuses: [["运行正常", "good"], ["配置中", "info"], ["待联调", "warn"], ["调用异常", "risk"]], owners: ["平台管理员", "蒋宁"], metricLabels: ["集成对象", "运行正常", "待联调", "调用异常"],
    workflow: [["定义集成契约", "确认系统边界、对象和数据方向"], ["配置安全映射", "维护认证、字段和编码映射"], ["联调与验证", "验证幂等、重试、顺序和异常"], ["运行与监控", "记录日志、指标、告警和审计证据"]],
  },
} satisfies Record<string, CatalogBlueprint>;

const pageActionOverrides: Record<string, string> = {
  "我的待办": "处理待办", "我的审批": "批量审批", "我的发起": "发起业务流程", "我的关注": "添加关注", "业务流程": "发起流程", "风险中心": "登记风险",
  "客户列表": "新建客户", "联系人": "新建联系人", "信用资料": "维护信用资料", "报价": "新建报价单", "订单审核": "发起订单审核", "变更记录": "申请订单变更", "待发货": "创建发货任务", "交付跟踪": "更新交付节点", "销售退货": "新建销售退货单", "价目表": "新建价目表", "折扣政策": "新建折扣政策", "销售合同": "新建销售合同", "销售预测": "新建销售预测",
  "供应商": "新建供应商", "采购申请": "新建采购申请", "询价单": "新建询价单", "比价决策": "发起比价决策", "采购合同": "新建采购合同", "采购价格": "维护采购价格", "采购订单": "新建采购订单", "到货协同": "登记到货", "采购退货": "新建采购退货单", "供应商绩效": "发起绩效评估", "供应商协同": "发起协同事项", "委外加工": "新建委外订单",
  "物料列表": "新建物料", "分类与属性": "新建物料分类", "计量单位": "新建计量单位", "产品结构视图": "查询产品结构", "BOM 列表": "新建BOM版本", "版本比较": "选择比较版本", "替代料": "维护替代关系", "路线列表": "新建工艺路线", "工序库": "新建标准工序", "作业指导书": "发布作业指导", "受控图纸": "上传受控图纸", "技术文件": "新建技术文件", "文档发布": "发起文档发布", "工程变更": "发起工程变更",
  "独立需求": "新建独立需求", "需求合并": "执行需求合并", "主生产计划": "新建主生产计划", "运算方案": "新建运算方案", "运算记录": "发起MRP运算", "供需建议": "生成业务单据", "物料齐套": "执行齐套检查", "可承诺量": "执行ATP检查", "产能负荷": "测算产能", "计划例外": "处理计划例外", "计划参数": "新建参数方案", "高级排程": "新建排程方案",
  "订单列表": "新建生产订单", "下达记录": "下达生产订单", "工单看板": "新建车间工单", "工序任务": "创建工序任务", "工序执行台": "开始工序作业", "电子作业指导": "发布现场指导", "派工队列": "创建派工", "生产报工": "新建报工", "报工审核": "发起报工审核", "班组与人员": "新建班组", "生产资源": "新增生产资源", "在制品": "查询在制品", "批次追溯": "查询批次", "序列号追溯": "查询序列号", "异常记录": "登记生产异常", "返工返修": "创建返工单", "报废处置": "发起报废审批", "扫码领料": "开始扫码领料", "扫码报工": "开始扫码报工", "标签补打": "申请标签补打",
  "现存量": "查询现存量", "可用量": "执行可用量检查", "库存台账": "查询库存流水", "收货上架": "创建上架任务", "待拣货": "创建拣货任务", "复核装箱": "开始复核装箱", "出库交接": "确认出库交接", "签收回单": "登记签收回单", "生产备料": "创建备料任务", "领退料": "新建领退料单", "成品入库": "创建入库单", "库存调拨": "新建调拨单", "库存调整": "新建调整单", "冻结解冻": "发起冻结操作", "其他出入库": "新建出入库单", "标签模板": "新建标签模板", "标签打印": "创建打印任务", "扫码作业": "开始扫码作业", "盘点作业": "发起盘点", "批次与序列号": "维护批次规则", "库存占用": "创建库存占用", "库存策略": "新建库存策略", "运输交接": "创建运输交接",
  "质量计划": "创建质量计划", "质量标准": "新建质量标准", "抽样方案": "新建抽样方案", "缺陷代码": "新建缺陷代码", "待检任务": "创建检验任务", "检验记录": "录入检验结果", "过程检验": "创建过程检验", "完工检验": "创建完工检验", "不合格记录": "登记不合格", "评审处置": "发起评审", "纠正措施": "创建纠正措施", "量检具台账": "新增量检具", "校准计划": "新建校准计划", "供应商质量": "发起供应商质量评估", "客诉记录": "登记客户投诉", "8D 改进": "发起8D改进", "SPC 分析": "刷新SPC分析", "质量追溯": "查询质量追溯",
  "设备台账": "新增设备", "设备状态": "更新设备状态", "点检计划": "新建点检计划", "保养计划": "新建保养计划", "维修工单": "创建维修工单", "计量抄表": "录入计量读数", "OEE 与停机": "刷新OEE与停机", "故障知识库": "新增故障知识", "工装模具": "新增工装模具", "备件管理": "新增备件",
  "应收管理": "登记应收事项", "销售开票": "申请销售开票", "收款核销": "登记收款核销", "客户对账": "生成客户对账单", "应付管理": "登记应付事项", "采购发票": "登记采购发票", "付款核销": "登记付款核销", "供应商对账": "生成供应商对账单", "成本期间": "新建成本期间", "标准成本": "发布标准成本", "材料成本": "执行材料核算", "制造成本": "执行成本结转", "实际成本": "执行实际成本核算", "成本差异": "分析成本差异", "总账税务": "创建记账凭证",
  "经营分析": "刷新经营分析", "生产看板": "刷新生产看板", "质量分析": "刷新质量分析", "交付分析": "刷新交付分析", "设备分析": "刷新设备分析", "库存分析": "刷新库存分析", "成本分析": "刷新成本分析", "指标定义": "新建指标", "自定义报表": "新建报表",
  "工厂与车间": "新建工厂或车间", "工作中心": "新建工作中心", "班次与日历": "新建生产日历", "仓库与库位": "新建仓库或库位", "业务原因码": "新建原因码", "组织架构": "新建组织单元", "用户管理": "邀请用户", "岗位管理": "新建岗位", "审批流程": "新建审批流程", "编号规则": "新建编号规则", "系统参数": "新建系统参数", "消息模板": "新建消息模板", "打印模板": "新建打印模板", "数据归档": "新建归档策略", "操作审计": "导出审计记录",
};

function stableCode(value: string) {
  return [...value].reduce((total, char) => (total * 31 + char.charCodeAt(0)) % 10000, 17);
}

function titleOf(route: ResolvedProductRoute) {
  return route.child?.label ?? route.module?.label ?? `${route.area.label}总览`;
}

function nounOf(title: string) {
  return title.replace(/列表|记录|管理|总览|分析|看板|作业|中心/g, "") || title;
}

function includesAny(value: string, terms: string[]) {
  return terms.some((term) => value.includes(term));
}

function selectBlueprint(route: ResolvedProductRoute): CatalogSelection {
  const title = titleOf(route);
  const path = route.pathname;
  const area = route.area.id;

  if (!route.module || title.endsWith("总览") || ["经营总览", "经营分析", "生产看板"].includes(title)) return { blueprint: area === "analytics" ? "analytics" : "dashboard", actionMode: "refresh" };

  if (area === "overview") {
    if (title === "消息中心") return { blueprint: "notification", noun: "消息规则" };
    if (includesAny(title, ["任务", "导入", "导出", "批量"])) return { blueprint: "job", noun: "任务" };
    if (title === "风险中心") return { blueprint: "risk", noun: "风险" };
    if (includesAny(title, ["待办", "审批", "发起", "关注", "工作"])) return { blueprint: "task", noun: "工作事项" };
    return { blueprint: "governance", noun: title };
  }

  if (area === "sales") {
    if (includesAny(path, ["customers"])) return { blueprint: "party", noun: title.includes("联系人") ? "联系人" : title.includes("信用") ? "信用档案" : "客户" };
    if (includesAny(path, ["pricing"])) return { blueprint: "pricing", noun: title.includes("折扣") ? "折扣政策" : "销售价格" };
    if (includesAny(title, ["预测"])) return { blueprint: "planning", noun: "销售预测" };
    if (includesAny(title, ["发货", "交付"])) return { blueprint: "logistics", noun: "交付任务" };
    return { blueprint: "commercial", noun: title.includes("合同") ? "销售合同" : title.includes("退货") ? "销售退货单" : title.includes("报价") ? "报价单" : "销售订单" };
  }

  if (area === "procurement") {
    if (includesAny(title, ["供应商", "绩效"]) && !title.includes("协同")) return { blueprint: "party", noun: title.includes("绩效") ? "供应商绩效" : "供应商" };
    if (includesAny(path, ["pricing"])) return { blueprint: "pricing", noun: "采购价格" };
    if (includesAny(title, ["到货"])) return { blueprint: "logistics", noun: "到货任务" };
    return { blueprint: "commercial", noun: includesAny(title, ["询价", "比价"]) ? "寻源单据" : title.includes("合同") ? "采购合同" : title.includes("申请") ? "采购申请" : title.includes("委外") ? "委外订单" : title.includes("退货") ? "采购退货单" : "采购订单" };
  }

  if (area === "product") return { blueprint: "engineering", noun: includesAny(title, ["物料"]) ? "物料" : includesAny(title, ["BOM", "结构", "替代"]) ? "BOM版本" : includesAny(title, ["工艺", "路线", "工序", "指导"]) ? "工艺对象" : includesAny(title, ["图纸", "文档", "文件"]) ? "技术文件" : "工程变更", actionMode: title === "产品结构视图" ? "query" : undefined };

  if (area === "planning") return { blueprint: "planning", noun: includesAny(title, ["需求"]) ? "需求" : includesAny(title, ["MRP", "运算", "供需"]) ? "MRP结果" : includesAny(title, ["齐套"]) ? "齐套检查" : includesAny(title, ["产能"]) ? "产能计划" : includesAny(title, ["参数"]) ? "计划参数" : includesAny(title, ["例外"]) ? "计划例外" : "计划" };

  if (area === "production") {
    if (includesAny(title, ["追溯", "批次", "序列号"])) return { blueprint: "traceability", noun: "生产追溯" };
    if (includesAny(title, ["异常", "返工", "报废"])) return { blueprint: "qualityIssue", noun: title };
    return { blueprint: "execution", noun: includesAny(title, ["人员", "班组"]) ? "班组" : includesAny(title, ["资源"]) ? "生产资源" : includesAny(title, ["报工"]) ? "报工记录" : includesAny(title, ["派工"]) ? "派工任务" : includesAny(title, ["在制"]) ? "在制对象" : includesAny(title, ["扫码", "标签"]) ? "现场作业" : includesAny(title, ["工序", "指导", "执行台"]) ? "工序任务" : "生产工单", actionMode: title === "在制品" ? "query" : undefined };
  }

  if (area === "warehouse") {
    if (includesAny(title, ["发货", "交接", "签收", "拣货", "装箱", "运输"])) return { blueprint: "logistics", noun: "物流任务" };
    if (includesAny(title, ["批次", "序列号"])) return { blueprint: "traceability", noun: "库存批次" };
    return { blueprint: "inventory", noun: includesAny(title, ["盘点"]) ? "盘点任务" : includesAny(title, ["标签", "条码", "扫码"]) ? "条码作业" : includesAny(title, ["占用"]) ? "库存占用" : includesAny(title, ["策略"]) ? "库存策略" : "库存作业", actionMode: includesAny(title, ["可用量", "库存台账"]) ? "query" : undefined };
  }

  if (area === "quality") {
    if (includesAny(title, ["不合格", "评审", "纠正", "客诉", "8D", "改进"])) return { blueprint: "qualityIssue", noun: title.includes("客诉") ? "客户投诉" : title.includes("纠正") ? "纠正措施" : "质量问题" };
    if (includesAny(title, ["追溯"])) return { blueprint: "traceability", noun: "质量追溯" };
    if (includesAny(title, ["SPC"])) return { blueprint: "analytics", noun: "SPC指标", actionMode: "feedback" };
    return { blueprint: "quality", noun: includesAny(title, ["量检具", "校准"]) ? "量检具任务" : includesAny(title, ["计划"]) ? "质量计划" : includesAny(title, ["标准"]) ? "质量标准" : includesAny(title, ["抽样"]) ? "抽样方案" : includesAny(title, ["缺陷"]) ? "缺陷代码" : includesAny(title, ["供应商"]) ? "供应商质量事项" : "检验任务" };
  }

  if (area === "equipment") {
    if (includesAny(title, ["维修", "故障"])) return { blueprint: "maintenance", noun: title.includes("知识") ? "故障知识" : "维修工单" };
    if (includesAny(title, ["OEE"])) return { blueprint: "analytics", noun: "OEE指标" };
    if (includesAny(title, ["采集"])) return { blueprint: "integration", noun: "设备接入", actionMode: "feedback" };
    return { blueprint: "equipment", noun: includesAny(title, ["点检"]) ? "点检任务" : includesAny(title, ["保养"]) ? "保养任务" : includesAny(title, ["抄表"]) ? "计量记录" : includesAny(title, ["备件"]) ? "设备备件" : includesAny(title, ["工装", "模具"]) ? "工装模具" : "设备资产" };
  }

  if (area === "finance") {
    if (includesAny(title, ["成本", "利润", "期间", "差异"])) return { blueprint: "costing", noun: title.includes("期间") ? "成本期间" : title.includes("利润") ? "订单利润" : "成本对象" };
    return { blueprint: "finance", noun: includesAny(title, ["应收"]) ? "应收记录" : includesAny(title, ["应付"]) ? "应付记录" : includesAny(title, ["开票", "发票"]) ? "发票" : includesAny(title, ["核销", "收款", "付款"]) ? "核销记录" : includesAny(title, ["对账"]) ? "对账单" : "财务凭证" };
  }

  if (area === "analytics") return { blueprint: "analytics", noun: `${title}指标`, actionMode: title === "指标定义" ? "form" : title.includes("报表") ? "feedback" : "refresh" };

  if (area === "settings") {
    if (includesAny(path, ["master-data"])) return { blueprint: "masterData", noun: title };
    if (includesAny(title, ["消息模板", "打印模板"])) return { blueprint: "template", noun: title };
    if (includesAny(path, ["jobs"])) return { blueprint: "job", noun: "平台任务" };
    if (includesAny(path, ["integrations"])) return { blueprint: "integration", noun: title, actionMode: "feedback" };
    if (title === "操作审计") return { blueprint: "governance", noun: title, actionMode: "export" };
    if (title === "数据归档") return { blueprint: "governance", noun: title, actionMode: "refresh" };
    return { blueprint: "governance", noun: title };
  }

  return { blueprint: "dashboard", noun: title, actionMode: "refresh" };
}

function makeRows(title: string, blueprint: CatalogBlueprint): BusinessRow[] {
  const seed = stableCode(title);
  return Array.from({ length: 32 }, (_, index) => {
    const status = blueprint.statuses[(seed + index) % blueprint.statuses.length];
    const cells = blueprint.samples.map((values) => values[(seed + index) % values.length]);
    const owner = blueprint.owners[(seed + index) % blueprint.owners.length];
    return {
      id: `${blueprint.prefix}-${String((seed % 800) + 100 + index).padStart(4, "0")}`,
      cells,
      status: status[0],
      tone: status[1],
      owner,
      description: `${title}业务记录：${cells[0]}，${blueprint.purpose} 当前责任人${owner}，状态为${status[0]}。`,
      ageInDays: index * 3,
    };
  });
}

function makeMetrics(title: string, blueprint: CatalogBlueprint) {
  const seed = stableCode(title);
  return [
    { label: blueprint.metricLabels[0], value: String(36 + seed % 68), note: "当前统计范围", tone: "info" as const },
    { label: blueprint.metricLabels[1], value: String(4 + seed % 13), note: "需要按时处理", tone: "warn" as const },
    { label: blueprint.metricLabels[2], value: `${82 + seed % 17}%`, note: "较上期保持稳定", tone: "good" as const },
    { label: blueprint.metricLabels[3], value: String(1 + seed % 6), note: "需要责任人关注", tone: "risk" as const },
  ];
}

export function getCatalogPageSpecialization(route: ResolvedProductRoute): BusinessPageSpecialization {
  const title = titleOf(route);
  const selection = selectBlueprint(route);
  const blueprint: CatalogBlueprint = blueprints[selection.blueprint];
  const noun = selection.noun ?? nounOf(title);
  const rows = makeRows(title, blueprint);
  const actionMode = selection.actionMode ?? blueprint.actionMode ?? "form";
  const action = selection.action ?? pageActionOverrides[title] ?? (actionMode === "refresh" ? `刷新${title}` : actionMode === "query" ? `查询${noun}` : actionMode === "export" ? `导出${noun}` : actionMode === "feedback" ? "提交试用反馈" : `${blueprint.actionVerb}${noun}`);
  const contextValues = [
    { label: blueprint.metricLabels[0], value: `${36 + stableCode(title) % 68}`, note: `覆盖${noun}的当前业务范围`, progress: 86, tone: "good" as const },
    { label: blueprint.metricLabels[1], value: `${4 + stableCode(title) % 13}`, note: "按责任和期限持续推进", progress: 64, tone: "warn" as const },
    { label: blueprint.metricLabels[2], value: `${82 + stableCode(title) % 17}%`, note: "以业务事实计算完成情况", progress: 82, tone: "info" as const },
    { label: blueprint.metricLabels[3], value: `${1 + stableCode(title) % 6}`, note: "可下钻到具体业务对象", progress: 28, tone: "risk" as const },
  ];

  return {
    definitionId: `catalog:${blueprint.id}`,
    title,
    description: `围绕${title}，${blueprint.purpose}`,
    icon: blueprint.icon,
    recordNoun: noun,
    primaryAction: action,
    primaryActionMode: actionMode,
    layout: blueprint.layout,
    context: {
      kicker: `${blueprintContextLabels[blueprint.id] ?? "业务运营管理"} · ${route.area.capability}`,
      title: `${title}业务态势`,
      summary: blueprint.purpose,
      items: contextValues,
    },
    metrics: makeMetrics(title, blueprint),
    views: [`全部${noun}`, blueprint.statuses[0][0], blueprint.statuses[1][0], "需要关注"],
    columns: [`${noun}编号`, ...blueprint.fields.map((field) => field.label)],
    rows,
    attentionTitle: `${title}待处理风险`,
    attentionItems: [
      { title: `${rows[1].id} 需要在今日完成处理`, detail: `${rows[1].cells[0]}存在时间、状态或跨部门依赖，需要责任人确认业务结果。`, owner: rows[1].owner, tone: "warn" },
      { title: `${rows[3].id} 存在业务风险`, detail: `${rows[3].cells[0]}的当前证据或约束尚未完整，建议进入详情下钻处理。`, owner: rows[3].owner, tone: "risk" },
    ],
    formFields: [...blueprint.fields, ownerField, remarkField],
    cellFields: blueprint.fields.map((field) => field.name),
    workflow: blueprint.workflow.map(([label, detail]) => ({ label, detail })),
  };
}
