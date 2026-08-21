"use client";
import { GsButton, GsCheckbox, GsDrawerHost, GsInput, GsModalHost, GsTextArea, GsPagination } from "./ui";
import { type FormEvent, useMemo, useRef, useState } from "react";
import Link from "next/link";
import type { ProductionOrderRecord } from "@/lib/contracts";
import { submitProductionOrderMutation } from "@/services/production-order-client-service";
import type { ProductionOrderPageData, ProductionOrderWritePayload } from "@/services/production-order-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const statusLabels: Record<ProductionOrderRecord["status"], string> = { DRAFT: "草稿", RELEASED: "已下达", IN_PROGRESS: "执行中", COMPLETED: "已完成", CANCELLED: "已取消" };
const statusTones: Record<ProductionOrderRecord["status"], string> = { DRAFT: "neutral", RELEASED: "info", IN_PROGRESS: "warn", COMPLETED: "good", CANCELLED: "risk" };
const sourceLabels: Record<ProductionOrderRecord["sourceType"], string> = { MANUAL: "人工创建", MRP: "MRP 建议", SALES_ORDER: "销售订单" };
const actionCopy = {
    RELEASE: { title: "下达生产订单", description: "下达后将进入 MRP 计划接收，并锁定订单业务内容。", confirm: "确认下达" },
    START: { title: "确认生产开工", description: "开工后订单进入执行中，后续由报工与完工业务更新进度。", confirm: "确认开工" },
    CANCEL: { title: "取消生产订单", description: "取消后不再作为 MRP 计划接收，必须填写可审计原因。", confirm: "确认取消" },
} as const;
type OrderAction = keyof typeof actionCopy;
function todayText() { return new Date().toISOString().slice(0, 10); }
function futureDate(days: number) { const date = new Date(); date.setDate(date.getDate() + days); return date.toISOString().slice(0, 10); }
function ProductionOrderForm({ initial, data, onClose, onSaved }: {
    initial?: ProductionOrderRecord;
    data: ProductionOrderPageData;
    onClose: () => void;
    onSaved: (order: ProductionOrderRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const materials = data.references.materials;
    const initialMaterial = materials.find((item) => item.id === initial?.materialId) ?? materials[0];
    const [materialText, setMaterialText] = useState(initialMaterial ? `${initialMaterial.code} · ${initialMaterial.name}` : "暂无可用物料");
    const [quantity, setQuantity] = useState(String(initial?.plannedQuantity ?? 1));
    const [startDate, setStartDate] = useState(initial?.plannedStartDate ?? todayText());
    const [receiptDate, setReceiptDate] = useState(initial?.plannedReceiptDate ?? futureDate(7));
    const [workshop, setWorkshop] = useState(initial?.workshop ?? "总装一车间");
    const [owner, setOwner] = useState(initial?.owner ?? "周启明");
    const [sourceText, setSourceText] = useState(sourceLabels[initial?.sourceType ?? "MANUAL"]);
    const [sourceNumber, setSourceNumber] = useState(initial?.sourceNumber ?? "");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        const material = materials.find((item) => `${item.code} · ${item.name}` === materialText);
        const sourceType = (Object.entries(sourceLabels).find(([, label]) => label === sourceText)?.[0] ?? "MANUAL") as ProductionOrderWritePayload["sourceType"];
        if (!material || Number(quantity) <= 0 || !startDate || !receiptDate || !workshop.trim() || !owner.trim()) {
            setError("请完整填写物料、数量、计划日期、车间和责任人。");
            return;
        }
        if (startDate > receiptDate) {
            setError("计划开工日期不能晚于计划完工日期。");
            return;
        }
        setPending(true);
        try {
            const payload: ProductionOrderWritePayload = { materialId: material.id, plannedQuantity: Number(quantity), plannedStartDate: startDate, plannedReceiptDate: receiptDate, workshop: workshop.trim(), owner: owner.trim(), sourceType, sourceId: initial?.sourceId ?? null, sourceNumber: sourceNumber.trim() || null };
            const order = await submitProductionOrderMutation(initial ? { operation: "update", id: initial.id, payload: { ...payload, expectedVersion: initial.version } } : { operation: "create", payload });
            onSaved(order);
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "生产订单保存失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}><section ref={ref} className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="production-order-form-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="precision_manufacturing" size={22}/></span><div><h2 id="production-order-form-title">{initial ? "编辑生产订单" : "新建生产订单"}</h2><p>维护计划数量、日期、执行车间和业务来源。</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭生产订单表单" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>自制物料<em>必填</em></span><RoundedSelect ariaLabel="生产订单物料" options={materials.map((item) => `${item.code} · ${item.name}`)} value={materialText} onValueChange={setMaterialText}/></label><label className="formField"><span>计划数量<em>必填</em></span><GsInput type="number" min="0.0001" step="0.0001" value={quantity} onChange={(event) => setQuantity(event.target.value)}/></label><label className="formField"><span>业务来源</span><RoundedSelect ariaLabel="生产订单业务来源" options={Object.values(sourceLabels)} value={sourceText} onValueChange={setSourceText}/></label><label className="formField"><span>计划开工<em>必填</em></span><GsInput type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)}/></label><label className="formField"><span>计划完工<em>必填</em></span><GsInput type="date" value={receiptDate} onChange={(event) => setReceiptDate(event.target.value)}/></label><label className="formField"><span>执行车间<em>必填</em></span><GsInput maxLength={120} value={workshop} onChange={(event) => setWorkshop(event.target.value)}/></label><label className="formField"><span>责任人<em>必填</em></span><GsInput maxLength={80} value={owner} onChange={(event) => setOwner(event.target.value)}/></label><label className="formField formFieldFull"><span>来源单号</span><GsInput maxLength={60} value={sourceNumber} onChange={(event) => setSourceNumber(event.target.value)} placeholder="可选，如销售订单或 MRP 运算编号"/></label></div>{error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="lock" size={16}/>下达后业务内容不可编辑</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "正在保存" : "保存草稿"}</GsButton></div></footer></form></section></GsModalHost>;
}
function ProductionActionDialog({ order, action, onClose, onSaved }: {
    order: ProductionOrderRecord;
    action: OrderAction;
    onClose: () => void;
    onSaved: (order: ProductionOrderRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const [comment, setComment] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const copy = actionCopy[action];
    async function submit(event: FormEvent) {
        event.preventDefault();
        if (action === "CANCEL" && !comment.trim()) {
            setError("取消生产订单必须填写原因。");
            return;
        }
        setPending(true);
        setError("");
        try {
            onSaved(await submitProductionOrderMutation({ operation: "action", id: order.id, action, expectedVersion: order.version, comment: comment.trim() || undefined }));
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "操作失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}><section ref={ref} className="businessDialog actionConfirmDialog" role="dialog" aria-modal="true" aria-labelledby="production-action-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action === "CANCEL" ? "block" : "task_alt"} size={22}/></span><div><h2 id="production-action-title">{copy.title}</h2><p>{order.orderNumber} · {order.materialCode}</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭生产订单操作确认" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="mrpRunTruthNotice"><MaterialIcon name="info" size={18}/><span><strong>业务影响</strong>{copy.description}</span></div><label className="formField formFieldFull"><span>{action === "CANCEL" ? "取消原因" : "操作说明"}{action === "CANCEL" ? <em>必填</em> : null}</span><GsTextArea maxLength={500} rows={3} value={comment} onChange={(event) => setComment(event.target.value)}/></label>{error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}<footer className="dialogFooter"><span>当前版本 V{order.version}</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>返回</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "正在处理" : copy.confirm}</GsButton></div></footer></form></section></GsModalHost>;
}
function ProductionOrderDrawer({ order, onClose, onEdit, onAction }: {
    order: ProductionOrderRecord;
    onClose: () => void;
    onEdit: () => void;
    onAction: (action: OrderAction) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const actions: OrderAction[] = order.status === "DRAFT" ? ["RELEASE", "CANCEL"] : order.status === "RELEASED" ? ["START", "CANCEL"] : [];
    return <GsDrawerHost onClose={onClose}><aside ref={ref} className="recordDrawer" role="dialog" aria-modal="true" aria-labelledby="production-detail-title" onMouseDown={(event) => event.stopPropagation()}><header className="recordDrawerHeader"><div><h2 id="production-detail-title">{order.orderNumber}</h2><p>{order.materialCode} · {order.materialName}</p></div><GsButton className="iconButton" aria-label="关闭生产订单详情" onClick={onClose} htmlType="submit"><MaterialIcon name="close"/></GsButton></header><div className="recordDrawerBody"><section className="salesOrderSummary"><div><small>状态</small><strong className={`businessStatus businessStatus${statusTones[order.status]}`}>{statusLabels[order.status]}</strong></div><div><small>计划数量</small><strong>{order.plannedQuantity} {order.unit}</strong></div><div><small>已完工</small><strong>{order.completedQuantity} {order.unit}</strong></div><div><small>在检数量</small><strong>{order.reportedQuantity} {order.unit}</strong></div></section><section className="drawerSection"><header><h3>计划与责任</h3></header><div className="detailLedger"><div><span>计划开工</span><strong>{order.plannedStartDate}</strong></div><div><span>计划完工</span><strong>{order.plannedReceiptDate}</strong></div><div><span>执行车间</span><strong>{order.workshop}</strong></div><div><span>责任人</span><strong>{order.owner}</strong></div><div><span>当前可报</span><strong>{order.reportableQuantity} {order.unit}</strong></div><div><span>来源单号</span><strong>{order.sourceNumber ?? "—"}</strong></div></div></section><section className="drawerSection"><header><h3>MRP 供给语义</h3></header><div className="mrpRunTruthNotice"><MaterialIcon name="inventory" size={18}/><span><strong>{["RELEASED", "IN_PROGRESS"].includes(order.status) ? "已进入计划接收" : "当前不进入计划接收"}</strong>{["RELEASED", "IN_PROGRESS"].includes(order.status) ? `未完工 ${order.outstandingQuantity} ${order.unit}，预计 ${order.plannedReceiptDate} 接收。` : "只有已下达或执行中的未完工数量可供 MRP 使用。"}</span></div></section>{order.cancellationReason ? <section className="drawerSection"><header><h3>取消原因</h3></header><p>{order.cancellationReason}</p></section> : null}</div><footer className="recordDrawerFooter">{order.status === "DRAFT" ? <GsButton className="secondaryButton" onClick={onEdit} htmlType="submit"><MaterialIcon name="edit" size={17}/>编辑</GsButton> : <Link className="secondaryButton" href="/planning/mrp/runs"><MaterialIcon name="calculate" size={17}/>查看 MRP</Link>}{order.status === "IN_PROGRESS" ? <Link className="primaryButton" href="/production/reporting/reports"><MaterialIcon name="fact_check" size={17}/>生产报工</Link> : null}{actions.map((action) => <GsButton key={action} className={action === "CANCEL" ? "secondaryButton" : "primaryButton"} onClick={() => onAction(action)} htmlType="submit">{actionCopy[action].confirm}</GsButton>)}</footer></aside></GsDrawerHost>;
}
export function ProductionOrderWorkspace({ initialData }: {
    initialData: ProductionOrderPageData;
}) {
    const [orders, setOrders] = useState(initialData.orders);
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState("全部状态");
    const [sortNewest, setSortNewest] = useState(true);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [detail, setDetail] = useState<ProductionOrderRecord | null>(null);
    const [editing, setEditing] = useState<ProductionOrderRecord | "create" | null>(null);
    const [action, setAction] = useState<{
        order: ProductionOrderRecord;
        action: OrderAction;
    } | null>(null);
    const [toast, setToast] = useState("");
    const filtered = useMemo(() => orders.filter((order) => (!query.trim() || `${order.orderNumber}${order.materialCode}${order.materialName}${order.workshop}`.toLowerCase().includes(query.trim().toLowerCase())) && (status === "全部状态" || statusLabels[order.status] === status)).sort((a, b) => (sortNewest ? -1 : 1) * a.updatedAt.localeCompare(b.updatedAt)), [orders, query, sortNewest, status]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const rows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const currentIds = rows.map((item) => item.id);
    const allCurrent = currentIds.length > 0 && currentIds.every((id) => selectedIds.has(id));
    function save(order: ProductionOrderRecord, message: string) { setOrders((current) => current.some((item) => item.id === order.id) ? current.map((item) => item.id === order.id ? order : item) : [order, ...current]); setEditing(null); setAction(null); setDetail(order); setToast(message); window.setTimeout(() => setToast(""), 3200); }
    function exportRows() { const chosen = orders.filter((item) => selectedIds.has(item.id)); const source = chosen.length ? chosen : filtered; const csv = ["生产订单,物料,计划数量,完工数量,计划开工,计划完工,车间,责任人,状态", ...source.map((item) => [item.orderNumber, `${item.materialCode} ${item.materialName}`, item.plannedQuantity, item.completedQuantity, item.plannedStartDate, item.plannedReceiptDate, item.workshop, item.owner, statusLabels[item.status]].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))].join("\n"); const href = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" })); const anchor = document.createElement("a"); anchor.href = href; anchor.download = `生产订单-${todayText()}.csv`; anchor.click(); URL.revokeObjectURL(href); }
    return <div className="businessPage productionOrderPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="precision_manufacturing" size={23}/></span><div><h2>生产订单</h2><p>控制自制物料的下达、开工、完工与计划接收，为 MRP 提供可信的在制供给。</p></div></div><div className="pageHeadingActions"><Link className="secondaryButton" href="/planning/mrp/runs"><MaterialIcon name="calculate" size={18}/>MRP 运算</Link><GsButton className="primaryButton" onClick={() => { setDetail(null); setEditing("create"); }} htmlType="submit"><MaterialIcon name="add" size={18}/>新建生产订单</GsButton></div></header><section className="businessMetrics"><div><small>生产订单</small><strong className="businessMetricinfo">{orders.length}</strong><em>当前租户可见范围</em></div><div><small>计划接收</small><strong className="businessMetricgood">{orders.filter((item) => ["RELEASED", "IN_PROGRESS"].includes(item.status)).length}</strong><em>已进入 MRP 供给</em></div><div><small>执行中</small><strong className="businessMetricwarn">{orders.filter((item) => item.status === "IN_PROGRESS").length}</strong><em>等待报工或完工</em></div><div><small>未完工数量</small><strong>{orders.filter((item) => ["RELEASED", "IN_PROGRESS"].includes(item.status)).reduce((sum, item) => sum + item.outstandingQuantity, 0)}</strong><em>按各物料单位汇总展示</em></div></section><section className="businessLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索生产订单" placeholder="搜索订单、物料或车间" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }}/></div><RoundedSelect ariaLabel="生产订单状态" options={["全部状态", ...Object.values(statusLabels)]} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }}/><div className="businessTableTools"><GsButton className="secondaryButton" onClick={() => setSortNewest((value) => !value)} htmlType="submit"><MaterialIcon name={sortNewest ? "south" : "north"} size={17}/>更新时间</GsButton><GsButton className="secondaryButton" onClick={exportRows} htmlType="submit"><MaterialIcon name="download" size={17}/>{selectedIds.size ? `导出所选（${selectedIds.size}）` : "导出当前"}</GsButton></div></div><div className="salesOrderTable" role="table" aria-label="生产订单列表"><div className="salesOrderTableHeader" role="row"><GsCheckbox className="selectionCheckbox" aria-label="选择当前页全部生产订单" checked={allCurrent} onChange={(event) => setSelectedIds((current) => {
            const next = new Set(current);
            currentIds.forEach((id) => {
                if (event.target.checked)
                    next.add(id);
                else
                    next.delete(id);
            });
            return next;
        })}/><span>生产订单 / 物料</span><span>计划数量</span><span>计划日期</span><span>车间 / 责任人</span><span>来源</span><span>状态</span><span>操作</span></div>{rows.length ? rows.map((order) => <div className="salesOrderTableRow" role="row" key={order.id} onClick={() => setDetail(order)}><GsCheckbox className="selectionCheckbox" onClick={(event) => event.stopPropagation()} aria-label={`选择${order.orderNumber}`} checked={selectedIds.has(order.id)} onChange={(event) => setSelectedIds((current) => {
                const next = new Set(current);
                if (event.target.checked)
                    next.add(order.id);
                else
                    next.delete(order.id);
                return next;
            })}/><span><strong>{order.orderNumber}</strong><small>{order.materialCode} · {order.materialName}</small></span><span><strong>{order.plannedQuantity} {order.unit}</strong><small>未完工 {order.outstandingQuantity}</small></span><span><strong>{order.plannedStartDate}</strong><small>完工 {order.plannedReceiptDate}</small></span><span><strong>{order.workshop}</strong><small>{order.owner}</small></span><span><strong>{sourceLabels[order.sourceType]}</strong><small>{order.sourceNumber ?? "无来源单号"}</small></span><em className={`businessStatus businessStatus${statusTones[order.status]}`}>{statusLabels[order.status]}</em><span className="businessRowActions"><GsButton aria-label={`查看${order.orderNumber}详情`} onClick={(event) => { event.stopPropagation(); setDetail(order); }} htmlType="submit"><MaterialIcon name="chevron_right" size={19}/></GsButton></span></div>) : <div className="businessEmptyState"><MaterialIcon name="precision_manufacturing" size={28}/><strong>没有符合条件的生产订单</strong><p>调整筛选条件，或创建第一张受控生产订单。</p></div>}</div><footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => {
        setPage(nextPage);
        setPageSize(nextPageSize);
    }}/></footer></section>{editing ? <ProductionOrderForm initial={editing === "create" ? undefined : editing} data={initialData} onClose={() => setEditing(null)} onSaved={(order) => save(order, `${order.orderNumber} 已保存`)}/> : null}{detail ? <ProductionOrderDrawer order={detail} onClose={() => setDetail(null)} onEdit={() => { setEditing(detail); setDetail(null); }} onAction={(nextAction) => { setAction({ order: detail, action: nextAction }); setDetail(null); }}/> : null}{action ? <ProductionActionDialog order={action.order} action={action.action} onClose={() => setAction(null)} onSaved={(order) => save(order, `${order.orderNumber} 已${statusLabels[order.status]}`)}/> : null}{toast ? <div className="toastMessage" role="status"><MaterialIcon name="task_alt" filled size={18}/>{toast}</div> : null}</div>;
}

