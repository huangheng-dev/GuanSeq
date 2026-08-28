"use client";

import { type FormEvent, useMemo, useState } from "react";

import type { EquipmentAsset, EquipmentOeeAction, EquipmentOeeDowntime, EquipmentOeeDowntimeCategory,
  EquipmentOeeRecord } from "@/lib/contracts";
import { EquipmentOeeClientError, loadEquipmentOeeDetail, refreshEquipmentOeePage,
  submitEquipmentOeeMutation } from "@/services/equipment-oee-client-service";
import type { EquipmentOeePageData } from "@/services/equipment-oee-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsDrawer, GsInput, GsModalHost, GsTextArea } from "./ui";

const statusLabels = { DRAFT: "草稿", SUBMITTED: "待审核", APPROVED: "已审核", REJECTED: "已驳回" } as const;
const statusTones = { DRAFT: "info", SUBMITTED: "warn", APPROVED: "good", REJECTED: "risk" } as const;
const categoryLabels: Record<EquipmentOeeDowntimeCategory, string> = {
  EQUIPMENT_FAILURE: "设备故障", SETUP_CHANGEOVER: "换型调机", MATERIAL_WAIT: "物料等待",
  QUALITY_HOLD: "质量等待", PERSONNEL_WAIT: "人员等待", PLANNED_MAINTENANCE: "计划保养", OTHER: "其他",
};
const actionLabels: Record<EquipmentOeeAction, string> = {
  UPDATE: "编辑记录", ADD_DOWNTIME: "登记停机", UPDATE_DOWNTIME: "修改停机", REMOVE_DOWNTIME: "移除停机",
  SUBMIT: "提交审核", APPROVE: "审核通过", REJECT: "驳回修正",
};

function dateText(value: string) {
  return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit",
    minute: "2-digit", hour12: false }).format(new Date(value));
}
function localValue(value: string) {
  const date = new Date(value); const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}
function percent(value: number) { return `${value.toFixed(1)}%`; }
function errorText(error: unknown) {
  if (error instanceof EquipmentOeeClientError && error.requestId) return `${error.message}（请求 ${error.requestId}）`;
  return error instanceof Error ? error.message : "OEE 操作失败";
}

