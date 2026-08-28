"use client";

import { type FormEvent, useMemo, useState } from "react";
import type { SalesReturnRecord } from "@/lib/contracts";
import { submitSalesReturn } from "@/services/sales-return-client-service";
import type { SalesReturnPageData } from "@/services/sales-return-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsInput, GsModalHost, GsPagination, GsTextArea } from "./ui";

const labels = { PENDING_RECEIPT: "待收货", RECEIVED: "待质检", COMPLETED: "已处置", CANCELLED: "已取消", REVERSED: "已冲回" } as const;
const actionLabels = { CANCEL: "取消授权", RECEIVE: "登记收货", INSPECT: "质量判定", REVERSE_RECEIPT: "冲回收货" } as const;
const tone = (status: SalesReturnRecord["status"]) => status === "COMPLETED" ? "good" : status === "PENDING_RECEIPT" || status === "RECEIVED" ? "warn" : "info";
const today = () => new Date().toISOString().slice(0, 10);

function CreateReturnDialog({ data, onClose, onSaved }: { data: SalesReturnPageData; onClose: () => void; onSaved: (record: SalesReturnRecord) => void }) {
  const orders = data.references.orders;
  const [orderId, setOrderId] = useState(orders[0]?.id ?? "");
  const order = useMemo(() => orders.find((item) => item.id === orderId), [orders, orderId]);
  const [returnDate, setReturnDate] = useState(today());
  const [reason, setReason] = useState("");
  const [note, setNote] = useState("");
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError("");
    if (!order || reason.trim().length < 4) return setError("请选择订单并填写至少 4 个字的退货原因。");
    const lines = order.lines.map((line) => ({ orderLineId: line.id, returnQuantity: Number(quantities[line.id] ?? 0) })).filter((line) => line.returnQuantity > 0);
    if (!lines.length) return setError("至少填写一行本次退货数量。");
    if (lines.some((input) => input.returnQuantity > (order.lines.find((line) => line.id === input.orderLineId)?.returnableQuantity ?? 0))) return setError("本次退货数量不能超过剩余可退数量。");
    setPending(true);
    try { onSaved(await submitSalesReturn({ operation: "create", salesOrderId: order.id, expectedOrderVersion: order.version, returnDate, reason: reason.trim(), note: note.trim() || null, lines })); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "退货授权建立失败"); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="assignment_return" size={22}/></span><div><h2>建立销售退货授权</h2><p>授权只保留管理事实；实物入库后才回写订单退货数量和库存。</p></div></header>
    <form onSubmit={submit}>
      <label className="formField formFieldFull"><span>销售订单<em>必填</em></span><RoundedSelect ariaLabel="销售订单" size="field" value={orderId} onValueChange={(value) => { setOrderId(value); setQuantities({}); }} options={orders.map((item) => ({ value: item.id, label: `${item.orderNumber} · ${item.customerName}` }))}/></label>
      <div className="formGrid two"><label className="formField"><span>退货日期<em>必填</em></span><GsInput type="date" value={returnDate} onChange={(event) => setReturnDate(event.target.value)}/></label><label className="formField"><span>责任原因<em>必填</em></span><GsInput value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} placeholder="例如：客户反馈装配尺寸异常"/></label></div>
      <div className="salesOrderTable" role="table" aria-label="销售退货授权明细"><div className="salesOrderTableHeader" role="row"><span>物料</span><span>毛发货 / 已退</span><span>在途授权</span><span>可退</span><span>本次授权</span></div>
        {order?.lines.map((line) => <div className="salesOrderTableRow" role="row" key={line.id}><span><b>{line.materialName}</b><small>{line.materialCode} · {line.unit}</small></span><strong>{line.grossDeliveredQuantity} / {line.returnedQuantity}</strong><span>{line.pendingReturnQuantity}</span><strong>{line.returnableQuantity}</strong><GsInput aria-label={`${line.materialCode}退货数量`} type="number" min="0" max={line.returnableQuantity} step="0.0001" value={quantities[line.id] ?? ""} onChange={(event) => setQuantities((current) => ({ ...current, [line.id]: event.target.value }))}/></div>)}
      </div>
      <label className="formField formFieldFull"><span>补充说明</span><GsTextArea value={note} maxLength={500} onChange={(event) => setNote(event.target.value)} placeholder="记录客户联系人、退货运输或现场情况"/></label>
      {error ? <p className="formError">{error}</p> : null}<footer className="dialogFooter"><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton><GsButton htmlType="submit" className="primaryButton" disabled={pending}>{pending ? "提交中..." : "确认授权"}</GsButton></footer>
    </form>
  </section></GsModalHost>;
}

