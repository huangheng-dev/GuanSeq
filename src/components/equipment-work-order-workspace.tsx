"use client";

import { type FormEvent, useEffect, useMemo, useState } from "react";

import type { EquipmentAsset, EquipmentOperatingStatus, EquipmentSparePart, EquipmentSparePartReference, EquipmentWorkOrder, EquipmentWorkOrderAction, EquipmentWorkOrderOutcome, EquipmentWorkOrderPage, EquipmentWorkOrderPriority, EquipmentWorkOrderStatus, EquipmentWorkType } from "@/lib/contracts";
import type { EquipmentMaintenanceCostMutation, EquipmentWorkOrderMutation, EquipmentWorkOrderPageData } from "@/services/equipment-work-order-server-service";
import { EquipmentWorkOrderClientError, loadEquipmentWorkOrderDetail, refreshEquipmentWorkOrders, submitEquipmentMaintenanceCostMutation, submitEquipmentWorkOrderMutation } from "@/services/equipment-work-order-client-service";
import { refreshEquipmentSpareParts } from "@/services/equipment-spare-part-client-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsDrawer, GsInput, GsModalHost, GsPagination, GsTextArea } from "./ui";

const typeLabels: Record<EquipmentWorkType, string> = { INSPECTION: "点检任务", PREVENTIVE_MAINTENANCE: "保养任务", REPAIR: "维修工单" };
const statusLabels: Record<EquipmentWorkOrderStatus, string> = { PLANNED: "已计划", IN_PROGRESS: "执行中", WAITING_ACCEPTANCE: "待验收", COMPLETED: "已完成", CANCELLED: "已取消" };
const statusTones: Record<EquipmentWorkOrderStatus, string> = { PLANNED: "info", IN_PROGRESS: "warn", WAITING_ACCEPTANCE: "warn", COMPLETED: "good", CANCELLED: "info" };
const priorityLabels: Record<EquipmentWorkOrderPriority, string> = { LOW: "低", MEDIUM: "中", HIGH: "高", URGENT: "紧急" };
const sourceLabels = { MANUAL: "人工计划", BREAKDOWN: "故障报修", INSPECTION_FAILURE: "点检异常", MAINTENANCE_FAILURE: "保养异常" } as const;
const assetStatusLabels: Record<EquipmentOperatingStatus, string> = { IDLE: "闲置", RUNNING: "运行中", DOWN: "故障", MAINTENANCE: "维修中", INACTIVE: "已停用" };
const actionLabels: Record<EquipmentWorkOrderAction, string> = { START: "开始执行", COMPLETE: "提交结果", SUBMIT_FOR_ACCEPTANCE: "提交验收", ACCEPT: "验收通过", REJECT: "验收驳回", CANCEL: "取消任务" };
const eventLabels: Record<string, string> = { CREATED: "建立计划", REPAIR_GENERATED: "自动生成维修", STARTED: "开始执行", EXECUTION_COMPLETED: "执行完成", SUBMITTED_FOR_ACCEPTANCE: "提交验收", ACCEPTED: "验收通过", REJECTED: "验收驳回", CANCELLED: "取消任务" };

function typeForView(view: "inspections" | "maintenance" | "work-orders"): EquipmentWorkType {
  return view === "inspections" ? "INSPECTION" : view === "maintenance" ? "PREVENTIVE_MAINTENANCE" : "REPAIR";
}
function iconFor(type: EquipmentWorkType) { return type === "INSPECTION" ? "fact_check" : type === "PREVENTIVE_MAINTENANCE" ? "home_repair_service" : "build"; }
function localDateTime(date: Date) { const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000); return local.toISOString().slice(0, 16); }
function dateText(value: string | null) { return value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "未记录"; }
function errorText(error: unknown) {
  if (error instanceof EquipmentWorkOrderClientError) return error.requestId ? `${error.message}（请求编号：${error.requestId}）` : error.message;
  return error instanceof Error ? error.message : "设备运维操作失败";
}

