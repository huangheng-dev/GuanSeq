"use client";

import { type FormEvent, useMemo, useState } from "react";

import type { EquipmentAsset, EquipmentMaintenanceGeneration, EquipmentMaintenancePlan, EquipmentMaintenancePlanAction,
  EquipmentMaintenancePlanPage, EquipmentWorkOrderPriority } from "@/lib/contracts";
import type { EquipmentMaintenancePlanPageData } from "@/services/equipment-maintenance-plan-server-service";
import { EquipmentMaintenancePlanClientError, loadEquipmentMaintenancePlanDetail, refreshEquipmentMaintenancePlans,
  submitEquipmentMaintenancePlanMutation } from "@/services/equipment-maintenance-plan-client-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsDrawer, GsInput, GsModalHost, GsTextArea } from "./ui";

const typeLabels = { INSPECTION: "周期点检", PREVENTIVE_MAINTENANCE: "周期保养" } as const;
const priorityLabels: Record<EquipmentWorkOrderPriority, string> = { LOW: "低", MEDIUM: "中", HIGH: "高", URGENT: "紧急" };
const actionLabels: Record<EquipmentMaintenancePlanAction, string> = { ACTIVATE: "启用模板", INACTIVATE: "停用模板" };
const eventLabels = { CREATED: "建立模板", ACTIVATED: "启用模板", INACTIVATED: "停用模板" } as const;
const statusLabels = { ACTIVE: "已启用", INACTIVE: "已停用" } as const;
const generationLabels = { DUE: "应生成", UPCOMING: "未到生成日", INACTIVE: "已停用" } as const;

function localDate(date = new Date()) { return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 10); }
function dateText(value: string) { return new Date(value).toLocaleString("zh-CN", { hour12: false }); }
function errorText(error: unknown) {
  if (error instanceof EquipmentMaintenancePlanClientError) return error.requestId ? `${error.message}（请求编号：${error.requestId}）` : error.message;
  return error instanceof Error ? error.message : "周期维护计划操作失败";
}

