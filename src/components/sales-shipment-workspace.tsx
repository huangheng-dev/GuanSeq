"use client";
import { GsButton, GsInput, GsModalHost, GsPagination, GsTextArea } from "./ui";
import { type FormEvent, useMemo, useState } from "react";
import type { SalesShipmentRecord } from "@/lib/contracts";
import { submitCreateSalesShipment } from "@/services/sales-shipment-client-service";
import type { SalesShipmentPageData } from "@/services/sales-shipment-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
function today() {
    return new Date().toISOString().slice(0, 10);
}
function ShipmentDialog({ data, onClose, onSaved }: {
    data: SalesShipmentPageData;
    onClose: () => void;
    onSaved: (shipment: SalesShipmentRecord) => void;
}) {
    const orders = data.references.releasedOrders;
    const warehouses = data.references.warehouses;
    const [salesOrderId, setSalesOrderId] = useState(orders[0]?.id ?? "");
    const order = useMemo(() => orders.find((item) => item.id === salesOrderId), [orders, salesOrderId]);
    const [warehouseId, setWarehouseId] = useState(warehouses.find((item) => item.code === "WH-FG")?.id ?? warehouses[0]?.id ?? "");
    const [plannedShippingDate, setPlannedShippingDate] = useState(today());
    const [quantities, setQuantities] = useState<Record<string, string>>({});
    const [note, setNote] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const orderOptions = orders.map((item) => ({ value: item.id, label: `${item.orderNumber} · ${item.customerName}` }));
    const warehouseOptions = warehouses.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }));
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        if (!order || !warehouseId || !plannedShippingDate) {
            setError("请选择销售订单、发货仓库和计划发货日期。");
            return;
        }
        const lines = order.lines
            .map((line) => ({ orderLineId: line.id, shippedQuantity: Number(quantities[line.id] ?? line.outstandingQuantity) }))
            .filter((line) => line.shippedQuantity > 0);
        if (!lines.length) {
            setError("至少填写一行本次发货数量。");
            return;
        }
        if (lines.some((line) => {
            const source = order.lines.find((item) => item.id === line.orderLineId);
            return !source || line.shippedQuantity > source.outstandingQuantity;
        })) {
            setError("本次发货数量不能超过未发数量。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitCreateSalesShipment({ salesOrderId: order.id, warehouseId, plannedShippingDate, note: note.trim() || null, lines }));
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "销售发货失败");
            setPending(false);
        }
    }
    return (<GsModalHost onClose={() => { if (!pending)
        onClose(); }}>
      <section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="local_shipping" size={22}/></span><div><h2>登记销售发货</h2><p>从合格成品库存出库，回写销售订单行已发数量并保留库存流水证据。</p></div></header>
        <form onSubmit={submit}>
          <label className="formField formFieldFull"><span>销售订单<em>必填</em></span><RoundedSelect ariaLabel="销售订单" options={orderOptions} value={salesOrderId} onValueChange={setSalesOrderId} size="field"/></label>
          <div className="formGrid two">
            <label className="formField"><span>发货仓库<em>必填</em></span><RoundedSelect ariaLabel="发货仓库" options={warehouseOptions} value={warehouseId} onValueChange={setWarehouseId} size="field"/></label>
            <label className="formField"><span>计划发货日期<em>必填</em></span><GsInput type="date" min={today()} value={plannedShippingDate} onChange={(event) => setPlannedShippingDate(event.target.value)}/></label>
          </div>
          <div className="salesOrderTable" role="table" aria-label="发货明细">
            <div className="salesOrderTableHeader" role="row"><span>物料</span><span>订单 / 已发</span><span>本次发货</span><span>单位</span><span>库存来源</span></div>
            {order?.lines.map((line) => <div className="salesOrderTableRow" role="row" key={line.id}><span><b>{line.materialName}</b><small>{line.materialCode}{line.materialSpecification ? ` · ${line.materialSpecification}` : ""}</small></span><strong>{line.orderedQuantity} / {line.deliveredQuantity}</strong><GsInput aria-label={`${line.materialCode}发货数量`} type="number" min="0" max={line.outstandingQuantity} step="0.0001" value={quantities[line.id] ?? line.outstandingQuantity} onChange={(event) => setQuantities((current) => ({ ...current, [line.id]: event.target.value }))}/><span>{line.unit}</span><em className="businessStatus businessStatusinfo">合格库存</em></div>)}
          </div>
          <label className="formField formFieldFull"><span>发货备注</span><GsTextArea value={note} maxLength={500} onChange={(event) => setNote(event.target.value)} placeholder="记录物流公司、承运单号或交接人"/></label>
          {error ? <p className="formError">{error}</p> : null}
          <footer className="dialogFooter"><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "提交中..." : "确认发货"}</GsButton></footer>
        </form>
      </section>
    </GsModalHost>);
}
export function SalesShipmentWorkspace({ initialData }: {
    initialData: SalesShipmentPageData;
}) {
    const [shipments, setShipments] = useState(initialData.shipments);
    const [creating, setCreating] = useState(false);
    const [toast, setToast] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const shippedQuantity = shipments.reduce((sum, item) => sum + item.totalShippedQuantity, 0);
    const pendingOrders = initialData.references.releasedOrders.length;
    const shippedOrders = new Set(shipments.map((item) => item.salesOrderId)).size;
    const totalPages = Math.max(1, Math.ceil(shipments.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const pageRows = shipments.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    function saved(shipment: SalesShipmentRecord) {
        setShipments((current) => [shipment, ...current]);
        setCreating(false);
        setToast(`${shipment.shipmentNumber} 已出库`);
        window.setTimeout(() => setToast(""), 2600);
    }
    return (<div className="businessPage">
      <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="local_shipping" size={23}/></span><div><h2>待发货协同</h2><p>按销售订单从合格成品库存发货，自动扣减库存并回写订单履约进度。</p></div></div><div className="pageHeadingActions"><GsButton className="primaryButton" disabled={!initialData.references.releasedOrders.length} onClick={() => setCreating(true)} htmlType="submit"><MaterialIcon name="add" size={18}/>登记发货</GsButton></div></header>
      <section className="businessMetrics"><div><small>发货单</small><strong>{shipments.length}</strong><em>当前租户范围</em></div><div><small>待发货订单</small><strong className="businessMetricwarn">{pendingOrders}</strong><em>仍有未发数量</em></div><div><small>累计发货</small><strong className="businessMetricgood">{shippedQuantity}</strong><em>合格库存出库</em></div><div><small>涉及订单</small><strong>{shippedOrders}</strong><em>已有出库记录</em></div></section>
      <section className="businessLedger"><div className="salesOrderTable" role="table" aria-label="销售发货列表"><div className="salesOrderTableHeader" role="row"><span>发货单</span><span>客户订单</span><span>发货物料</span><span>数量 / 仓库</span><span>库存批次</span><span>状态</span></div>{shipments.length ? pageRows.map((shipment) => <div className="salesOrderTableRow" role="row" key={shipment.id}><strong>{shipment.shipmentNumber}<small>{new Date(shipment.createdAt).toLocaleString("zh-CN")}</small></strong><span><b>{shipment.customerName}</b><small>{shipment.orderNumber} · 计划 {shipment.plannedShippingDate}</small></span><span>{shipment.lines.map((line) => <b key={line.id}>{line.materialName} <small>{line.shippedQuantity} {line.unit}</small></b>)}</span><strong>{shipment.totalShippedQuantity}<small>{shipment.warehouseCode} · {shipment.warehouseName}</small></strong><span>{shipment.lines.map((line) => <small key={line.id}>{line.stockSummary}</small>)}</span><em className="businessStatus businessStatusgood">已发货</em></div>) : <div className="emptyState"><MaterialIcon name="inventory_2" size={28}/><b>暂无销售发货记录</b><span>从已下达且有可发成品库存的销售订单登记第一笔发货。</span></div>}</div><footer className="businessLedgerFooter"><span>共 {shipments.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={shipments.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer></section>
      <div className="ledgerInsight"><MaterialIcon name="fact_check" size={18}/>发货只扣减 AVAILABLE 合格库存；库存不足、超订单未发量或无权限时会被后端拒绝。</div>
      {creating ? <ShipmentDialog data={initialData} onClose={() => setCreating(false)} onSaved={saved}/> : null}
      {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}
    </div>);
}