function ReturnActionDialog({ record, action, data, onClose, onSaved }: { record: SalesReturnRecord; action: keyof typeof actionLabels; data: SalesReturnPageData; onClose: () => void; onSaved: (record: SalesReturnRecord) => void }) {
  const [warehouseId, setWarehouseId] = useState(data.references.warehouses[0]?.id ?? "");
  const locations = data.references.locations.filter((item) => item.warehouseId === warehouseId);
  const [locationId, setLocationId] = useState(locations[0]?.id ?? "");
  const [reason, setReason] = useState("");
  const [lots, setLots] = useState<Record<string, string>>({});
  const [accepted, setAccepted] = useState<Record<string, string>>({});
  const [rejected, setRejected] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError("");
    if (reason.trim().length < 4) return setError("请填写至少 4 个字的动作原因。");
    if (action === "RECEIVE" && (!warehouseId || !locationId)) return setError("请选择退货收货仓库和库位。");
    if (action === "INSPECT" && record.lines.some((line) => Number(accepted[line.id] ?? line.receivedQuantity) + Number(rejected[line.id] ?? 0) !== line.receivedQuantity)) return setError("每行合格与不合格数量之和必须等于待检数量。");
    const lines = action === "RECEIVE" ? record.lines.map((line) => ({ returnLineId: line.id, lotNumber: lots[line.id]?.trim() || null }))
      : action === "INSPECT" ? record.lines.map((line) => ({ returnLineId: line.id, acceptedQuantity: Number(accepted[line.id] ?? line.receivedQuantity), rejectedQuantity: Number(rejected[line.id] ?? 0) })) : undefined;
    setPending(true);
    try { onSaved(await submitSalesReturn({ operation: "action", id: record.id, action, expectedVersion: record.version, reason: reason.trim(), warehouseId: action === "RECEIVE" ? warehouseId : null, locationId: action === "RECEIVE" ? locationId : null, lines })); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "销售退货动作失败"); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action === "INSPECT" ? "fact_check" : action === "RECEIVE" ? "move_to_inbox" : "undo"} size={22}/></span><div><h2>{actionLabels[action]} · {record.returnNumber}</h2><p>{record.customerName} · {record.orderNumber} · 授权数量 {record.totalReturnQuantity}</p></div></header>
    <form onSubmit={submit}>
      {action === "RECEIVE" ? <div className="formGrid two"><label className="formField"><span>收货仓库<em>必填</em></span><RoundedSelect ariaLabel="收货仓库" size="field" value={warehouseId} onValueChange={(value) => { setWarehouseId(value); setLocationId(data.references.locations.find((item) => item.warehouseId === value)?.id ?? ""); }} options={data.references.warehouses.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))}/></label><label className="formField"><span>待检库位<em>必填</em></span><RoundedSelect ariaLabel="待检库位" size="field" value={locationId} onValueChange={setLocationId} options={locations.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))}/></label></div> : null}
      {action === "RECEIVE" || action === "INSPECT" ? <div className="salesOrderTable" role="table" aria-label={`${actionLabels[action]}明细`}><div className="salesOrderTableHeader" role="row"><span>物料</span><span>授权 / 待检</span><span>{action === "RECEIVE" ? "退货批次" : "合格数量"}</span><span>{action === "RECEIVE" ? "入库状态" : "不合格数量"}</span><span>单位</span></div>{record.lines.map((line) => <div className="salesOrderTableRow" role="row" key={line.id}><span><b>{line.materialName}</b><small>{line.materialCode}</small></span><strong>{line.authorizedQuantity} / {line.receivedQuantity}</strong>{action === "RECEIVE" ? <><GsInput aria-label={`${line.materialCode}批次`} value={lots[line.id] ?? ""} maxLength={80} onChange={(event) => setLots((current) => ({ ...current, [line.id]: event.target.value }))}/><em className="businessStatus businessStatuswarn">INSPECTION</em></> : <><GsInput aria-label={`${line.materialCode}合格数量`} type="number" min="0" step="0.0001" value={accepted[line.id] ?? line.receivedQuantity} onChange={(event) => setAccepted((current) => ({ ...current, [line.id]: event.target.value }))}/><GsInput aria-label={`${line.materialCode}不合格数量`} type="number" min="0" step="0.0001" value={rejected[line.id] ?? "0"} onChange={(event) => setRejected((current) => ({ ...current, [line.id]: event.target.value }))}/></>}<span>{line.unit}</span></div>)}</div> : null}
      <label className="formField formFieldFull"><span>动作原因<em>必填</em></span><GsTextArea value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} placeholder="写明责任判断、现场证据或冲回原因"/></label>
      {error ? <p className="formError">{error}</p> : null}<footer className="dialogFooter"><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>返回</GsButton><GsButton htmlType="submit" className="primaryButton" disabled={pending}>{pending ? "处理中..." : `确认${actionLabels[action]}`}</GsButton></footer>
    </form>
  </section></GsModalHost>;
}