function WorkOrderCreateDialog({ workType, assets, onClose, onSaved }: { workType: EquipmentWorkType; assets: EquipmentAsset[]; onClose: () => void; onSaved: (order: EquipmentWorkOrder) => void }) {
  const candidates = assets.filter((asset) => asset.operatingStatus !== "INACTIVE" && (workType !== "REPAIR" || asset.operatingStatus === "DOWN"));
  const assetOptions = candidates.map((asset) => `${asset.assetCode} · ${asset.assetName} · ${assetStatusLabels[asset.operatingStatus]}`);
  const now = new Date(); const due = new Date(now.getTime() + (workType === "REPAIR" ? 8 : 24) * 60 * 60 * 1000);
  const [assetLabel, setAssetLabel] = useState(assetOptions[0] ?? "");
  const [title, setTitle] = useState(""); const [description, setDescription] = useState("");
  const [priorityLabel, setPriorityLabel] = useState(workType === "REPAIR" ? "高" : "中");
  const [plannedStartAt, setPlannedStartAt] = useState(localDateTime(now)); const [dueAt, setDueAt] = useState(localDateTime(due));
  const [assignee, setAssignee] = useState(candidates[0]?.responsiblePerson ?? ""); const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  const asset = candidates[assetOptions.indexOf(assetLabel)];
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!asset || !title.trim() || description.trim().length < 4 || !assignee.trim() || reason.trim().length < 4) { setError("请选择设备，并填写标题、作业要求、责任人和至少 4 个字符的创建原因。"); return; }
    if (new Date(plannedStartAt) > new Date(dueAt)) { setError("计划开始时间不能晚于要求完成时间。"); return; }
    const priority = Object.entries(priorityLabels).find(([, label]) => label === priorityLabel)?.[0] as EquipmentWorkOrderPriority;
    setPending(true); setError("");
    try { onSaved(await submitEquipmentWorkOrderMutation({ operation: "create", assetId: asset.id, workType, title: title.trim(), description: description.trim(), priority, plannedStartAt: new Date(plannedStartAt).toISOString(), dueAt: new Date(dueAt).toISOString(), assignee: assignee.trim(), reason: reason.trim(), assetExpectedVersion: asset.version })); }
    catch (failure) { setError(errorText(failure)); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentWorkOrderDialog" role="dialog" aria-modal="true" aria-labelledby="equipment-work-order-form-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={iconFor(workType)} size={22}/></span><div><h2 id="equipment-work-order-form-title">新建{typeLabels[workType]}</h2><p>创建一次性计划任务，周期自动生成尚未接入。</p></div><GsButton className="iconButton" onClick={onClose} disabled={pending} aria-label="关闭运维工单表单" htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    {candidates.length ? <form onSubmit={submit}><div className="formGrid equipmentFormGrid">
      <label className="formField formFieldFull"><span>关联设备<em>必填</em></span><RoundedSelect ariaLabel="关联设备" options={assetOptions} value={assetLabel} onValueChange={(value) => { setAssetLabel(value); const selected = candidates[assetOptions.indexOf(value)]; if (selected) setAssignee(selected.responsiblePerson); }}/><small>{workType === "REPAIR" ? "仅列出当前故障设备。" : "已停用设备不会进入候选范围。"}</small></label>
      <label className="formField formFieldFull"><span>任务标题<em>必填</em></span><GsInput value={title} maxLength={160} placeholder={workType === "INSPECTION" ? "例如 主轴润滑与安全联锁点检" : workType === "PREVENTIVE_MAINTENANCE" ? "例如 月度气路与夹具保养" : "例如 主轴温升异常维修"} onChange={(event) => setTitle(event.target.value)}/></label>
      <label className="formField formFieldFull"><span>作业要求<em>必填</em></span><GsTextArea rows={3} maxLength={1000} value={description} onChange={(event) => setDescription(event.target.value)}/></label>
      <label className="formField"><span>优先级<em>必填</em></span><RoundedSelect ariaLabel="工单优先级" options={Object.values(priorityLabels)} value={priorityLabel} onValueChange={setPriorityLabel}/></label>
      <label className="formField"><span>责任人<em>必填</em></span><GsInput value={assignee} maxLength={80} onChange={(event) => setAssignee(event.target.value)}/></label>
      <label className="formField"><span>计划开始<em>必填</em></span><GsInput type="datetime-local" value={plannedStartAt} onChange={(event) => setPlannedStartAt(event.target.value)}/></label>
      <label className="formField"><span>要求完成<em>必填</em></span><GsInput type="datetime-local" value={dueAt} onChange={(event) => setDueAt(event.target.value)}/></label>
      <label className="formField formFieldFull"><span>创建原因<em>必填</em></span><GsTextArea rows={2} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/><small>请求编号与原因会进入不可变工单事件。</small></label>
    </div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="event" size={16}/>当前交付为一次性计划任务</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">保存计划</GsButton></div></footer></form> : <div className="businessEmptyState"><MaterialIcon name="block" size={28}/><strong>没有可创建此类工单的设备</strong><p>{workType === "REPAIR" ? "请先从设备台账报告故障，系统会自动生成维修工单。" : "当前工作区没有可用设备。"}</p><GsButton onClick={onClose} htmlType="button">关闭</GsButton></div>}
  </section></GsModalHost>;
}

function WorkOrderActionDialog({ order, action, onClose, onSaved }: { order: EquipmentWorkOrder; action: EquipmentWorkOrderAction; onClose: () => void; onSaved: (order: EquipmentWorkOrder) => void }) {
  const [reason, setReason] = useState(""); const [outcomeLabel, setOutcomeLabel] = useState("通过"); const [notes, setNotes] = useState("");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  const requiresOutcome = action === "COMPLETE"; const requiresNotes = requiresOutcome || action === "SUBMIT_FOR_ACCEPTANCE";
  async function submit(event: FormEvent) {
    event.preventDefault(); if (reason.trim().length < 4) { setError("请填写至少 4 个字符的动作原因。"); return; }
    if (requiresNotes && notes.trim().length < 4) { setError("请填写至少 4 个字符的执行或维修记录。"); return; }
    const input: EquipmentWorkOrderMutation = { operation: "action", id: order.id, action, reason: reason.trim(), expectedVersion: order.version, assetExpectedVersion: order.assetVersion,
      outcome: requiresOutcome ? (outcomeLabel === "通过" ? "PASS" : "FAIL") as EquipmentWorkOrderOutcome : null, completionNotes: requiresNotes ? notes.trim() : null };
    setPending(true); setError(""); try { onSaved(await submitEquipmentWorkOrderMutation(input)); } catch (failure) { setError(errorText(failure)); setPending(false); }
  }
  return <GsModalHost zIndex={1100} onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentActionDialog" role="dialog" aria-modal="true" aria-labelledby="equipment-work-order-action-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action === "REJECT" || action === "CANCEL" ? "report_problem" : "published_with_changes"} size={22}/></span><div><h2 id="equipment-work-order-action-title">{actionLabels[action]}</h2><p>{order.workOrderNumber} · {statusLabels[order.status]} · 工单版本 {order.version} / 设备版本 {order.assetVersion}</p></div><GsButton className="iconButton" onClick={onClose} disabled={pending} aria-label="关闭运维工单动作" htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid">
    {requiresOutcome ? <label className="formField formFieldFull"><span>执行结论<em>必填</em></span><RoundedSelect ariaLabel="执行结论" options={["通过", "不通过"]} value={outcomeLabel} onValueChange={setOutcomeLabel}/><small>不通过会把设备置为故障并自动建立维修工单。</small></label> : null}
    {requiresNotes ? <label className="formField formFieldFull"><span>{action === "SUBMIT_FOR_ACCEPTANCE" ? "维修结果" : "执行记录"}<em>必填</em></span><GsTextArea rows={4} maxLength={1000} value={notes} onChange={(event) => setNotes(event.target.value)}/></label> : null}
    <label className="formField formFieldFull"><span>动作原因<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label>
  </div><div className="mrpRunTruthNotice"><MaterialIcon name="link" size={18}/><span><strong>设备与工单原子联动</strong>状态、版本和事件在同一事务提交；不会向设备发送控制命令。</span></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>非法状态、过期版本或设备冲突会拒绝整笔操作</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent={action === "REJECT" || action === "CANCEL" ? "danger" : "primary"} loading={pending} htmlType="submit">确认{actionLabels[action]}</GsButton></div></footer></form></section></GsModalHost>;
}

