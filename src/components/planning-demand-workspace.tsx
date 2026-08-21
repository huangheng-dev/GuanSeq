"use client";
import { GsButton, GsCheckbox, GsDrawerHost, GsInput, GsModalHost, GsTextArea, GsPagination } from "./ui";
import { type FormEvent, useMemo, useRef, useState } from "react";
import Link from "next/link";
import type { IndependentDemandRecord, PlanningDemandReferenceData } from "@/lib/contracts";
import { submitPlanningDemandMutation } from "@/services/planning-demand-client-service";
import type { PlanningDemandPageData, PlanningDemandWritePayload } from "@/services/planning-demand-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const statusLabels: Record<IndependentDemandRecord["status"], string> = {
    DRAFT: "草稿",
    ACTIVE: "有效",
    CANCELLED: "已取消",
};
const sourceLabels: Record<IndependentDemandRecord["sourceType"], string> = {
    SALES_ORDER: "销售订单",
    MANUAL: "人工需求",
};
const priorityLabels: Record<IndependentDemandRecord["priority"], string> = {
    LOW: "低",
    NORMAL: "普通",
    HIGH: "高",
    URGENT: "紧急",
};
const priorityCodes = Object.fromEntries(Object.entries(priorityLabels).map(([code, label]) => [label, code])) as Record<string, PlanningDemandWritePayload["priority"]>;
type DemandAction = "ACTIVATE" | "CANCEL";
function materialLabel(material: PlanningDemandReferenceData["materials"][number]) {
    return `${material.code} · ${material.name}${material.specification ? ` · ${material.specification}` : ""}`;
}
function statusTone(status: IndependentDemandRecord["status"]) {
    if (status === "ACTIVE")
        return "good";
    if (status === "CANCELLED")
        return "risk";
    return "info";
}
function priorityTone(priority: IndependentDemandRecord["priority"]) {
    if (priority === "URGENT")
        return "risk";
    if (priority === "HIGH")
        return "warn";
    return "info";
}
function defaultRequiredDate() {
    const date = new Date();
    date.setDate(date.getDate() + 7);
    return date.toISOString().slice(0, 10);
}
function DemandFormDialog({ demand, references, onClose, onSaved }: {
    demand: IndependentDemandRecord | null;
    references: PlanningDemandReferenceData;
    onClose: () => void;
    onSaved: (demand: IndependentDemandRecord) => void;
}) {
    const dialogRef = useRef<HTMLElement>(null);
    const materialOptions = references.materials.map(materialLabel);
    const selectedMaterial = demand ? references.materials.find((item) => item.id === demand.materialId) : references.materials[0];
    const [material, setMaterial] = useState(selectedMaterial ? materialLabel(selectedMaterial) : "");
    const [quantity, setQuantity] = useState(String(demand?.quantity ?? 1));
    const [requiredDate, setRequiredDate] = useState(demand?.requiredDate ?? defaultRequiredDate());
    const [priority, setPriority] = useState(priorityLabels[demand?.priority ?? "NORMAL"]);
    const [owner, setOwner] = useState(demand?.owner ?? "林浩");
    const [note, setNote] = useState(demand?.note ?? "");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        const materialRecord = references.materials.find((item) => materialLabel(item) === material);
        const numericQuantity = Number(quantity);
        if (!materialRecord || !Number.isFinite(numericQuantity) || numericQuantity <= 0 || !requiredDate || !owner.trim()) {
            setError("请完整填写物料、有效数量、需求日期和负责人。");
            return;
        }
        const payload: PlanningDemandWritePayload = {
            materialId: materialRecord.id,
            quantity: numericQuantity,
            requiredDate,
            priority: priorityCodes[priority],
            owner: owner.trim(),
            note: note.trim() || null,
        };
        setPending(true);
        try {
            const saved = await submitPlanningDemandMutation(demand
                ? { operation: "update", id: demand.id, payload: { ...payload, expectedVersion: demand.version } }
                : { operation: "create", payload });
            onSaved(saved.demand);
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "独立需求保存失败，请重试");
            setPending(false);
        }
    }
    return (<GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}>
      <section ref={dialogRef} className="businessDialog planningDemandDialog" role="dialog" aria-modal="true" aria-labelledby="planning-demand-form-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader">
          <div className="dialogTitleMark"><MaterialIcon name="event_note" size={22}/></div>
          <div><h2 id="planning-demand-form-title">{demand ? `编辑 ${demand.demandNumber}` : "新建独立需求"}</h2><p>人工需求先保存为草稿，激活后才进入 MRP 输入范围。</p></div>
          <GsButton className="iconButton" htmlType="button" onClick={onClose} disabled={pending} aria-label="关闭独立需求表单"><MaterialIcon name="close"/></GsButton>
        </header>
        <form onSubmit={submit}>
          <div className="formGrid planningDemandFormGrid">
            <label className="formField formFieldFull"><span>物料<em>必填</em></span><RoundedSelect ariaLabel="需求物料" options={materialOptions} value={material} onValueChange={setMaterial} size="field"/></label>
            <label className="formField"><span>需求数量<em>必填</em></span><GsInput type="number" min="0.0001" step="0.0001" value={quantity} onChange={(event) => setQuantity(event.target.value)}/></label>
            <label className="formField"><span>需求日期<em>必填</em></span><GsInput type="date" value={requiredDate} onChange={(event) => setRequiredDate(event.target.value)}/></label>
            <label className="formField"><span>优先级<em>必填</em></span><RoundedSelect ariaLabel="需求优先级" options={Object.values(priorityLabels)} value={priority} onValueChange={setPriority} size="field"/></label>
            <label className="formField"><span>负责人<em>必填</em></span><GsInput value={owner} maxLength={80} onChange={(event) => setOwner(event.target.value)}/></label>
            <label className="formField formFieldFull"><span>需求说明</span><GsTextArea rows={4} maxLength={500} value={note} onChange={(event) => setNote(event.target.value)} placeholder="说明需求背景、用途或计划约束"/></label>
          </div>
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
          <footer className="dialogFooter"><span><MaterialIcon name="shield" size={16}/>保存产生版本和操作证据</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" htmlType="submit" disabled={pending}>{pending ? "正在保存" : "保存需求"}</GsButton></div></footer>
        </form>
      </section>
    </GsModalHost>);
}
function DemandActionDialog({ demand, action, onClose, onDone }: {
    demand: IndependentDemandRecord;
    action: DemandAction;
    onClose: () => void;
    onDone: (demand: IndependentDemandRecord) => void;
}) {
    const dialogRef = useRef<HTMLElement>(null);
    const [comment, setComment] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function confirm() {
        if (action === "CANCEL" && !comment.trim()) {
            setError("取消需求必须填写原因。");
            return;
        }
        setPending(true);
        setError("");
        try {
            const result = await submitPlanningDemandMutation({ operation: "action", id: demand.id, action, expectedVersion: demand.version, comment: comment.trim() || undefined });
            onDone(result.demand);
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "需求状态操作失败，请重试");
            setPending(false);
        }
    }
    const actionName = action === "ACTIVATE" ? "激活需求" : "取消需求";
    return (<GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}>
      <section ref={dialogRef} className="deleteConfirmDialog planningDemandActionDialog" role="dialog" aria-modal="true" aria-labelledby="planning-demand-action-title" onMouseDown={(event) => event.stopPropagation()}>
        <span className="deleteConfirmIcon"><MaterialIcon name={action === "ACTIVATE" ? "task_alt" : "block"} size={24}/></span>
        <div><h2 id="planning-demand-action-title">{actionName} · {demand.demandNumber}</h2><p>{action === "ACTIVATE" ? "激活后该需求将进入 MRP 的有效输入范围。" : "取消后该需求将退出 MRP 输入范围，操作不可直接恢复。"}</p>{action === "CANCEL" ? <GsTextArea aria-label="取消原因" rows={3} value={comment} onChange={(event) => setComment(event.target.value)} placeholder="请填写取消原因"/> : null}{error ? <div className="formError" role="alert">{error}</div> : null}</div>
        <footer><GsButton className="secondaryButton" onClick={onClose} disabled={pending} htmlType="submit">返回</GsButton><GsButton className={action === "CANCEL" ? "dangerButton" : "primaryButton"} onClick={() => void confirm()} disabled={pending} htmlType="submit">{pending ? "正在处理" : actionName}</GsButton></footer>
      </section>
    </GsModalHost>);
}
function DemandDrawer({ demand, onClose, onEdit, onAction }: {
    demand: IndependentDemandRecord;
    onClose: () => void;
    onEdit: () => void;
    onAction: (action: DemandAction) => void;
}) {
    const drawerRef = useRef<HTMLElement>(null);
    const isManual = demand.sourceType === "MANUAL";
    return (<GsDrawerHost onClose={onClose}>
      <aside ref={drawerRef} className="recordDrawer planningDemandDrawer" role="dialog" aria-modal="true" aria-labelledby="planning-demand-detail-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="recordDrawerHeader"><div><h2 id="planning-demand-detail-title">{demand.demandNumber}</h2><p>{sourceLabels[demand.sourceType]} · {demand.materialCode}</p></div><GsButton className="iconButton" aria-label="关闭需求详情" onClick={onClose} htmlType="submit"><MaterialIcon name="close"/></GsButton></header>
        <section className="salesOrderSummary planningDemandSummary">
          <div><small>需求状态</small><strong className={`businessStatus businessStatus${statusTone(demand.status)}`}>{statusLabels[demand.status]}</strong></div>
          <div><small>MRP 输入</small><strong>{demand.status === "ACTIVE" ? "已就绪" : "未进入"}</strong></div>
          <div><small>需求日期</small><strong>{demand.requiredDate}</strong></div>
          <div><small>负责人</small><strong>{demand.owner}</strong></div>
        </section>
        <section className="planningDemandDetailBlock"><h3>物料与数量</h3><div><strong>{demand.materialCode} · {demand.materialName}</strong><small>{demand.materialSpecification ?? "无规格"}</small><b>{demand.quantity} {demand.unit}</b></div></section>
        <section className="planningDemandDetailBlock"><h3>来源与证据</h3><dl><div><dt>来源类型</dt><dd>{sourceLabels[demand.sourceType]}</dd></div>{demand.sourceNumber ? <div><dt>来源单据</dt><dd><Link href="/sales/orders/list">{demand.sourceNumber}<MaterialIcon name="arrow_outward" size={15}/></Link></dd></div> : null}{demand.sourceCustomer ? <div><dt>来源客户</dt><dd>{demand.sourceCustomer}</dd></div> : null}<div><dt>优先级</dt><dd><em className={`businessStatus businessStatus${priorityTone(demand.priority)}`}>{priorityLabels[demand.priority]}</em></dd></div><div><dt>说明</dt><dd>{demand.note ?? "—"}</dd></div></dl></section>
        {demand.cancellationReason ? <div className="salesOrderRejection"><MaterialIcon name="warning" size={18}/><span><strong>取消原因</strong>{demand.cancellationReason}</span></div> : null}
        <footer className="recordDrawerFooter">{isManual && demand.status === "DRAFT" ? <><GsButton className="secondaryButton" onClick={onEdit} htmlType="submit"><MaterialIcon name="edit" size={17}/>编辑</GsButton><GsButton className="primaryButton" onClick={() => onAction("ACTIVATE")} htmlType="submit">激活需求</GsButton></> : null}{isManual && (demand.status === "DRAFT" || demand.status === "ACTIVE") ? <GsButton className="dangerButton" onClick={() => onAction("CANCEL")} htmlType="submit">取消需求</GsButton> : null}</footer>
      </aside>
    </GsDrawerHost>);
}
export function PlanningDemandWorkspace({ initialData }: {
    initialData: PlanningDemandPageData;
}) {
    const [demands, setDemands] = useState(initialData.demands);
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState("全部状态");
    const [source, setSource] = useState("全部来源");
    const [sortAscending, setSortAscending] = useState(true);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const [detail, setDetail] = useState<IndependentDemandRecord | null>(null);
    const [editing, setEditing] = useState<IndependentDemandRecord | null | undefined>(undefined);
    const [actionTarget, setActionTarget] = useState<{
        demand: IndependentDemandRecord;
        action: DemandAction;
    } | null>(null);
    const [toast, setToast] = useState("");
    const filtered = useMemo(() => demands.filter((demand) => {
        const matchesQuery = !query.trim() || `${demand.demandNumber}${demand.materialCode}${demand.materialName}${demand.sourceNumber ?? ""}${demand.owner}`.toLowerCase().includes(query.trim().toLowerCase());
        const matchesStatus = status === "全部状态" || statusLabels[demand.status] === status;
        const matchesSource = source === "全部来源" || sourceLabels[demand.sourceType] === source;
        return matchesQuery && matchesStatus && matchesSource;
    }).sort((a, b) => (sortAscending ? 1 : -1) * a.requiredDate.localeCompare(b.requiredDate)), [demands, query, sortAscending, source, status]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const currentIds = pageRows.map((demand) => demand.id);
    const selectedDemands = demands.filter((demand) => selectedIds.has(demand.id));
    const allCurrent = currentIds.length > 0 && currentIds.every((id) => selectedIds.has(id));
    const activeDemands = demands.filter((demand) => demand.status === "ACTIVE");
    const urgentDemands = activeDemands.filter((demand) => demand.priority === "URGENT" || demand.priority === "HIGH");
    function replaceDemand(demand: IndependentDemandRecord, message: string) {
        setDemands((current) => current.some((item) => item.id === demand.id) ? current.map((item) => item.id === demand.id ? demand : item) : [demand, ...current]);
        setDetail(null);
        setEditing(undefined);
        setActionTarget(null);
        setToast(message);
        window.setTimeout(() => setToast(""), 2600);
    }
    function openAction(demand: IndependentDemandRecord, action: DemandAction) {
        setDetail(null);
        setActionTarget({ demand, action });
    }
    function exportDemands(rows: IndependentDemandRecord[]) {
        const content = ["需求编号,来源,来源单据,物料编码,物料名称,数量,单位,需求日期,优先级,状态,负责人", ...rows.map((demand) => [demand.demandNumber, sourceLabels[demand.sourceType], demand.sourceNumber ?? "", demand.materialCode, demand.materialName, demand.quantity, demand.unit, demand.requiredDate, priorityLabels[demand.priority], statusLabels[demand.status], demand.owner].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))].join("\n");
        const href = URL.createObjectURL(new Blob([`\uFEFF${content}`], { type: "text/csv;charset=utf-8" }));
        const anchor = document.createElement("a");
        anchor.href = href;
        anchor.download = `独立需求-${new Date().toISOString().slice(0, 10)}.csv`;
        anchor.click();
        URL.revokeObjectURL(href);
    }
    return (<div className="businessPage planningDemandPage">
      <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="event_note" size={23}/></span><div><h2>独立需求</h2><p>统一承接销售订单与人工需求，确认数量、日期、来源和责任后进入 MRP 输入。</p></div></div><div className="pageHeadingActions"><GsButton className="secondaryButton" onClick={() => exportDemands(filtered)} htmlType="submit"><MaterialIcon name="download" size={18}/>导出</GsButton><GsButton className="primaryButton" onClick={() => setEditing(null)} htmlType="submit"><MaterialIcon name="add" size={18}/>新建人工需求</GsButton></div></header>
      <section className="businessMetrics"><div><small>需求总数</small><strong className="businessMetricinfo">{demands.length}</strong><em>当前租户可见范围</em></div><div><small>MRP 有效输入</small><strong className="businessMetricgood">{activeDemands.length}</strong><em>仅有效状态进入运算</em></div><div><small>销售订单来源</small><strong className="businessMetricinfo">{demands.filter((demand) => demand.sourceType === "SALES_ORDER").length}</strong><em>由订单下达自动产生</em></div><div><small>高优先需求</small><strong className="businessMetricwarn">{urgentDemands.length}</strong><em>高与紧急优先级</em></div></section>
      <section className="businessLedger planningDemandLedger">
        <div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索独立需求" placeholder="搜索需求编号、来源单据、物料或负责人" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }}/></div><RoundedSelect ariaLabel="需求状态" options={["全部状态", ...Object.values(statusLabels)]} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }}/><RoundedSelect ariaLabel="需求来源" options={["全部来源", ...Object.values(sourceLabels)]} value={source} onValueChange={(value) => { setSource(value); setPage(1); }}/><div className="businessTableTools"><GsButton className="secondaryButton" onClick={() => setSortAscending((value) => !value)} htmlType="submit"><MaterialIcon name={sortAscending ? "arrow_upward" : "arrow_downward"} size={17}/>需求日期</GsButton><GsButton className="secondaryButton" onClick={() => exportDemands(selectedDemands.length ? selectedDemands : filtered)} htmlType="submit"><MaterialIcon name="download" size={17}/>{selectedDemands.length ? `导出所选（${selectedDemands.length}）` : "导出当前"}</GsButton></div></div>
        <div className="planningDemandTable" role="table" aria-label="独立需求列表">
          <div className="planningDemandTableHeader" role="row"><GsCheckbox className="selectionCheckbox" aria-label="选择当前页全部需求" checked={allCurrent} onChange={(event) => setSelectedIds((current) => { const next = new Set(current); currentIds.forEach((id) => event.target.checked ? next.add(id) : next.delete(id)); return next; })}/><span>需求编号</span><span>来源</span><span>物料</span><span>数量</span><GsButton onClick={() => setSortAscending((value) => !value)} htmlType="submit">需求日期<MaterialIcon name={sortAscending ? "arrow_upward" : "arrow_downward"} size={15}/></GsButton><span>优先级</span><span>状态</span><span>操作</span></div>
          {pageRows.map((demand) => <div className="planningDemandTableRow" role="row" key={demand.id} onClick={() => setDetail(demand)}><GsCheckbox className="selectionCheckbox" onClick={(event) => event.stopPropagation()} aria-label={`选择${demand.demandNumber}`} checked={selectedIds.has(demand.id)} onChange={(event) => setSelectedIds((current) => {
                const next = new Set(current);
                if (event.target.checked)
                    next.add(demand.id);
                else
                    next.delete(demand.id);
                return next;
            })}/><strong>{demand.demandNumber}</strong><span><b>{sourceLabels[demand.sourceType]}</b><small>{demand.sourceNumber ?? "计划员录入"}</small></span><span><b>{demand.materialCode} · {demand.materialName}</b><small>{demand.materialSpecification ?? "无规格"}</small></span><span><b>{demand.quantity} {demand.unit}</b><small>{demand.owner}</small></span><strong>{demand.requiredDate}</strong><em className={`businessStatus businessStatus${priorityTone(demand.priority)}`}>{priorityLabels[demand.priority]}</em><em className={`businessStatus businessStatus${statusTone(demand.status)}`}>{statusLabels[demand.status]}</em><span className="businessRowActions"><GsButton aria-label={`查看${demand.demandNumber}详情`} onClick={(event) => { event.stopPropagation(); setDetail(demand); }} htmlType="submit"><MaterialIcon name="chevron_right" size={19}/></GsButton></span></div>)}
        </div>
        <footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => {
        setPage(nextPage);
        setPageSize(nextPageSize);
    }}/></footer>
      </section>
      {detail ? <DemandDrawer demand={detail} onClose={() => setDetail(null)} onEdit={() => { setEditing(detail); setDetail(null); }} onAction={(action) => openAction(detail, action)}/> : null}
      {editing !== undefined ? <DemandFormDialog demand={editing} references={initialData.references} onClose={() => setEditing(undefined)} onSaved={(demand) => replaceDemand(demand, editing ? `${demand.demandNumber} 已更新` : `${demand.demandNumber} 已创建为草稿`)}/> : null}
      {actionTarget ? <DemandActionDialog demand={actionTarget.demand} action={actionTarget.action} onClose={() => setActionTarget(null)} onDone={(demand) => replaceDemand(demand, `${demand.demandNumber} 已${actionTarget.action === "ACTIVATE" ? "激活" : "取消"}`)}/> : null}
      {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}
    </div>);
}