export function SalesReturnWorkspace({ initialData }: { initialData: SalesReturnPageData }) {
  const [records, setRecords] = useState(initialData.page.items);
  const [referenceOrders, setReferenceOrders] = useState(initialData.references.orders);
  const [creating, setCreating] = useState(false);
  const [acting, setActing] = useState<{ record: SalesReturnRecord; action: keyof typeof actionLabels } | null>(null);
  const [toast, setToast] = useState("");
  const [page, setPage] = useState(1); const [pageSize, setPageSize] = useState(10);
  const currentPage = Math.min(page, Math.max(1, Math.ceil(records.length / pageSize)));
  const rows = records.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const pendingReceipt = records.filter((item) => item.status === "PENDING_RECEIPT").length;
  const pendingInspection = records.filter((item) => item.status === "RECEIVED").length;
  function saved(record: SalesReturnRecord) {
    if (!records.some((item) => item.id === record.id)) {
      setReferenceOrders((current) => current.flatMap((order) => {
        if (order.id !== record.salesOrderId) return [order];
        const lines = order.lines.flatMap((line) => {
          const authorized = record.lines.find((item) => item.orderLineId === line.id)?.authorizedQuantity ?? 0;
          const returnableQuantity = line.returnableQuantity - authorized;
          return returnableQuantity > 0 ? [{ ...line, pendingReturnQuantity: line.pendingReturnQuantity + authorized, returnableQuantity }] : [];
        });
        return lines.length ? [{ ...order, lines }] : [];
      }));
    }
    setRecords((current) => current.some((item) => item.id === record.id) ? current.map((item) => item.id === record.id ? record : item) : [record, ...current]);
    setCreating(false); setActing(null); setToast(`${record.returnNumber} 已更新为${labels[record.status]}`); window.setTimeout(() => setToast(""), 2600);
  }
  const dialogData = { ...initialData, references: { ...initialData.references, orders: referenceOrders } };
  return <div className="businessPage">
    <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="assignment_return" size={23}/></span><div><h2>销售退货与质量处置</h2><p>从客户退货授权到待检收货、合格回库或不合格隔离，保留订单与库存责任证据。</p></div></div><div className="pageHeadingActions"><GsButton className="primaryButton" disabled={!initialData.page.canCreate || !referenceOrders.length} onClick={() => setCreating(true)} htmlType="button"><MaterialIcon name="add" size={18}/>建立退货授权</GsButton></div></header>
    <section className="businessMetrics"><div><small>退货单</small><strong>{records.length}</strong><em>当前租户范围</em></div><div><small>待收货</small><strong className="businessMetricwarn">{pendingReceipt}</strong><em>管理授权尚未形成库存</em></div><div><small>待质检</small><strong className="businessMetricwarn">{pendingInspection}</strong><em>库存位于 INSPECTION</em></div><div><small>累计入库退货</small><strong className="businessMetricgood">{records.filter((item) => item.status === "RECEIVED" || item.status === "COMPLETED").reduce((sum, item) => sum + item.totalReturnQuantity, 0)}</strong><em>不含取消和冲回</em></div></section>
    <section className="businessLedger"><div className="salesOrderTable" role="table" aria-label="销售退货列表"><div className="salesOrderTableHeader" role="row"><span>退货单 / 状态</span><span>客户订单</span><span>退货物料</span><span>数量 / 库存</span><span>责任证据</span><span>可执行动作</span></div>{rows.length ? rows.map((record) => <div className="salesOrderTableRow" role="row" key={record.id}><strong>{record.returnNumber}<small>{record.returnDate}</small><em className={`businessStatus businessStatus${tone(record.status)}`}>{labels[record.status]}</em></strong><span><b>{record.customerName}</b><small>{record.orderNumber}</small></span><span>{record.lines.map((line) => <b key={line.id}>{line.materialName}<small>{line.authorizedQuantity} {line.unit}</small></b>)}</span><strong>{record.totalReturnQuantity}<small>{record.warehouseCode ? `${record.warehouseCode}/${record.locationCode}` : "尚未形成库存"}</small></strong><span><small>{record.reason}</small><small>{record.events[0] ? `${record.events[0].action} · ${new Date(record.events[0].occurredAt).toLocaleString("zh-CN")}` : "待记录"}</small></span><span className="pageHeadingActions">{record.availableActions.map((action) => <GsButton key={action} htmlType="button" className={action === "RECEIVE" || action === "INSPECT" ? "primaryButton" : "secondaryButton"} onClick={() => setActing({ record, action })}>{actionLabels[action]}</GsButton>)}</span></div>) : <div className="emptyState"><MaterialIcon name="assignment_return" size={28}/><b>暂无销售退货记录</b><span>有实际退货需求时，从已发货订单建立第一笔授权。</span></div>}</div><footer className="businessLedgerFooter"><span>共 {records.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={records.length} pageSizeOptions={[10, 20, 50]} onChange={(next, size) => { setPage(next); setPageSize(size); }}/></footer></section>
    <div className="ledgerInsight"><MaterialIcon name="verified" size={18}/>退货收货一律先进入 INSPECTION；质量判定后合格转 AVAILABLE、不合格转 BLOCKED。退款与红字发票仍由财务人员单独确认。</div>
    {creating ? <CreateReturnDialog data={dialogData} onClose={() => setCreating(false)} onSaved={saved}/> : null}
    {acting ? <ReturnActionDialog data={dialogData} record={acting.record} action={acting.action} onClose={() => setActing(null)} onSaved={saved}/> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}
  </div>;
}
