"use client";

import { type FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";

import type { RoutingRecord } from "@/lib/contracts";
import { submitRoutingMutation } from "@/services/routing-client-service";
import type { RoutingPageData, RoutingWritePayload } from "@/services/routing-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsCheckbox, GsDrawer, GsInput, GsModal, GsPagination, GsTextArea } from "./ui";

const statusLabels: Record<RoutingRecord["status"], string> = { DRAFT: "草稿", PUBLISHED: "已发布", INACTIVE: "已停用" };
const statusTones: Record<RoutingRecord["status"], string> = { DRAFT: "warn", PUBLISHED: "good", INACTIVE: "info" };
const eventLabels: Record<RoutingRecord["events"][number]["action"], string> = { CREATED: "创建草稿", UPDATED: "更新路线", PUBLISHED: "发布生效", INACTIVATED: "停用版本" };
type FormOperation = { key: string; operationCode: string; operationName: string; workCenterCode: string; workCenterName: string; setupMinutes: string; runMinutesPerUnit: string; queueMinutes: string; inspectionRequired: boolean; instructionSummary: string };

function todayText() { return new Date().toISOString().slice(0, 10); }
function formatDateTime(value: string) { return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value)); }
function emptyOperation(): FormOperation { return { key: crypto.randomUUID(), operationCode: "", operationName: "", workCenterCode: "", workCenterName: "", setupMinutes: "0", runMinutesPerUnit: "0", queueMinutes: "0", inspectionRequired: false, instructionSummary: "" }; }
function replaceRouting(items: RoutingRecord[], saved: RoutingRecord) { return items.some((item) => item.id === saved.id) ? items.map((item) => item.id === saved.id ? saved : item) : [saved, ...items]; }