function RecordDialog({ assets, initial, onClose, onSaved }: { assets: EquipmentAsset[]; initial?: EquipmentOeeRecord;
  onClose: () => void; onSaved: (record: EquipmentOeeRecord) => void }) {
  const now = new Date(); now.setMinutes(0, 0, 0); const later = new Date(now.getTime() + 8 * 3600000);
  const [assetId, setAssetId] = useState(initial?.assetId ?? assets[0]?.id ?? "");
  const [windowStart, setWindowStart] = useState(initial ? localValue(initial.windowStart) : localValue(now.toISOString()));
  const [windowEnd, setWindowEnd] = useState(initial ? localValue(initial.windowEnd) : localValue(later.toISOString()));
  const [plannedMinutes, setPlannedMinutes] = useState(String(initial?.plannedProductionMinutes ?? 480));
  const [idealSeconds, setIdealSeconds] = useState(String(initial?.idealCycleSeconds ?? 60));
  const [totalCount, setTotalCount] = useState(String(initial?.totalCount ?? 0));
  const [goodCount, setGoodCount] = useState(String(initial?.goodCount ?? 0));
  const [shiftName, setShiftName] = useState(initial?.shiftName ?? "白班");
  const [productionReference, setProductionReference] = useState(initial?.productionReference ?? "");
  const [sourceReference, setSourceReference] = useState(initial?.sourceReference ?? "");
  const [reason, setReason] = useState(initial ? "修正人工核实的统计口径" : "建立人工核实 OEE 记录");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setPending(true); setError("");
    const total = Number(totalCount); const good = Number(goodCount); const planned = Number(plannedMinutes); const ideal = Number(idealSeconds);
    if (new Date(windowEnd) <= new Date(windowStart)) { setError("统计结束时间必须晚于开始时间。"); setPending(false); return; }
    if (good > total) { setError("合格产量不能大于总产量。"); setPending(false); return; }
    try {
      const common = { windowStart: new Date(windowStart).toISOString(), windowEnd: new Date(windowEnd).toISOString(),
        plannedProductionMinutes: planned, idealCycleSeconds: ideal, totalCount: total, goodCount: good,
        shiftName: shiftName.trim(), productionReference: productionReference.trim() || null,
        sourceReference: sourceReference.trim() || null, reason: reason.trim() };
      const saved = initial
        ? await submitEquipmentOeeMutation({ operation: "act", id: initial.id, action: "UPDATE",
            expectedVersion: initial.version, ...common })
        : await submitEquipmentOeeMutation({ operation: "create", assetId, ...common });
      onSaved(saved);
    } catch (caught) { setError(errorText(caught)); setPending(false); }
  }

  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentOeeDialog" role="dialog" aria-modal="true" aria-labelledby="oee-record-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="speed" size={22}/></span><div><h2 id="oee-record-title">{initial ? "修正 OEE 记录" : "建立 OEE 记录"}</h2><p>来源固定为人工核实；系统统一计算指标并保留责任证据。</p></div><GsButton className="iconButton" aria-label="关闭 OEE 表单" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <form onSubmit={submit}><div className="formGrid">
      <label className="formField formFieldFull"><span>设备<em>必填</em></span><RoundedSelect ariaLabel="OEE 设备" size="field" disabled={Boolean(initial)} options={assets.map((asset) => ({ value: asset.id, label: `${asset.assetCode} · ${asset.assetName}` }))} value={assetId} onValueChange={setAssetId}/></label>
      <label className="formField"><span>统计开始<em>必填</em></span><GsInput type="datetime-local" value={windowStart} onChange={(event) => setWindowStart(event.target.value)}/></label>
      <label className="formField"><span>统计结束<em>必填</em></span><GsInput type="datetime-local" value={windowEnd} onChange={(event) => setWindowEnd(event.target.value)}/></label>
      <label className="formField"><span>净计划生产分钟<em>必填</em></span><GsInput type="number" min="0.01" step="0.01" value={plannedMinutes} onChange={(event) => setPlannedMinutes(event.target.value)}/></label>
      <label className="formField"><span>理想节拍（秒/件）<em>必填</em></span><GsInput type="number" min="0.0001" step="0.0001" value={idealSeconds} onChange={(event) => setIdealSeconds(event.target.value)}/></label>
      <label className="formField"><span>总产量<em>必填</em></span><GsInput type="number" min="0" step="1" value={totalCount} onChange={(event) => setTotalCount(event.target.value)}/></label>
      <label className="formField"><span>合格产量<em>必填</em></span><GsInput type="number" min="0" step="1" value={goodCount} onChange={(event) => setGoodCount(event.target.value)}/></label>
      <label className="formField"><span>班次<em>必填</em></span><GsInput value={shiftName} maxLength={80} onChange={(event) => setShiftName(event.target.value)}/></label>
      <label className="formField"><span>生产引用</span><GsInput value={productionReference} maxLength={120} onChange={(event) => setProductionReference(event.target.value)} placeholder="订单、批次或班组单号"/></label>
      <label className="formField formFieldFull"><span>核实来源</span><GsInput value={sourceReference} maxLength={160} onChange={(event) => setSourceReference(event.target.value)} placeholder="纸质表、电子记录或现场核对说明"/></label>
      <label className="formField formFieldFull"><span>操作原因<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label>
    </div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="person_check" size={16}/>人工核实不会显示成自动采集</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">保存草稿</GsButton></div></footer></form>
  </section></GsModalHost>;
}

