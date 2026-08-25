"use client";

import { type FormEvent, useMemo, useState } from "react";

import type {
  EquipmentAsset,
  EquipmentAssetAction,
  EquipmentAssetCategory,
  EquipmentAssetPage,
  EquipmentOperatingStatus,
} from "@/lib/contracts";
import type { EquipmentAssetPageData, EquipmentAssetWritePayload } from "@/services/equipment-asset-server-service";
import {
  EquipmentAssetClientError,
  loadEquipmentAssetDetail,
  refreshEquipmentAssets,
  submitEquipmentAssetMutation,
} from "@/services/equipment-asset-client-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsDrawer, GsInput, GsModalHost, GsPagination, GsTextArea } from "./ui";

const statusLabels: Record<EquipmentOperatingStatus, string> = {
  IDLE: "闲置", RUNNING: "运行中", DOWN: "故障", MAINTENANCE: "维修中", INACTIVE: "已停用",
};
const statusTones: Record<EquipmentOperatingStatus, string> = {
  IDLE: "info", RUNNING: "good", DOWN: "danger", MAINTENANCE: "warn", INACTIVE: "info",
};
const categoryLabels: Record<EquipmentAssetCategory, string> = {
  PRODUCTION: "生产设备", QUALITY: "质量设备", UTILITY: "动力设备", LOGISTICS: "物流设备", OTHER: "其他设备",
};
const actionLabels: Record<EquipmentAssetAction, string> = {
  START: "开机", STOP: "停机", REPORT_BREAKDOWN: "报故障", START_MAINTENANCE: "开始维修", COMPLETE_MAINTENANCE: "维修完成", INACTIVATE: "停用设备",
};
const eventLabels: Record<string, string> = {
  CREATED: "建立台账", UPDATED: "更新台账", STARTED: "设备开机", STOPPED: "设备停机",
  BREAKDOWN_REPORTED: "报告故障", MAINTENANCE_STARTED: "开始维修", MAINTENANCE_COMPLETED: "维修完成", INACTIVATED: "停用设备",
};

function actionsFor(status: EquipmentOperatingStatus): EquipmentAssetAction[] {
  if (status === "IDLE") return ["START", "REPORT_BREAKDOWN", "INACTIVATE"];
  if (status === "RUNNING") return ["STOP", "REPORT_BREAKDOWN"];
  return [];
}

function errorText(error: unknown) {
  if (error instanceof EquipmentAssetClientError) return error.requestId ? `${error.message}（请求编号：${error.requestId}）` : error.message;
  return error instanceof Error ? error.message : "设备操作失败";
}

type AssetFormState = {
  assetCode: string; assetName: string; category: EquipmentAssetCategory; manufacturer: string; model: string;
  serialNumber: string; workCenterCode: string; workCenterName: string; location: string; responsiblePerson: string;
  commissioningDate: string; reason: string;
};