function RoutingFormDialog({ record, references, onClose, onSaved }: { record?: RoutingRecord; references: RoutingPageData["referenceData"]; onClose: () => void; onSaved: (routing: RoutingRecord) => void }) {
  const [materialCode, setMaterialCode] = useState(record?.materialCode ?? references.materials[0]?.code ?? "");
  const [versionCode, setVersionCode] = useState(record?.versionCode ?? "V1.0");
  const [baseQuantity, setBaseQuantity] = useState(String(record?.baseQuantity ?? 1));
  const [effectiveFrom, setEffectiveFrom] = useState(record?.effectiveFrom ?? todayText());
  const [owner, setOwner] = useState(record?.owner ?? "");
  const [changeReason, setChangeReason] = useState(record?.changeReason ?? "");
  const [operations, setOperations] = useState<FormOperation[]>(record?.operations.map((item) => ({ key: item.id, operationCode: item.operationCode, operationName: item.operationName, workCenterCode: item.workCenterCode, workCenterName: item.workCenterName, setupMinutes: String(item.setupMinutes), runMinutesPerUnit: String(item.runMinutesPerUnit), queueMinutes: String(item.queueMinutes), inspectionRequired: item.inspectionRequired, instructionSummary: item.instructionSummary ?? "" })) ?? [emptyOperation()]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  function updateOperation(key: string, patch: Partial<FormOperation>) { setOperations((current) => current.map((item) => item.key === key ? { ...item, ...patch } : item)); }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError("");
    const material = references.materials.find((item) => item.code === materialCode);
    if (!material || !versionCode.trim() || !owner.trim() || !changeReason.trim() || !effectiveFrom) { setError("请完整填写物料、版本、生效日期、负责人和变更原因。"); return; }
    if (!operations.length) { setError("工艺路线至少需要一道工序。"); return; }
    const mapped = operations.map((item) => ({ ...item, operationCode: item.operationCode.trim().toUpperCase(), operationName: item.operationName.trim(), workCenterCode: item.workCenterCode.trim().toUpperCase(), workCenterName: item.workCenterName.trim(), setupMinutes: Number(item.setupMinutes), runMinutesPerUnit: Number(item.runMinutesPerUnit), queueMinutes: Number(item.queueMinutes) }));
    if (mapped.some((item) => !item.operationCode || !item.operationName || !item.workCenterCode || !item.workCenterName || !Number.isFinite(item.setupMinutes) || !Number.isFinite(item.runMinutesPerUnit) || !Number.isFinite(item.queueMinutes) || item.setupMinutes < 0 || item.runMinutesPerUnit < 0 || item.queueMinutes < 0 || (item.setupMinutes === 0 && item.runMinutesPerUnit === 0))) { setError("请检查工序、工作中心与工时；准备或单件工时至少一项必须大于 0。"); return; }
    if (new Set(mapped.map((item) => item.operationCode)).size !== mapped.length) { setError("同一工艺路线不能重复使用工序编码。"); return; }
    const payload: RoutingWritePayload = { materialId: material.id, usageType: "PRODUCTION", versionCode: versionCode.trim(), baseQuantity: Number(baseQuantity), effectiveFrom, owner: owner.trim(), changeReason: changeReason.trim(), operations: mapped.map((item) => ({ operationCode: item.operationCode, operationName: item.operationName, workCenterCode: item.workCenterCode, workCenterName: item.workCenterName, setupMinutes: item.setupMinutes, runMinutesPerUnit: item.runMinutesPerUnit, queueMinutes: item.queueMinutes, inspectionRequired: item.inspectionRequired, instructionSummary: item.instructionSummary.trim() || null })) };
    if (!Number.isFinite(payload.baseQuantity) || payload.baseQuantity <= 0) { setError("基准数量必须大于 0。"); return; }
    setPending(true);
    try { onSaved(await submitRoutingMutation(record ? { operation: "update", id: record.id, payload: { ...payload, expectedVersion: record.version } } : { operation: "create", payload })); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "工艺路线保存失败，请重试"); setPending(false); }
  }

  return <GsModal className="gsModal routingDialog" open width={1180} title={record ? "编辑工艺路线草稿" : "新建工艺路线版本"} footer={null} closable={!pending} keyboard={!pending} onCancel={pending ? undefined : onClose}>
    <p className="gsModalDescription">维护受控工序顺序、工作中心和标准工时，发布后不可直接改写。</p>
    <form onSubmit={submit}>
      <div className="formGrid bomHeaderFields">
        <label className="formField formFieldFull"><span>适用物料<em>必填 · 仅自制物料</em></span><RoundedSelect ariaLabel="选择工艺路线物料" size="field" options={references.materials.map((item) => item.code)} value={materialCode} onValueChange={setMaterialCode} disabled={Boolean(record)} /></label>
        <label className="formField"><span>版本编码<em>必填</em></span><GsInput value={versionCode} maxLength={32} onChange={(event) => setVersionCode(event.target.value)} /></label>
        <label className="formField"><span>生产用途</span><RoundedSelect ariaLabel="工艺路线用途" size="field" options={["生产"]} value="生产" disabled /></label>
        <label className="formField"><span>基准数量<em>必填</em></span><GsInput type="number" min="0.000001" step="0.000001" value={baseQuantity} onChange={(event) => setBaseQuantity(event.target.value)} /></label>
        <label className="formField"><span>计划生效日<em>必填</em></span><GsInput type="date" value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} /></label>
        <label className="formField"><span>负责人<em>必填</em></span><GsInput value={owner} maxLength={80} onChange={(event) => setOwner(event.target.value)} /></label>
        <label className="formField formFieldFull"><span>变更原因<em>必填 · 纳入审计</em></span><GsTextArea value={changeReason} maxLength={500} rows={2} onChange={(event) => setChangeReason(event.target.value)} /></label>
      </div>
      <section className="bomLineEditor routingOperationEditor">
        <header><div><h3>工序明细</h3><p>顺序按 10、20、30 自动编号；标准工时单位为分钟。</p></div><GsButton htmlType="button" icon={<MaterialIcon name="add" size={17} />} onClick={() => setOperations((current) => [...current, emptyOperation()])}>添加工序</GsButton></header>
        <div className="routingOperationHeader"><span>序</span><span>工序编码 / 名称</span><span>工作中心编码 / 名称</span><span>准备</span><span>单件</span><span>排队</span><span>质检</span><span>作业摘要</span><span>操作</span></div>
        {operations.map((item, index) => <div className="routingOperationRow" key={item.key}><strong>{(index + 1) * 10}</strong><span className="routingDoubleField"><GsInput aria-label={`第${index + 1}道工序编码`} placeholder="OP-ASM" value={item.operationCode} maxLength={40} onChange={(event) => updateOperation(item.key, { operationCode: event.target.value })} /><GsInput aria-label={`第${index + 1}道工序名称`} placeholder="机械装配" value={item.operationName} maxLength={120} onChange={(event) => updateOperation(item.key, { operationName: event.target.value })} /></span><span className="routingDoubleField"><GsInput aria-label={`第${index + 1}道工作中心编码`} placeholder="WC-ASM-01" value={item.workCenterCode} maxLength={40} onChange={(event) => updateOperation(item.key, { workCenterCode: event.target.value })} /><GsInput aria-label={`第${index + 1}道工作中心名称`} placeholder="总装中心" value={item.workCenterName} maxLength={120} onChange={(event) => updateOperation(item.key, { workCenterName: event.target.value })} /></span><GsInput aria-label={`第${index + 1}道准备工时`} type="number" min="0" step="0.001" value={item.setupMinutes} onChange={(event) => updateOperation(item.key, { setupMinutes: event.target.value })} /><GsInput aria-label={`第${index + 1}道单件工时`} type="number" min="0" step="0.001" value={item.runMinutesPerUnit} onChange={(event) => updateOperation(item.key, { runMinutesPerUnit: event.target.value })} /><GsInput aria-label={`第${index + 1}道排队时间`} type="number" min="0" step="0.001" value={item.queueMinutes} onChange={(event) => updateOperation(item.key, { queueMinutes: event.target.value })} /><span className="routingInspection"><GsCheckbox ariaLabel={`第${index + 1}道需要质检`} checked={item.inspectionRequired} onCheckedChange={(checked) => updateOperation(item.key, { inspectionRequired: checked })} /></span><GsInput aria-label={`第${index + 1}道作业摘要`} placeholder="关键步骤与控制要求" value={item.instructionSummary} maxLength={500} onChange={(event) => updateOperation(item.key, { instructionSummary: event.target.value })} /><GsButton intent="text" htmlType="button" aria-label={`删除第${index + 1}道工序`} disabled={operations.length === 1} icon={<MaterialIcon name="delete" size={18} />} onClick={() => setOperations((current) => current.filter((operation) => operation.key !== item.key))} /></div>)}
      </section>
      {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18} />{error}</div> : null}
      <footer className="dialogFooter"><span><MaterialIcon name="verified_user" size={16} />保存为草稿，发布需再次确认</span><div><GsButton htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton intent="primary" htmlType="submit" loading={pending}>保存草稿</GsButton></div></footer>
    </form>
  </GsModal>;
}

