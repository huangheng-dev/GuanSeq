"use client";
import { GsButton, GsInput, GsModalHost, GsPagination } from "./ui";
import { type FormEvent, useMemo, useState } from "react";
import Link from "next/link";
import type { PurchaseReceiptRecord } from "@/lib/contracts";
import { submitCreatePurchaseReceipt } from "@/services/purchase-receipt-client-service";
import type { PurchaseReceiptPageData } from "@/services/purchase-receipt-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const statusLabels: Record<PurchaseReceiptRecord["status"], string> = {
    PENDING_INSPECTION: "待检",
    PARTIALLY_RECEIVED: "部分入库",
    RECEIVED: "已入库",
    REJECTED_CLOSED: "不合格关闭",
};
const tone = (status: PurchaseReceiptRecord["status"]) => status === "RECEIVED" ? "good" : status === "PENDING_INSPECTION" ? "warn" : status === "PARTIALLY_RECEIVED" ? "info" : "risk";
function ReceiptForm({ data, onClose, onSaved }: {
    data: PurchaseReceiptPageData;
    onClose: () => void;
    onSaved: (receipt: PurchaseReceiptRecord) => void;
}) {
    const orders = data.references.releasedOrders;
    const [orderId, setOrderId] = useState(orders[0]?.id ?? "");
    const order = useMemo(() => orders.find((item) => item.id === orderId), [orders, orderId]);
    const [warehouseId, setWarehouseId] = useState(data.references.warehouses[0]?.id ?? "");
    const warehouse = data.references.warehouses.find((item) => item.id === warehouseId);
    const locations = data.references.locations.filter((item) => item.warehouseId === warehouseId);
    const [locationId, setLocationId] = useState(locations[0]?.id ?? "");
    const [quantities, setQuantities] = useState<Record<string, string>>({});
    const [lots, setLots] = useState<Record<string, string>>({});
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const orderOptions = orders.map((item) => ({ value: item.id, label: `${item.orderNumber} · ${item.supplierName}` }));
    const warehouseOptions = data.references.warehouses.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }));
    const locationOptions = locations.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }));
    function setQuantity(lineId: string, value: string) { setQuantities((current) => ({ ...current, [lineId]: value })); }
    function setLot(lineId: string, value: string) { setLots((current) => ({ ...current, [lineId]: value })); }
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        if (!order || !warehouse || !locationId) {
            setError("请选择已下达采购订单和收货库位。");
            return;
        }
        const lines = order.lines.map((line) => ({ orderLineId: line.id, receivedQuantity: Number(quantities[line.id] ?? line.outstandingQuantity), lotNumber: (lots[line.id] ?? `LOT-${line.materialCode}-TODAY`).trim() })).filter((line) => line.receivedQuantity > 0);
        if (!lines.length) {
            setError("至少填写一行本次收货数量。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitCreatePurchaseReceipt({ purchaseOrderId: order.id, warehouseId, locationId, source: "DESKTOP_FORM", lines }));
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "采购收货登记失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => { if (!pending)
        onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="inbox" size={22}/></span><div><h2>登记采购到货</h2><p>免检物料直接入合格库存；需检物料自动创建 IQC 并进入待检库存。</p></div></header>
    <form onSubmit={submit}>
      <label className="formField formFieldFull"><span>采购订单<em>必填</em></span><RoundedSelect ariaLabel="采购订单" options={orderOptions} value={orderId} onValueChange={setOrderId} size="field"/></label>
      <div className="formGrid two">
        <label className="formField"><span>收货仓库<em>必填</em></span><RoundedSelect ariaLabel="收货仓库" options={warehouseOptions} value={warehouseId} onValueChange={(value) => { setWarehouseId(value); setLocationId(data.references.locations.find((location) => location.warehouseId === value)?.id ?? ""); }} size="field"/></label>
        <label className="formField"><span>收货库位<em>必填</em></span><RoundedSelect ariaLabel="收货库位" options={locationOptions} value={locationId} onValueChange={setLocationId} size="field"/></label>
      </div>
      <div className="salesOrderTable" role="table" aria-label="收货明细">
        <div className="salesOrderTableHeader" role="row"><span>物料</span><span>未收</span><span>本次收货</span><span>批号</span><span>质量</span></div>
        {order?.lines.map((line) => <div className="salesOrderTableRow" role="row" key={line.id}><span><b>{line.materialName}</b><small>{line.materialCode} · {line.unit}</small></span><strong>{line.outstandingQuantity}</strong><GsInput aria-label={`${line.materialCode}收货数量`} type="number" min="0" max={line.outstandingQuantity} step="0.0001" value={quantities[line.id] ?? line.outstandingQuantity} onChange={(event) => setQuantity(line.id, event.target.value)}/><GsInput aria-label={`${line.materialCode}批号`} value={lots[line.id] ?? `LOT-${line.materialCode}-TODAY`} onChange={(event) => setLot(line.id, event.target.value)}/><em className={`businessStatus businessStatus${line.inspectionRequired ? "warn" : "good"}`}>{line.inspectionRequired ? "IQC" : "免检"}</em></div>)}
      </div>
      {error ? <p className="formError">{error}</p> : null}
      <footer className="dialogFooter"><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "提交中..." : "登记到货"}</GsButton></footer>
    </form>
  </section></GsModalHost>;
}
export function PurchaseReceiptWorkspace({ initialData }: {
    initialData: PurchaseReceiptPageData;
}) {
    const [receipts, setReceipts] = useState(initialData.receipts);
    const [creating, setCreating] = useState(false);
    const [toast, setToast] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const totalPages = Math.max(1, Math.ceil(receipts.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const pageRows = receipts.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const pending = receipts.filter((item) => item.status === "PENDING_INSPECTION").length;
    const received = receipts.reduce((sum, item) => sum + item.acceptedQuantity, 0);
    const rejected = receipts.reduce((sum, item) => sum + item.rejectedQuantity, 0);
    function saved(receipt: PurchaseReceiptRecord) { setReceipts((current) => [receipt, ...current]); setCreating(false); setToast(`${receipt.receiptNumber} 已登记`); window.setTimeout(() => setToast(""), 2600); }
    return <div className="businessPage">
    <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="local_shipping" size={23}/></span><div><h2>采购到货协同</h2><p>登记供应商到货，承接 IQC 判定、合格入库和采购订单行收货进度。</p></div></div><div className="pageHeadingActions"><Link className="secondaryButton" href="/procurement/mobile-receiving"><MaterialIcon name="barcode_scanner" size={18}/>扫码收货</Link><GsButton className="primaryButton" disabled={!initialData.references.canCreate || !initialData.references.releasedOrders.length} onClick={() => setCreating(true)} htmlType="submit"><MaterialIcon name="add" size={18}/>登记到货</GsButton></div></header>
    <section className="businessMetrics"><div><small>收货单</small><strong>{receipts.length}</strong><em>当前租户范围</em></div><div><small>待 IQC</small><strong className="businessMetricwarn">{pending}</strong><em>到货后等待判定</em></div><div><small>合格入库</small><strong className="businessMetricgood">{received}</strong><em>可被生产备料领用</em></div><div><small>不合格隔离</small><strong className="businessMetricrisk">{rejected}</strong><em>等待后续退货/评审</em></div></section>
    <section className="businessLedger"><div className="salesOrderTable" role="table" aria-label="采购收货列表"><div className="salesOrderTableHeader" role="row"><span>收货单</span><span>采购订单 / 供应商</span><span>物料与状态</span><span>收货 / 合格 / 不合格</span><span>库位</span><span>状态</span></div>{receipts.length ? pageRows.map((receipt) => <div className="salesOrderTableRow" role="row" key={receipt.id}><strong>{receipt.receiptNumber}<small>{new Date(receipt.createdAt).toLocaleString("zh-CN")}</small></strong><span><b>{receipt.orderNumber}</b><small>{receipt.supplierName}</small></span><span>{receipt.lines.map((line) => <b key={line.id}>{line.materialName} <small>{line.stockSummary}</small></b>)}</span><strong>{receipt.totalReceivedQuantity} / {receipt.acceptedQuantity} / {receipt.rejectedQuantity}</strong><span><b>{receipt.locationName}</b><small>{receipt.lines.map((line) => line.lotNumber || "无批次").join(" / ")}</small></span><em className={`businessStatus businessStatus${tone(receipt.status)}`}>{statusLabels[receipt.status]}</em></div>) : <div className="emptyState"><MaterialIcon name="inbox" size={28}/><b>暂无采购收货记录</b><span>从已下达采购订单登记第一笔到货。</span></div>}</div><footer className="businessLedgerFooter"><span>共 {receipts.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={receipts.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer></section>
    <div className="ledgerInsight"><MaterialIcon name="fact_check" size={18}/>待检任务在 <Link href="/quality/incoming">来料检验</Link> 中处理，合格数量自动回写库存与采购订单。</div>
    {creating ? <ReceiptForm data={initialData} onClose={() => setCreating(false)} onSaved={saved}/> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}
  </div>;
}