function AssetFormDialog({ asset, onClose, onSaved }: { asset: EquipmentAsset | null; onClose: () => void; onSaved: (asset: EquipmentAsset) => void }) {
  const [form, setForm] = useState<AssetFormState>({
    assetCode: asset?.assetCode ?? "", assetName: asset?.assetName ?? "", category: asset?.category ?? "PRODUCTION",
    manufacturer: asset?.manufacturer ?? "", model: asset?.model ?? "", serialNumber: asset?.serialNumber ?? "",
    workCenterCode: asset?.workCenterCode ?? "", workCenterName: asset?.workCenterName ?? "", location: asset?.location ?? "",
    responsiblePerson: asset?.responsiblePerson ?? "", commissioningDate: asset?.commissioningDate ?? "", reason: "",
  });
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const set = (key: keyof AssetFormState, value: string) => setForm((current) => ({ ...current, [key]: value }));

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!form.assetCode.trim() || !form.assetName.trim() || !form.location.trim() || !form.responsiblePerson.trim() || form.reason.trim().length < 4) {
      setError("请填写设备编码、名称、位置、责任人和至少 4 个字符的原因。"); return;
    }
    const payload: EquipmentAssetWritePayload = {
      assetName: form.assetName.trim(), category: form.category, manufacturer: form.manufacturer.trim() || null,
      model: form.model.trim() || null, serialNumber: form.serialNumber.trim() || null,
      workCenterCode: form.workCenterCode.trim() || null, workCenterName: form.workCenterName.trim() || null,
      location: form.location.trim(), responsiblePerson: form.responsiblePerson.trim(),
      commissioningDate: form.commissioningDate || null, reason: form.reason.trim(),
    };
    setPending(true); setError("");
    try {
      onSaved(await submitEquipmentAssetMutation(asset
        ? { operation: "update", id: asset.id, payload: { ...payload, expectedVersion: asset.version } }
        : { operation: "create", assetCode: form.assetCode.trim(), payload }));
    } catch (reason) { setError(errorText(reason)); setPending(false); }
  }

  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentAssetDialog" role="dialog" aria-modal="true" aria-labelledby="equipment-form-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={asset ? "precision_manufacturing" : "add_circle"} size={22}/></span><div><h2 id="equipment-form-title">{asset ? "编辑设备台账" : "新建设备台账"}</h2><p>{asset ? `${asset.assetCode} · 版本 ${asset.version}` : "新建设备固定从闲置状态开始"}</p></div><GsButton className="iconButton" onClick={onClose} disabled={pending} aria-label="关闭设备表单" htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <form onSubmit={submit}><div className="formGrid equipmentFormGrid">
      <label className="formField"><span>设备编码<em>必填</em></span><GsInput value={form.assetCode} disabled={Boolean(asset)} maxLength={40} placeholder="例如 EQ-CNC-008" onChange={(event) => set("assetCode", event.target.value)}/><small>租户内唯一，创建后不可修改。</small></label>
      <label className="formField"><span>设备名称<em>必填</em></span><GsInput value={form.assetName} maxLength={120} onChange={(event) => set("assetName", event.target.value)}/></label>
      <label className="formField"><span>设备类别<em>必填</em></span><RoundedSelect ariaLabel="设备类别" options={Object.values(categoryLabels)} value={categoryLabels[form.category]} onValueChange={(value) => { const code = Object.entries(categoryLabels).find(([, label]) => label === value)?.[0] as EquipmentAssetCategory | undefined; if (code) setForm((current) => ({ ...current, category: code })); }}/></label>
      <label className="formField"><span>责任人<em>必填</em></span><GsInput value={form.responsiblePerson} maxLength={80} onChange={(event) => set("responsiblePerson", event.target.value)}/></label>
      <label className="formField formFieldFull"><span>设备位置<em>必填</em></span><GsInput value={form.location} maxLength={160} placeholder="车间 / 区域 / 工位" onChange={(event) => set("location", event.target.value)}/></label>
      <label className="formField"><span>工作中心编码</span><GsInput value={form.workCenterCode} maxLength={40} onChange={(event) => set("workCenterCode", event.target.value)}/></label>
      <label className="formField"><span>工作中心名称</span><GsInput value={form.workCenterName} maxLength={120} onChange={(event) => set("workCenterName", event.target.value)}/></label>
      <label className="formField"><span>制造商</span><GsInput value={form.manufacturer} maxLength={120} onChange={(event) => set("manufacturer", event.target.value)}/></label>
      <label className="formField"><span>型号</span><GsInput value={form.model} maxLength={120} onChange={(event) => set("model", event.target.value)}/></label>
      <label className="formField"><span>序列号</span><GsInput value={form.serialNumber} maxLength={120} onChange={(event) => set("serialNumber", event.target.value)}/></label>
      <label className="formField"><span>投产日期</span><GsInput type="date" value={form.commissioningDate} onChange={(event) => set("commissioningDate", event.target.value)}/></label>
      <label className="formField formFieldFull"><span>{asset ? "变更原因" : "建档原因"}<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={form.reason} onChange={(event) => set("reason", event.target.value)}/><small>原因会随操作者和请求编号进入不可变事件。</small></label>
    </div><div className="mrpRunTruthNotice"><MaterialIcon name="sensors_off" size={18}/><span><strong>人工维护事实</strong>本表单不连接 PLC、网关或 Broker，也不产生自动遥测。</span></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="history" size={16}/>乐观锁保护并发修改</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">{pending ? "正在保存" : "保存设备"}</GsButton></div></footer></form>
  </section></GsModalHost>;
}

