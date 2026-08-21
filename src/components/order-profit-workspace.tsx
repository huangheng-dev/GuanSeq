"use client";
import { GsButton, GsPagination } from "./ui";
import { useMemo, useState } from "react";
import type { OrderProfitRecord } from "@/lib/contracts";
import { submitSettleOrderProfit } from "@/services/order-profit-client-service";
import type { OrderProfitPageData } from "@/services/order-profit-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const currency = new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY", maximumFractionDigits: 2 });
const number = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 2 });
function money(value: number) {
    return currency.format(value);
}
function marginText(value: number | null) {
    if (value === null)
        return "—";
    return `${(value * 100).toFixed(2)}%`;
}
function costStatusLabel(status: string) {
    return status === "COMPLETE" ? "成本完整" : "缺成本证据";
}
export function OrderProfitWorkspace({ initialData }: {
    initialData: OrderProfitPageData;
}) {
    const [settlements, setSettlements] = useState(initialData.settlements);
    const [orders, setOrders] = useState(initialData.references.orders);
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [pendingId, setPendingId] = useState<string | null>(null);
    const [error, setError] = useState("");
    const [toast, setToast] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const pendingOrders = useMemo(() => orders.filter((item) => !item.settled), [orders]);
    const filteredSettlements = useMemo(() => {
        if (statusFilter === "ALL")
            return settlements;
        return settlements.filter((item) => item.costStatus === statusFilter);
    }, [settlements, statusFilter]);
    const totalPages = Math.max(1, Math.ceil(filteredSettlements.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const settlementRows = filteredSettlements.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const totals = useMemo(() => settlements.reduce((acc, item) => ({
        revenue: acc.revenue + item.revenue,
        material: acc.material + item.materialCost,
        profit: acc.profit + item.grossProfit,
    }), { revenue: 0, material: 0, profit: 0 }), [settlements]);
    const missingCount = settlements.filter((item) => item.costStatus === "MISSING_COST").length;
    async function settle(salesOrderId: string, orderNumber: string) {
        setError("");
        setPendingId(salesOrderId);
        try {
            const settlement = await submitSettleOrderProfit({ salesOrderId });
            setSettlements((current) => [settlement, ...current.filter((item) => item.id !== settlement.id)]);
            setOrders((current) => current.map((item) => item.salesOrderId === salesOrderId ? {
                ...item,
                settled: true,
                settlementId: settlement.id,
                settlementNumber: settlement.settlementNumber,
                costStatus: settlement.costStatus,
            } : item));
            setToast(`${orderNumber} 已生成 ${settlement.settlementNumber}`);
            window.setTimeout(() => setToast(""), 2800);
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "订单利润结算失败");
        }
        finally {
            setPendingId(null);
        }
    }
    return (<div className="businessPage">
      <header className="pageHeading businessPageHeading">
        <div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="account_balance_wallet" size={23}/></span><div><h2>订单利润</h2><p>按已发数量生成收入、材料成本、加工成本和毛利快照；加工成本暂记 0，后续由成本中心费率补齐。</p></div></div>
        <div className="pageHeadingActions"><RoundedSelect ariaLabel="按成本状态筛选" value={statusFilter} onValueChange={setStatusFilter} options={[{ value: "ALL", label: "全部结算" }, { value: "COMPLETE", label: "成本完整" }, { value: "MISSING_COST", label: "缺成本证据" }]}/></div>
      </header>

      <section className="businessMetrics">
        <div><small>已结算订单</small><strong>{settlements.length}</strong><em>利润快照不可直接改单</em></div>
        <div><small>待结算订单</small><strong className={pendingOrders.length ? "businessMetricwarn" : ""}>{pendingOrders.length}</strong><em>已有发货数量</em></div>
        <div><small>收入 / 毛利</small><strong>{money(totals.revenue)}</strong><em>{money(totals.profit)}</em></div>
        <div><small>缺成本证据</small><strong className={missingCount ? "businessMetricwarn" : "businessMetricgood"}>{missingCount}</strong><em>不得宣称完整结算</em></div>
      </section>

      {error ? <p className="formError" role="alert">{error}</p> : null}

      <section className="businessLedger">
        <div className="sectionHeading"><div><p className="eyebrow">待结算订单</p><h3>发货后可生成利润快照</h3></div><strong>{pendingOrders.length} 单</strong></div>
        <div className="salesOrderTable" role="table" aria-label="待结算订单">
          <div className="salesOrderTableHeader" role="row"><span>销售订单</span><span>订单 / 已发</span><span>收入候选</span><span>成本证据</span><span>操作</span></div>
          {pendingOrders.length ? pendingOrders.map((order) => <div className="salesOrderTableRow" role="row" key={order.salesOrderId}>
            <strong>{order.orderNumber}<small>{order.customerName}</small></strong>
            <strong>{number.format(order.orderedQuantity)}<small>已发 {number.format(order.shippedQuantity)}</small></strong>
            <strong>{money(order.revenueCandidate)}<small>{order.orderStatus === "SHIPPED" ? "全部发货" : "部分发货"}</small></strong>
            <em className="businessStatus businessStatusinfo">需按生产领料计价</em>
            <GsButton className="primaryButton" disabled={pendingId === order.salesOrderId} onClick={() => settle(order.salesOrderId, order.orderNumber)} htmlType="submit">{pendingId === order.salesOrderId ? "结算中..." : "生成结算"}</GsButton>
          </div>) : <div className="emptyState"><MaterialIcon name="task_alt" size={28}/><b>暂无待结算订单</b><span>已发货订单均已生成利润快照。</span></div>}
        </div>
      </section>

      <section className="businessLedger">
        <div className="sectionHeading"><div><p className="eyebrow">利润结算台账</p><h3>收入、材料成本与毛利</h3></div><strong>{filteredSettlements.length} 条</strong></div>
        <div className="salesOrderTable" role="table" aria-label="订单利润结算列表">
          <div className="salesOrderTableHeader" role="row"><span>结算单 / 客户</span><span>发货 / 生产证据</span><span>收入</span><span>材料 / 加工</span><span>毛利 / 毛利率</span><span>成本状态</span></div>
          {filteredSettlements.length ? settlementRows.map((settlement) => <SettlementRow key={settlement.id} settlement={settlement}/>) : <div className="emptyState"><MaterialIcon name="request_quote" size={28}/><b>暂无利润结算</b><span>从上方待结算订单生成第一笔订单利润。</span></div>}
        </div><footer className="businessLedgerFooter"><span>共 {filteredSettlements.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filteredSettlements.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer>
      </section>

      <div className="ledgerInsight"><MaterialIcon name="fact_check" size={18}/>第一版成本口径只按实际领退料净用量和有效标准成本结转直接材料；没有生产订单、领料、合格入库或标准成本时会明确标记为缺成本证据。</div>
      {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}
    </div>);
}
function SettlementRow({ settlement }: {
    settlement: OrderProfitRecord;
}) {
    const missing = settlement.missingItems.length > 0;
    return (<div className="salesOrderTableRow" role="row">
      <strong>{settlement.settlementNumber}<small>{settlement.orderNumber} · {settlement.customerName}</small></strong>
      <span>{settlement.lines.map((line) => <b key={line.id}>{line.materialCode} {number.format(line.shippedQuantity)}<small>{line.productionOrderNumber ?? "无生产工单"} · 合格 {line.acceptedQuantity === null ? "—" : number.format(line.acceptedQuantity)}</small></b>)}</span>
      <strong>{money(settlement.revenue)}<small>{settlement.currency} · 未税收入</small></strong>
      <strong>{money(settlement.materialCost)}<small>加工 {money(settlement.processingCost)}</small></strong>
      <strong className={settlement.grossProfit < 0 ? "businessMetricwarn" : "businessMetricgood"}>{money(settlement.grossProfit)}<small>{marginText(settlement.grossMargin)}</small></strong>
      <em className={`businessStatus ${missing ? "businessStatuswarn" : "businessStatusgood"}`}>{costStatusLabel(settlement.costStatus)}<small>{missing ? settlement.missingItems[0] : new Date(settlement.settledAt).toLocaleString("zh-CN")}</small></em>
    </div>);
}