type CostAction = "ISSUE_SPARE" | "RETURN_SPARE" | "RECORD_LABOR" | "REVERSE_LABOR";
const costActionLabels: Record<CostAction, string> = { ISSUE_SPARE: "领用备件", RETURN_SPARE: "退回备件", RECORD_LABOR: "登记人工", REVERSE_LABOR: "冲销人工" };

function MaintenanceCostDialog({ order, action, onClose, onSaved }: { order: EquipmentWorkOrder; action: CostAction; onClose: () => void; onSaved: (order: EquipmentWorkOrder) => void }) {
  const [spares, setSpares] = useState<EquipmentSparePart[]>([]); const [references, setReferences] = useState<EquipmentSparePartReference | null>(null);
  const [loading, setLoading] = useState(action === "ISSUE_SPARE" || action === "RETURN_SPARE"); const [pending, setPending] = useState(false); const [error, setError] = useState("");
  const [choice, setChoice] = useState(""); const [locationChoice, setLocationChoice] = useState(""); const [quantity, setQuantity] = useState("");
  const [technician, setTechnician] = useState(order.assignee); const [hours, setHours] = useState(""); const [hourlyRate, setHourlyRate] = useState(""); const [reason, setReason] = useState("");
  const issues = (order.costEvidence?.spareTransactions ?? []).filter((item) => item.transactionType === "ISSUE" && item.returnableQuantity > 0);
  const laborEntries = (order.costEvidence?.laborTransactions ?? []).filter((item) => item.transactionType === "ENTRY" && !item.reversed);
  useEffect(() => {
    if (action !== "ISSUE_SPARE" && action !== "RETURN_SPARE") return;
    let active = true; refreshEquipmentSpareParts().then((result) => { if (!active) return; setSpares(result.page.items.filter((item) => item.status === "ACTIVE")); setReferences(result.references); setLoading(false); })
      .catch((failure) => { if (active) { setError(errorText(failure)); setLoading(false); } }); return () => { active = false; };
  }, [action]);
  const spareLabels = spares.map((item) => `${item.materialCode} · ${item.materialName} · 可用 ${item.availableQuantity} ${item.unit}`);
  const issueLabels = issues.map((item) => `${item.materialCode} · ${item.materialName} · 可退 ${item.returnableQuantity} ${item.unit}`);
  const laborLabels = laborEntries.map((item) => `${item.technicianName} · ${item.hours}h · ${item.currency} ${item.amount.toFixed(2)}`);
  const selectedSpare = spares[spareLabels.indexOf(choice || spareLabels[0])]; const selectedIssue = issues[issueLabels.indexOf(choice || issueLabels[0])];
  const selectedLabor = laborEntries[laborLabels.indexOf(choice || laborLabels[0])];
  const locations = references?.locations.filter((item) => item.warehouseId === selectedIssue?.warehouseId) ?? [];
  const locationLabels = locations.map((item) => `${item.code} · ${item.name}`);
  async function submit(event: FormEvent) {
    event.preventDefault(); if (reason.trim().length < 4) { setError("请填写至少 4 个字符的业务原因。"); return; }
    let input: EquipmentMaintenanceCostMutation;
    if (action === "ISSUE_SPARE") {
      const amount = Number(quantity); if (!selectedSpare || !Number.isFinite(amount) || amount <= 0) { setError("请选择备件并填写大于零的领用数量。"); return; }
      input = { operation: "issueSpare", id: order.id, sparePartId: selectedSpare.id, warehouseId: selectedSpare.preferredWarehouseId, quantity: amount, reason: reason.trim(), expectedVersion: order.version };
    } else if (action === "RETURN_SPARE") {
      const amount = Number(quantity); const location = locations[locationLabels.indexOf(locationChoice || locationLabels[0])];
      if (!selectedIssue || !location || !Number.isFinite(amount) || amount <= 0 || amount > selectedIssue.returnableQuantity) { setError("请选择原领用、退回库位，并填写不超过可退量的数量。"); return; }
      input = { operation: "returnSpare", id: order.id, issueTransactionId: selectedIssue.id, locationId: location.id, quantity: amount, reason: reason.trim(), expectedVersion: order.version };
    } else if (action === "RECORD_LABOR") {
      const hourValue = Number(hours); const rateValue = Number(hourlyRate); if (!technician.trim() || !Number.isFinite(hourValue) || hourValue <= 0 || hourValue > 24 || !Number.isFinite(rateValue) || rateValue <= 0) { setError("请填写责任人、0–24 小时范围内的工时和大于零的小时费率。"); return; }
      input = { operation: "recordLabor", id: order.id, technicianName: technician.trim(), hours: hourValue, hourlyRate: rateValue, currency: order.costEvidence?.currency ?? "CNY", reason: reason.trim(), expectedVersion: order.version };
    } else {
      if (!selectedLabor) { setError("请选择尚未冲销的人工登记。"); return; }
      input = { operation: "reverseLabor", id: order.id, entryId: selectedLabor.id, reason: reason.trim(), expectedVersion: order.version };
    }
    setPending(true); setError(""); try { const result = await submitEquipmentMaintenanceCostMutation(input); onSaved({ ...order, version: result.workOrderVersion, costEvidence: result.costEvidence }); }
    catch (failure) { setError(errorText(failure)); setPending(false); }
  }
  const options = action === "ISSUE_SPARE" ? spareLabels : action === "RETURN_SPARE" ? issueLabels : laborLabels;
  return <GsModalHost zIndex={1100} onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="maintenance-cost-dialog-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action.includes("LABOR") ? "engineering" : "inventory_2"} size={22}/></span><div><h2 id="maintenance-cost-dialog-title">{costActionLabels[action]}</h2><p>{order.workOrderNumber} · 工单版本 {order.version} · 运维成本估算</p></div><GsButton className="iconButton" onClick={onClose} disabled={pending} aria-label="关闭维修成本表单" htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <form onSubmit={submit}><div className="formGrid">{loading ? <div className="businessEmptyState formFieldFull"><strong>正在读取备件与仓库事实</strong></div> : null}
      {action === "ISSUE_SPARE" || action === "RETURN_SPARE" || action === "REVERSE_LABOR" ? <label className="formField formFieldFull"><span>{action === "ISSUE_SPARE" ? "备件" : action === "RETURN_SPARE" ? "原领用事务" : "原人工登记"}<em>必填</em></span><RoundedSelect ariaLabel={costActionLabels[action]} options={options.length ? options : ["暂无可用记录"]} value={choice || options[0] || "暂无可用记录"} onValueChange={(value) => { setChoice(value); setLocationChoice(""); }}/></label> : null}
      {action === "RETURN_SPARE" ? <label className="formField formFieldFull"><span>退回库位<em>必填</em></span><RoundedSelect ariaLabel="退回库位" options={locationLabels.length ? locationLabels : ["暂无可用库位"]} value={locationChoice || locationLabels[0] || "暂无可用库位"} onValueChange={setLocationChoice}/></label> : null}
      {action === "ISSUE_SPARE" || action === "RETURN_SPARE" ? <label className="formField"><span>数量<em>必填</em></span><GsInput type="number" min="0.0001" step="0.0001" value={quantity} onChange={(event) => setQuantity(event.target.value)}/></label> : null}
      {action === "RECORD_LABOR" ? <><label className="formField"><span>维修人员<em>必填</em></span><GsInput value={technician} maxLength={80} onChange={(event) => setTechnician(event.target.value)}/></label><label className="formField"><span>实际小时<em>必填</em></span><GsInput type="number" min="0.01" max="24" step="0.01" value={hours} onChange={(event) => setHours(event.target.value)}/></label><label className="formField"><span>估算小时费率<em>必填</em></span><GsInput type="number" min="0.01" step="0.01" value={hourlyRate} onChange={(event) => setHourlyRate(event.target.value)}/><small>不是工资或财务凭证。</small></label></> : null}
      <label className="formField formFieldFull"><span>{action === "REVERSE_LABOR" ? "冲销原因" : "业务原因"}<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label>
    </div><div className="mrpRunTruthNotice"><MaterialIcon name="verified" size={18}/><span><strong>不可变证据</strong>领退同步库存流水；成本冻结当时口径；错误人工通过冲销恢复。</span></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>并发冲突会拒绝整笔操作</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent={action === "REVERSE_LABOR" ? "danger" : "primary"} loading={pending} htmlType="submit">确认{costActionLabels[action]}</GsButton></div></footer></form>
  </section></GsModalHost>;
}

