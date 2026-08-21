"use client";
import { GsButton, GsCheckbox, GsDrawerHost, GsInput, GsModalHost, GsTextArea, GsPagination } from "./ui";
import { type FormEvent, useMemo, useRef, useState } from "react";
import type { InventoryMovementType, InventoryRecord } from "@/lib/contracts";
import { submitInventoryMovement } from "@/services/inventory-client-service";
import type { InventoryPageData } from "@/services/inventory-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const qualityLabels: Record<InventoryRecord["qualityStatus"], string> = { AVAILABLE: "合格", INSPECTION: "待检", BLOCKED: "冻结" };
const movementLabels: Record<InventoryMovementType, string> = { RECEIPT: "入库", ISSUE: "出库", RETURN: "生产退料", ALLOCATE: "分配", DEALLOCATE: "取消分配", FREEZE: "冻结", UNFREEZE: "解冻" };
const manualMovementTypes = Object.keys(movementLabels).filter((key): key is InventoryMovementType => key !== "RETURN");
function formatQuantity(value: number) { return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 3 }).format(value); }
function formatDateTime(value: string) { return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value)); }
function InventoryMovementDialog({ balance, onClose, onSaved }: {
    balance: InventoryRecord;
    onClose: () => void;
    onSaved: (record: InventoryRecord) => void;
}) {
    const dialogRef = useRef<HTMLElement>(null);
    const requestIdRef = useRef(`inventory-ui-${crypto.randomUUID()}`);
    const [typeLabel, setTypeLabel] = useState(movementLabels.RECEIPT);
    const [quantity, setQuantity] = useState("");
    const [reason, setReason] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const movementType = manualMovementTypes.find((key) => movementLabels[key] === typeLabel) ?? "RECEIPT";
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        const numericQuantity = Number(quantity);
        if (!Number.isFinite(numericQuantity) || numericQuantity <= 0 || !reason.trim()) {
            setError("请填写大于 0 的数量和明确的业务原因。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitInventoryMovement({ id: balance.id, movementType, quantity: numericQuantity, reason: reason.trim(), expectedVersion: balance.version }, requestIdRef.current));
        }
        catch (cause) {
            setError(cause instanceof Error ? cause.message : "库存事务过账失败，请重试");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}><section ref={dialogRef} className="businessDialog inventoryMovementDialog" role="dialog" aria-modal="true" aria-labelledby="inventory-movement-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="sync_alt" size={22}/></span><div><h2 id="inventory-movement-title">登记库存事务</h2><p>{balance.materialCode} · {balance.warehouseName} / {balance.locationCode}</p></div><GsButton className="iconButton" htmlType="button" onClick={onClose} disabled={pending} aria-label="关闭库存事务表单"><MaterialIcon name="close"/></GsButton></header>
    <form onSubmit={submit}><div className="formGrid">
      <label className="formField"><span>事务类型<em>必填</em></span><RoundedSelect ariaLabel="库存事务类型" options={manualMovementTypes.map((key) => movementLabels[key])} value={typeLabel} onValueChange={setTypeLabel}/></label>
      <label className="formField"><span>数量（{balance.unit}）<em>必填</em></span><GsInput type="number" min="0.001" step="0.001" value={quantity} onChange={(event) => setQuantity(event.target.value)} placeholder="请输入本次变动数量"/></label>
      <label className="formField formFieldFull"><span>业务原因<em>必填</em></span><GsTextArea value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} placeholder="填写来源单据、责任人或更正原因，作为审计证据"/></label>
    </div><div className="inventoryRuleNotice"><MaterialIcon name="verified_user" size={18}/><span>提交后生成不可删除的库存流水；错误记录应以反向事务更正。当前余额版本为 V{balance.version}。</span></div>
    {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
    <footer className="dialogFooter"><span><MaterialIcon name="fingerprint" size={16}/>请求号确保重复提交不重复记账</span><div><GsButton className="secondaryButton" htmlType="button" disabled={pending} onClick={onClose}>取消</GsButton><GsButton className="primaryButton" htmlType="submit" disabled={pending}>{pending ? "正在过账" : `确认${movementLabels[movementType]}`}</GsButton></div></footer></form>
  </section></GsModalHost>;
}
function InventoryDrawer({ balance, onClose, onPost }: {
    balance: InventoryRecord;
    onClose: () => void;
    onPost: () => void;
}) {
    const drawerRef = useRef<HTMLElement>(null);
    return <GsDrawerHost onClose={onClose}><aside ref={drawerRef} className="recordDrawer inventoryDrawer" role="dialog" aria-modal="true" aria-labelledby="inventory-detail-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="recordDrawerHeader"><div><h2 id="inventory-detail-title">{balance.materialCode} · {balance.lotNumber || "无批次"}</h2><p>{balance.materialName} · {balance.warehouseName} / {balance.locationCode}</p></div><GsButton className="iconButton" aria-label="关闭库存余额详情" onClick={onClose} htmlType="submit"><MaterialIcon name="close"/></GsButton></header>
    <section className="salesOrderSummary inventorySummary"><div><small>现存量</small><strong>{formatQuantity(balance.onHandQuantity)} {balance.unit}</strong></div><div><small>已分配 / 冻结</small><strong>{formatQuantity(balance.allocatedQuantity)} / {formatQuantity(balance.frozenQuantity)}</strong></div><div><small>可用量</small><strong>{formatQuantity(balance.availableQuantity)} {balance.unit}</strong></div><div><small>质量状态</small><strong className={`businessStatus inventoryQuality${balance.qualityStatus.toLowerCase()}`}>{qualityLabels[balance.qualityStatus]}</strong></div></section>
    <section className="inventoryDimensionGrid"><div><small>仓库</small><strong>{balance.warehouseCode} · {balance.warehouseName}</strong></div><div><small>库位</small><strong>{balance.locationCode} · {balance.locationName}</strong></div><div><small>物料规格</small><strong>{balance.materialSpecification ?? "未维护"}</strong></div><div><small>余额版本</small><strong>V{balance.version} · {formatDateTime(balance.updatedAt)}</strong></div></section>
    <section className="mrpRunDetailSection inventoryMovementSection"><header><div><h3>库存流水</h3><p>按发生时间倒序保留前后余额和请求编号。</p></div><strong>{balance.movements.length}</strong></header><div className="inventoryMovementList">{balance.movements.length ? balance.movements.map((item) => <article key={item.id}><span className="inventoryMovementIcon"><MaterialIcon name={item.movementType === "RECEIPT" || item.movementType === "DEALLOCATE" || item.movementType === "UNFREEZE" ? "south_west" : "north_east"} size={17}/></span><div><strong>{item.movementNumber} · {movementLabels[item.movementType]}</strong><p>{item.reason}</p><small>现存 {formatQuantity(item.beforeOnHand)} → {formatQuantity(item.afterOnHand)} · 分配 {formatQuantity(item.beforeAllocated)} → {formatQuantity(item.afterAllocated)} · 冻结 {formatQuantity(item.beforeFrozen)} → {formatQuantity(item.afterFrozen)}</small><small>{formatDateTime(item.occurredAt)} · {item.requestId}</small></div><em>{formatQuantity(item.quantity)} {balance.unit}</em></article>) : <p className="inventoryNoMovements">暂无库存流水。</p>}</div></section>
    <footer className="recordDrawerFooter"><GsButton className="primaryButton" onClick={onPost} htmlType="submit"><MaterialIcon name="sync_alt" size={17}/>登记库存事务</GsButton></footer>
  </aside></GsDrawerHost>;
}
export function InventoryWorkspace({ initialData }: {
    initialData: InventoryPageData;
}) {
    const [balances, setBalances] = useState(initialData.balances);
    const [query, setQuery] = useState("");
    const [quality, setQuality] = useState("全部质量状态");
    const [warehouse, setWarehouse] = useState("全部仓库");
    const [sortAvailableDesc, setSortAvailableDesc] = useState(true);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [detail, setDetail] = useState<InventoryRecord | null>(null);
    const [posting, setPosting] = useState<InventoryRecord | null>(null);
    const [toast, setToast] = useState("");
    const warehouseLabels = initialData.referenceData.warehouses.map((item) => `${item.code} · ${item.name}`);
    const filtered = useMemo(() => balances.filter((item) => {
        const text = `${item.materialCode}${item.materialName}${item.lotNumber}${item.locationCode}${item.warehouseName}`.toLowerCase();
        return (!query.trim() || text.includes(query.trim().toLowerCase())) && (quality === "全部质量状态" || qualityLabels[item.qualityStatus] === quality) && (warehouse === "全部仓库" || `${item.warehouseCode} · ${item.warehouseName}` === warehouse);
    }).sort((a, b) => (sortAvailableDesc ? -1 : 1) * (a.availableQuantity - b.availableQuantity)), [balances, query, quality, warehouse, sortAvailableDesc]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const currentIds = pageRows.map((item) => item.id);
    const allCurrent = currentIds.length > 0 && currentIds.every((id) => selectedIds.has(id));
    const selectedRows = balances.filter((item) => selectedIds.has(item.id));
    function exportRows(rows: InventoryRecord[]) { const content = ["物料编码,物料名称,批次,仓库,库位,质量状态,现存量,已分配,冻结量,可用量,单位", ...rows.map((item) => [item.materialCode, item.materialName, item.lotNumber, item.warehouseName, item.locationCode, qualityLabels[item.qualityStatus], item.onHandQuantity, item.allocatedQuantity, item.frozenQuantity, item.availableQuantity, item.unit].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))].join("\n"); const href = URL.createObjectURL(new Blob([`\uFEFF${content}`], { type: "text/csv;charset=utf-8" })); const anchor = document.createElement("a"); anchor.href = href; anchor.download = `库存余额-${new Date().toISOString().slice(0, 10)}.csv`; anchor.click(); URL.revokeObjectURL(href); }
    function saved(record: InventoryRecord) { setBalances((items) => items.map((item) => item.id === record.id ? record : item)); setDetail(null); setPosting(null); setToast(`${record.materialCode} 库存事务已过账`); window.setTimeout(() => setToast(""), 3200); }
    const totals = balances.reduce((result, item) => ({ onHand: result.onHand + item.onHandQuantity, allocatedFrozen: result.allocatedFrozen + item.allocatedQuantity + item.frozenQuantity, available: result.available + item.availableQuantity }), { onHand: 0, allocatedFrozen: 0, available: 0 });
    return <div className="businessPage inventoryPage">
    <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="inventory_2" size={23}/></span><div><h2>库存余额</h2><p>按仓库、库位、物料、批次和质量状态查看库存事实，通过不可变流水完成每次变动。</p></div></div><div className="pageHeadingActions"><GsButton className="primaryButton" disabled={!balances.length} onClick={() => setPosting(detail ?? balances[0])} htmlType="submit"><MaterialIcon name="sync_alt" size={18}/>登记库存事务</GsButton></div></header>
    {initialData.source === "unavailable" ? <section className="businessUnavailable"><MaterialIcon name="cloud_off" size={24}/><div><strong>库存服务暂时不可用</strong><p>{initialData.error}</p></div></section> : null}
    <section className="businessMetrics"><div><small>库存余额</small><strong className="businessMetricinfo">{balances.length}</strong><em>物料 · 仓库 · 库位 · 批次</em></div><div><small>现存量</small><strong>{formatQuantity(totals.onHand)}</strong><em>包含待检与冻结库存</em></div><div><small>分配与冻结</small><strong className="businessMetricwarn">{formatQuantity(totals.allocatedFrozen)}</strong><em>当前不可自由使用</em></div><div><small>可用量</small><strong className="businessMetricgood">{formatQuantity(totals.available)}</strong><em>仅合格库存参与计划</em></div></section>
    <section className="businessLedger inventoryLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索库存余额" placeholder="搜索物料、批次、仓库或库位" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }}/></div><div className="businessFilters"><RoundedSelect ariaLabel="库存质量状态" options={["全部质量状态", ...Object.values(qualityLabels)]} value={quality} onValueChange={(value) => { setQuality(value); setPage(1); }}/><RoundedSelect ariaLabel="库存仓库" options={["全部仓库", ...warehouseLabels]} value={warehouse} onValueChange={(value) => { setWarehouse(value); setPage(1); }}/></div><div className="businessTableTools"><GsButton className="secondaryButton" onClick={() => setSortAvailableDesc((value) => !value)} htmlType="submit"><MaterialIcon name={sortAvailableDesc ? "south" : "north"} size={17}/>可用量</GsButton><GsButton className="secondaryButton" onClick={() => exportRows(selectedRows.length ? selectedRows : filtered)} htmlType="submit"><MaterialIcon name="download" size={17}/>{selectedRows.length ? `导出所选（${selectedRows.length}）` : "导出当前"}</GsButton></div></div>
      <div className="inventoryTable" role="table" aria-label="库存余额列表"><div className="inventoryTableHeader" role="row"><GsCheckbox className="selectionCheckbox" aria-label="选择当前页全部库存余额" checked={allCurrent} onChange={(event) => setSelectedIds((current) => { const next = new Set(current); currentIds.forEach((id) => event.target.checked ? next.add(id) : next.delete(id)); return next; })}/><span>物料 / 批次</span><span>仓库 / 库位</span><span>质量</span><span>现存量</span><span>分配 / 冻结</span><span>可用量</span><span>更新时间</span><span>操作</span></div>
      {pageRows.length ? pageRows.map((item) => <div className="inventoryTableRow" role="row" key={item.id} onClick={() => setDetail(item)}><GsCheckbox className="selectionCheckbox" onClick={(event) => event.stopPropagation()} aria-label={`选择${item.materialCode}`} checked={selectedIds.has(item.id)} onChange={(event) => setSelectedIds((current) => {
                const next = new Set(current);
                if (event.target.checked)
                    next.add(item.id);
                else
                    next.delete(item.id);
                return next;
            })}/><span><strong>{item.materialCode} · {item.materialName}</strong><small>{item.lotNumber || "无批次"}</small></span><span><strong>{item.warehouseCode} · {item.warehouseName}</strong><small>{item.locationCode} · {item.locationName}</small></span><em className={`businessStatus inventoryQuality${item.qualityStatus.toLowerCase()}`}>{qualityLabels[item.qualityStatus]}</em><span><strong>{formatQuantity(item.onHandQuantity)} {item.unit}</strong><small>版本 V{item.version}</small></span><span><strong>{formatQuantity(item.allocatedQuantity)} / {formatQuantity(item.frozenQuantity)}</strong><small>已分配 / 冻结</small></span><span><strong>{formatQuantity(item.availableQuantity)} {item.unit}</strong><small>{item.qualityStatus === "AVAILABLE" ? "可参与计划" : "不参与可用量"}</small></span><span><strong>{formatDateTime(item.updatedAt)}</strong><small>{item.movements[0]?.movementNumber ?? "暂无流水"}</small></span><span className="businessRowActions"><GsButton aria-label={`登记${item.materialCode}库存事务`} onClick={(event) => { event.stopPropagation(); setPosting(item); }} htmlType="submit"><MaterialIcon name="sync_alt" size={18}/></GsButton><GsButton aria-label={`查看${item.materialCode}库存详情`} onClick={(event) => { event.stopPropagation(); setDetail(item); }} htmlType="submit"><MaterialIcon name="chevron_right" size={19}/></GsButton></span></div>) : <div className="businessEmptyState"><MaterialIcon name="inventory_2" size={28}/><strong>没有符合条件的库存余额</strong><p>调整搜索、质量状态或仓库筛选条件。</p></div>}</div>
      <footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => {
        setPage(nextPage);
        setPageSize(nextPageSize);
    }}/></footer>
    </section>
    {detail && !posting ? <InventoryDrawer balance={detail} onClose={() => setDetail(null)} onPost={() => { setDetail(null); setPosting(detail); }}/> : null}
    {posting ? <InventoryMovementDialog balance={posting} onClose={() => setPosting(null)} onSaved={saved}/> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}
  </div>;
}

