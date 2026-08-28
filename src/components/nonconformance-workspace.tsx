"use client";

import { useState, type FormEvent } from "react";

import type { Nonconformance, NonconformanceAction, NonconformancePage, NonconformanceView } from "@/lib/nonconformance-contracts";
import { loadNonconformanceDetail, NonconformanceClientError, refreshNonconformances, submitNonconformanceAction } from "@/services/nonconformance-client-service";
import type { NonconformancePageData } from "@/services/nonconformance-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsDrawerHost, GsInput, GsModalHost, GsPagination, GsTextArea } from "./ui";

const statusLabels: Record<Nonconformance["status"], string> = {
  OPEN: "待评审", REVIEWED: "待处置", ACTION_REQUIRED: "待计划措施", ACTION_IN_PROGRESS: "措施执行中",
  VERIFICATION_PENDING: "待有效性验证", CLOSED: "已关闭",
};
const severityLabels = { LOW: "低", MEDIUM: "中", HIGH: "高", CRITICAL: "严重" } as const;
const sourceLabels = { INCOMING_INSPECTION: "来料检验", FINAL_INSPECTION: "完工检验" } as const;
const dispositionLabels = { RETURN_TO_SUPPLIER: "退回供应商", REWORK: "返工", SCRAP: "报废", CONCESSION: "让步接收", SORTING: "筛选", OTHER: "其他" } as const;
const actionLabels = { REVIEW: "提交评审", DISPOSE: "提交处置决定", PLAN_ACTION: "制定纠正措施", COMPLETE_ACTION: "提交完成证据", VERIFY: "有效性验证", REOPEN: "重新打开" } as const;
const viewCopy: Record<NonconformanceView, { title: string; subtitle: string; icon: string }> = {
  records: { title: "不合格记录", subtitle: "统一查看检验自动形成的不合格事实、责任和证据", icon: "fact_check" },
  reviews: { title: "不合格评审", subtitle: "处理待评审、待处置和待验证的质量决策", icon: "rule" },
  actions: { title: "纠正与预防措施", subtitle: "跟踪根因、责任人、期限、完成证据与有效性", icon: "published_with_changes" },
};

type ActionName = NonconformanceAction["action"];
type FormValues = { severity: keyof typeof severityLabels; capaRequired: "是" | "否"; immediateContainment: string; reviewConclusion: string;
  dispositionType: keyof typeof dispositionLabels; dispositionDecision: string; dispositionEvidence: string; dispositionOwner: string;
  rootCause: string; correctiveAction: string; actionOwner: string; actionDueDate: string; actionCompletionEvidence: string;
  effective: "是" | "否"; verificationConclusion: string; reason: string };
const initialForm: FormValues = { severity: "MEDIUM", capaRequired: "否", immediateContainment: "", reviewConclusion: "",
  dispositionType: "REWORK", dispositionDecision: "", dispositionEvidence: "", dispositionOwner: "", rootCause: "", correctiveAction: "",
  actionOwner: "", actionDueDate: "", actionCompletionEvidence: "", effective: "是", verificationConclusion: "", reason: "" };