function WorkOrderDrawer({ order, canMaintain, loading, error, onClose, onAction, onCostAction }: { order: EquipmentWorkOrder; canMaintain: boolean; loading: boolean; error: string; onClose: () => void; onAction: (action: EquipmentWorkOrderAction) => void; onCostAction: (action: CostAction) => void }) {
  return <GsDrawer open title={`${order.workOrderNumber} · ${order.title}`} onClose={onClose} className="equipmentAssetDrawer"><div className="equipmentDetailStatus"><em className={`businessStatus businessStatus${statusTones[order.status]}`}>{statusLabels[order.status]}</em><span>{typeLabels[order.workType]}</span><small>设备状态：{assetStatusLabels[order.assetOperatingStatus]} · 版本 {order.assetVersion}</small></div>
    <dl className="detailLedger"><div><dt>设备</dt><dd>{order.assetName} · {order.assetCode}</dd></div><div><dt>位置</dt><dd>{order.assetLocation}</dd></div><div><dt>来源</dt><dd>{sourceLabels[order.sourceType]}</dd></div><div><dt>优先级</dt><dd>{priorityLabels[order.priority]}</dd></div><div><dt>责任人</dt><dd>{order.assignee}</dd></div><div><dt>计划开始</dt><dd>{dateText(order.plannedStartAt)}</dd></div><div><dt>要求完成</dt><dd>{dateText(order.dueAt)}</dd></div><div><dt>执行结论</dt><dd>{order.outcome ? (order.outcome === "PASS" ? "通过" : "不通过") : "待提交"}</dd></div></dl>
    <section className="drawerSection"><div className="sectionTitleCompact"><h3>作业要求</h3><span>工单版本 {order.version}</span></div><p className="equipmentWorkOrderNarrative">{order.description}</p>{order.completionNotes ? <p className="equipmentWorkOrderResult"><strong>最近执行记录</strong>{order.completionNotes}</p> : null}</section>
    {order.workType === "REPAIR" && order.costEvidence ? <section className="drawerSection maintenanceCostEvidence"><div className="sectionTitleCompact"><h3>维修成本证据</h3><span>{order.costEvidence.currency} · 运维估算</span></div><div className="maintenanceCostTotals"><div><small>净备件</small><strong>{order.costEvidence.spareCost.toFixed(2)}</strong></div><div><small>净人工</small><strong>{order.costEvidence.laborCost.toFixed(2)}</strong></div><div><small>成本合计</small><strong>{order.costEvidence.totalCost.toFixed(2)}</strong></div></div><p className="equipmentWorkOrderNarrative">{order.costEvidence.basis}</p>{canMaintain && order.costEvidence.availableActions.length ? <div className="equipmentActionButtons"><GsButton onClick={() => onCostAction("ISSUE_SPARE")} htmlType="button">领用备件</GsButton><GsButton onClick={() => onCostAction("RETURN_SPARE")} disabled={!order.costEvidence.spareTransactions.some((item) => item.transactionType === "ISSUE" && item.returnableQuantity > 0)} htmlType="button">退回备件</GsButton><GsButton onClick={() => onCostAction("RECORD_LABOR")} htmlType="button">登记人工</GsButton><GsButton onClick={() => onCostAction("REVERSE_LABOR")} disabled={!order.costEvidence.laborTransactions.some((item) => item.transactionType === "ENTRY" && !item.reversed)} htmlType="button">冲销人工</GsButton></div> : null}<div className="maintenanceCostLedger"><h4>备件事务</h4>{order.costEvidence.spareTransactions.length ? order.costEvidence.spareTransactions.map((item) => <p key={item.id}><strong>{item.transactionType === "ISSUE" ? "领用" : "退回"} {item.materialName}</strong><span>{item.quantity} {item.unit} · {item.currency} {item.amount.toFixed(2)}</span><small>{item.warehouseCode} · {item.reason} · {dateText(item.occurredAt)}</small></p>) : <p><small>尚未领用备件。</small></p>}<h4>人工事务</h4>{order.costEvidence.laborTransactions.length ? order.costEvidence.laborTransactions.map((item) => <p key={item.id}><strong>{item.transactionType === "ENTRY" ? "登记" : "冲销"} {item.technicianName}</strong><span>{item.hours}h · {item.currency} {item.amount.toFixed(2)}</span><small>{item.reason} · {dateText(item.occurredAt)}</small></p>) : <p><small>尚未登记人工。</small></p>}</div></section> : null}
    {canMaintain && order.availableActions.length ? <section className="drawerSection"><div className="sectionTitleCompact"><h3>可执行动作</h3><span>以后端状态机为准</span></div><div className="equipmentActionButtons">{order.availableActions.map((action) => <GsButton key={action} intent={action === "REJECT" || action === "CANCEL" ? "danger" : "secondary"} onClick={() => onAction(action)} htmlType="button">{actionLabels[action]}</GsButton>)}</div></section> : !canMaintain ? <section className="workspaceRoleBoundary"><MaterialIcon name="lock" size={19}/><div><strong>当前角色为只读</strong><p>设备经理、生产经理或管理员可以执行运维工单。</p></div></section> : null}
    <section className="drawerSection"><div className="sectionTitleCompact"><h3>运维证据</h3><span>{loading ? "加载中" : `${order.events.length} 条`}</span></div>{error ? <div className="formError" role="alert">{error}</div> : null}<ol className="evidenceTimeline equipmentTimeline">{order.events.map((event) => <li key={event.id}><span/><div><strong>{eventLabels[event.action] ?? event.action} · {statusLabels[event.toStatus]}</strong><small>{event.reason}{event.outcome ? ` · ${event.outcome === "PASS" ? "通过" : "不通过"}` : ""}</small><small>请求编号：{event.requestId}</small></div><time>{dateText(event.occurredAt)}</time></li>)}</ol></section>
  </GsDrawer>;
}