function ActionDialog({ asset, action, onClose, onSaved }: { asset: EquipmentAsset; action: EquipmentAssetAction; onClose: () => void; onSaved: (asset: EquipmentAsset) => void }) {
  const [reason, setReason] = useState(""); const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent) { event.preventDefault(); if (reason.trim().length < 4) { setError("请填写至少 4 个字符的状态变更原因。"); return; } setPending(true); setError(""); try { onSaved(await submitEquipmentAssetMutation({ operation: "action", id: asset.id, action, reason: reason.trim(), expectedVersion: asset.version })); } catch (failure) { setError(errorText(failure)); setPending(false); } }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentActionDialog" role="dialog" aria-modal="true" aria-labelledby="equipment-action-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action === "REPORT_BREAKDOWN" ? "report_problem" : "published_with_changes"} size={22}/></span><div><h2 id="equipment-action-title">{actionLabels[action]}</h2><p>{asset.assetCode} · {statusLabels[asset.operatingStatus]} · 版本 {asset.version}</p></div><GsButton className="iconButton" onClick={onClose} disabled={pending} aria-label="关闭状态动作" htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>动作原因<em>必填</em></span><GsTextArea rows={4} maxLength={500} value={reason} placeholder="说明现场事实、依据或处置结果" onChange={(event) => setReason(event.target.value)}/></label></div><div className="mrpRunTruthNotice"><MaterialIcon name="person_edit" size={18}/><span><strong>人工状态流转</strong>提交只改变贯序设备事实，不向设备发送远程控制命令。</span></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>请求会记录操作者、原因与请求编号</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent={action === "INACTIVATE" || action === "REPORT_BREAKDOWN" ? "danger" : "primary"} loading={pending} htmlType="submit">确认{actionLabels[action]}</GsButton></div></footer></form></section></GsModalHost>;
}