function DowntimeDialog({ record, initial, onClose, onSaved }: { record: EquipmentOeeRecord; initial?: EquipmentOeeDowntime;
  onClose: () => void; onSaved: (record: EquipmentOeeRecord) => void }) {
  const [start, setStart] = useState(localValue(initial?.startedAt ?? record.windowStart));
  const [end, setEnd] = useState(localValue(initial?.endedAt ?? new Date(new Date(record.windowStart).getTime() + 30 * 60000).toISOString()));
  const [category, setCategory] = useState<EquipmentOeeDowntimeCategory>(initial?.reasonCategory ?? "EQUIPMENT_FAILURE");
  const [party, setParty] = useState(initial?.responsibleParty ?? "设备组");
  const [description, setDescription] = useState(initial?.description ?? "记录现场停机原因与处置事实");
  const [reason, setReason] = useState(initial ? "修正停机时间与责任证据" : "登记停机时间与责任证据");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); setPending(true); setError(""); try {
    onSaved(await submitEquipmentOeeMutation({ operation: "act", id: record.id,
      action: initial ? "UPDATE_DOWNTIME" : "ADD_DOWNTIME", expectedVersion: record.version,
      downtimeId: initial?.id, downtimeStartedAt: new Date(start).toISOString(), downtimeEndedAt: new Date(end).toISOString(),
      reasonCategory: category, responsibleParty: party.trim(), description: description.trim(), reason: reason.trim() }));
  } catch (caught) { setError(errorText(caught)); setPending(false); } }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentGenerateDialog" role="dialog" aria-modal="true" aria-labelledby="oee-downtime-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="timer_off" size={22}/></span><div><h2 id="oee-downtime-title">{initial ? "修改停机事件" : "登记停机事件"}</h2><p>{record.recordNumber} · 停机必须位于统计窗口且不得重叠。</p></div><GsButton className="iconButton" aria-label="关闭停机表单" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid">
    <label className="formField"><span>停机开始<em>必填</em></span><GsInput type="datetime-local" value={start} onChange={(event) => setStart(event.target.value)}/></label><label className="formField"><span>停机结束<em>必填</em></span><GsInput type="datetime-local" value={end} onChange={(event) => setEnd(event.target.value)}/></label>
    <label className="formField"><span>原因分类<em>必填</em></span><RoundedSelect ariaLabel="停机原因" size="field" options={Object.entries(categoryLabels).map(([value, label]) => ({ value, label }))} value={category} onValueChange={(value) => setCategory(value as EquipmentOeeDowntimeCategory)}/></label>
    <label className="formField"><span>责任方<em>必填</em></span><GsInput value={party} maxLength={80} onChange={(event) => setParty(event.target.value)}/></label>
    <label className="formField formFieldFull"><span>事实说明<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={description} onChange={(event) => setDescription(event.target.value)}/></label>
    <label className="formField formFieldFull"><span>操作原因<em>必填</em></span><GsTextArea rows={2} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label>
  </div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>所有停机都占用净计划生产时间并影响可用率。</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">保存停机</GsButton></div></footer></form></section></GsModalHost>;
}

function ActionDialog({ record, action, downtime, onClose, onSaved }: { record: EquipmentOeeRecord;
  action: "SUBMIT" | "APPROVE" | "REJECT" | "REMOVE_DOWNTIME"; downtime?: EquipmentOeeDowntime;
  onClose: () => void; onSaved: (record: EquipmentOeeRecord) => void }) {
  const defaults = { SUBMIT: "提交已核实的 OEE 与停机证据", APPROVE: "复核统计口径并批准冻结指标",
    REJECT: "统计口径需要修正后重新提交", REMOVE_DOWNTIME: "移除误登记的停机事件并保留证据" };
  const [reason, setReason] = useState(defaults[action]); const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); setPending(true); setError(""); try {
    onSaved(await submitEquipmentOeeMutation({ operation: "act", id: record.id, action, expectedVersion: record.version,
      downtimeId: downtime?.id, reason: reason.trim() }));
  } catch (caught) { setError(errorText(caught)); setPending(false); } }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentGenerateDialog" role="dialog" aria-modal="true" aria-labelledby="oee-action-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action === "APPROVE" ? "verified" : action === "REJECT" ? "undo" : action === "REMOVE_DOWNTIME" ? "delete_sweep" : "send"} size={22}/></span><div><h2 id="oee-action-title">{actionLabels[action]}</h2><p>{record.recordNumber} · 当前版本 {record.version}</p></div><GsButton className="iconButton" aria-label="关闭 OEE 操作" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>责任说明<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>{action === "APPROVE" ? "审核后记录、停机和指标均冻结。" : "动作将记录责任人、时间和请求编号。"}</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent={action === "REJECT" || action === "REMOVE_DOWNTIME" ? "danger" : "primary"} loading={pending} htmlType="submit">确认{actionLabels[action]}</GsButton></div></footer></form></section></GsModalHost>;
}