function RoutingActionDialog({ record, action, onClose, onDone }: { record: RoutingRecord; action: "PUBLISH" | "INACTIVATE"; onClose: () => void; onDone: (routing: RoutingRecord) => void }) {
  const [pending, setPending] = useState(false); const [error, setError] = useState(""); const publishing = action === "PUBLISH";
  async function confirm() { setPending(true); setError(""); try { onDone(await submitRoutingMutation({ operation: "action", id: record.id, action, expectedVersion: record.version })); } catch (reason) { setError(reason instanceof Error ? reason.message : "工艺路线状态操作失败"); setPending(false); } }
  return <GsModal className="gsModal bomActionDialog" open width={520} title={publishing ? "确认发布此工艺路线？" : "确认停用此工艺路线？"} closable={!pending} keyboard={!pending} onCancel={pending ? undefined : onClose} footer={[<GsButton key="cancel" onClick={onClose} disabled={pending}>取消</GsButton>, <GsButton key="confirm" intent={publishing ? "primary" : "danger"} onClick={confirm} loading={pending}>{publishing ? "确认发布" : "确认停用"}</GsButton>]}><div className="gsConfirmContent"><span className="deleteConfirmIcon"><MaterialIcon name={publishing ? "publish" : "block"} size={23} /></span><div><p>{publishing ? "发布后将成为计划与生产可引用的有效路线，工序和标准工时不能直接编辑。" : "停用后，新的计划和生产单据将不能再引用此版本；历史证据仍会保留。"}</p><strong>{record.routingNumber} · {record.materialCode} · {record.versionCode}</strong>{error ? <p className="deleteConfirmError" role="alert">{error}</p> : null}</div></div></GsModal>;
}

