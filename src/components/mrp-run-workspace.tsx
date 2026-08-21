"use client";
import { GsButton, GsCheckbox, GsDrawerHost, GsInput, GsModalHost, GsPagination } from "./ui";
import { type FormEvent, useMemo, useRef, useState } from "react";
import Link from "next/link";
import type { MrpRunRecord } from "@/lib/contracts";
import { submitMrpRun } from "@/services/mrp-run-client-service";
import type { MrpRunPageData } from "@/services/mrp-run-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const statusLabels: Record<MrpRunRecord["status"], string> = {
    PREPARING: "检查中",
    BLOCKED: "已阻断",
    COMPLETED: "已完成",
};
const exceptionLabels: Record<MrpRunRecord["exceptions"][number]["code"], string> = {
    SCHEDULED_RECEIPTS_UNAVAILABLE: "计划接收缺失",
    STOCK_POSITION_UNAVAILABLE: "库存余额缺失",
    LEAD_TIME_UNAVAILABLE: "提前期缺失",
    BOM_UNAVAILABLE: "BOM 缺失",
    ROUTING_UNAVAILABLE: "工艺路线缺失",
};
const procurementLabels: Record<MrpRunRecord["demands"][number]["procurementType"], string> = {
    MAKE: "自制",
    BUY: "采购",
    OUTSOURCE: "委外",
};
const recommendationLabels: Record<MrpRunRecord["netRequirements"][number]["recommendationType"], string> = {
    NONE: "无需建议", PRODUCTION: "生产建议", PURCHASE: "采购建议", OUTSOURCE: "委外建议", BLOCKED: "已阻断",
};
function todayText() {
    return new Date().toISOString().slice(0, 10);
}
function futureDate(days: number) {
    const date = new Date();
    date.setDate(date.getDate() + days);
    return date.toISOString().slice(0, 10);
}
function formatDateTime(value: string) {
    return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value));
}
function MrpRunDialog({ onClose, onCreated }: {
    onClose: () => void;
    onCreated: (run: MrpRunRecord) => void;
}) {
    const dialogRef = useRef<HTMLElement>(null);
    const requestIdRef = useRef(`mrp-ui-${crypto.randomUUID()}`);
    const [name, setName] = useState(`滚动计划检查 · ${todayText()}`);
    const [horizonStart, setHorizonStart] = useState(todayText());
    const [horizonEnd, setHorizonEnd] = useState(futureDate(45));
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        if (!name.trim() || !horizonStart || !horizonEnd) {
            setError("请填写检查名称和完整计划期间。");
            return;
        }
        if (horizonStart > horizonEnd) {
            setError("计划开始日期不能晚于结束日期。");
            return;
        }
        setPending(true);
        try {
            const result = await submitMrpRun({ name: name.trim(), horizonStart, horizonEnd }, requestIdRef.current);
            onCreated(result.run);
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "MRP 运算失败，请重试");
            setPending(false);
        }
    }
    return (<GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}>
      <section ref={dialogRef} className="businessDialog mrpRunDialog" role="dialog" aria-modal="true" aria-labelledby="mrp-run-form-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader">
          <span className="dialogTitleMark"><MaterialIcon name="calculate" size={22}/></span>
          <div><h2 id="mrp-run-form-title">发起 MRP 运算</h2><p>冻结期间内的有效需求与供给，计算净需求并形成可审核计划建议。</p></div>
          <GsButton className="iconButton" htmlType="button" onClick={onClose} disabled={pending} aria-label="关闭MRP检查表单"><MaterialIcon name="close"/></GsButton>
        </header>
        <form onSubmit={submit}>
          <div className="formGrid mrpRunFormGrid">
            <label className="formField formFieldFull"><span>检查名称<em>必填</em></span><GsInput value={name} maxLength={120} onChange={(event) => setName(event.target.value)}/></label>
            <label className="formField"><span>计划开始<em>必填</em></span><GsInput type="date" value={horizonStart} onChange={(event) => setHorizonStart(event.target.value)}/></label>
            <label className="formField"><span>计划结束<em>必填</em></span><GsInput type="date" value={horizonEnd} onChange={(event) => setHorizonEnd(event.target.value)}/></label>
          </div>
          <div className="mrpRunTruthNotice"><MaterialIcon name="verified_user" size={18}/><span><strong>冻结供需并执行净算</strong>结果进入供需建议审核；审核后仍只创建采购或生产草稿，不会绕过下游状态机。</span></div>
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
          <footer className="dialogFooter"><span><MaterialIcon name="lock_clock" size={16}/>供需快照创建后不可变</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" htmlType="submit" disabled={pending}>{pending ? "正在运算" : "执行 MRP 运算"}</GsButton></div></footer>
        </form>
      </section>
    </GsModalHost>);
}
function MrpRunDrawer({ run, onClose }: {
    run: MrpRunRecord;
    onClose: () => void;
}) {
    const drawerRef = useRef<HTMLElement>(null);
    return (<GsDrawerHost onClose={onClose}>
      <aside ref={drawerRef} className="recordDrawer mrpRunDrawer" role="dialog" aria-modal="true" aria-labelledby="mrp-run-detail-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="recordDrawerHeader"><div><h2 id="mrp-run-detail-title">{run.runNumber}</h2><p>{run.name}</p></div><GsButton className="iconButton" aria-label="关闭MRP记录详情" onClick={onClose} htmlType="submit"><MaterialIcon name="close"/></GsButton></header>
        <section className="salesOrderSummary mrpRunSummary">
          <div><small>检查结果</small><strong className={`businessStatus businessStatus${run.status === "BLOCKED" ? "risk" : "good"}`}>{statusLabels[run.status]}</strong></div>
          <div><small>冻结需求</small><strong>{run.demandCount} 项</strong></div>
          <div><small>计划期间</small><strong>{run.horizonStart} 至 {run.horizonEnd}</strong></div>
          <div><small>发起时间</small><strong>{formatDateTime(run.startedAt)}</strong></div>
        </section>
        <section className="mrpRunDetailSection">
          <header><div><h3>运算异常</h3><p>缺失的业务事实会阻断对应建议，并保留处理路径。</p></div><strong>{run.exceptionCount}</strong></header>
          <div className="mrpExceptionList">{run.exceptions.length ? run.exceptions.map((item) => <article key={item.id}><span><MaterialIcon name="error" size={18}/></span><div><strong>{exceptionLabels[item.code]}{item.materialCode ? ` · ${item.materialCode}` : ""}</strong><p>{item.message}</p><small>处理建议：{item.resolutionPath}</small></div></article>) : <div className="businessEmptyState"><MaterialIcon name="task_alt" size={24}/><strong>本次运算没有阻断项</strong><p>供需事实完整，净需求结果已形成。</p></div>}</div>
        </section>
        <section className="mrpRunDetailSection">
          <header><div><h3>净需求与计划建议</h3><p>按需求日期依次消费可用库存和到期计划接收，自制缺口按 BOM 展开。</p></div><strong>{run.netRequirements.length}</strong></header>
          <div className="mrpSupplyList">{run.netRequirements.map((item) => <article key={item.id}><div><strong>{item.materialCode} · {item.materialName}</strong><small>{item.sourceType === "BOM_COMPONENT" ? `BOM 组件 · ${item.parentMaterialCode}` : "独立需求"} · 层级 {item.requirementLevel}</small></div><div><strong>{item.grossQuantity} {item.unit}</strong><small>毛需求 · 库存 {item.availableConsumed} · 在途 {item.scheduledReceiptConsumed}</small></div><div><strong>{item.netQuantity} {item.unit}</strong><small>{recommendationLabels[item.recommendationType]}{item.recommendedReleaseDate ? ` · ${item.recommendedReleaseDate} 下达` : ""}</small></div></article>)}</div>
        </section>
        <section className="mrpRunDetailSection">
          <header><div><h3>计划接收快照</h3><p>冻结已下达采购订单和已下达/执行中生产订单的未完成数量。</p></div><strong>{run.scheduledReceipts.length}</strong></header>
          <div className="mrpSupplyList">{run.scheduledReceipts.length ? run.scheduledReceipts.map((receipt) => <article key={receipt.id}><div><strong>{receipt.sourceOrderNumber} · {receipt.sourceName ?? "未命名来源"}</strong><small>{receipt.sourceType === "PRODUCTION_ORDER" ? "生产订单" : "采购订单"} · {receipt.materialCode} · {receipt.materialName}</small></div><div><strong>{receipt.outstandingQuantity} {receipt.unit}</strong><small>未完成数量</small></div><div><strong>{receipt.expectedReceiptDate}</strong><small>预计接收</small></div></article>) : <div className="businessEmptyState"><MaterialIcon name="inventory_2" size={24}/><strong>本次范围没有计划接收</strong><p>零在途是有效事实，净需求将依据库存和实际缺口计算。</p></div>}</div>
        </section>
        <section className="mrpRunDetailSection">
          <header><div><h3>库存供给快照</h3><p>冻结运算时点的现存、分配、冻结与可用数量。</p></div><strong>{run.supplies.length}</strong></header>
          <div className="mrpSupplyList">{run.supplies.map((supply) => <article key={supply.id}><div><strong>{supply.materialCode} · {supply.materialName}</strong><small>{supply.balanceCount} 个库存余额</small></div><div><strong>{supply.availableQuantity} {supply.unit}</strong><small>可用量</small></div><div><strong>{supply.onHandQuantity} / {supply.allocatedQuantity} / {supply.frozenQuantity}</strong><small>现存 / 分配 / 冻结</small></div></article>)}</div>
        </section>
        <section className="mrpRunDetailSection">
          <header><div><h3>冻结需求快照</h3><p>来源需求后续改变不会改写本次检查证据。</p></div><strong>{run.demands.length}</strong></header>
          <div className="mrpSnapshotList">{run.demands.map((demand) => <article key={demand.id}><div><strong>{demand.demandNumber}</strong><small>{demand.sourceNumber ?? "人工需求"}</small></div><div><strong>{demand.materialCode} · {demand.materialName}</strong><small>{procurementLabels[demand.procurementType]} · {demand.owner}</small></div><div><strong>{demand.quantity} {demand.unit}</strong><small>{demand.requiredDate}</small></div></article>)}</div>
        </section>
        <section className="mrpRunEvidence"><MaterialIcon name="fingerprint" size={18}/><div><strong>审计请求号</strong><p>{run.requestId ?? "未记录"}</p></div></section>
        <footer className="recordDrawerFooter"><Link className="secondaryButton" href="/planning/demand/independent"><MaterialIcon name="event_note" size={17}/>查看独立需求</Link><Link className="primaryButton" href="/planning/mrp/recommendations"><MaterialIcon name="account_tree" size={17}/>处理供需建议</Link></footer>
      </aside>
    </GsDrawerHost>);
}
export function MrpRunWorkspace({ initialData }: {
    initialData: MrpRunPageData;
}) {
    const [runs, setRuns] = useState(initialData.runs);
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState("全部状态");
    const [sortNewest, setSortNewest] = useState(true);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [creating, setCreating] = useState(false);
    const [detail, setDetail] = useState<MrpRunRecord | null>(null);
    const [toast, setToast] = useState("");
    const filtered = useMemo(() => runs.filter((run) => {
        const matchesQuery = !query.trim() || `${run.runNumber}${run.name}`.toLowerCase().includes(query.trim().toLowerCase());
        const matchesStatus = status === "全部状态" || statusLabels[run.status] === status;
        return matchesQuery && matchesStatus;
    }).sort((a, b) => (sortNewest ? -1 : 1) * a.startedAt.localeCompare(b.startedAt)), [query, runs, sortNewest, status]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const currentIds = pageRows.map((run) => run.id);
    const allCurrent = currentIds.length > 0 && currentIds.every((id) => selectedIds.has(id));
    const selectedRuns = runs.filter((run) => selectedIds.has(run.id));
    function exportRuns(rows: MrpRunRecord[]) {
        const content = ["运算编号,名称,计划开始,计划结束,冻结需求,阻断项,状态,发起时间,请求号", ...rows.map((run) => [run.runNumber, run.name, run.horizonStart, run.horizonEnd, run.demandCount, run.exceptionCount, statusLabels[run.status], run.startedAt, run.requestId ?? ""].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))].join("\n");
        const href = URL.createObjectURL(new Blob([`\uFEFF${content}`], { type: "text/csv;charset=utf-8" }));
        const anchor = document.createElement("a");
        anchor.href = href;
        anchor.download = `MRP运算记录-${todayText()}.csv`;
        anchor.click();
        URL.revokeObjectURL(href);
    }
    return (<div className="businessPage mrpRunPage">
      <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="calculate" size={23}/></span><div><h2>MRP 运算记录</h2><p>冻结需求、库存和采购/生产计划接收，执行时间分段净算并保留完整证据。</p></div></div><div className="pageHeadingActions"><Link className="secondaryButton" href="/planning/mrp/recommendations"><MaterialIcon name="account_tree" size={18}/>供需建议</Link><GsButton className="primaryButton" onClick={() => { setDetail(null); setCreating(true); }} htmlType="submit"><MaterialIcon name="play_arrow" size={18}/>发起 MRP 运算</GsButton></div></header>
      <section className="mrpReadinessBanner"><span><MaterialIcon name="verified" size={20}/></span><div><strong>库存、采购在途、生产在制、提前期和 BOM 已进入净需求运算</strong><p>运算结果只形成可审核的计划建议，不会绕过业务状态机直接创建采购或生产订单。</p></div><Link href="/planning/parameters">计划参数 <MaterialIcon name="arrow_outward" size={16}/></Link></section>
      <section className="businessMetrics"><div><small>运算记录</small><strong className="businessMetricinfo">{runs.length}</strong><em>当前租户可见范围</em></div><div><small>已完成</small><strong className="businessMetricgood">{runs.filter((run) => run.status === "COMPLETED").length}</strong><em>供需事实满足净算</em></div><div><small>计划建议</small><strong className="businessMetricwarn">{runs.reduce((sum, run) => sum + run.netRequirements.filter((item) => !["NONE", "BLOCKED"].includes(item.recommendationType)).length, 0)}</strong><em>待计划人员审核转单</em></div><div><small>已阻断</small><strong className="businessMetricrisk">{runs.filter((run) => run.status === "BLOCKED").length}</strong><em>按异常路径补齐事实</em></div></section>
      <section className="businessLedger mrpRunLedger">
        <div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索MRP运算记录" placeholder="搜索运算编号或检查名称" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }}/></div><RoundedSelect ariaLabel="MRP运算状态" options={["全部状态", ...Object.values(statusLabels)]} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }}/><div className="businessTableTools"><GsButton className="secondaryButton" onClick={() => setSortNewest((value) => !value)} htmlType="submit"><MaterialIcon name={sortNewest ? "south" : "north"} size={17}/>发起时间</GsButton><GsButton className="secondaryButton" onClick={() => exportRuns(selectedRuns.length ? selectedRuns : filtered)} htmlType="submit"><MaterialIcon name="download" size={17}/>{selectedRuns.length ? `导出所选（${selectedRuns.length}）` : "导出当前"}</GsButton></div></div>
        <div className="mrpRunTable" role="table" aria-label="MRP运算记录列表">
          <div className="mrpRunTableHeader" role="row"><GsCheckbox className="selectionCheckbox" aria-label="选择当前页全部MRP记录" checked={allCurrent} onChange={(event) => setSelectedIds((current) => { const next = new Set(current); currentIds.forEach((id) => event.target.checked ? next.add(id) : next.delete(id)); return next; })}/><span>运算编号 / 名称</span><span>计划期间</span><span>冻结需求</span><span>阻断项</span><span>发起时间</span><span>状态</span><span>操作</span></div>
          {pageRows.length ? pageRows.map((run) => <div className="mrpRunTableRow" role="row" key={run.id} onClick={() => setDetail(run)}><GsCheckbox className="selectionCheckbox" onClick={(event) => event.stopPropagation()} aria-label={`选择${run.runNumber}`} checked={selectedIds.has(run.id)} onChange={(event) => setSelectedIds((current) => {
                const next = new Set(current);
                if (event.target.checked)
                    next.add(run.id);
                else
                    next.delete(run.id);
                return next;
            })}/><span><strong>{run.runNumber}</strong><small>{run.name}</small></span><span><strong>{run.horizonStart}</strong><small>至 {run.horizonEnd}</small></span><span><strong>{run.demandCount} 项</strong><small>合计 {run.totalQuantity}</small></span><span><strong>{run.exceptionCount} 项</strong><small>{run.exceptionCount ? "需要处理" : "无阻断"}</small></span><span><strong>{formatDateTime(run.startedAt)}</strong><small>{run.requestId ?? "无请求号"}</small></span><em className={`businessStatus businessStatus${run.status === "BLOCKED" ? "risk" : "good"}`}>{statusLabels[run.status]}</em><span className="businessRowActions"><GsButton aria-label={`查看${run.runNumber}详情`} onClick={(event) => { event.stopPropagation(); setDetail(run); }} htmlType="submit"><MaterialIcon name="chevron_right" size={19}/></GsButton></span></div>) : <div className="businessEmptyState"><MaterialIcon name="calculate" size={28}/><strong>没有符合条件的运算记录</strong><p>调整筛选条件，或发起一次真实的 MRP 运算。</p></div>}
        </div>
        <footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => {
        setPage(nextPage);
        setPageSize(nextPageSize);
    }}/></footer>
      </section>
      {creating ? <MrpRunDialog onClose={() => setCreating(false)} onCreated={(run) => { setRuns((current) => [run, ...current]); setCreating(false); setDetail(run); setToast(`${run.runNumber} 已形成供需快照与净需求结果`); window.setTimeout(() => setToast(""), 3200); }}/> : null}
      {detail ? <MrpRunDrawer run={detail} onClose={() => setDetail(null)}/> : null}
      {toast ? <div className="toastMessage" role="status"><MaterialIcon name="fact_check" filled size={18}/>{toast}</div> : null}
    </div>);
}

