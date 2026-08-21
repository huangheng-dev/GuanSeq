"use client";
import { GsButton, GsCheckbox, GsDrawerHost, GsInput, GsModalHost, GsTextArea, GsPagination } from "./ui";
/* eslint-disable @typescript-eslint/no-unused-expressions -- collection selection callbacks use add/delete as branch actions */
import { type FormEvent, useMemo, useRef, useState } from "react";
import Link from "next/link";
import type { FinalInspectionRecord } from "@/lib/contracts";
import { submitFinalInspection } from "@/services/production-execution-client-service";
import type { FinalInspectionPageData } from "@/services/production-execution-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const statusLabels: Record<FinalInspectionRecord["status"], string> = { PENDING: "待检验", COMPLETED: "已判定" };
const resultLabels = { PASSED: "合格", PARTIALLY_PASSED: "部分合格", FAILED: "不合格" } as const;
function todayText() { return new Date().toISOString().slice(0, 10); }
function InspectionForm({ inspection, onClose, onSaved }: {
    inspection: FinalInspectionRecord;
    onClose: () => void;
    onSaved: (item: FinalInspectionRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const [accepted, setAccepted] = useState(String(inspection.inspectionQuantity));
    const [rejected, setRejected] = useState("0");
    const [inspector, setInspector] = useState("吴倩");
    const [defect, setDefect] = useState("");
    const [conclusion, setConclusion] = useState("按完工检验要求完成全数检查，符合项准予放行。");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        const acceptedQuantity = Number(accepted);
        const rejectedQuantity = Number(rejected);
        setError("");
        if (!Number.isFinite(acceptedQuantity) || !Number.isFinite(rejectedQuantity) || acceptedQuantity < 0 || rejectedQuantity < 0 || acceptedQuantity + rejectedQuantity !== inspection.inspectionQuantity) {
            setError(`合格与不合格数量之和必须等于送检数量 ${inspection.inspectionQuantity} ${inspection.unit}。`);
            return;
        }
        if (!inspector.trim() || !conclusion.trim() || (rejectedQuantity > 0 && !defect.trim())) {
            setError("请填写检验员、结论；存在不合格数量时还必须填写缺陷说明。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitFinalInspection({ id: inspection.id, acceptedQuantity, rejectedQuantity, inspector: inspector.trim(), defectDescription: defect.trim() || null, conclusion: conclusion.trim(), expectedVersion: inspection.version }));
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "检验判定提交失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}><section ref={ref} className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="inspection-form-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="verified" size={22}/></span><div><h2 id="inspection-form-title">提交完工检验</h2><p>{inspection.inspectionNumber} · {inspection.materialCode} · 送检 {inspection.inspectionQuantity} {inspection.unit}</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭完工检验表单" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid"><label className="formField"><span>合格数量<em>必填</em></span><GsInput type="number" min="0" max={inspection.inspectionQuantity} step="0.0001" value={accepted} onChange={(event) => setAccepted(event.target.value)}/></label><label className="formField"><span>不合格数量<em>必填</em></span><GsInput type="number" min="0" max={inspection.inspectionQuantity} step="0.0001" value={rejected} onChange={(event) => setRejected(event.target.value)}/></label><label className="formField formFieldFull"><span>检验员<em>必填</em></span><GsInput maxLength={80} value={inspector} onChange={(event) => setInspector(event.target.value)}/></label><label className="formField formFieldFull"><span>缺陷说明{Number(rejected) > 0 ? <em>必填</em> : null}</span><GsTextArea rows={3} maxLength={500} value={defect} onChange={(event) => setDefect(event.target.value)} placeholder="存在不合格数量时，记录缺陷位置、现象和代码"/></label><label className="formField formFieldFull"><span>检验结论<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={conclusion} onChange={(event) => setConclusion(event.target.value)}/></label></div>{error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="lock" size={16}/>检验判定提交后不可覆盖</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "正在提交" : "确认判定"}</GsButton></div></footer></form></section></GsModalHost>;
}
function InspectionDrawer({ inspection, onClose, onInspect }: {
    inspection: FinalInspectionRecord;
    onClose: () => void;
    onInspect: () => void;
}) {
    const ref = useRef<HTMLElement>(null);
    return <GsDrawerHost onClose={onClose}><aside ref={ref} className="recordDrawer" role="dialog" aria-modal="true" aria-labelledby="inspection-detail-title" onMouseDown={(event) => event.stopPropagation()}><header className="recordDrawerHeader"><div><h2 id="inspection-detail-title">{inspection.inspectionNumber}</h2><p>{inspection.sourceNumber} · {inspection.orderNumber}</p></div><GsButton className="iconButton" aria-label="关闭完工检验详情" onClick={onClose} htmlType="submit"><MaterialIcon name="close"/></GsButton></header><div className="recordDrawerBody"><section className="salesOrderSummary"><div><small>检验状态</small><strong className={`businessStatus businessStatus${inspection.status === "COMPLETED" ? "good" : "warn"}`}>{statusLabels[inspection.status]}</strong></div><div><small>送检数量</small><strong>{inspection.inspectionQuantity} {inspection.unit}</strong></div><div><small>合格数量</small><strong>{inspection.acceptedQuantity ?? "—"}</strong></div><div><small>不合格</small><strong>{inspection.rejectedQuantity ?? "—"}</strong></div></section><section className="drawerSection"><header><h3>来源与物料</h3></header><div className="detailLedger"><div><span>生产报工</span><strong>{inspection.sourceNumber}</strong></div><div><span>生产订单</span><strong>{inspection.orderNumber}</strong></div><div><span>物料</span><strong>{inspection.materialCode}</strong></div><div><span>物料名称</span><strong>{inspection.materialName}</strong></div><div><span>规格</span><strong>{inspection.materialSpecification ?? "—"}</strong></div><div><span>创建时间</span><strong>{new Date(inspection.createdAt).toLocaleString("zh-CN", { hour12: false })}</strong></div></div></section><section className="drawerSection"><header><h3>检验判定</h3></header>{inspection.status === "COMPLETED" ? <div className="detailLedger"><div><span>结果</span><strong>{inspection.result ? resultLabels[inspection.result] : "—"}</strong></div><div><span>检验员</span><strong>{inspection.inspector}</strong></div><div><span>缺陷说明</span><strong>{inspection.defectDescription ?? "无"}</strong></div><div><span>判定时间</span><strong>{inspection.completedAt ? new Date(inspection.completedAt).toLocaleString("zh-CN", { hour12: false }) : "—"}</strong></div><div className="detailLedgerWide"><span>结论</span><strong>{inspection.conclusion}</strong></div></div> : <div className="mrpRunTruthNotice"><MaterialIcon name="science" size={18}/><span><strong>等待质量检验</strong>检验完成前，报工数量不会进入成品库存。</span></div>}</section></div><footer className="recordDrawerFooter"><Link className="secondaryButton" href="/production/reporting/reports"><MaterialIcon name="fact_check" size={17}/>生产报工</Link>{inspection.status === "PENDING" ? <GsButton className="primaryButton" onClick={onInspect} htmlType="submit">开始检验</GsButton> : null}</footer></aside></GsDrawerHost>;
}
export function FinalInspectionWorkspace({ initialData }: {
    initialData: FinalInspectionPageData;
}) {
    const [items, setItems] = useState(initialData.inspections);
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState("全部状态");
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [detail, setDetail] = useState<FinalInspectionRecord | null>(null);
    const [editing, setEditing] = useState<FinalInspectionRecord | null>(null);
    const [toast, setToast] = useState("");
    const filtered = useMemo(() => items.filter((item) => (!query.trim() || `${item.inspectionNumber}${item.sourceNumber}${item.orderNumber}${item.materialCode}${item.materialName}`.toLowerCase().includes(query.trim().toLowerCase())) && (status === "全部状态" || statusLabels[item.status] === status)).sort((a, b) => b.createdAt.localeCompare(a.createdAt)), [items, query, status]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const rows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const currentIds = rows.map((item) => item.id);
    const allCurrent = currentIds.length > 0 && currentIds.every((id) => selectedIds.has(id));
    function saved(item: FinalInspectionRecord) { setItems((current) => current.map((row) => row.id === item.id ? item : row)); setEditing(null); setDetail(item); setToast(`${item.inspectionNumber} 已完成判定`); window.setTimeout(() => setToast(""), 3200); }
    function exportRows() { const chosen = items.filter((item) => selectedIds.has(item.id)); const source = chosen.length ? chosen : filtered; const csv = ["检验单,生产报工,生产订单,物料,送检数量,合格数量,不合格数量,结果", ...source.map((item) => [item.inspectionNumber, item.sourceNumber, item.orderNumber, item.materialCode, item.inspectionQuantity, item.acceptedQuantity ?? "", item.rejectedQuantity ?? "", item.result ? resultLabels[item.result] : "待检验"].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))].join("\n"); const href = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" })); const anchor = document.createElement("a"); anchor.href = href; anchor.download = `完工检验-${todayText()}.csv`; anchor.click(); URL.revokeObjectURL(href); }
    return <div className="businessPage qualityInspectionPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="verified" size={23}/></span><div><h2>完工检验</h2><p>承接生产报工送检，记录全数判定、缺陷与放行证据，决定后续可入库数量。</p></div></div><div className="pageHeadingActions"><Link className="secondaryButton" href="/production/reporting/reports"><MaterialIcon name="fact_check" size={18}/>生产报工</Link></div></header><section className="businessMetrics"><div><small>检验任务</small><strong className="businessMetricinfo">{items.length}</strong><em>当前租户可见范围</em></div><div><small>待检验</small><strong className="businessMetricwarn">{items.filter((item) => item.status === "PENDING").length}</strong><em>等待质量人员判定</em></div><div><small>已判定</small><strong className="businessMetricgood">{items.filter((item) => item.status === "COMPLETED").length}</strong><em>结论不可静默覆盖</em></div><div><small>含不合格</small><strong className="businessMetricrisk">{items.filter((item) => (item.rejectedQuantity ?? 0) > 0).length}</strong><em>需返修或重新生产</em></div></section><section className="businessLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索完工检验" placeholder="搜索检验单、报工单、订单或物料" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }}/></div><RoundedSelect ariaLabel="完工检验状态" options={["全部状态", ...Object.values(statusLabels)]} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }}/><div className="businessTableTools"><GsButton className="secondaryButton" onClick={exportRows} htmlType="submit"><MaterialIcon name="download" size={17}/>{selectedIds.size ? `导出所选（${selectedIds.size}）` : "导出当前"}</GsButton></div></div><div className="salesOrderTable" role="table" aria-label="完工检验列表"><div className="salesOrderTableHeader" role="row"><GsCheckbox className="selectionCheckbox" aria-label="选择当前页全部检验任务" checked={allCurrent} onChange={(event) => setSelectedIds((current) => { const next = new Set(current); currentIds.forEach((id) => event.target.checked ? next.add(id) : next.delete(id)); return next; })}/><span>检验单 / 报工单</span><span>生产订单 / 物料</span><span>送检数量</span><span>判定数量</span><span>检验员</span><span>状态</span><span>操作</span></div>{rows.length ? rows.map((item) => <div className="salesOrderTableRow" role="row" key={item.id} onClick={() => setDetail(item)}><GsCheckbox className="selectionCheckbox" onClick={(event) => event.stopPropagation()} aria-label={`选择${item.inspectionNumber}`} checked={selectedIds.has(item.id)} onChange={(event) => setSelectedIds((current) => { const next = new Set(current); event.target.checked ? next.add(item.id) : next.delete(item.id); return next; })}/><span><strong>{item.inspectionNumber}</strong><small>{item.sourceNumber}</small></span><span><strong>{item.orderNumber}</strong><small>{item.materialCode} · {item.materialName}</small></span><span><strong>{item.inspectionQuantity} {item.unit}</strong><small>完工送检</small></span><span><strong>{item.result ? resultLabels[item.result] : "尚未判定"}</strong><small>合格 {item.acceptedQuantity ?? "—"} · 不合格 {item.rejectedQuantity ?? "—"}</small></span><span><strong>{item.inspector ?? "—"}</strong><small>{item.completedAt ? new Date(item.completedAt).toLocaleDateString("zh-CN") : "等待检验"}</small></span><em className={`businessStatus businessStatus${item.status === "COMPLETED" ? "good" : "warn"}`}>{statusLabels[item.status]}</em><span className="businessRowActions">{item.status === "PENDING" ? <GsButton aria-label={`检验${item.inspectionNumber}`} onClick={(event) => { event.stopPropagation(); setEditing(item); }} htmlType="submit"><MaterialIcon name="edit" size={18}/></GsButton> : null}<GsButton aria-label={`查看${item.inspectionNumber}详情`} onClick={(event) => { event.stopPropagation(); setDetail(item); }} htmlType="submit"><MaterialIcon name="chevron_right" size={19}/></GsButton></span></div>) : <div className="businessEmptyState"><MaterialIcon name="verified" size={28}/><strong>没有符合条件的完工检验</strong><p>生产报工提交后，待检任务会自动出现在这里。</p></div>}</div><footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => {
        setPage(nextPage);
        setPageSize(nextPageSize);
    }}/></footer></section>{detail ? <InspectionDrawer inspection={detail} onClose={() => setDetail(null)} onInspect={() => { setEditing(detail); setDetail(null); }}/> : null}{editing ? <InspectionForm inspection={editing} onClose={() => setEditing(null)} onSaved={saved}/> : null}{toast ? <div className="toastMessage" role="status"><MaterialIcon name="task_alt" filled size={18}/>{toast}</div> : null}</div>;
}

