"use client";

import { type FormEvent, useState } from "react";
import type { PurchaseReturnRecord } from "@/lib/contracts";
import { submitPurchaseReturn } from "@/services/purchase-return-client-service";
import type { PurchaseReturnPageData } from "@/services/purchase-return-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsInput, GsModalHost, GsPagination, GsTextArea } from "./ui";

const labels = { PENDING_SHIPMENT: "待退回出库", SHIPPED: "已退回供应商", CANCELLED: "已取消", REVERSED: "已冲回" } as const;
const actionLabels = { CANCEL: "取消退货", SHIP: "退回出库", REVERSE: "冲回出库" } as const;
const qualityLabels = { AVAILABLE: "合格库存", BLOCKED: "不合格隔离" } as const;
const today = () => new Date().toISOString().slice(0, 10);
const tone = (status: PurchaseReturnRecord["status"]) => status === "SHIPPED" ? "good" : status === "PENDING_SHIPMENT" ? "warn" : "info";

function CreateDialog({ data, onClose, onSaved }: { data: PurchaseReturnPageData; onClose: () => void; onSaved: (record: PurchaseReturnRecord) => void }) {
  const orders = data.references.orders.filter((item) => item.lines.some((line) => line.returnableQuantity > 0));
  const [orderId, setOrderId] = useState(orders[0]?.id ?? "");
  const order = orders.find((item) => item.id === orderId);
  const [returnDate, setReturnDate] = useState(today());
  const [reason, setReason] = useState("");
  const [note, setNote] = useState("");
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const key = (receiptLineId: string, quality: string) => `${receiptLineId}:${quality}`;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError("");
    if (!order || reason.trim().length < 4) return setError("请选择采购订单并填写至少 4 个字的退货原因。");
    const lines = order.lines.map((line) => ({ purchaseReceiptLineId: line.purchaseReceiptLineId, qualityStatus: line.qualityStatus, returnQuantity: Number(quantities[key(line.purchaseReceiptLineId, line.qualityStatus)] ?? 0) })).filter((line) => line.returnQuantity > 0);
    if (!lines.length) return setError("至少填写一行本次退货数量。");
    if (lines.some((input) => input.returnQuantity > (order.lines.find((line) => line.purchaseReceiptLineId === input.purchaseReceiptLineId && line.qualityStatus === input.qualityStatus)?.returnableQuantity ?? 0))) return setError("本次退货数量不能超过该库存事实的可退数量。");
    setPending(true);
    try { onSaved(await submitPurchaseReturn({ operation: "create", purchaseOrderId: order.id, expectedOrderVersion: order.version, returnDate, reason: reason.trim(), note: note.trim() || null, lines })); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "采购退货建立失败"); setPending(false); }
  }

  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="assignment_return" size={22}/></span><div><h2>建立采购退货</h2><p>按原收货行、质量状态和库存余额授权；授权本身不会改库存或自动生成红字。</p></div></header>
    <form onSubmit={submit}>
      <label className="formField formFieldFull"><span>采购订单<em>必填</em></span><RoundedSelect ariaLabel="采购订单" size="field" value={orderId} onValueChange={(value) => { setOrderId(value); setQuantities({}); }} options={orders.map((item) => ({ value: item.id, label: `${item.orderNumber} · ${item.supplierName}` }))}/></label>
      <div className="formGrid two"><label className="formField"><span>退货日期<em>必填</em></span><GsInput type="date" value={returnDate} onChange={(event) => setReturnDate(event.target.value)}/></label><label className="formField"><span>责任原因<em>必填</em></span><GsInput value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} placeholder="例如：来料尺寸异常，供应商确认退回"/></label></div>
      <div className="salesOrderTable" role="table" aria-label="采购退货明细"><div className="salesOrderTableHeader" role="row"><span>收货 / 物料</span><span>质量状态</span><span>仓库 / 批次</span><span>来源 / 待退</span><span>库存可用</span><span>本次退货</span></div>
        {order?.lines.map((line) => { const inputKey = key(line.purchaseReceiptLineId, line.qualityStatus); return <div className="salesOrderTableRow" role="row" key={inputKey}><span><b>{line.materialName}</b><small>{line.receiptNumber} · {line.materialCode}</small></span><em className={`businessStatus businessStatus${line.qualityStatus === "AVAILABLE" ? "good" : "warn"}`}>{qualityLabels[line.qualityStatus]}</em><span><b>{line.warehouseCode}/{line.locationCode}</b><small>{line.lotNumber ?? "无批次"}</small></span><strong>{line.sourceQuantity} / {line.pendingQuantity}</strong><span>{line.stockAvailableQuantity}</span><GsInput aria-label={`${line.materialCode}${qualityLabels[line.qualityStatus]}退货数量`} type="number" min="0" max={line.returnableQuantity} step="0.0001" value={quantities[inputKey] ?? ""} onChange={(event) => setQuantities((current) => ({ ...current, [inputKey]: event.target.value }))}/></div>; })}
      </div>
      <label className="formField formFieldFull"><span>补充说明</span><GsTextArea value={note} maxLength={500} onChange={(event) => setNote(event.target.value)} placeholder="记录供应商联系人、运输安排或质量证据编号"/></label>
      {error ? <p className="formError" role="alert">{error}</p> : null}<footer className="dialogFooter"><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton><GsButton htmlType="submit" className="primaryButton" disabled={pending}>{pending ? "提交中..." : "确认退货授权"}</GsButton></footer>
    </form>
  </section></GsModalHost>;
}