function errorText(error: unknown) {
  if (error instanceof NonconformanceClientError) return `${error.message}${error.requestId ? `（请求 ${error.requestId}）` : ""}`;
  return error instanceof Error ? error.message : "操作失败，请刷新后重试";
}
function formatTime(value: string | null) { return value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—"; }
function actionFor(item: Nonconformance): ActionName | null {
  return item.status === "OPEN" ? "REVIEW" : item.status === "REVIEWED" ? "DISPOSE" : item.status === "ACTION_REQUIRED" ? "PLAN_ACTION"
    : item.status === "ACTION_IN_PROGRESS" ? "COMPLETE_ACTION" : item.status === "VERIFICATION_PENDING" ? "VERIFY" : item.status === "CLOSED" ? "REOPEN" : null;
}
function canAct(action: ActionName | null, page: NonconformancePage) {
  if (!action) return false;
  if (action === "REVIEW" || action === "DISPOSE" || action === "REOPEN") return page.canReview;
  if (action === "VERIFY") return page.canVerify;
  return page.canExecuteAction;
}

function ActionDialog({ item, action, pending, error, onClose, onSubmit }: { item: Nonconformance; action: ActionName; pending: boolean; error: string; onClose: () => void; onSubmit: (action: NonconformanceAction) => void }) {
  const [values, setValues] = useState<FormValues>(initialForm);
  const set = (key: keyof FormValues, value: string) => setValues((current) => ({ ...current, [key]: value }));
  function submit(event: FormEvent) {
    event.preventDefault();
    const base = { expectedVersion: item.version };
    const payload: NonconformanceAction = action === "REVIEW" ? { action, ...base, severity: values.severity, capaRequired: values.capaRequired === "是", immediateContainment: values.immediateContainment, reviewConclusion: values.reviewConclusion }
      : action === "DISPOSE" ? { action, ...base, dispositionType: values.dispositionType, dispositionDecision: values.dispositionDecision, dispositionEvidence: values.dispositionEvidence, dispositionOwner: values.dispositionOwner }
      : action === "PLAN_ACTION" ? { action, ...base, rootCause: values.rootCause, correctiveAction: values.correctiveAction, actionOwner: values.actionOwner, actionDueDate: values.actionDueDate }
      : action === "COMPLETE_ACTION" ? { action, ...base, actionCompletionEvidence: values.actionCompletionEvidence }
      : action === "VERIFY" ? { action, ...base, effective: values.effective === "是", verificationConclusion: values.verificationConclusion }
      : { action, ...base, reason: values.reason };
    onSubmit(payload);
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog nonconformanceDialog" role="dialog" aria-modal="true" aria-labelledby="nonconformance-action-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="rule" size={22}/></span><div><h2 id="nonconformance-action-title">{actionLabels[action]}</h2><p>{item.caseNumber} · 当前版本 {item.version}</p></div><GsButton className="iconButton" aria-label="关闭不合格操作" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <form onSubmit={submit}><div className="formGrid">
      {action === "REVIEW" ? <><label className="formField"><span>严重度<em>必填</em></span><RoundedSelect ariaLabel="严重度" size="field" options={Object.entries(severityLabels).map(([value, label]) => ({ value, label }))} value={values.severity} onValueChange={(value) => set("severity", value)}/></label><label className="formField"><span>是否需要 CAPA<em>必填</em></span><RoundedSelect ariaLabel="是否需要 CAPA" size="field" options={["否", "是"]} value={values.capaRequired} onValueChange={(value) => set("capaRequired", value)}/></label><label className="formField formFieldFull"><span>即时遏制措施<em>必填</em></span><GsTextArea rows={3} maxLength={1000} value={values.immediateContainment} onChange={(event) => set("immediateContainment", event.target.value)}/></label><label className="formField formFieldFull"><span>评审结论<em>必填</em></span><GsTextArea rows={3} maxLength={1000} value={values.reviewConclusion} onChange={(event) => set("reviewConclusion", event.target.value)}/></label></> : null}
      {action === "DISPOSE" ? <><label className="formField"><span>处置类型<em>必填</em></span><RoundedSelect ariaLabel="处置类型" size="field" options={Object.entries(dispositionLabels).map(([value, label]) => ({ value, label }))} value={values.dispositionType} onValueChange={(value) => set("dispositionType", value)}/></label><label className="formField"><span>处置责任人<em>必填</em></span><GsInput maxLength={120} value={values.dispositionOwner} onChange={(event) => set("dispositionOwner", event.target.value)}/></label><label className="formField formFieldFull"><span>处置决定<em>必填</em></span><GsTextArea rows={3} maxLength={1000} value={values.dispositionDecision} onChange={(event) => set("dispositionDecision", event.target.value)}/></label><label className="formField formFieldFull"><span>执行证据<em>必填</em></span><GsTextArea rows={3} maxLength={1000} value={values.dispositionEvidence} onChange={(event) => set("dispositionEvidence", event.target.value)}/></label></> : null}
      {action === "PLAN_ACTION" ? <><label className="formField"><span>措施责任人<em>必填</em></span><GsInput maxLength={120} value={values.actionOwner} onChange={(event) => set("actionOwner", event.target.value)}/></label><label className="formField"><span>到期日<em>必填</em></span><GsInput type="date" min={new Date().toISOString().slice(0, 10)} value={values.actionDueDate} onChange={(event) => set("actionDueDate", event.target.value)}/></label><label className="formField formFieldFull"><span>根因分析<em>必填</em></span><GsTextArea rows={3} maxLength={1000} value={values.rootCause} onChange={(event) => set("rootCause", event.target.value)}/></label><label className="formField formFieldFull"><span>纠正措施<em>必填</em></span><GsTextArea rows={3} maxLength={1000} value={values.correctiveAction} onChange={(event) => set("correctiveAction", event.target.value)}/></label></> : null}
      {action === "COMPLETE_ACTION" ? <label className="formField formFieldFull"><span>完成证据<em>必填</em></span><GsTextArea rows={4} maxLength={1000} value={values.actionCompletionEvidence} onChange={(event) => set("actionCompletionEvidence", event.target.value)}/></label> : null}
      {action === "VERIFY" ? <><label className="formField"><span>措施是否有效<em>必填</em></span><RoundedSelect ariaLabel="措施是否有效" size="field" options={["是", "否"]} value={values.effective} onValueChange={(value) => set("effective", value)}/></label><label className="formField formFieldFull"><span>验证结论<em>必填</em></span><GsTextArea rows={4} maxLength={1000} value={values.verificationConclusion} onChange={(event) => set("verificationConclusion", event.target.value)}/></label></> : null}
      {action === "REOPEN" ? <label className="formField formFieldFull"><span>重新打开原因<em>必填</em></span><GsTextArea rows={4} minLength={4} maxLength={1000} value={values.reason} onChange={(event) => set("reason", event.target.value)}/></label> : null}
    </div><div className="mrpRunTruthNotice"><MaterialIcon name="info" size={18}/><span><strong>质量决策边界</strong>本动作只记录评审、处置与验证证据；退货、返工、报废和库存移动仍需在对应业务模块执行。</span></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>提交时校验版本并记录责任人、时间与请求编号</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">确认提交</GsButton></div></footer></form>
  </section></GsModalHost>;
}

function DetailDrawer({ item, page, loading, onClose, onAction }: { item: Nonconformance; page: NonconformancePage; loading: boolean; onClose: () => void; onAction: (action: ActionName) => void }) {
  const action = actionFor(item);
  return <GsDrawerHost size={600} onClose={onClose}><aside className="recordDrawer nonconformanceDrawer" role="dialog" aria-modal="true" aria-labelledby="nonconformance-detail-title" onMouseDown={(event) => event.stopPropagation()}><header className="recordDrawerHeader"><div><h2 id="nonconformance-detail-title">{item.caseNumber}</h2><p>{sourceLabels[item.sourceType]} · {item.inspectionNumber}</p></div><GsButton className="iconButton" aria-label="关闭不合格详情" onClick={onClose} htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <div className="recordDrawerBody"><section className="salesOrderSummary"><div><small>状态</small><strong>{statusLabels[item.status]}</strong></div><div><small>严重度</small><strong>{item.severity ? severityLabels[item.severity] : "待评审"}</strong></div><div><small>不合格数量</small><strong>{item.nonconformingQuantity} {item.unit}</strong></div><div><small>版本 / 更新</small><strong>v{item.version} · {formatTime(item.updatedAt)}</strong></div></section>
      <section className="drawerSection"><header><h3>来源与缺陷事实</h3></header><div className="detailLedger"><div><span>来源单据</span><strong>{item.sourceDocumentNumber}</strong></div><div><span>关联订单</span><strong>{item.orderNumber}</strong></div><div><span>物料</span><strong>{item.materialCode} · {item.materialName}</strong></div><div><span>供应商</span><strong>{item.supplierName ?? "不适用"}</strong></div></div><p>{item.defectDescription}</p></section>
      {item.reviewConclusion ? <section className="drawerSection"><header><h3>评审与处置</h3></header><div className="detailLedger"><div><span>是否 CAPA</span><strong>{item.capaRequired ? "是" : "否"}</strong></div><div><span>处置类型</span><strong>{item.dispositionType ? dispositionLabels[item.dispositionType] : "待处置"}</strong></div><div><span>处置责任人</span><strong>{item.dispositionOwner ?? "—"}</strong></div><div><span>处置证据</span><strong>{item.dispositionEvidence ?? "—"}</strong></div></div><p><strong>即时遏制：</strong>{item.immediateContainment}</p><p><strong>评审结论：</strong>{item.reviewConclusion}</p></section> : null}
      {item.correctiveAction ? <section className="drawerSection"><header><h3>纠正措施</h3></header><div className="detailLedger"><div><span>责任人</span><strong>{item.actionOwner}</strong></div><div><span>到期日</span><strong>{item.actionDueDate}{item.overdue ? " · 已逾期" : ""}</strong></div><div><span>完成证据</span><strong>{item.actionCompletionEvidence ?? "待提交"}</strong></div><div><span>验证结果</span><strong>{item.verificationEffective === null ? "待验证" : item.verificationEffective ? "有效" : "无效"}</strong></div></div><p><strong>根因：</strong>{item.rootCause}</p><p><strong>措施：</strong>{item.correctiveAction}</p></section> : null}
      <section className="drawerSection nonconformanceTimeline"><header><h3>责任与状态证据</h3><span>{loading ? "加载中" : `${item.events.length} 条`}</span></header>{item.events.length ? item.events.map((event) => <article key={event.id}><span className="timelineDot"/><div><strong>{actionLabels[event.action as keyof typeof actionLabels] ?? event.action}</strong><small>{event.fromStatus ? `${statusLabels[event.fromStatus as Nonconformance["status"]] ?? event.fromStatus} → ` : ""}{statusLabels[event.toStatus as Nonconformance["status"]] ?? event.toStatus}</small><p>{event.reason ?? "无补充说明"}</p><code>{event.actorUsername} · {formatTime(event.occurredAt)} · {event.requestId}</code></div></article>) : <p>暂无事件证据。</p>}</section>
    </div><footer className="recordDrawerFooter"><span>关闭时间：{formatTime(item.closedAt)}</span>{action && canAct(action, page) ? <GsButton intent="primary" onClick={() => onAction(action)} htmlType="button">{actionLabels[action]}</GsButton> : null}</footer>
  </aside></GsDrawerHost>;
}

export function NonconformanceWorkspace({ initialData }: { initialData: NonconformancePageData }) {
  if (initialData.source === "unavailable") return <div className="businessUnavailable" role="alert"><MaterialIcon name="cloud_off" size={32}/><strong>{initialData.message}</strong><p>请求编号：{initialData.requestId}。页面不会回退到 Mock 数据。</p></div>;
  return <AvailableNonconformanceWorkspace initialData={initialData}/>;
}

function AvailableNonconformanceWorkspace({ initialData }: { initialData: Extract<NonconformancePageData, { source: "backend" }> }) {
  const view = initialData.view;
  const [page, setPage] = useState(initialData.page);
  const [query, setQuery] = useState(""); const [status, setStatus] = useState("ALL"); const [severity, setSeverity] = useState("ALL"); const [sourceType, setSourceType] = useState("ALL"); const [overdue, setOverdue] = useState(false);
  const [pending, setPending] = useState(false); const [notice, setNotice] = useState(""); const [error, setError] = useState("");
  const [detail, setDetail] = useState<Nonconformance | null>(null); const [detailLoading, setDetailLoading] = useState(false); const [activeAction, setActiveAction] = useState<ActionName | null>(null); const [actionRequestId, setActionRequestId] = useState<string | null>(null);
  const filters = (nextPage = 0, nextSize = page.size) => ({ query: query.trim() || undefined, status, severity, sourceType, overdue, page: nextPage, size: nextSize });
  async function load(nextPage = 0, nextSize = page.size, message = "") { setPending(true); setError(""); try { setPage(await refreshNonconformances(view, filters(nextPage, nextSize))); setNotice(message); } catch (caught) { setError(errorText(caught)); } finally { setPending(false); } }
  async function open(item: Nonconformance) { setDetail(item); setDetailLoading(true); setError(""); try { setDetail(await loadNonconformanceDetail(item.id)); } catch (caught) { setError(errorText(caught)); } finally { setDetailLoading(false); } }
  async function submit(action: NonconformanceAction) { if (!detail) return; const requestId = actionRequestId ?? crypto.randomUUID(); setActionRequestId(requestId); setPending(true); setError(""); try { const updated = await submitNonconformanceAction(detail.id, action, requestId); setDetail(updated); setActiveAction(null); setActionRequestId(null); setNotice(`${actionLabels[action.action]}成功`); setPage(await refreshNonconformances(view, filters(page.page))); } catch (caught) { setError(errorText(caught)); } finally { setPending(false); } }
  const copy = viewCopy[view]; const summary = page.summary;
  return <div className="businessPage nonconformancePage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name={copy.icon} size={23}/></span><div><h2>{copy.title}</h2><p>{copy.subtitle}</p></div></div><div className="pageHeadingActions"><GsButton onClick={() => void load(page.page, page.size, "质量事实已刷新")} loading={pending} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新</GsButton></div></header>
    {notice ? <div className="formSuccess" role="status">{notice}</div> : null}{error ? <div className="formError" role="alert">{error}</div> : null}
    <section className="nonconformanceMetrics" aria-label="不合格概览"><article><span>待评审 / 待处置</span><strong>{summary.open + summary.reviewed}</strong></article><article><span>措施处理中</span><strong>{summary.actionRequired + summary.actionInProgress}</strong></article><article><span>待验证</span><strong>{summary.verificationPending}</strong></article><article className={summary.overdue ? "isRisk" : ""}><span>逾期 CAPA</span><strong>{summary.overdue}</strong></article><article><span>已关闭</span><strong>{summary.closed}</strong></article></section>
    <section className="workspaceRoleBoundary"><MaterialIcon name="verified_user" size={20}/><div><strong>后端事实 · 工作区隔离 · 乐观锁防并发覆盖</strong><p>检验完成且存在不合格数量时自动建档；每个动作保留责任人、时间、请求编号和版本证据。</p></div></section>
    <section className="businessLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索不合格记录" maxLength={120} value={query} onChange={(event) => setQuery(event.target.value)} placeholder="编号、检验、订单、物料或供应商"/></div><RoundedSelect ariaLabel="筛选状态" options={[{ value: "ALL", label: "全部状态" }, ...Object.entries(statusLabels).map(([value, label]) => ({ value, label }))]} value={status} onValueChange={setStatus}/><RoundedSelect ariaLabel="筛选严重度" options={[{ value: "ALL", label: "全部严重度" }, ...Object.entries(severityLabels).map(([value, label]) => ({ value, label }))]} value={severity} onValueChange={setSeverity}/><RoundedSelect ariaLabel="筛选来源" options={[{ value: "ALL", label: "全部来源" }, ...Object.entries(sourceLabels).map(([value, label]) => ({ value, label }))]} value={sourceType} onValueChange={setSourceType}/><RoundedSelect ariaLabel="筛选逾期" options={[{ value: "false", label: "全部时效" }, { value: "true", label: "仅看逾期" }]} value={String(overdue)} onValueChange={(value) => setOverdue(value === "true")}/><div className="businessTableTools"><GsButton onClick={() => { setQuery(""); setStatus("ALL"); setSeverity("ALL"); setSourceType("ALL"); setOverdue(false); }} disabled={pending} htmlType="button">重置</GsButton><GsButton intent="primary" onClick={() => void load(0, page.size, "筛选条件已应用")} loading={pending} htmlType="button"><MaterialIcon name="filter_alt" size={17}/>查询</GsButton></div></div>
      <div className="nonconformanceTable" role="table"><div className="nonconformanceTableHeader" role="row"><span>记录 / 来源</span><span>物料与缺陷</span><span>风险 / 状态</span><span>责任与期限</span><span>更新时间</span></div>{page.items.length ? page.items.map((item) => <GsButton className="nonconformanceTableRow" role="row" key={item.id} onClick={() => void open(item)} htmlType="button"><span><strong>{item.caseNumber}</strong><small>{sourceLabels[item.sourceType]} · {item.inspectionNumber}</small></span><span><strong>{item.materialCode} · {item.materialName}</strong><small>{item.defectDescription}</small></span><span><strong className={`businessStatus ${item.overdue ? "businessStatusDanger" : ""}`}>{item.overdue ? "已逾期 · " : ""}{statusLabels[item.status]}</strong><small>{item.severity ? `${severityLabels[item.severity]}风险` : "待定级"} · {item.nonconformingQuantity} {item.unit}</small></span><span><strong>{item.actionOwner ?? item.dispositionOwner ?? "待分派"}</strong><small>{item.actionDueDate ?? "暂无措施期限"}</small></span><span><strong>{formatTime(item.updatedAt)}</strong><small>v{item.version} · 查看证据</small></span></GsButton>) : <div className="businessEmptyState"><MaterialIcon name="fact_check" size={30}/><strong>当前队列没有不合格记录</strong><p>完成检验且不合格数量大于零后，系统会自动建立正式记录；不会用 Mock 填充。</p></div>}</div>
      <GsPagination current={page.page + 1} pageSize={page.size} total={page.totalElements} disabled={pending} onChange={(next, size) => void load(next - 1, size)} showTotal={(total) => `共 ${total} 条`}/>
    </section>
    <section className="rolePermissionLimit"><MaterialIcon name="account_tree" size={19}/><div><strong>质量决定不等于业务执行</strong><p>处置记录提供审批和证据边界；采购退货、生产返工、报废出库等仍由各事实模块执行并独立审计。</p></div></section>
    {detail ? <DetailDrawer item={detail} page={page} loading={detailLoading} onClose={() => setDetail(null)} onAction={setActiveAction}/> : null}
    {detail && activeAction ? <ActionDialog key={`${detail.id}-${activeAction}-${detail.version}`} item={detail} action={activeAction} pending={pending} error={error} onClose={() => { setActiveAction(null); setActionRequestId(null); setError(""); }} onSubmit={(action) => void submit(action)}/> : null}
  </div>;
}