function RoutingDrawer({ record, onClose, onEdit, onAction }: { record: RoutingRecord; onClose: () => void; onEdit: () => void; onAction: (action: "PUBLISH" | "INACTIVATE") => void }) {
  const totalMinutes = record.operations.reduce((sum, item) => sum + item.setupMinutes + item.runMinutesPerUnit * record.baseQuantity + item.queueMinutes, 0);
  const footer = <div className="recordDrawerFooter"><Link className="secondaryButton" href="/product/boms/list"><MaterialIcon name="account_tree" size={17} />BOM 版本</Link>{record.status === "DRAFT" ? <><GsButton icon={<MaterialIcon name="edit" size={17} />} onClick={onEdit}>编辑</GsButton><GsButton intent="primary" icon={<MaterialIcon name="publish" size={17} />} onClick={() => onAction("PUBLISH")}>发布版本</GsButton></> : record.status === "PUBLISHED" ? <GsButton intent="danger" icon={<MaterialIcon name="block" size={17} />} onClick={() => onAction("INACTIVATE")}>停用版本</GsButton> : null}</div>;
  return <GsDrawer className="gsDrawer routingDrawer" open title={<div><strong>{record.routingNumber}</strong><p>{record.materialCode} · {record.materialName} · {record.versionCode}</p></div>} onClose={onClose} footer={footer}><section className="salesOrderSummary bomSummary"><div><small>版本状态</small><strong className={`businessStatus businessStatus${statusTones[record.status]}`}>{statusLabels[record.status]}</strong></div><div><small>用途 / 基准</small><strong>生产 · {record.baseQuantity} {record.materialUnit}</strong></div><div><small>生效窗口</small><strong>{record.effectiveFrom} 至 {record.effectiveTo ?? "持续有效"}</strong></div><div><small>标准历时</small><strong>{totalMinutes.toFixed(1)} 分钟</strong></div></section><section className="bomDrawerReason"><MaterialIcon name="history_edu" size={19} /><div><strong>变更原因</strong><p>{record.changeReason}</p></div></section><section className="bomDrawerSection"><header><div><h3>工序路线</h3><p>发布版本保留工序、工作中心和标准工时快照。</p></div><strong>{record.operations.length} 道</strong></header><div className="bomDetailTable routingDetailTable"><div className="routingDetailHeader"><span>序</span><span>工序</span><span>工作中心</span><span>准备 / 单件 / 排队</span><span>质量控制</span></div>{record.operations.map((item) => <div className="routingDetailRow" key={item.id}><strong>{item.sequenceNumber}</strong><span><strong>{item.operationCode}</strong><small>{item.operationName}</small></span><span><strong>{item.workCenterCode}</strong><small>{item.workCenterName}</small></span><span>{item.setupMinutes} / {item.runMinutesPerUnit} / {item.queueMinutes} 分钟</span><span>{item.inspectionRequired ? "需检验" : "常规控制"}<small>{item.instructionSummary ?? "未填写作业摘要"}</small></span></div>)}</div></section><section className="bomDrawerSection"><header><div><h3>版本证据</h3><p>创建、修改、发布和停用均保留请求号。</p></div><strong>{record.events.length} 条</strong></header><ol className="recordAudit bomAudit">{record.events.map((event) => <li key={event.id}><span /><div><strong>{eventLabels[event.action]}</strong><p>{event.fromStatus ? `${statusLabels[event.fromStatus]} → ` : ""}{statusLabels[event.toStatus]} · 请求号 {event.requestId ?? "未记录"}</p><time>{formatDateTime(event.occurredAt)}</time></div></li>)}</ol></section></GsDrawer>;
}