function OeeDrawer({ record, onClose, onEdit, onDowntime, onAction }: { record: EquipmentOeeRecord; onClose: () => void;
  onEdit: () => void; onDowntime: (item?: EquipmentOeeDowntime) => void;
  onAction: (action: "SUBMIT" | "APPROVE" | "REJECT" | "REMOVE_DOWNTIME", item?: EquipmentOeeDowntime) => void }) {
  const editable = record.availableActions.includes("UPDATE");
  return <GsDrawer open onClose={onClose} ariaLabel="OEE 记录详情"><header className="recordDrawerHeader"><div><h2>{record.assetName}</h2><p>{record.recordNumber} · {record.assetCode} · {record.shiftName}</p></div><GsButton className="iconButton" aria-label="关闭 OEE 详情" onClick={onClose} htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <section className="drawerSection"><div className="oeeMetricStrip"><span><small>OEE</small><strong>{percent(record.oeeRate)}</strong></span><span><small>可用率</small><strong>{percent(record.availabilityRate)}</strong></span><span><small>性能率</small><strong>{percent(record.performanceRate)}</strong></span><span><small>质量率</small><strong>{percent(record.qualityRate)}</strong></span></div><dl className="detailLedger"><div><dt>状态 / 来源</dt><dd>{statusLabels[record.status]} · 人工核实</dd></div><div><dt>统计窗口</dt><dd>{dateText(record.windowStart)} — {dateText(record.windowEnd)}</dd></div><div><dt>计划 / 运行</dt><dd>{record.plannedProductionMinutes} / {record.runMinutes} 分钟</dd></div><div><dt>停机合计</dt><dd>{record.downtimeMinutes} 分钟</dd></div><div><dt>理想节拍</dt><dd>{record.idealCycleSeconds} 秒/件</dd></div><div><dt>产量</dt><dd>{record.goodCount} 合格 / {record.totalCount} 总数</dd></div><div><dt>生产引用</dt><dd>{record.productionReference ?? "未填写"}</dd></div><div><dt>核实来源</dt><dd>{record.sourceReference ?? "未填写"}</dd></div></dl>{record.rejectionReason ? <div className="formError" role="status">驳回原因：{record.rejectionReason}</div> : null}</section>
    {record.availableActions.length ? <section className="drawerSection"><div className="sectionTitleCompact"><div><h3>可执行动作</h3><small>由服务端权限与状态机返回</small></div></div><div className="equipmentAlertActions">{editable ? <><GsButton onClick={onEdit} htmlType="button">编辑口径</GsButton><GsButton onClick={() => onDowntime()} htmlType="button">登记停机</GsButton><GsButton intent="primary" onClick={() => onAction("SUBMIT")} htmlType="button">提交审核</GsButton></> : null}{record.availableActions.includes("APPROVE") ? <GsButton intent="primary" onClick={() => onAction("APPROVE")} htmlType="button">审核通过</GsButton> : null}{record.availableActions.includes("REJECT") ? <GsButton intent="danger" onClick={() => onAction("REJECT")} htmlType="button">驳回修正</GsButton> : null}</div></section> : null}
    <section className="drawerSection"><div className="sectionTitleCompact"><div><h3>停机证据</h3><small>{record.downtimes.length} 条 · 不得越界或重叠</small></div></div>{record.downtimes.length ? <div className="oeeDowntimeList">{record.downtimes.map((item) => <article key={item.id}><span><strong>{categoryLabels[item.reasonCategory]} · {item.durationMinutes} 分钟</strong><small>{dateText(item.startedAt)} — {dateText(item.endedAt)}</small><p>{item.description} · 责任方 {item.responsibleParty}</p></span>{editable ? <div><GsButton aria-label="修改停机" onClick={() => onDowntime(item)} htmlType="button"><MaterialIcon name="edit" size={17}/></GsButton><GsButton aria-label="移除停机" onClick={() => onAction("REMOVE_DOWNTIME", item)} htmlType="button"><MaterialIcon name="delete" size={17}/></GsButton></div> : null}</article>)}</div> : <p className="drawerEmpty">尚未登记停机；无停机时可直接提交审核。</p>}</section>
    <section className="drawerSection"><div className="sectionTitleCompact"><div><h3>不可变事件</h3><small>{record.events.length} 条责任证据</small></div></div><div className="equipmentAlertTimeline">{record.events.map((event) => <div key={event.id}><span className="timelineDot"/><span><strong>{event.action}</strong><small>{dateText(event.occurredAt)}</small><p>{event.reason}</p><em>请求 {event.requestId}</em></span></div>)}</div></section>
  </GsDrawer>;
}