function ActionDialog({ record, action, onClose, onSaved }: { record: PurchaseReturnRecord; action: keyof typeof actionLabels; onClose: () => void; onSaved: (record: PurchaseReturnRecord) => void }) {
  const [reason, setReason] = useState(""); const [pending, setPending] = useState(false); const [error, setError] = useState("");
  const consequence = action === "SHIP" ? "将按锁定的仓库、库位、批次和质量状态扣减库存；合格库存退货还会冲减净合格收货，并在超额开票时标记应付复核。" : action === "REVERSE" ? "将把原出库数量退回同一库存事实并恢复净合格收货；不会自动撤销已人工开具的红字或退款。" : "只释放退货授权，不发生库存、收货或财务变化。";
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(""); if (reason.trim().length < 4) return setError("请填写至少 4 个字的动作原因。"); setPending(true);
    try { onSaved(await submitPurchaseReturn({ operation: "action", id: record.id, action, expectedVersion: record.version, reason: reason.trim() })); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "采购退货动作失败"); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action === "SHIP" ? "local_shipping" : "undo"} size={22}/></span><div><h2>{actionLabels[action]} · {record.returnNumber}</h2><p>{record.supplierName} · {record.orderNumber} · 授权数量 {record.totalReturnQuantity}</p></div></header>
    <div className="ledgerInsight"><MaterialIcon name="info" size={18}/>{consequence}</div>
    <form onSubmit={submit}><label className="formField formFieldFull"><span>动作原因<em>必填</em></span><GsTextArea value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} placeholder="写明供应商确认、仓库复核或冲回证据"/></label>{error ? <p className="formError" role="alert">{error}</p> : null}<footer className="dialogFooter"><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>返回</GsButton><GsButton htmlType="submit" className="primaryButton" disabled={pending}>{pending ? "处理中..." : `确认${actionLabels[action]}`}</GsButton></footer></form>
  </section></GsModalHost>;
}