export function RoutingWorkspace({ initialData }: { initialData: RoutingPageData }) {
  const [routings, setRoutings] = useState(initialData.routings);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("全部状态");
  const [sortNewest, setSortNewest] = useState(true);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [editing, setEditing] = useState<RoutingRecord | "new" | null>(null);
  const [detail, setDetail] = useState<RoutingRecord | null>(null);
  const [confirmAction, setConfirmAction] = useState<"PUBLISH" | "INACTIVATE" | null>(null);
  const [toast, setToast] = useState("");
  useEffect(() => { if (!toast) return; const timer = window.setTimeout(() => setToast(""), 2600); return () => window.clearTimeout(timer); }, [toast]);
  const filtered = useMemo(() => routings.filter((item) => (!query.trim() || `${item.routingNumber}${item.materialCode}${item.materialName}${item.versionCode}${item.owner}`.toLowerCase().includes(query.trim().toLowerCase())) && (status === "全部状态" || statusLabels[item.status] === status)).sort((a, b) => (sortNewest ? -1 : 1) * a.updatedAt.localeCompare(b.updatedAt)), [query, routings, sortNewest, status]);
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize)); const currentPage = Math.min(page, totalPages); const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize); const currentIds = pageRows.map((item) => item.id); const allCurrent = currentIds.length > 0 && currentIds.every((id) => selectedIds.has(id)); const selected = routings.filter((item) => selectedIds.has(item.id));
  function saveResult(saved: RoutingRecord, message: string) { setRoutings((current) => replaceRouting(current, saved)); setDetail(saved); setEditing(null); setConfirmAction(null); setToast(message); }
  function exportRows(rows: RoutingRecord[]) { const csv = ["路线编号,物料编码,物料名称,版本,生效日期,失效日期,工序数,状态,负责人", ...rows.map((item) => [item.routingNumber, item.materialCode, item.materialName, item.versionCode, item.effectiveFrom, item.effectiveTo ?? "", item.operations.length, statusLabels[item.status], item.owner].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))].join("\n"); const href = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" })); const anchor = document.createElement("a"); anchor.href = href; anchor.download = `工艺路线-${todayText()}.csv`; anchor.click(); URL.revokeObjectURL(href); }
  if (initialData.source === "unavailable") return <div className="businessPage"><section className="routeState"><MaterialIcon name="cloud_off" size={34} /><h2>工艺路线服务暂时不可用</h2><p>{initialData.error}</p><GsButton intent="primary" icon={<MaterialIcon name="refresh" size={18} />} onClick={() => window.location.reload()}>重新加载</GsButton></section></div>;
  const badges = { "全部状态": routings.length, "草稿": routings.filter((item) => item.status === "DRAFT").length, "已发布": routings.filter((item) => item.status === "PUBLISHED").length, "已停用": routings.filter((item) => item.status === "INACTIVE").length };
  return <div className="businessPage bomPage routingPage">
    <header className="pageHeading businessPageHeading">
      <div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="route" size={23} /></span><div><h2>工艺路线</h2><p>以受控版本维护制造顺序、工作中心、标准工时与质量控制点，为计划和生产提供同一工艺事实。</p></div></div>
      <div className="pageHeadingActions"><Link className="secondaryButton" href="/product/boms/list"><MaterialIcon name="account_tree" size={18} />BOM 版本</Link><GsButton intent="primary" icon={<MaterialIcon name="add" size={18} />} onClick={() => { setDetail(null); setEditing("new"); }}>新建工艺路线</GsButton></div>
    </header>
    <section className="businessMetrics"><div><small>路线版本</small><strong className="businessMetricinfo">{routings.length}</strong><em>当前租户可见范围</em></div><div><small>已发布</small><strong className="businessMetricgood">{badges["已发布"]}</strong><em>可供计划与生产引用</em></div><div><small>草稿待处理</small><strong className="businessMetricwarn">{badges["草稿"]}</strong><em>发布前仍可编辑</em></div><div><small>受控工序</small><strong className="businessMetricinfo">{routings.reduce((sum, item) => sum + item.operations.length, 0)}</strong><em>工艺版本内工序数</em></div></section>
    <section className="businessLedger bomLedger">
      <div className="businessToolbar">
        <div className="businessSearch"><MaterialIcon name="search" size={18} /><GsInput variant="borderless" aria-label="搜索工艺路线" placeholder="搜索路线编号、物料、版本或负责人" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} /></div>
        <div className="businessFilters"><RoundedSelect ariaLabel="工艺路线状态筛选" options={Object.keys(badges)} optionBadges={badges} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }} /></div>
        <div className="businessTableTools"><GsButton icon={<MaterialIcon name={sortNewest ? "south" : "north"} size={17} />} onClick={() => setSortNewest((value) => !value)}>更新时间</GsButton><GsButton icon={<MaterialIcon name="download" size={17} />} onClick={() => exportRows(selected.length ? selected : filtered)}>{selected.length ? `导出所选（${selected.length}）` : "导出当前"}</GsButton></div>
      </div>
      {selected.length ? <div className="businessBulkBar"><div><strong>已选择 {selected.length} 个版本</strong><span>批量发布会绕过逐版本校验，本页仅支持批量导出。</span></div><nav><GsButton intent="text" icon={<MaterialIcon name="download" size={17} />} onClick={() => exportRows(selected)}>导出所选</GsButton><GsButton intent="text" onClick={() => setSelectedIds(new Set())}>取消选择</GsButton></nav></div> : null}
      <div className="bomTable routingTable" role="table" aria-label="工艺路线列表">
        <div className="routingTableHeader" role="row"><GsCheckbox ariaLabel="选择当前页全部工艺路线" checked={allCurrent} onCheckedChange={(checked) => setSelectedIds((current) => { const next = new Set(current); currentIds.forEach((id) => checked ? next.add(id) : next.delete(id)); return next; })} /><span>路线编号 / 物料</span><span>版本 / 基准</span><span>生效窗口</span><span>工序 / 工时</span><span>负责人 / 更新</span><span>状态</span><span>操作</span></div>
        {pageRows.length ? pageRows.map((item) => { const minutes = item.operations.reduce((sum, operation) => sum + operation.setupMinutes + operation.runMinutesPerUnit * item.baseQuantity + operation.queueMinutes, 0); return <div className="routingTableRow" role="row" key={item.id} onClick={() => setDetail(item)}><span><GsCheckbox ariaLabel={`选择${item.routingNumber}`} checked={selectedIds.has(item.id)} onCheckedChange={(checked) => setSelectedIds((current) => { const next = new Set(current); if (checked) next.add(item.id); else next.delete(item.id); return next; })} /></span><span><strong>{item.routingNumber}</strong><small>{item.materialCode} · {item.materialName}</small></span><span><strong>{item.versionCode}</strong><small>生产 · 基准 {item.baseQuantity} {item.materialUnit}</small></span><span><strong>{item.effectiveFrom}</strong><small>至 {item.effectiveTo ?? "持续有效"}</small></span><span><strong>{item.operations.length} 道</strong><small>标准历时 {minutes.toFixed(1)} 分钟</small></span><span><strong>{item.owner}</strong><small>{formatDateTime(item.updatedAt)}</small></span><em className={`businessStatus businessStatus${statusTones[item.status]}`}>{statusLabels[item.status]}</em><span className="businessRowActions"><GsButton intent="text" aria-label={`查看${item.routingNumber}`} icon={<MaterialIcon name="chevron_right" size={20} />} onClick={(event) => { event.stopPropagation(); setDetail(item); }} />{item.status === "DRAFT" ? <GsButton intent="text" aria-label={`编辑${item.routingNumber}`} icon={<MaterialIcon name="edit" size={18} />} onClick={(event) => { event.stopPropagation(); setDetail(null); setEditing(item); }} /> : null}</span></div>; }) : <div className="businessEmptyState"><MaterialIcon name="route" size={28} /><strong>没有符合条件的工艺路线</strong><p>调整筛选条件，或新建一个受控工艺路线草稿。</p></div>}
      </div>
      <footer className="businessPagination"><span>共 {filtered.length} 条 · 第 {currentPage} / {totalPages} 页</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer>
    </section>
    {editing ? <RoutingFormDialog record={editing === "new" ? undefined : editing} references={initialData.referenceData} onClose={() => setEditing(null)} onSaved={(saved) => saveResult(saved, editing === "new" ? "工艺路线草稿已创建" : "工艺路线草稿已更新")} /> : null}
    {detail ? <RoutingDrawer record={detail} onClose={() => setDetail(null)} onEdit={() => { setEditing(detail); setDetail(null); }} onAction={setConfirmAction} /> : null}
    {detail && confirmAction ? <RoutingActionDialog record={detail} action={confirmAction} onClose={() => setConfirmAction(null)} onDone={(saved) => saveResult(saved, confirmAction === "PUBLISH" ? "工艺路线已发布并可供计划引用" : "工艺路线已停用")} /> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" size={18} />{toast}</div> : null}
  </div>;
}