export function EquipmentOeeWorkspace({ initialData }: { initialData: EquipmentOeePageData }) {
  const [data, setData] = useState(initialData); const [query, setQuery] = useState(""); const [status, setStatus] = useState("ALL");
  const [refreshing, setRefreshing] = useState(false); const [error, setError] = useState(""); const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<EquipmentOeeRecord | null>(null); const [editOpen, setEditOpen] = useState(false);
  const [downtimeEdit, setDowntimeEdit] = useState<EquipmentOeeDowntime | "new" | null>(null);
  const [action, setAction] = useState<{ type: "SUBMIT" | "APPROVE" | "REJECT" | "REMOVE_DOWNTIME"; item?: EquipmentOeeDowntime } | null>(null);
  const backend = data.source === "backend" ? data : null;
  const filtered = useMemo(() => backend?.page.items.filter((item) => (status === "ALL" || item.status === status)
    && (!query.trim() || `${item.recordNumber} ${item.assetCode} ${item.assetName} ${item.shiftName} ${item.productionReference ?? ""}`.toLowerCase().includes(query.trim().toLowerCase()))) ?? [], [backend, query, status]);

  async function refresh() { setRefreshing(true); setError(""); try { setData(await refreshEquipmentOeePage()); } catch (caught) { setError(errorText(caught)); } finally { setRefreshing(false); } }
  async function open(item: EquipmentOeeRecord) { setError(""); try { setDetail(await loadEquipmentOeeDetail(item.id)); } catch (caught) { setError(errorText(caught)); } }
  async function saved(record: EquipmentOeeRecord) { setDetail(record); setCreateOpen(false); setEditOpen(false); setDowntimeEdit(null); setAction(null); await refresh(); }

  if (!backend) return <section className="backendUnavailableState" role="alert"><MaterialIcon name="cloud_off" size={26}/><strong>OEE 服务暂时不可用</strong><p>{data.source === "unavailable" ? data.message : "尚未取得 OEE 数据。"}</p><GsButton onClick={refresh} loading={refreshing} htmlType="button">重新检查</GsButton></section>;
  return <section className="equipmentOeeWorkspace"><header className="telemetryHeader"><div><span>设备与资产</span><h2>OEE 与停机</h2><p>用核实、审批和停机责任证据建立可信指标，为未来自动来源保留稳定模型。</p></div><div><GsButton onClick={refresh} loading={refreshing} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新</GsButton>{backend.page.canMaintain ? <GsButton intent="primary" onClick={() => setCreateOpen(true)} disabled={!backend.assets.items.length} htmlType="button"><MaterialIcon name="add" size={17}/>建立记录</GsButton> : null}</div></header>
    <section className="telemetryTruthBanner oeeTruthBanner"><MaterialIcon name="person_check" size={23}/><div><strong>当前来源：人工核实</strong><p>指标不由仿真或稀疏遥测推断；真实设备、生产和质量来源以后写入同一模型，不影响当前正式使用。</p></div><span>现场自动来源未接入</span></section>
    <div className="equipmentAlertMetrics oeeMetrics"><span><small>已审核 OEE</small><strong>{percent(backend.page.averageOeeRate)}</strong><em>{backend.page.approvedRecordCount} 条冻结记录</em></span><span><small>平均可用率</small><strong>{percent(backend.page.averageAvailabilityRate)}</strong><em>计划时间与停机</em></span><span><small>平均性能率</small><strong>{percent(backend.page.averagePerformanceRate)}</strong><em>节拍与运行时间</em></span><span><small>平均质量率</small><strong>{percent(backend.page.averageQualityRate)}</strong><em>合格数与总数</em></span></div>
    <div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索 OEE" placeholder="搜索记录、设备、班次或生产引用" value={query} onChange={(event) => setQuery(event.target.value)}/></div><RoundedSelect ariaLabel="OEE 状态" options={[{ value: "ALL", label: "全部状态" }, ...Object.entries(statusLabels).map(([value, label]) => ({ value, label }))]} value={status} onValueChange={setStatus}/></div>
    {error ? <div className="formError" role="alert">{error}</div> : null}
    <div className="oeeTable" role="table" aria-label="OEE 记录列表"><div className="oeeTableHeader" role="row"><span>记录 / 设备</span><span>统计窗口</span><span>时间事实</span><span>OEE 构成</span><span>状态 / 来源</span><span>操作</span></div>{filtered.length ? filtered.map((item) => <div className="oeeTableRow" role="row" key={item.id}><span><strong>{item.recordNumber}</strong><small>{item.assetCode} · {item.assetName}</small></span><span><strong>{item.shiftName}</strong><small>{dateText(item.windowStart)} — {dateText(item.windowEnd)}</small></span><span><strong>运行 {item.runMinutes} 分钟</strong><small>计划 {item.plannedProductionMinutes} · 停机 {item.downtimeMinutes}</small></span><span><strong>{percent(item.oeeRate)}</strong><small>A {percent(item.availabilityRate)} · P {percent(item.performanceRate)} · Q {percent(item.qualityRate)}</small></span><span><em className={`businessStatus businessStatus${statusTones[item.status]}`}>{statusLabels[item.status]}</em><small>人工核实</small></span><span className="businessRowActions"><GsButton aria-label={`查看${item.recordNumber}`} onClick={() => open(item)} htmlType="button"><MaterialIcon name="visibility" size={18}/></GsButton></span></div>) : <div className="businessEmptyState"><MaterialIcon name="speed" size={28}/><strong>没有符合条件的 OEE 记录</strong><p>{backend.page.canMaintain ? "建立首条人工核实记录，登记停机后提交审核。" : "当前工作区尚未建立可查看记录。"}</p></div>}</div>
    {createOpen ? <RecordDialog assets={backend.assets.items} onClose={() => setCreateOpen(false)} onSaved={saved}/> : null}
    {detail ? <OeeDrawer record={detail} onClose={() => setDetail(null)} onEdit={() => setEditOpen(true)} onDowntime={(item) => setDowntimeEdit(item ?? "new")} onAction={(type, item) => setAction({ type, item })}/> : null}
    {detail && editOpen ? <RecordDialog assets={backend.assets.items} initial={detail} onClose={() => setEditOpen(false)} onSaved={saved}/> : null}
    {detail && downtimeEdit ? <DowntimeDialog record={detail} initial={downtimeEdit === "new" ? undefined : downtimeEdit} onClose={() => setDowntimeEdit(null)} onSaved={saved}/> : null}
    {detail && action ? <ActionDialog record={detail} action={action.type} downtime={action.item} onClose={() => setAction(null)} onSaved={saved}/> : null}
  </section>;
}
