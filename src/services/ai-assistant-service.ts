export type AiAssistantContext = {
  pathname: string;
  pageTitle: string;
  pageDescription: string;
  workspace: string;
  metrics: Array<{ label: string; value: string; note: string }>;
  alerts: Array<{ title: string; detail: string; owner: string }>;
};

export type AiAssistantSource = {
  label: string;
  detail: string;
  href: string;
};

export type AiAssistantResponse = {
  summary: string;
  insights: string[];
  sources: AiAssistantSource[];
  actions: Array<{ label: string; href: string }>;
  confidence: "高" | "中";
  mode: "mock";
  generatedAt: string;
};

export type AskAiAssistantInput = {
  question: string;
  context: AiAssistantContext;
};

function includesAny(value: string, keywords: string[]) {
  return keywords.some((keyword) => value.includes(keyword));
}

// 当前为前端契约 Mock。未来由 guanseq-ai 适配器替换，组件无需直接接触模型 SDK。
export async function askManufacturingAssistant({ question, context }: AskAiAssistantInput): Promise<AiAssistantResponse> {
  const normalizedQuestion = question.trim();
  if (!normalizedQuestion) throw new Error("请输入需要分析的问题。");

  await new Promise((resolve) => setTimeout(resolve, 720));

  const generatedAt = new Date().toISOString();
  const firstAlert = context.alerts[0];
  const commonSource: AiAssistantSource = {
    label: context.pageTitle,
    detail: `${context.workspace} · 当前页面业务数据`,
    href: context.pathname,
  };

  if (includesAny(normalizedQuestion, ["风险", "异常", "逾期", "问题"])) {
    return {
      summary: firstAlert ? `当前最需要关注的是“${firstAlert.title}”。建议先核实影响范围，再由责任人确认处置方案。` : "当前页面暂未发现高优先级异常，建议继续关注交付、质量与产能变化。",
      insights: firstAlert
        ? [firstAlert.detail, `当前责任归属：${firstAlert.owner}。`, "建议将处理结果回写业务单据，并保留依据与审批记录。"]
        : ["当前指标未触发高风险条件。", "建议核对未来七天的交付承诺与物料齐套情况。"],
      sources: [commonSource, { label: "风险中心", detail: "跨订单、供应、生产与质量风险", href: "/risks" }],
      actions: [{ label: "查看风险中心", href: "/risks" }, { label: "查看 MRP 建议", href: "/planning/mrp/recommendations" }],
      confidence: "高",
      mode: "mock",
      generatedAt,
    };
  }

  if (includesAny(normalizedQuestion, ["日报", "摘要", "总结", "经营"])) {
    const metricSummary = context.metrics.slice(0, 3).map((metric) => `${metric.label} ${metric.value}`).join("，");
    return {
      summary: `${context.workspace}当前经营摘要：${metricSummary || "关键业务指标运行平稳"}。`,
      insights: [
        "交付执行仍是今日管理重点，应优先处理影响客户承诺的事项。",
        firstAlert ? `首要风险：${firstAlert.title}。` : "当前没有新增高优先级风险。",
        "建议班前会确认责任人、完成时点和业务证据。",
      ],
      sources: [commonSource, { label: "制造经营总览", detail: "订单、生产、质量与交付综合指标", href: "/" }],
      actions: [{ label: "打开经营总览", href: "/" }, { label: "查看经营分析", href: "/analytics/operations" }],
      confidence: "高",
      mode: "mock",
      generatedAt,
    };
  }

  if (includesAny(normalizedQuestion, ["产能", "排程", "计划", "工单"])) {
    return {
      summary: "建议先处理瓶颈产能与关键物料约束，再确认插单是否影响已承诺订单。当前结果仅用于计划员决策参考。",
      insights: ["核对机加车间未来七天负荷与可用班次。", "验证关键物料齐套时间是否覆盖计划开工时间。", "调整后重新检查订单承诺日期与质量检验能力。"],
      sources: [commonSource, { label: "产能计划", detail: "车间负荷、班次与能力约束", href: "/planning/capacity" }],
      actions: [{ label: "查看产能计划", href: "/planning/capacity" }, { label: "查看生产工单", href: "/production/work-orders/operations" }],
      confidence: "中",
      mode: "mock",
      generatedAt,
    };
  }

  return {
    summary: `我已结合“${context.pageTitle}”页面上下文整理建议。当前为前端演示结果，需要业务人员结合真实单据进一步确认。`,
    insights: [context.pageDescription, "优先核对状态、责任人、交期和关联业务证据。", "涉及下达、变更、审批或删除的动作必须由用户确认。"],
    sources: [commonSource],
    actions: [{ label: `返回${context.pageTitle}`, href: context.pathname }],
    confidence: "中",
    mode: "mock",
    generatedAt,
  };
}
