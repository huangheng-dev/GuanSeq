"use client";
import { GsButton, GsCheckbox, GsDrawerHost, GsInput, GsModalHost, GsPagination, GsTextArea } from "./ui";
import { type FormEvent, useMemo, useRef, useState } from "react";
import type { PurchaseOrderRecord, PurchaseOrderReferenceData } from "@/lib/contracts";
import { submitPurchaseOrderMutation } from "@/services/procurement-client-service";
import type { ProcurementPageData, PurchaseOrderWritePayload } from "@/services/procurement-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const statusLabels: Record<PurchaseOrderRecord["status"], string> = { DRAFT: "草稿", PENDING_APPROVAL: "待审核", APPROVED: "已审核", REJECTED: "已驳回", RELEASED: "已下达" };
const actionLabels = { SUBMIT: "提交审核", APPROVE: "通过审核", REJECT: "驳回订单", RELEASE: "下达订单" } as const;
type Action = keyof typeof actionLabels;
type Line = {
    material: string;
    quantity: string;
    unitPrice: string;
};
const money = (value: number, currency: string) => new Intl.NumberFormat("zh-CN", { style: "currency", currency }).format(value);
const tone = (status: PurchaseOrderRecord["status"]) => status === "RELEASED" ? "good" : status === "REJECTED" ? "risk" : status === "PENDING_APPROVAL" ? "warn" : "info";
const supplierLabel = (item: PurchaseOrderReferenceData["suppliers"][number]) => `${item.code} · ${item.name}`;
const materialLabel = (item: PurchaseOrderReferenceData["materials"][number]) => `${item.code} · ${item.name}${item.specification ? ` · ${item.specification}` : ""}`;
function PurchaseForm({ order, references, onClose, onSaved }: {
    order: PurchaseOrderRecord | null;
    references: PurchaseOrderReferenceData;
    onClose: () => void;
    onSaved: (value: PurchaseOrderRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const suppliers = references.suppliers.map(supplierLabel);
    const materials = references.materials.map(materialLabel);
    const [supplier, setSupplier] = useState(() => order ? supplierLabel(references.suppliers.find((x) => x.id === order.supplierId) ?? references.suppliers[0]) : suppliers[0] ?? "");
    const [currency, setCurrency] = useState<string>(order?.currency ?? "CNY");
    const [tax, setTax] = useState(`${Math.round((order?.taxRate ?? 0.13) * 100)}%`);
    const [requested, setRequested] = useState(order?.requestedReceiptDate ?? "2026-08-25");
    const [promised, setPromised] = useState(order?.promisedReceiptDate ?? "2026-08-25");
    const [buyer, setBuyer] = useState(order?.buyer ?? "唐工");
    const [lines, setLines] = useState<Line[]>(() => order?.lines.map((line) => ({ material: materialLabel(references.materials.find((x) => x.id === line.materialId) ?? references.materials[0]), quantity: String(line.orderedQuantity), unitPrice: String(line.unitPrice) })) ?? [{ material: materials[0] ?? "", quantity: "1", unitPrice: "0" }]);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const net = lines.reduce((sum, line) => sum + Number(line.quantity || 0) * Number(line.unitPrice || 0), 0);
    const taxRate = Number(tax.replace("%", "")) / 100;
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        const supplierRecord = references.suppliers.find((x) => supplierLabel(x) === supplier);
        const payloadLines = lines.map((line) => ({ materialId: references.materials.find((x) => materialLabel(x) === line.material)?.id ?? "", orderedQuantity: Number(line.quantity), unitPrice: Number(line.unitPrice) }));
        if (!supplierRecord || !requested || !buyer.trim() || payloadLines.some((line) => !line.materialId || line.orderedQuantity <= 0 || line.unitPrice < 0)) {
            setError("请完整填写供应商、到货日期、采购员和有效明细。");
            return;
        }
        setPending(true);
        const payload: PurchaseOrderWritePayload = { supplierId: supplierRecord.id, currency: currency as PurchaseOrderWritePayload["currency"], taxRate, requestedReceiptDate: requested, promisedReceiptDate: promised || null, buyer: buyer.trim(), lines: payloadLines };
        try {
            onSaved(await submitPurchaseOrderMutation(order ? { operation: "update", id: order.id, payload: { ...payload, expectedVersion: order.version } } : { operation: "create", payload }));
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "采购订单保存失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => { if (!pending)
        onClose(); }}><section ref={ref} className="businessDialog salesOrderDialog" role="dialog" aria-modal="true" aria-labelledby="purchase-form-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="shopping_cart" size={22}/></span><div><h2 id="purchase-form-title">{order ? `编辑 ${order.orderNumber}` : "新建采购订单"}</h2><p>仅可选择当前租户启用的供应商与采购/委外物料。</p></div><GsButton className="iconButton" htmlType="button" onClick={onClose} disabled={pending} aria-label="关闭采购订单表单"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid salesOrderHeaderFields"><label className="formField formFieldFull"><span>供应商<em>必填</em></span><RoundedSelect ariaLabel="供应商" options={suppliers} value={supplier} onValueChange={setSupplier} size="field"/></label><label className="formField"><span>币种</span><RoundedSelect ariaLabel="币种" options={["CNY", "USD", "EUR"]} value={currency} onValueChange={setCurrency} size="field"/></label><label className="formField"><span>税率</span><RoundedSelect ariaLabel="税率" options={["13%", "9%", "6%", "0%"]} value={tax} onValueChange={setTax} size="field"/></label><label className="formField"><span>要求到货<em>必填</em></span><GsInput type="date" value={requested} onChange={(event) => setRequested(event.target.value)}/></label><label className="formField"><span>承诺到货</span><GsInput type="date" value={promised} onChange={(event) => setPromised(event.target.value)}/></label><label className="formField formFieldFull"><span>采购员<em>必填</em></span><GsInput value={buyer} onChange={(event) => setBuyer(event.target.value)}/></label></div><section className="salesOrderLines"><header><div><h3>采购明细</h3><p>同一物料只能出现一次，收货进度由后续到货闭环更新。</p></div><GsButton htmlType="button" className="secondaryButton" onClick={() => setLines((current) => [...current, { material: materials.find((x) => !current.some((line) => line.material === x)) ?? materials[0] ?? "", quantity: "1", unitPrice: "0" }])}><MaterialIcon name="add" size={17}/>添加明细</GsButton></header><div className="salesOrderLineHeader"><span>物料</span><span>数量</span><span>未税单价</span><span>未税金额</span><span>操作</span></div>{lines.map((line, index) => <div className="salesOrderLine" key={`${index}-${line.material}`}><RoundedSelect ariaLabel={`第${index + 1}行物料`} options={materials} value={line.material} onValueChange={(value) => setLines((current) => current.map((item, i) => i === index ? { ...item, material: value } : item))} size="field"/><GsInput aria-label={`第${index + 1}行数量`} type="number" min="0.0001" step="0.0001" value={line.quantity} onChange={(event) => setLines((current) => current.map((item, i) => i === index ? { ...item, quantity: event.target.value } : item))}/><GsInput aria-label={`第${index + 1}行未税单价`} type="number" min="0" step="0.0001" value={line.unitPrice} onChange={(event) => setLines((current) => current.map((item, i) => i === index ? { ...item, unitPrice: event.target.value } : item))}/><strong>{money(Number(line.quantity || 0) * Number(line.unitPrice || 0), currency)}</strong><GsButton htmlType="button" aria-label={`删除第${index + 1}行`} disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, i) => i !== index))}><MaterialIcon name="delete" size={18}/></GsButton></div>)}<footer><span>未税合计 <strong>{money(net, currency)}</strong></span><span>税额 <strong>{money(net * taxRate, currency)}</strong></span><span>含税合计 <strong>{money(net * (1 + taxRate), currency)}</strong></span></footer></section>{error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="shield" size={16}/>保存产生版本与审计证据</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "正在保存" : "保存订单"}</GsButton></div></footer></form></section></GsModalHost>;
}
function ActionDialog({ order, action, onClose, onDone }: {
    order: PurchaseOrderRecord;
    action: Action;
    onClose: () => void;
    onDone: (value: PurchaseOrderRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const [comment, setComment] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function confirm() { if (action === "REJECT" && !comment.trim()) {
        setError("驳回订单必须填写原因。");
        return;
    } setPending(true); try {
        onDone(await submitPurchaseOrderMutation({ operation: "action", id: order.id, action, expectedVersion: order.version, comment: comment.trim() || undefined }));
    }
    catch (reason) {
        setError(reason instanceof Error ? reason.message : "状态操作失败");
        setPending(false);
    } }
    return <GsModalHost onClose={() => { if (!pending)
        onClose(); }}><section ref={ref} className="deleteConfirmDialog salesOrderActionDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}><span className="deleteConfirmIcon"><MaterialIcon name={action === "REJECT" ? "undo" : action === "RELEASE" ? "local_shipping" : "task_alt"} size={24}/></span><div><h2>{actionLabels[action]} · {order.orderNumber}</h2><p>{action === "RELEASE" ? "下达后，未收数量将成为 MRP 可冻结的采购计划接收。" : "该操作将写入采购订单状态与审计证据。"}</p>{action !== "SUBMIT" ? <GsTextArea aria-label="操作说明" rows={3} value={comment} onChange={(event) => setComment(event.target.value)} placeholder={action === "REJECT" ? "请填写驳回原因" : "可填写操作说明"}/> : null}{error ? <div className="formError" role="alert">{error}</div> : null}</div><footer><GsButton className="secondaryButton" onClick={onClose} disabled={pending} htmlType="submit">取消</GsButton><GsButton className={action === "REJECT" ? "dangerButton" : "primaryButton"} onClick={() => void confirm()} disabled={pending} htmlType="submit">{pending ? "正在处理" : actionLabels[action]}</GsButton></footer></section></GsModalHost>;
}
function Drawer({ order, onClose, onEdit, onAction }: {
    order: PurchaseOrderRecord;
    onClose: () => void;
    onEdit: () => void;
    onAction: (action: Action) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    return <GsDrawerHost onClose={onClose}><aside ref={ref} className="recordDrawer salesOrderDrawer" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}><header className="recordDrawerHeader"><div><h2>{order.orderNumber}</h2><p>{order.supplierCode} · {order.supplierName}</p></div><GsButton className="iconButton" aria-label="关闭采购订单详情" onClick={onClose} htmlType="submit"><MaterialIcon name="close"/></GsButton></header><section className="salesOrderSummary"><div><small>订单状态</small><strong className={`businessStatus businessStatus${tone(order.status)}`}>{statusLabels[order.status]}</strong></div><div><small>含税金额</small><strong>{money(order.totalGrossAmount, order.currency)}</strong></div><div><small>要求 / 承诺到货</small><strong>{order.requestedReceiptDate} / {order.promisedReceiptDate ?? "待承诺"}</strong></div><div><small>采购员</small><strong>{order.buyer}</strong></div></section>{order.rejectionReason ? <div className="salesOrderRejection"><MaterialIcon name="warning" size={18}/><span><strong>驳回原因</strong>{order.rejectionReason}</span></div> : null}<section className="salesOrderDrawerLines"><h3>采购与收货进度</h3>{order.lines.map((line) => <article key={line.id}><div><strong>{line.materialCode} · {line.materialName}</strong><small>{line.materialSpecification ?? "无规格"}</small></div><span>{line.receivedQuantity} / {line.orderedQuantity} {line.unit}<small> 已收 / 订购</small></span><b>在途 {line.outstandingQuantity}</b></article>)}</section><footer className="recordDrawerFooter">{order.status === "DRAFT" || order.status === "REJECTED" ? <GsButton className="secondaryButton" onClick={onEdit} htmlType="submit"><MaterialIcon name="edit" size={17}/>编辑</GsButton> : null}{order.status === "DRAFT" ? <GsButton className="primaryButton" onClick={() => onAction("SUBMIT")} htmlType="submit">提交审核</GsButton> : null}{order.status === "PENDING_APPROVAL" ? <><GsButton className="dangerButton" onClick={() => onAction("REJECT")} htmlType="submit">驳回</GsButton><GsButton className="primaryButton" onClick={() => onAction("APPROVE")} htmlType="submit">通过审核</GsButton></> : null}{order.status === "APPROVED" ? <GsButton className="primaryButton" onClick={() => onAction("RELEASE")} htmlType="submit"><MaterialIcon name="local_shipping" size={17}/>下达订单</GsButton> : null}</footer></aside></GsDrawerHost>;
}
export function ProcurementOrderWorkspace({ initialData }: {
    initialData: ProcurementPageData;
}) {
    const [orders, setOrders] = useState(initialData.orders);
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState("全部状态");
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [detail, setDetail] = useState<PurchaseOrderRecord | null>(null);
    const [editing, setEditing] = useState<PurchaseOrderRecord | null | undefined>();
    const [action, setAction] = useState<Action | null>(null);
    const [toast, setToast] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const filtered = useMemo(() => orders.filter((order) => (!query.trim() || `${order.orderNumber}${order.supplierName}${order.buyer}`.toLowerCase().includes(query.toLowerCase())) && (status === "全部状态" || statusLabels[order.status] === status)), [orders, query, status]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const all = filtered.length > 0 && filtered.every((x) => selected.has(x.id));
    const released = orders.filter((x) => x.status === "RELEASED");
    const inTransit = released.reduce((sum, order) => sum + order.lines.reduce((lineSum, line) => lineSum + line.outstandingQuantity, 0), 0);
    function saved(order: PurchaseOrderRecord, message: string) { setOrders((current) => current.some((x) => x.id === order.id) ? current.map((x) => x.id === order.id ? order : x) : [order, ...current]); setEditing(undefined); setAction(null); setDetail(order); setToast(message); window.setTimeout(() => setToast(""), 2600); }
    // eslint-disable-next-line @typescript-eslint/no-unused-expressions
    return <div className="businessPage salesOrderPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="shopping_cart" size={23}/></span><div><h2>采购订单</h2><p>从供应商、采购物料、价格和到货承诺建立受控订单，下达后形成 MRP 在途供给。</p></div></div><div className="pageHeadingActions"><GsButton className="primaryButton" onClick={() => { setDetail(null); setEditing(null); }} htmlType="submit"><MaterialIcon name="add" size={18}/>新建采购订单</GsButton></div></header><section className="businessMetrics"><div><small>采购订单</small><strong className="businessMetricinfo">{orders.length}</strong><em>当前租户范围</em></div><div><small>待审核</small><strong className="businessMetricwarn">{orders.filter((x) => x.status === "PENDING_APPROVAL").length}</strong><em>需要采购负责人复核</em></div><div><small>已下达</small><strong className="businessMetricgood">{released.length}</strong><em>正式供应承诺</em></div><div><small>在途数量</small><strong className="businessMetricgood">{inTransit}</strong><em>已下达且未收齐</em></div></section><section className="businessLedger salesOrderLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索采购订单" placeholder="搜索订单号、供应商或采购员" value={query} onChange={(event) => setQuery(event.target.value)}/></div><RoundedSelect ariaLabel="采购订单状态" options={["全部状态", ...Object.values(statusLabels)]} value={status} onValueChange={setStatus}/></div><div className="salesOrderTable" role="table" aria-label="采购订单列表"><div className="salesOrderTableHeader" role="row"><GsCheckbox className="selectionCheckbox" aria-label="选择全部采购订单" checked={all} onChange={(event) => setSelected(event.target.checked ? new Set(filtered.map((x) => x.id)) : new Set())}/><span>订单号</span><span>供应商</span><span>采购明细</span><span>金额 / 币种</span><span>要求 / 承诺到货</span><span>状态</span><span>操作</span></div>{pageRows.map((order) => <div className="salesOrderTableRow" role="row" key={order.id} onClick={() => setDetail(order)}><GsCheckbox className="selectionCheckbox" onClick={(event) => event.stopPropagation()} aria-label={`选择${order.orderNumber}`} checked={selected.has(order.id)} onChange={(event) => setSelected((current) => { const next = new Set(current); event.target.checked ? next.add(order.id) : next.delete(order.id); return next; })}/><strong>{order.orderNumber}</strong><span><b>{order.supplierName}</b><small>{order.supplierCode}</small></span><span><b>{order.lines[0].materialName}</b><small>{order.lines.length} 项 · 在途 {order.lines.reduce((sum, line) => sum + line.outstandingQuantity, 0)}</small></span><span><b>{money(order.totalGrossAmount, order.currency)}</b><small>税率 {Math.round(order.taxRate * 100)}%</small></span><span><b>{order.requestedReceiptDate}</b><small>{order.promisedReceiptDate ?? "待承诺"}</small></span><em className={`businessStatus businessStatus${tone(order.status)}`}>{statusLabels[order.status]}</em><span className="businessRowActions"><GsButton aria-label={`查看${order.orderNumber}详情`} htmlType="submit"><MaterialIcon name="chevron_right" size={19}/></GsButton></span></div>)}</div><footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer></section>{detail ? <Drawer order={detail} onClose={() => setDetail(null)} onEdit={() => { setEditing(detail); setDetail(null); }} onAction={setAction}/> : null}{editing !== undefined ? <PurchaseForm order={editing} references={initialData.references} onClose={() => setEditing(undefined)} onSaved={(order) => saved(order, `${order.orderNumber} 已保存`)}/> : null}{detail && action ? <ActionDialog order={detail} action={action} onClose={() => setAction(null)} onDone={(order) => saved(order, `${order.orderNumber} 已${actionLabels[action]}`)}/> : null}{toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}</div>;
}