export function PurchaseReturnWorkspace({ initialData }: { initialData: PurchaseReturnPageData }) {
  const [records, setRecords] = useState(initialData.page.items); const [referenceOrders, setReferenceOrders] = useState(initialData.references.orders);
  const [creating, setCreating] = useState(false); const [acting, setActing] = useState<{ record: PurchaseReturnRecord; action: keyof typeof actionLabels } | null>(null); const [toast, setToast] = useState("");
  const [page, setPage] = useState(1); const [pageSize, setPageSize] = useState(10); const currentPage = Math.min(page, Math.max(1, Math.ceil(records.length / pageSize))); const rows = records.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  function adjustReferences(record: PurchaseReturnRecord, direction: 1 | -1) {
    setReferenceOrders((current) => current.map((order) => {
      if (order.id !== record.purchaseOrderId) return order;
      const lines = order.lines.map((line) => {
        const quantity = record.lines.find((item) => item.purchaseReceiptLineId === line.purchaseReceiptLineId && item.qualityStatus === line.qualityStatus)?.authorizedQuantity ?? 0;
        const returnableQuantity = Math.max(0, line.returnableQuantity + direction * quantity);
        const pendingQuantity = Math.max(0, line.pendingQuantity - direction * quantity);
        return { ...line, pendingQuantity, returnableQuantity };
      });
      return { ...order, lines };
    }));
  }
  function saved(record: PurchaseReturnRecord) {
    const previous = records.find((item) => item.id === record.id);
    if (!previous) adjustReferences(record, -1);
    else if (previous.status === "PENDING_SHIPMENT" && record.status === "CANCELLED") adjustReferences(record, 1);
    setRecords((current) => current.some((item) => item.id === record.id) ? current.map((item) => item.id === record.id ? record : item) : [record, ...current]);
    setCreating(false); setActing(null); setToast(`${record.returnNumber} 已更新为${labels[record.status]}`); window.setTimeout(() => setToast(""), 2600);
    if (record.status === "SHIPPED" || record.status === "REVERSED") window.setTimeout(() => window.location.reload(), 500);
  }
  const dialogData = { ...initialData, references: { ...initialData.references, orders: referenceOrders } };
  const pending = records.filter((item) => item.status === "PENDING_SHIPMENT").length; const shipped = records.filter((item) => item.status === "SHIPPED").length; const effectiveRecords = records.filter((item) => item.status === "PENDING_SHIPMENT" || item.status === "SHIPPED");
  return <div className="businessPage">
    <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="assignment_return" size={23}/></span><div><h2>采购退货与供应商处置</h2><p>从原收货与质量库存建立退货授权，经仓库退回出库，形成应付复核与可冲回证据。</p></div></div><div className="pageHeadingActions"><GsButton className="primaryButton" disabled={!initialData.page.canCreate || !referenceOrders.some((order) => order.lines.some((line) => line.returnableQuantity > 0))} onClick={() => setCreating(true)} htmlType="button"><MaterialIcon name="add" size={18}/>建立采购退货</GsButton></div></header>
    <section className="businessMetrics"><div><small>退货单</small><strong>{records.length}</strong><em>当前租户范围</em></div><div><small>待退回出库</small><strong className="businessMetricwarn">{pending}</strong><em>授权尚未改变库存</em></div><div><small>已退回供应商</small><strong className="businessMetricgood">{shipped}</strong><em>已形成不可变库存流水</em></div><div><small>有效合格 / 隔离退货</small><strong>{effectiveRecords.reduce((sum, item) => sum + item.acceptedReturnQuantity, 0)} / {effectiveRecords.reduce((sum, item) => sum + item.blockedReturnQuantity, 0)}</strong><em>已取消、已冲回不计入</em></div></section>
    <section className="businessLedger"><div className="salesOrderTable" role="table" aria-label="采购退货列表"><div className="salesOrderTableHeader" role="row"><span>退货单 / 状态</span><span>供应商 / 采购订单</span><span>物料与质量</span><span>授权 / 已出库</span><span>责任证据</span><span>可执行动作</span></div>{rows.length ? rows.map((record) => <div className="salesOrderTableRow" role="row" key={record.id}><strong>{record.returnNumber}<small>{record.returnDate}</small><em className={`businessStatus businessStatus${tone(record.status)}`}>{labels[record.status]}</em></strong><span><b>{record.supplierName}</b><small>{record.orderNumber} · {record.supplierCode}</small></span><span>{record.lines.map((line) => <b key={line.id}>{line.materialName}<small>{qualityLabels[line.qualityStatus]} · {line.warehouseCode ? `${line.warehouseCode}/${line.locationCode}` : "待出库"}</small></b>)}</span><strong>{record.totalReturnQuantity}<small>已出库 {record.lines.reduce((sum, line) => sum + line.shippedQuantity, 0)}</small></strong><span><small>{record.reason}</small><small>{record.events[0] ? `${record.events[0].action} · ${new Date(record.events[0].occurredAt).toLocaleString("zh-CN")}` : "待记录"}</small></span><span className="pageHeadingActions">{record.availableActions.map((action) => <GsButton key={action} htmlType="button" className={action === "SHIP" ? "primaryButton" : "secondaryButton"} onClick={() => setActing({ record, action })}>{actionLabels[action]}</GsButton>)}</span></div>) : <div className="emptyState"><MaterialIcon name="assignment_return" size={28}/><b>暂无采购退货记录</b><span>有真实供应商退回需求时，从已收货库存建立第一笔退货授权。</span></div>}</div><footer className="businessLedgerFooter"><span>共 {records.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={records.length} pageSizeOptions={[10, 20, 50]} onChange={(next, size) => { setPage(next); setPageSize(size); }}/></footer></section>
    <div className="ledgerInsight"><MaterialIcon name="verified" size={18}/>退回出库不自动等同于供应商红字或退款；财务在应付台账看到“采购退货待复核”后独立确认税务与资金动作。</div>
    {creating ? <CreateDialog data={dialogData} onClose={() => setCreating(false)} onSaved={saved}/> : null}{acting ? <ActionDialog record={acting.record} action={acting.action} onClose={() => setActing(null)} onSaved={saved}/> : null}{toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}
  </div>;
}