function PlanCreateDialog({ assets, onClose, onSaved }: { assets: EquipmentAsset[]; onClose: () => void; onSaved: (plan: EquipmentMaintenancePlan) => void }) {
  const candidates = assets.filter((asset) => asset.operatingStatus !== "INACTIVE");
  const assetOptions = candidates.map((asset) => `${asset.assetCode} · ${asset.assetName}`);
  const [assetLabel, setAssetLabel] = useState(assetOptions[0] ?? "");
  const [planCode, setPlanCode] = useState(""); const [name, setName] = useState("");
  const [workTypeLabel, setWorkTypeLabel] = useState("周期保养"); const [description, setDescription] = useState("");
  const [priorityLabel, setPriorityLabel] = useState("中"); const [intervalDays, setIntervalDays] = useState("30");
  const [leadDays, setLeadDays] = useState("3"); const [firstDueDate, setFirstDueDate] = useState(localDate());
  const [plannedStartTime, setPlannedStartTime] = useState("08:30"); const [dueTime, setDueTime] = useState("11:30");
  const [assignee, setAssignee] = useState(candidates[0]?.responsiblePerson ?? ""); const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  const asset = candidates[assetOptions.indexOf(assetLabel)];
  async function submit(event: FormEvent) {
    event.preventDefault();
    const interval = Number(intervalDays); const lead = Number(leadDays);
    if (!asset || !/^[A-Z0-9][A-Z0-9_-]{1,39}$/.test(planCode) || !name.trim() || description.trim().length < 4 || !assignee.trim() || reason.trim().length < 4) {
      setError("请选择设备，并填写合法模板编码、名称、作业要求、责任人和至少 4 个字符的创建原因。"); return;
    }
    if (!Number.isInteger(interval) || interval < 1 || interval > 3650 || !Number.isInteger(lead) || lead < 0 || lead > 365) {
      setError("周期天数应为 1–3650，提前期应为 0–365 的整数。"); return;
    }
    if (plannedStartTime > dueTime) { setError("计划开始时间不能晚于要求完成时间。"); return; }
    const workType = workTypeLabel === "周期点检" ? "INSPECTION" : "PREVENTIVE_MAINTENANCE";
    const priority = Object.entries(priorityLabels).find(([, label]) => label === priorityLabel)?.[0] as EquipmentWorkOrderPriority;
    setPending(true); setError("");
    try {
      const saved = await submitEquipmentMaintenancePlanMutation({ operation: "createPlan", planCode, name: name.trim(), workType,
        assetId: asset.id, description: description.trim(), priority, intervalDays: interval, leadDays: lead, firstDueDate,
        plannedStartTime, dueTime, assignee: assignee.trim(), reason: reason.trim(), assetExpectedVersion: asset.version });
      if ("planCode" in saved) onSaved(saved);
    } catch (failure) { setError(errorText(failure)); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentPlanDialog" role="dialog" aria-modal="true" aria-labelledby="equipment-plan-create-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="event_repeat" size={22}/></span><div><h2 id="equipment-plan-create-title">新建周期维护模板</h2><p>周期参数创建后不可静默改写；换周期时停用旧模板再新建。</p></div><GsButton className="iconButton" aria-label="关闭周期维护模板表单" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    {candidates.length ? <form onSubmit={submit}><div className="formGrid equipmentFormGrid">
      <label className="formField"><span>模板编码<em>必填</em></span><GsInput value={planCode} maxLength={40} placeholder="PLAN-CNC-MONTHLY" onChange={(event) => setPlanCode(event.target.value.toUpperCase())}/></label>
      <label className="formField"><span>任务类型<em>必填</em></span><RoundedSelect ariaLabel="周期任务类型" options={["周期保养", "周期点检"]} value={workTypeLabel} onValueChange={setWorkTypeLabel}/></label>
      <label className="formField formFieldFull"><span>模板名称<em>必填</em></span><GsInput value={name} maxLength={160} onChange={(event) => setName(event.target.value)} placeholder="例如 加工中心月度润滑保养"/></label>
      <label className="formField formFieldFull"><span>适用设备<em>必填</em></span><RoundedSelect ariaLabel="周期模板适用设备" options={assetOptions} value={assetLabel} onValueChange={(value) => { setAssetLabel(value); const selected = candidates[assetOptions.indexOf(value)]; if (selected) setAssignee(selected.responsiblePerson); }}/></label>
      <label className="formField"><span>周期天数<em>必填</em></span><GsInput type="number" min="1" max="3650" step="1" value={intervalDays} onChange={(event) => setIntervalDays(event.target.value)}/></label>
      <label className="formField"><span>提前生成天数<em>必填</em></span><GsInput type="number" min="0" max="365" step="1" value={leadDays} onChange={(event) => setLeadDays(event.target.value)}/></label>
      <label className="formField"><span>首次到期日<em>必填</em></span><GsInput type="date" value={firstDueDate} onChange={(event) => setFirstDueDate(event.target.value)}/></label>
      <label className="formField"><span>优先级<em>必填</em></span><RoundedSelect ariaLabel="周期任务优先级" options={Object.values(priorityLabels)} value={priorityLabel} onValueChange={setPriorityLabel}/></label>
      <label className="formField"><span>计划开始<em>必填</em></span><GsInput type="time" value={plannedStartTime} onChange={(event) => setPlannedStartTime(event.target.value)}/></label>
      <label className="formField"><span>要求完成<em>必填</em></span><GsInput type="time" value={dueTime} onChange={(event) => setDueTime(event.target.value)}/></label>
      <label className="formField formFieldFull"><span>责任人<em>必填</em></span><GsInput value={assignee} maxLength={80} onChange={(event) => setAssignee(event.target.value)}/></label>
      <label className="formField formFieldFull"><span>作业要求<em>必填</em></span><GsTextArea rows={3} maxLength={1000} value={description} onChange={(event) => setDescription(event.target.value)}/></label>
      <label className="formField formFieldFull"><span>创建原因<em>必填</em></span><GsTextArea rows={2} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/><small>原因与请求编号进入模板不可变事件。</small></label>
    </div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="lock" size={16}/>周期、提前期与首次到期日冻结</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">启用模板</GsButton></div></footer></form> : <div className="businessEmptyState"><strong>没有可用设备</strong><p>已停用设备不能建立周期维护模板。</p></div>}
  </section></GsModalHost>;
}

function GenerateDialog({ onClose, onGenerated }: { onClose: () => void; onGenerated: (run: EquipmentMaintenanceGeneration) => void }) {
  const [asOfDate, setAsOfDate] = useState(localDate()); const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault(); if (reason.trim().length < 4) { setError("请填写至少 4 个字符的生成原因。"); return; }
    setPending(true); setError("");
    try { const result = await submitEquipmentMaintenancePlanMutation({ operation: "generateDue", asOfDate, reason: reason.trim() }); if ("generatedCount" in result) onGenerated(result); }
    catch (failure) { setError(errorText(failure)); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentGenerateDialog" role="dialog" aria-modal="true" aria-labelledby="equipment-generate-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="playlist_add_check" size={22}/></span><div><h2 id="equipment-generate-title">生成到期任务</h2><p>按模板提前期计算到期实例；模板 + 到期日保证唯一。</p></div><GsButton className="iconButton" aria-label="关闭到期任务生成" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>生成截止日期<em>必填</em></span><GsInput type="date" value={asOfDate} onChange={(event) => setAsOfDate(event.target.value)}/><small>首版由人员确认日期并触发，不依赖后台调度服务。</small></label><label className="formField formFieldFull"><span>生成原因<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="例如 班前确认并生成本周到期维护任务"/></label></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="fingerprint" size={16}/>同一请求可安全重试</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">确认生成</GsButton></div></footer></form></section></GsModalHost>;
}

function PlanActionDialog({ plan, action, onClose, onSaved }: { plan: EquipmentMaintenancePlan; action: EquipmentMaintenancePlanAction; onClose: () => void; onSaved: (plan: EquipmentMaintenancePlan) => void }) {
  const [reason, setReason] = useState(""); const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent) { event.preventDefault(); if (reason.trim().length < 4) { setError("请填写至少 4 个字符的状态变更原因。"); return; } setPending(true); setError(""); try { const result = await submitEquipmentMaintenancePlanMutation({ operation: "planAction", id: plan.id, action, reason: reason.trim(), expectedVersion: plan.version }); if ("planCode" in result) onSaved(result); } catch (failure) { setError(errorText(failure)); setPending(false); } }
  return <GsModalHost zIndex={1100} onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentGenerateDialog" role="dialog" aria-modal="true" aria-labelledby="equipment-plan-action-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action === "INACTIVATE" ? "pause_circle" : "play_circle"} size={22}/></span><div><h2 id="equipment-plan-action-title">{actionLabels[action]}</h2><p>{plan.planCode} · 当前版本 {plan.version}</p></div><GsButton className="iconButton" aria-label="关闭模板状态动作" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>变更原因<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>{action === "INACTIVATE" ? "停用后不再生成新任务，已有工单不受影响。" : "启用后从保留的下次到期日继续生成。"}</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent={action === "INACTIVATE" ? "danger" : "primary"} loading={pending} htmlType="submit">{actionLabels[action]}</GsButton></div></footer></form></section></GsModalHost>;
}

function PlanDrawer({ plan, loading, error, canMaintain, onClose, onAction }: { plan: EquipmentMaintenancePlan; loading: boolean; error: string; canMaintain: boolean; onClose: () => void; onAction: (action: EquipmentMaintenancePlanAction) => void }) {
  return <GsDrawer open onClose={onClose} ariaLabel="周期维护模板详情"><header className="recordDrawerHeader"><div><h2>{plan.name}</h2><p>{plan.planCode} · {typeLabels[plan.workType]}</p></div><GsButton className="iconButton" aria-label="关闭周期维护模板详情" onClick={onClose} htmlType="button"><MaterialIcon name="close"/></GsButton></header><section className="drawerSection"><dl className="detailLedger"><div><dt>设备</dt><dd>{plan.assetName} · {plan.assetCode}</dd></div><div><dt>模板状态</dt><dd>{statusLabels[plan.status]}</dd></div><div><dt>周期 / 提前期</dt><dd>{plan.intervalDays} 天 / 提前 {plan.leadDays} 天</dd></div><div><dt>下次到期</dt><dd>{plan.nextDueDate}</dd></div><div><dt>下次生成日</dt><dd>{plan.nextGenerationDate}</dd></div><div><dt>作业窗口</dt><dd>{plan.plannedStartTime.slice(0, 5)}–{plan.dueTime.slice(0, 5)}</dd></div><div><dt>责任人</dt><dd>{plan.assignee}</dd></div><div><dt>逾期未关闭</dt><dd>{plan.overdueWorkOrderCount} 张</dd></div></dl><p className="equipmentWorkOrderNarrative">{plan.description}</p>{plan.overdueWorkOrderNumbers.length ? <p className="equipmentWorkOrderResult"><strong>逾期工单</strong>{plan.overdueWorkOrderNumbers.join("、")}</p> : null}</section>{canMaintain && plan.availableActions[0] ? <section className="drawerSection"><div className="equipmentActionButtons"><GsButton intent={plan.availableActions[0] === "INACTIVATE" ? "danger" : "primary"} onClick={() => onAction(plan.availableActions[0])} htmlType="button">{actionLabels[plan.availableActions[0]]}</GsButton></div></section> : null}<section className="drawerSection"><div className="sectionTitleCompact"><h3>模板证据</h3><span>{loading ? "加载中" : `${plan.events.length} 条`}</span></div>{error ? <div className="formError" role="alert">{error}</div> : null}<ol className="evidenceTimeline equipmentTimeline">{plan.events.map((event) => <li key={event.id}><span/><div><strong>{eventLabels[event.action]} · {statusLabels[event.toStatus]}</strong><small>{event.reason}</small><small>请求编号：{event.requestId}</small></div><time>{dateText(event.occurredAt)}</time></li>)}</ol></section></GsDrawer>;
}

export function EquipmentMaintenancePlanSection({ initialData, assets, onGenerated }: { initialData: EquipmentMaintenancePlanPageData | null; assets: EquipmentAsset[]; onGenerated: () => Promise<void> }) {
  const [page, setPage] = useState<EquipmentMaintenancePlanPage | null>(initialData?.page ?? null);
  const [message, setMessage] = useState(initialData?.source === "unavailable" ? initialData.message : "");
  const [query, setQuery] = useState(""); const [refreshing, setRefreshing] = useState(false); const [createOpen, setCreateOpen] = useState(false);
  const [generateOpen, setGenerateOpen] = useState(false); const [selected, setSelected] = useState<EquipmentMaintenancePlan | null>(null);
  const [action, setAction] = useState<EquipmentMaintenancePlanAction | null>(null); const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(""); const [toast, setToast] = useState("");
  const filtered = useMemo(() => !page ? [] : page.items.filter((plan) => !query.trim() || `${plan.planCode}${plan.name}${plan.assetCode}${plan.assetName}${plan.assignee}`.toLowerCase().includes(query.trim().toLowerCase())), [page, query]);
  async function refresh() { setRefreshing(true); try { setPage(await refreshEquipmentMaintenancePlans()); setMessage(""); } catch (failure) { setMessage(errorText(failure)); } finally { setRefreshing(false); } }
  function replace(plan: EquipmentMaintenancePlan) {
    setPage((current) => {
      if (!current) return current;
      const previous = current.items.find((item) => item.id === plan.id);
      const activeDelta = Number(plan.status === "ACTIVE") - Number(previous?.status === "ACTIVE");
      const dueDelta = Number(plan.generationStatus === "DUE") - Number(previous?.generationStatus === "DUE");
      const overdueDelta = plan.overdueWorkOrderCount - (previous?.overdueWorkOrderCount ?? 0);
      return {
        ...current,
        items: previous
          ? current.items.map((item) => item.id === plan.id ? { ...plan, events: [] } : item)
          : [{ ...plan, events: [] }, ...current.items],
        totalElements: previous ? current.totalElements : current.totalElements + 1,
        activeCount: current.activeCount + activeDelta,
        generationDueCount: current.generationDueCount + dueDelta,
        overdueWorkOrderCount: current.overdueWorkOrderCount + overdueDelta,
      };
    });
    setSelected(plan); setCreateOpen(false); setAction(null);
    setToast(`${plan.planCode} 已${plan.status === "ACTIVE" ? "启用" : "停用"}`);
    window.setTimeout(() => setToast(""), 2600);
  }
  async function generated(run: EquipmentMaintenanceGeneration) { setGenerateOpen(false); await Promise.all([refresh(), onGenerated()]); setToast(`生成完成：新增 ${run.generatedCount}，已存在 ${run.existingCount}，跳过 ${run.skippedCount}`); window.setTimeout(() => setToast(""), 4200); }
  async function openDetail(plan: EquipmentMaintenancePlan) { setSelected(plan); setDetailLoading(true); setDetailError(""); try { setSelected(await loadEquipmentMaintenancePlanDetail(plan.id)); } catch (failure) { setDetailError(errorText(failure)); } finally { setDetailLoading(false); } }
  if (!page) return <section className="backendUnavailableState equipmentPlanUnavailable" role="alert"><MaterialIcon name="cloud_off" size={26}/><strong>周期维护计划暂时不可用</strong><p>{message || "尚未取得周期维护模板数据。"}</p><GsButton onClick={refresh} loading={refreshing} htmlType="button">重新检查</GsButton></section>;
  return <section className="equipmentPlanSection" aria-labelledby="equipment-plan-section-title"><header className="equipmentPlanSectionHeader"><div><span>周期模板</span><h3 id="equipment-plan-section-title">到期任务生成控制</h3><p>人工触发、按模板与到期日幂等生成；不伪装成后台自动调度。</p></div><div><GsButton onClick={refresh} loading={refreshing} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新模板</GsButton>{page.canMaintain ? <><GsButton onClick={() => setGenerateOpen(true)} htmlType="button"><MaterialIcon name="playlist_add_check" size={17}/>生成到期任务</GsButton><GsButton intent="primary" onClick={() => setCreateOpen(true)} htmlType="button"><MaterialIcon name="add" size={17}/>新建周期模板</GsButton></> : null}</div></header><div className="equipmentPlanMetrics"><div><small>活动模板</small><strong>{page.activeCount}</strong></div><div><small>当前应生成</small><strong className={page.generationDueCount ? "businessMetricwarn" : "businessMetricgood"}>{page.generationDueCount}</strong></div><div><small>逾期未关闭</small><strong className={page.overdueWorkOrderCount ? "businessMetricdanger" : "businessMetricgood"}>{page.overdueWorkOrderCount}</strong></div><div><small>最近批次</small><strong>{page.recentRuns[0]?.generatedCount ?? 0}</strong><em>{page.recentRuns[0] ? `${page.recentRuns[0].asOfDate} 新增` : "尚未生成"}</em></div></div><div className="equipmentPlanToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索周期维护模板" value={query} placeholder="搜索模板、设备或责任人" onChange={(event) => setQuery(event.target.value)}/></div></div><div className="equipmentPlanTable" role="table" aria-label="周期维护模板列表"><div className="equipmentPlanTableHeader" role="row"><span>模板 / 类型</span><span>设备 / 责任</span><span>周期 / 提前期</span><span>下次节点 / 逾期</span><span>操作</span></div>{filtered.length ? filtered.map((plan) => <div className="equipmentPlanTableRow" role="row" key={plan.id}><span><strong>{plan.name}</strong><small>{plan.planCode} · {typeLabels[plan.workType]}</small></span><span><strong>{plan.assetName}</strong><small>{plan.assetCode} · {plan.assignee}</small></span><span><strong>每 {plan.intervalDays} 天</strong><small>提前 {plan.leadDays} 天生成 · {plan.plannedStartTime.slice(0, 5)}–{plan.dueTime.slice(0, 5)}</small></span><span><em className={`businessStatus businessStatus${plan.generationStatus === "DUE" ? "warn" : plan.status === "INACTIVE" ? "info" : "good"}`}>{generationLabels[plan.generationStatus]}</em><small>到期 {plan.nextDueDate} · 逾期 {plan.overdueWorkOrderCount}</small></span><span className="businessRowActions"><GsButton aria-label={`查看${plan.planCode}`} onClick={() => openDetail(plan)} htmlType="button"><MaterialIcon name="visibility" size={18}/></GsButton>{page.canMaintain && plan.availableActions[0] ? <GsButton aria-label={`${actionLabels[plan.availableActions[0]]}${plan.planCode}`} onClick={() => { setSelected(plan); setAction(plan.availableActions[0]); }} htmlType="button"><MaterialIcon name={plan.availableActions[0] === "INACTIVATE" ? "pause" : "play_arrow"} size={18}/></GsButton> : null}</span></div>) : <div className="businessEmptyState"><MaterialIcon name="event_repeat" size={28}/><strong>没有周期维护模板</strong><p>创建模板后可按截止日期生成真实点检或保养工单。</p></div>}</div>{message ? <div className="formError" role="alert">{message}</div> : null}{selected ? <PlanDrawer plan={selected} loading={detailLoading} error={detailError} canMaintain={page.canMaintain} onClose={() => setSelected(null)} onAction={setAction}/> : null}{createOpen ? <PlanCreateDialog assets={assets} onClose={() => setCreateOpen(false)} onSaved={replace}/> : null}{generateOpen ? <GenerateDialog onClose={() => setGenerateOpen(false)} onGenerated={generated}/> : null}{selected && action ? <PlanActionDialog plan={selected} action={action} onClose={() => setAction(null)} onSaved={replace}/> : null}{toast ? <div className="toastMessage" role="status"><MaterialIcon name="info" size={18}/>{toast}</div> : null}</section>;
}