function AssetDetailDrawer({ asset, canMaintain, loading, error, onClose, onEdit, onAction }: { asset: EquipmentAsset; canMaintain: boolean; loading: boolean; error: string; onClose: () => void; onEdit: () => void; onAction: (action: EquipmentAssetAction) => void }) {
  const actions = actionsFor(asset.operatingStatus);
  return <GsDrawer open title={`${asset.assetCode} · ${asset.assetName}`} onClose={onClose} className="equipmentAssetDrawer"><div className="equipmentDetailStatus"><em className={`businessStatus businessStatus${statusTones[asset.operatingStatus]}`}>{statusLabels[asset.operatingStatus]}</em><span>人工维护状态</span><small>最近变更 {new Date(asset.statusChangedAt).toLocaleString("zh-CN", { hour12: false })}</small></div>
    <dl className="detailLedger"><div><dt>类别</dt><dd>{categoryLabels[asset.category]}</dd></div><div><dt>责任人</dt><dd>{asset.responsiblePerson}</dd></div><div><dt>位置</dt><dd>{asset.location}</dd></div><div><dt>工作中心</dt><dd>{asset.workCenterName || "未关联"}{asset.workCenterCode ? ` · ${asset.workCenterCode}` : ""}</dd></div><div><dt>制造商 / 型号</dt><dd>{[asset.manufacturer, asset.model].filter(Boolean).join(" · ") || "未填写"}</dd></div><div><dt>序列号</dt><dd>{asset.serialNumber || "未填写"}</dd></div><div><dt>投产日期</dt><dd>{asset.commissioningDate || "未填写"}</dd></div><div><dt>并发版本</dt><dd>{asset.version}</dd></div></dl>
    {canMaintain ? <section className="drawerSection"><div className="sectionTitleCompact"><h3>可执行动作</h3><span>以后端状态机为准</span></div><div className="equipmentActionButtons"><GsButton disabled={asset.operatingStatus === "RUNNING" || asset.operatingStatus === "INACTIVE"} onClick={onEdit} htmlType="button"><MaterialIcon name="edit" size={17}/>编辑台账</GsButton>{actions.map((action) => <GsButton key={action} intent={action === "REPORT_BREAKDOWN" || action === "INACTIVATE" ? "danger" : "secondary"} onClick={() => onAction(action)} htmlType="button">{actionLabels[action]}</GsButton>)}</div>{asset.operatingStatus === "DOWN" || asset.operatingStatus === "MAINTENANCE" ? <p className="equipmentWorkOrderNarrative">维修开工、完工和验收请从“维修工单”推进，设备台账不允许绕过工单证据链。</p> : null}</section> : <section className="workspaceRoleBoundary"><MaterialIcon name="lock" size={19}/><div><strong>当前角色为只读</strong><p>设备经理、生产经理或管理员可以维护台账和状态。</p></div></section>}
    <section className="drawerSection"><div className="sectionTitleCompact"><h3>状态与变更证据</h3><span>{loading ? "加载中" : `${asset.events.length} 条`}</span></div>{error ? <div className="formError" role="alert">{error}</div> : null}<ol className="evidenceTimeline equipmentTimeline">{asset.events.map((event) => <li key={event.id}><span/><div><strong>{eventLabels[event.action] ?? event.action} · {statusLabels[event.toStatus]}</strong><small>{event.reason}</small><small>请求编号：{event.requestId}</small></div><time>{new Date(event.occurredAt).toLocaleString("zh-CN", { hour12: false })}</time></li>)}</ol>{!loading && !error && !asset.events.length ? <div className="businessEmptyState"><MaterialIcon name="history_toggle_off" size={25}/><strong>暂无事件详情</strong><p>关闭后重新打开详情即可再次加载。</p></div> : null}</section>
  </GsDrawer>;
}

function UnavailableState({ data, onRecovered }: { data: Extract<EquipmentAssetPageData, { source: "unavailable" }>; onRecovered: (page: EquipmentAssetPage) => void }) {
  const [pending, setPending] = useState(false); const [message, setMessage] = useState(data.message);
  async function retry() { setPending(true); try { onRecovered(await refreshEquipmentAssets()); } catch (error) { setMessage(errorText(error)); setPending(false); } }
  return <div className="businessPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="precision_manufacturing" size={23}/></span><div><h2>设备台账</h2><p>设备事实、责任和人工受控状态。</p></div></div></header><section className="backendUnavailableState" role="alert"><MaterialIcon name={data.status === 403 ? "lock" : "cloud_off"} size={30}/><strong>{data.status === 403 ? "当前角色无权读取设备台账" : "设备台账服务暂时不可用"}</strong><p>{message}</p><small>请求编号：{data.requestId}</small><GsButton onClick={retry} loading={pending} htmlType="button">重新检查</GsButton></section></div>;
}