function UnavailableState({ data, onRecovered }: { data: Extract<EquipmentWorkOrderPageData, { source: "unavailable" }>; onRecovered: (page: EquipmentWorkOrderPage, assets: EquipmentAsset[]) => void }) {
  const [pending, setPending] = useState(false); const [message, setMessage] = useState(data.message);
  async function retry() { setPending(true); try { const result = await refreshEquipmentWorkOrders(); onRecovered(result.page, result.assets); } catch (error) { setMessage(errorText(error)); setPending(false); } }
  return <div className="businessPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="home_repair_service" size={23}/></span><div><h2>设备运维</h2><p>点检、保养、维修与验收证据。</p></div></div></header><section className="backendUnavailableState" role="alert"><MaterialIcon name={data.status === 403 ? "lock" : "cloud_off"} size={30}/><strong>{data.status === 403 ? "当前角色无权读取设备运维工单" : "设备运维服务暂时不可用"}</strong><p>{message}</p><small>请求编号：{data.requestId}</small><GsButton onClick={retry} loading={pending} htmlType="button">重新检查</GsButton></section></div>;
}

export function EquipmentWorkOrderWorkspace({ initialData, view }: { initialData: EquipmentWorkOrderPageData; view: "inspections" | "maintenance" | "work-orders" }) {
  const workType = typeForView(view); const title = view === "inspections" ? "点检计划" : view === "maintenance" ? "保养计划" : "维修工单";
  const [pageData, setPageData] = useState<EquipmentWorkOrderPage | null>(initialData.page); const [assets, setAssets] = useState(initialData.assets);
  const [unavailable, setUnavailable] = useState(initialData.source === "unavailable" ? initialData : null);
  const [query, setQuery] = useState(""); const [status, setStatus] = useState("全部状态"); const [page, setPage] = useState(1); const [pageSize, setPageSize] = useState(10);
  const [refreshing, setRefreshing] = useState(false); const [createOpen, setCreateOpen] = useState(false);
  const [referenceTime] = useState(() => Date.now());
  const [selected, setSelected] = useState<EquipmentWorkOrder | null>(null); const [action, setAction] = useState<EquipmentWorkOrderAction | null>(null);
  const [costAction, setCostAction] = useState<CostAction | null>(null);
  const [detailLoading, setDetailLoading] = useState(false); const [detailError, setDetailError] = useState(""); const [toast, setToast] = useState("");
  const filtered = useMemo(() => !pageData ? [] : pageData.items.filter((order) => order.workType === workType && (status === "全部状态" || statusLabels[order.status] === status) && (!query.trim() || `${order.workOrderNumber}${order.assetCode}${order.assetName}${order.title}${order.assignee}`.toLowerCase().includes(query.trim().toLowerCase()))), [pageData, query, status, workType]);
  if (!pageData && unavailable) return <UnavailableState data={unavailable} onRecovered={(next, nextAssets) => { setPageData(next); setAssets(nextAssets); setUnavailable(null); }}/>;
  if (!pageData) return null;
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize)); const currentPage = Math.min(page, totalPages); const rows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const count = (target: EquipmentWorkOrderStatus) => filtered.filter((order) => order.status === target).length;
  const overdue = filtered.filter((order) => !["COMPLETED", "CANCELLED"].includes(order.status) && new Date(order.dueAt).getTime() < referenceTime).length;
  function replace(saved: EquipmentWorkOrder) { setPageData((current) => current ? { ...current, items: current.items.some((item) => item.id === saved.id) ? current.items.map((item) => item.id === saved.id ? { ...saved, events: [] } : item) : [{ ...saved, events: [] }, ...current.items], totalElements: current.items.some((item) => item.id === saved.id) ? current.totalElements : current.totalElements + 1 } : current); setSelected(saved); setCreateOpen(false); setAction(null); setToast(saved.outcome === "FAIL" ? `${saved.workOrderNumber} 已记录异常并联动维修工单` : `${saved.workOrderNumber} 已更新`); window.setTimeout(() => setToast(""), 2800); }
  function replaceCost(saved: EquipmentWorkOrder) { setPageData((current) => current ? { ...current, items: current.items.map((item) => item.id === saved.id ? { ...saved, events: [] } : item) } : current); setSelected(saved); setCostAction(null); setToast(`${saved.workOrderNumber} 的维修成本证据已更新`); window.setTimeout(() => setToast(""), 2600); }
  async function openDetail(order: EquipmentWorkOrder) { setSelected(order); setDetailLoading(true); setDetailError(""); try { setSelected(await loadEquipmentWorkOrderDetail(order.id)); } catch (error) { setDetailError(errorText(error)); } finally { setDetailLoading(false); } }
  async function refresh() { setRefreshing(true); try { const result = await refreshEquipmentWorkOrders(); setPageData(result.page); setAssets(result.assets); setToast("设备运维计划与状态已刷新"); window.setTimeout(() => setToast(""), 2200); } catch (error) { setToast(errorText(error)); } finally { setRefreshing(false); } }
  return <div className="businessPage equipmentWorkOrderPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name={iconFor(workType)} size={23}/></span><div><h2>{title}</h2><p>{workType === "INSPECTION" ? "计划现场点检，异常自动转入维修闭环。" : workType === "PREVENTIVE_MAINTENANCE" ? "按停机窗口执行保养并记录结果。" : "从故障报修推进到维修完工与现场验收。"}</p></div></div><div className="pageHeadingActions"><GsButton onClick={refresh} loading={refreshing} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新</GsButton>{pageData.canMaintain ? <GsButton intent="primary" onClick={() => setCreateOpen(true)} htmlType="button"><MaterialIcon name="add" size={17}/>新建{typeLabels[workType]}</GsButton> : null}</div></header>
    <section className="equipmentTruthBanner"><MaterialIcon name="person_edit" size={21}/><div><strong>人工执行记录</strong><p>工单、设备状态与验收证据真实落库；周期自动生成、设备采集、报警和 OEE 尚未接入。</p></div><span>自动采集未接入</span></section>
    <section className="businessMetrics"><div><small>已计划</small><strong className="businessMetricinfo">{count("PLANNED")}</strong><em>等待开始执行</em></div><div><small>执行中</small><strong className="businessMetricwarn">{count("IN_PROGRESS")}</strong><em>现场责任处理中</em></div><div><small>待验收</small><strong className={count("WAITING_ACCEPTANCE") ? "businessMetricwarn" : "businessMetricinfo"}>{count("WAITING_ACCEPTANCE")}</strong><em>仅维修工单适用</em></div><div><small>已逾期</small><strong className={overdue ? "businessMetricdanger" : "businessMetricgood"}>{overdue}</strong><em>未完成且超过要求时间</em></div></section>
    <section className="businessLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label={`搜索${title}`} value={query} placeholder="搜索工单号、设备、标题或责任人" onChange={(event) => { setQuery(event.target.value); setPage(1); }}/></div><div className="equipmentFilters"><RoundedSelect ariaLabel="工单状态" options={["全部状态", ...Object.values(statusLabels)]} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }}/></div></div>
      <div className="equipmentWorkOrderTable" role="table" aria-label={`${title}列表`}><div className="equipmentWorkOrderTableHeader" role="row"><span>工单 / 任务</span><span>设备 / 来源</span><span>状态 / 优先级</span><span>计划 / 责任</span><span>操作</span></div>{rows.length ? rows.map((order) => <div className="equipmentWorkOrderTableRow" role="row" key={order.id}><span><strong>{order.title}</strong><small>{order.workOrderNumber} · {typeLabels[order.workType]}</small></span><span><strong>{order.assetName}</strong><small>{order.assetCode} · {sourceLabels[order.sourceType]}</small></span><span><em className={`businessStatus businessStatus${statusTones[order.status]}`}>{statusLabels[order.status]}</em><small>{priorityLabels[order.priority]}优先级 · 设备{assetStatusLabels[order.assetOperatingStatus]}</small></span><span><strong>{dateText(order.dueAt)}</strong><small>{order.assignee}</small></span><span className="businessRowActions"><GsButton onClick={() => openDetail(order)} aria-label={`查看${order.workOrderNumber}`} htmlType="button"><MaterialIcon name="visibility" size={18}/></GsButton>{pageData.canMaintain && order.availableActions[0] ? <GsButton onClick={() => { setSelected(order); setAction(order.availableActions[0]); }} aria-label={`处理${order.workOrderNumber}`} htmlType="button"><MaterialIcon name="published_with_changes" size={18}/></GsButton> : null}</span></div>) : <div className="businessEmptyState"><MaterialIcon name={iconFor(workType)} size={28}/><strong>没有符合条件的{typeLabels[workType]}</strong><p>{pageData.canMaintain ? "新建一次性计划任务后，可按状态机执行并保留证据。" : "当前工作区暂时没有可见任务。"}</p>{pageData.canMaintain ? <GsButton intent="primary" onClick={() => setCreateOpen(true)} htmlType="button">新建{typeLabels[workType]}</GsButton> : null}</div>}</div>
      <footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextSize) => { setPage(nextPage); setPageSize(nextSize); }}/></footer>
    </section>
    {!pageData.canMaintain ? <section className="workspaceRoleBoundary"><MaterialIcon name="shield_lock" size={20}/><div><strong>当前角色只读</strong><p>设备经理、生产经理和管理员可以维护运维任务；后端权限是唯一可信边界。</p></div></section> : null}
    {selected ? <WorkOrderDrawer order={selected} canMaintain={pageData.canMaintain} loading={detailLoading} error={detailError} onClose={() => setSelected(null)} onAction={setAction} onCostAction={setCostAction}/> : null}
    {createOpen ? <WorkOrderCreateDialog workType={workType} assets={assets} onClose={() => setCreateOpen(false)} onSaved={replace}/> : null}
    {selected && action ? <WorkOrderActionDialog order={selected} action={action} onClose={() => setAction(null)} onSaved={replace}/> : null}
    {selected && costAction ? <MaintenanceCostDialog order={selected} action={costAction} onClose={() => setCostAction(null)} onSaved={replaceCost}/> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="info" size={18}/>{toast}</div> : null}
  </div>;
}
