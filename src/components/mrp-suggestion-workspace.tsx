"use client";
import { GsButton, GsCheckbox, GsDrawerHost, GsInput, GsModalHost, GsTextArea, GsPagination } from "./ui";
import Link from "next/link";
import { type FormEvent, useMemo, useRef, useState } from "react";
import type { MrpSuggestion } from "@/lib/contracts";
import { submitMrpSuggestionMutation } from "@/services/mrp-suggestion-client-service";
import type { MrpSuggestionPageData } from "@/services/mrp-suggestion-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const statusLabels = { PROPOSED: "待审核", APPROVED: "已审核", REJECTED: "已驳回", CONVERTED: "已转单" } as const;
const typeLabels = { PRODUCTION: "生产建议", PURCHASE: "采购建议", OUTSOURCE: "委外建议" } as const;
const tone = (status: MrpSuggestion["decisionStatus"]) => status === "CONVERTED" ? "good" : status === "REJECTED" ? "risk" : status === "PROPOSED" ? "warn" : "info";
const today = () => new Date().toISOString().slice(0, 10);
const safeDate = (value: string | null) => value && value > today() ? value : today();
function ActionDialog({ item, mode, data, onClose, onSaved }: {
    item: MrpSuggestion;
    mode: "approve" | "reject" | "convert";
    data: MrpSuggestionPageData;
    onClose: () => void;
    onSaved: (item: MrpSuggestion, message: string) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const requestId = useRef(`mrp-suggestion-${crypto.randomUUID()}`);
    const purchase = item.recommendationType !== "PRODUCTION";
    const [comment, setComment] = useState("");
    const [supplier, setSupplier] = useState(data.references.suppliers[0]?.id ?? "");
    const [currency, setCurrency] = useState<"CNY" | "USD" | "EUR">("CNY");
    const [taxRate, setTaxRate] = useState("0.13");
    const [unitPrice, setUnitPrice] = useState("0");
    const [receiptDate, setReceiptDate] = useState(safeDate(item.requiredDate));
    const [buyer, setBuyer] = useState("唐工");
    const [startDate, setStartDate] = useState(safeDate(item.recommendedReleaseDate));
    const [finishDate, setFinishDate] = useState(safeDate(item.requiredDate));
    const [workshop, setWorkshop] = useState("总装一车间");
    const [owner, setOwner] = useState("周启明");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        if (mode === "reject" && !comment.trim()) {
            setError("驳回建议必须填写原因。");
            return;
        }
        if (mode === "convert" && purchase && (!supplier || !buyer.trim() || !receiptDate)) {
            setError("请选择供应商，并填写采购员和要求到货日期。");
            return;
        }
        if (mode === "convert" && !purchase && (!startDate || !finishDate || !workshop.trim() || !owner.trim())) {
            setError("请填写完整的生产计划信息。");
            return;
        }
        if (mode === "convert" && !purchase && startDate > finishDate) {
            setError("计划开工日期不能晚于完工日期。");
            return;
        }
        setPending(true);
        try {
            const result = mode === "convert"
                ? await submitMrpSuggestionMutation(purchase ? { operation: "convert", id: item.id, expectedVersion: item.version, supplierId: supplier, currency, taxRate: Number(taxRate), unitPrice: Number(unitPrice), requestedReceiptDate: receiptDate, buyer: buyer.trim() } : { operation: "convert", id: item.id, expectedVersion: item.version, plannedStartDate: startDate, plannedReceiptDate: finishDate, workshop: workshop.trim(), owner: owner.trim() }, requestId.current)
                : await submitMrpSuggestionMutation({ operation: "action", id: item.id, action: mode === "approve" ? "APPROVE" : "REJECT", expectedVersion: item.version, comment: comment.trim() || undefined }, requestId.current);
            onSaved(result, mode === "convert" ? `${result.convertedOrderNumber} 已创建为草稿` : mode === "approve" ? "计划建议已审核" : "计划建议已驳回");
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "操作失败，请重试");
            setPending(false);
        }
    }
    const title = mode === "approve" ? "审核计划建议" : mode === "reject" ? "驳回计划建议" : purchase ? "转为采购草稿" : "转为生产草稿";
    return <GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}><section ref={ref} className="businessDialog mrpSuggestionDialog" role="dialog" aria-modal="true" aria-labelledby="mrp-suggestion-action-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={mode === "convert" ? "conversion_path" : "fact_check"} size={22}/></span><div><h2 id="mrp-suggestion-action-title">{title}</h2><p>{item.materialCode} · {item.materialName} · {item.netQuantity} {item.unit}</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭MRP建议操作" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header>
    <form onSubmit={submit}>
      {mode !== "convert" ? <div className="formGrid"><label className="formField formFieldFull"><span>{mode === "reject" ? "驳回原因" : "审核意见"}{mode === "reject" ? <em>必填</em> : null}</span><GsTextArea value={comment} maxLength={500} onChange={(event) => setComment(event.target.value)} placeholder={mode === "reject" ? "说明需要重新规划的业务原因" : "可填写审核依据或边界条件"}/></label></div> : purchase ? <div className="formGrid">
        <label className="formField"><span>供应商<em>必填</em></span><RoundedSelect ariaLabel="转单供应商" size="field" value={data.references.suppliers.find((x) => x.id === supplier)?.name ?? ""} options={data.references.suppliers.map((x) => x.name)} onValueChange={(value) => setSupplier(data.references.suppliers.find((x) => x.name === value)?.id ?? "")}/></label>
        <label className="formField"><span>币种</span><RoundedSelect ariaLabel="采购币种" size="field" value={currency} options={["CNY", "USD", "EUR"]} onValueChange={(value) => setCurrency(value as typeof currency)}/></label>
        <label className="formField"><span>含税税率</span><GsInput type="number" min="0" max="1" step="0.01" value={taxRate} onChange={(event) => setTaxRate(event.target.value)}/></label><label className="formField"><span>未税单价</span><GsInput type="number" min="0" step="0.01" value={unitPrice} onChange={(event) => setUnitPrice(event.target.value)}/></label>
        <label className="formField"><span>要求到货<em>必填</em></span><GsInput type="date" min={today()} value={receiptDate} onChange={(event) => setReceiptDate(event.target.value)}/></label><label className="formField"><span>采购员<em>必填</em></span><GsInput value={buyer} onChange={(event) => setBuyer(event.target.value)}/></label>
      </div> : <div className="formGrid"><label className="formField"><span>计划开工<em>必填</em></span><GsInput type="date" min={today()} value={startDate} onChange={(event) => setStartDate(event.target.value)}/></label><label className="formField"><span>计划完工<em>必填</em></span><GsInput type="date" min={startDate || today()} value={finishDate} onChange={(event) => setFinishDate(event.target.value)}/></label><label className="formField"><span>生产车间<em>必填</em></span><GsInput value={workshop} onChange={(event) => setWorkshop(event.target.value)}/></label><label className="formField"><span>责任人<em>必填</em></span><GsInput value={owner} onChange={(event) => setOwner(event.target.value)}/></label></div>}
      {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
      <footer className="dialogFooter"><span><MaterialIcon name="verified_user" size={16}/>审核与转单分别留存责任和请求号</span><div><GsButton htmlType="button" className="secondaryButton" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className={mode === "reject" ? "dangerButton" : "primaryButton"} disabled={pending} htmlType="submit">{pending ? "正在处理" : title}</GsButton></div></footer>
    </form>
  </section></GsModalHost>;
}
function Drawer({ item, onClose, onAction }: {
    item: MrpSuggestion;
    onClose: () => void;
    onAction: (mode: "approve" | "reject" | "convert") => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const orderHref = item.convertedOrderType === "PURCHASE_ORDER" ? "/procurement/orders" : "/production/orders/list";
    return <GsDrawerHost onClose={onClose}><aside ref={ref} className="recordDrawer mrpSuggestionDrawer" role="dialog" aria-modal="true" aria-labelledby="mrp-suggestion-detail-title" onMouseDown={(event) => event.stopPropagation()}><header className="recordDrawerHeader"><div><h2 id="mrp-suggestion-detail-title">{item.materialCode} · {item.materialName}</h2><p>{item.runNumber} · {typeLabels[item.recommendationType]}</p></div><GsButton className="iconButton" aria-label="关闭建议详情" onClick={onClose} htmlType="submit"><MaterialIcon name="close"/></GsButton></header>
    <section className="salesOrderSummary"><div><small>当前状态</small><strong className={`businessStatus businessStatus${tone(item.decisionStatus)}`}>{statusLabels[item.decisionStatus]}</strong></div><div><small>净需求</small><strong>{item.netQuantity} {item.unit}</strong></div><div><small>建议下达</small><strong>{item.recommendedReleaseDate ?? "待补齐"}</strong></div><div><small>需求日期</small><strong>{item.requiredDate}</strong></div></section>
    <section className="drawerSection"><h3>供需依据</h3><div className="detailLedger"><div><span>毛需求</span><strong>{item.grossQuantity} {item.unit}</strong></div><div><span>需求来源</span><strong>{item.sourceType === "BOM_COMPONENT" ? `BOM 组件 · ${item.parentMaterialCode}` : "独立需求"}</strong></div><div><span>获取方式</span><strong>{item.procurementType === "MAKE" ? "自制" : item.procurementType === "BUY" ? "采购" : "委外"}</strong></div><div><span>所属运算</span><strong>{item.runName}</strong></div></div></section>
    <section className="drawerSection"><h3>处理证据</h3><div className="detailLedger"><div><span>审核意见</span><strong>{item.decisionComment ?? "暂无"}</strong></div><div><span>审核时间</span><strong>{item.decidedAt ? new Date(item.decidedAt).toLocaleString("zh-CN") : "尚未审核"}</strong></div><div><span>转单结果</span><strong>{item.convertedOrderNumber ?? "尚未转单"}</strong></div></div></section>
    <footer className="recordDrawerFooter">{item.convertedOrderNumber ? <Link className="secondaryButton" href={orderHref}>查看 {item.convertedOrderNumber}<MaterialIcon name="arrow_outward" size={17}/></Link> : null}{item.decisionStatus === "PROPOSED" ? <><GsButton className="secondaryButton" onClick={() => onAction("reject")} htmlType="submit">驳回</GsButton><GsButton className="primaryButton" onClick={() => onAction("approve")} htmlType="submit">审核通过</GsButton></> : null}{item.decisionStatus === "APPROVED" ? <GsButton className="primaryButton" onClick={() => onAction("convert")} htmlType="submit">创建执行草稿</GsButton> : null}</footer>
  </aside></GsDrawerHost>;
}
export function MrpSuggestionWorkspace({ initialData }: {
    initialData: MrpSuggestionPageData;
}) {
    const [items, setItems] = useState(initialData.suggestions);
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState("全部状态");
    const [type, setType] = useState("全部类型");
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [detail, setDetail] = useState<MrpSuggestion | null>(null);
    const [action, setAction] = useState<"approve" | "reject" | "convert" | null>(null);
    const [toast, setToast] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const filtered = useMemo(() => items.filter((item) => (!query.trim() || `${item.materialCode}${item.materialName}${item.runNumber}`.toLowerCase().includes(query.trim().toLowerCase())) && (status === "全部状态" || statusLabels[item.decisionStatus] === status) && (type === "全部类型" || typeLabels[item.recommendationType] === type)), [items, query, status, type]);
    const pages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const current = Math.min(page, pages);
    const rows = filtered.slice((current - 1) * pageSize, current * pageSize);
    const pageIds = rows.map((x) => x.id);
    const allPage = pageIds.length > 0 && pageIds.every((id) => selected.has(id));
    function save(item: MrpSuggestion, message: string) { setItems((currentItems) => currentItems.map((x) => x.id === item.id ? item : x)); setDetail(item); setAction(null); setToast(message); window.setTimeout(() => setToast(""), 3000); }
    function exportRows() { const chosen = selected.size ? filtered.filter((x) => selected.has(x.id)) : filtered; const csv = ["运算编号,物料编码,物料名称,净需求,单位,类型,状态,需求日期,转单编号", ...chosen.map((x) => [x.runNumber, x.materialCode, x.materialName, x.netQuantity, x.unit, typeLabels[x.recommendationType], statusLabels[x.decisionStatus], x.requiredDate, x.convertedOrderNumber ?? ""].map((v) => `"${String(v).replaceAll('"', '""')}"`).join(","))].join("\n"); const href = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" })); const a = document.createElement("a"); a.href = href; a.download = `MRP供需建议-${today()}.csv`; a.click(); URL.revokeObjectURL(href); }
    return <div className="businessPage mrpSuggestionPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="account_tree" size={23}/></span><div><h2>供需建议</h2><p>审核净需求建议，并按业务责任转为采购、委外或生产草稿单。</p></div></div><div className="pageHeadingActions"><Link className="secondaryButton" href="/planning/mrp/runs"><MaterialIcon name="calculate" size={18}/>MRP 运算</Link></div></header>
    <section className="businessMetrics"><div><small>计划建议</small><strong className="businessMetricinfo">{items.length}</strong><em>可执行净需求结果</em></div><div><small>待审核</small><strong className="businessMetricwarn">{items.filter((x) => x.decisionStatus === "PROPOSED").length}</strong><em>等待计划责任人处理</em></div><div><small>已审核</small><strong>{items.filter((x) => x.decisionStatus === "APPROVED").length}</strong><em>可创建下游草稿单</em></div><div><small>已转单</small><strong className="businessMetricgood">{items.filter((x) => x.decisionStatus === "CONVERTED").length}</strong><em>已建立来源追溯</em></div></section>
    <section className="businessLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索供需建议" placeholder="搜索物料或MRP运算编号" value={query} onChange={(e) => { setQuery(e.target.value); setPage(1); }}/></div><RoundedSelect ariaLabel="建议状态" value={status} options={["全部状态", ...Object.values(statusLabels)]} onValueChange={(v) => { setStatus(v); setPage(1); }}/><RoundedSelect ariaLabel="建议类型" value={type} options={["全部类型", ...Object.values(typeLabels)]} onValueChange={(v) => { setType(v); setPage(1); }}/><div className="businessTableTools"><GsButton className="secondaryButton" onClick={exportRows} htmlType="submit"><MaterialIcon name="download" size={17}/>{selected.size ? `导出所选（${selected.size}）` : "导出当前"}</GsButton></div></div>
      <div className="mrpSuggestionTable" role="table" aria-label="MRP供需建议列表"><div className="mrpSuggestionTableHeader" role="row"><GsCheckbox className="selectionCheckbox" aria-label="选择当前页全部建议" checked={allPage} onChange={(e) => setSelected((old) => { const next = new Set(old); pageIds.forEach((id) => e.target.checked ? next.add(id) : next.delete(id)); return next; })}/><span>物料 / 运算</span><span>净需求</span><span>建议下达 / 需求</span><span>类型</span><span>状态</span><span>操作</span></div>
        {rows.length ? rows.map((item) => <div className="mrpSuggestionTableRow" role="row" key={item.id} onClick={() => setDetail(item)}><GsCheckbox className="selectionCheckbox" onClick={(e) => e.stopPropagation()} aria-label={`选择${item.materialCode}`} checked={selected.has(item.id)} onChange={(e) => setSelected((old) => {
                const next = new Set(old);
                if (e.target.checked)
                    next.add(item.id);
                else
                    next.delete(item.id);
                return next;
            })}/><span><strong>{item.materialCode} · {item.materialName}</strong><small>{item.runNumber}{item.parentMaterialCode ? ` · 来源 ${item.parentMaterialCode}` : ""}</small></span><span><strong>{item.netQuantity} {item.unit}</strong><small>毛需求 {item.grossQuantity}</small></span><span><strong>{item.recommendedReleaseDate ?? "待补齐"}</strong><small>需求 {item.requiredDate}</small></span><span><strong>{typeLabels[item.recommendationType]}</strong><small>{item.procurementType === "MAKE" ? "自制" : item.procurementType === "BUY" ? "采购" : "委外"}</small></span><em className={`businessStatus businessStatus${tone(item.decisionStatus)}`}>{statusLabels[item.decisionStatus]}</em><span className="businessRowActions"><GsButton aria-label={`查看${item.materialCode}建议详情`} onClick={(e) => { e.stopPropagation(); setDetail(item); }} htmlType="submit"><MaterialIcon name="chevron_right" size={19}/></GsButton></span></div>) : <div className="businessEmptyState"><MaterialIcon name="account_tree" size={28}/><strong>没有符合条件的供需建议</strong><p>可调整筛选条件，或先发起一次产生净需求的 MRP 运算。</p></div>}
      </div><footer className="businessLedgerFooter"><span>第 {filtered.length ? (current - 1) * pageSize + 1 : 0}–{Math.min(current * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={current} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => {
        setPage(nextPage);
        setPageSize(nextPageSize);
    }}/></footer>
    </section>{detail ? <Drawer item={detail} onClose={() => setDetail(null)} onAction={setAction}/> : null}{detail && action ? <ActionDialog item={detail} mode={action} data={initialData} onClose={() => setAction(null)} onSaved={save}/> : null}{toast ? <div className="toastMessage" role="status"><MaterialIcon name="task_alt" filled size={18}/>{toast}</div> : null}
  </div>;
}