export function EquipmentAssetWorkspace({ initialData, view }: { initialData: EquipmentAssetPageData; view: "assets" | "status" }) {
  const [pageData, setPageData] = useState<EquipmentAssetPage | null>(initialData.page);
  const [unavailable, setUnavailable] = useState(initialData.source === "unavailable" ? initialData : null);
  const [query, setQuery] = useState(""); const [status, setStatus] = useState("全部状态"); const [category, setCategory] = useState("全部类别");
  const [page, setPage] = useState(1); const [pageSize, setPageSize] = useState(10); const [refreshing, setRefreshing] = useState(false);
  const [dialog, setDialog] = useState<{ type: "create" } | { type: "edit"; asset: EquipmentAsset } | { type: "action"; asset: EquipmentAsset; action: EquipmentAssetAction } | null>(null);
  const [selected, setSelected] = useState<EquipmentAsset | null>(null); const [detailLoading, setDetailLoading] = useState(false); const [detailError, setDetailError] = useState(""); const [toast, setToast] = useState("");
  const statusOptions = ["全部状态", ...Object.values(statusLabels)]; const categoryOptions = ["全部类别", ...Object.values(categoryLabels)];

  const filtered = useMemo(() => !pageData ? [] : pageData.items.filter((asset) => {
    const text = `${asset.assetCode}${asset.assetName}${asset.location}${asset.responsiblePerson}${asset.workCenterCode ?? ""}${asset.workCenterName ?? ""}`.toLowerCase();
    return (!query.trim() || text.includes(query.trim().toLowerCase())) && (status === "全部状态" || statusLabels[asset.operatingStatus] === status) && (category === "全部类别" || categoryLabels[asset.category] === category);
  }), [pageData, query, status, category]);

  if (!pageData && unavailable) return <UnavailableState data={unavailable} onRecovered={(next) => { setPageData(next); setUnavailable(null); }}/>;
  if (!pageData) return null;
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize)); const currentPage = Math.min(page, totalPages); const rows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const counts = (target: EquipmentOperatingStatus) => pageData.items.filter((asset) => asset.operatingStatus === target).length;

  function replace(saved: EquipmentAsset) { setPageData((current) => current ? { ...current, items: current.items.some((item) => item.id === saved.id) ? current.items.map((item) => item.id === saved.id ? { ...saved, events: [] } : item) : [{ ...saved, events: [] }, ...current.items], totalElements: current.items.some((item) => item.id === saved.id) ? current.totalElements : current.totalElements + 1 } : current); setSelected(saved); setDialog(null); setToast(`${saved.assetCode} 的设备事实已更新`); window.setTimeout(() => setToast(""), 2600); }
  async function openDetail(asset: EquipmentAsset) { setSelected(asset); setDetailLoading(true); setDetailError(""); try { setSelected(await loadEquipmentAssetDetail(asset.id)); } catch (error) { setDetailError(errorText(error)); } finally { setDetailLoading(false); } }
  async function refresh() { setRefreshing(true); try { setPageData(await refreshEquipmentAssets()); setToast("设备台账与状态已刷新"); window.setTimeout(() => setToast(""), 2200); } catch (error) { setToast(errorText(error)); } finally { setRefreshing(false); } }

  return <div className="businessPage equipmentAssetPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name={view === "status" ? "monitor_heart" : "precision_manufacturing"} size={23}/></span><div><h2>{view === "status" ? "设备状态" : "设备台账"}</h2><p>{view === "status" ? "按现场责任维护运行、故障与维修状态。" : "统一维护设备身份、位置、责任和当前状态。"}</p></div></div><div className="pageHeadingActions"><GsButton onClick={refresh} loading={refreshing} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新</GsButton>{pageData.canMaintain ? <GsButton intent="primary" onClick={() => setDialog({ type: "create" })} htmlType="button"><MaterialIcon name="add" size={17}/>新建设备</GsButton> : null}</div></header>
    <section className="equipmentTruthBanner"><MaterialIcon name="sensors_off" size={21}/><div><strong>人工维护状态</strong><p>真实设备采集、网关、点位、报警和 OEE 尚未接入；此处时间表示人员提交业务事实的时间。</p></div><span>遥测未接入</span></section>
    <section className="businessMetrics"><div><small>设备总数</small><strong className="businessMetricinfo">{pageData.items.length}</strong><em>当前工作区</em></div><div><small>运行中</small><strong className="businessMetricgood">{counts("RUNNING")}</strong><em>人工确认开机</em></div><div><small>故障</small><strong className={counts("DOWN") ? "businessMetricdanger" : "businessMetricinfo"}>{counts("DOWN")}</strong><em>需要现场处置</em></div><div><small>维修中</small><strong className={counts("MAINTENANCE") ? "businessMetricwarn" : "businessMetricinfo"}>{counts("MAINTENANCE")}</strong><em>等待维修完成</em></div></section>
    <section className="businessLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索设备" value={query} placeholder="搜索编码、名称、位置、责任人或工作中心" onChange={(event) => { setQuery(event.target.value); setPage(1); }}/></div><div className="equipmentFilters"><RoundedSelect ariaLabel="设备状态" options={statusOptions} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }}/><RoundedSelect ariaLabel="设备类别" options={categoryOptions} value={category} onValueChange={(value) => { setCategory(value); setPage(1); }}/></div></div>
      <div className="equipmentAssetTable" role="table" aria-label="设备台账列表"><div className="equipmentAssetTableHeader" role="row"><span>设备 / 类别</span><span>人工状态</span><span>位置 / 工作中心</span><span>责任 / 最近变更</span><span>操作</span></div>{rows.length ? rows.map((asset) => <div className="equipmentAssetTableRow" role="row" key={asset.id}><span><strong>{asset.assetName}</strong><small>{asset.assetCode} · {categoryLabels[asset.category]}</small></span><span><em className={`businessStatus businessStatus${statusTones[asset.operatingStatus]}`}>{statusLabels[asset.operatingStatus]}</em><small>版本 {asset.version}</small></span><span><strong>{asset.location}</strong><small>{asset.workCenterName || "未关联工作中心"}{asset.workCenterCode ? ` · ${asset.workCenterCode}` : ""}</small></span><span><strong>{asset.responsiblePerson}</strong><small>{new Date(asset.statusChangedAt).toLocaleString("zh-CN", { hour12: false })}</small></span><span className="businessRowActions"><GsButton onClick={() => openDetail(asset)} aria-label={`查看${asset.assetName}`} htmlType="button"><MaterialIcon name="visibility" size={18}/></GsButton>{pageData.canMaintain && actionsFor(asset.operatingStatus).length ? <GsButton onClick={() => setDialog({ type: "action", asset, action: actionsFor(asset.operatingStatus)[0] })} aria-label={`处理${asset.assetName}`} htmlType="button"><MaterialIcon name="published_with_changes" size={18}/></GsButton> : null}</span></div>) : <div className="businessEmptyState"><MaterialIcon name="precision_manufacturing" size={28}/><strong>没有符合条件的设备</strong><p>{pageData.items.length ? "调整搜索、状态或类别筛选。" : pageData.canMaintain ? "建立第一台设备台账后，状态动作会形成可追溯证据。" : "当前工作区尚未建立设备台账。"}</p>{!pageData.items.length && pageData.canMaintain ? <GsButton intent="primary" onClick={() => setDialog({ type: "create" })} htmlType="button">新建设备</GsButton> : null}</div>}</div>
      <footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextSize) => { setPage(nextPage); setPageSize(nextSize); }}/></footer>
    </section>
    {!pageData.canMaintain ? <section className="workspaceRoleBoundary"><MaterialIcon name="shield_lock" size={20}/><div><strong>当前角色只读</strong><p>设备经理、生产经理和管理员可以维护设备台账与人工状态；后端权限是唯一可信边界。</p></div></section> : null}
    {selected ? <AssetDetailDrawer asset={selected} canMaintain={pageData.canMaintain} loading={detailLoading} error={detailError} onClose={() => setSelected(null)} onEdit={() => setDialog({ type: "edit", asset: selected })} onAction={(action) => setDialog({ type: "action", asset: selected, action })}/> : null}
    {dialog?.type === "create" ? <AssetFormDialog asset={null} onClose={() => setDialog(null)} onSaved={replace}/> : null}
    {dialog?.type === "edit" ? <AssetFormDialog asset={dialog.asset} onClose={() => setDialog(null)} onSaved={replace}/> : null}
    {dialog?.type === "action" ? <ActionDialog asset={dialog.asset} action={dialog.action} onClose={() => setDialog(null)} onSaved={replace}/> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="info" size={18}/>{toast}</div> : null}
  </div>;
}
