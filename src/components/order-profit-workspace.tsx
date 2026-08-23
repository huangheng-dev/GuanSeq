"use client";
import { GsButton, GsPagination, GsModalHost, GsTextArea } from "./ui";
import { useMemo, useState } from "react";
import type { OrderProfitRecord } from "@/lib/contracts";
import { submitSettleOrderProfit, submitResettleOrderProfit, fetchOrderProfitHistory } from "@/services/order-profit-client-service";
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
function statusBadge(status: string) {
    if (status === "IMPACTED")
        return { label: "需重算", cls: "businessStatuswarn" };
    if (status === "SUPERSEDED")
        return { label: "已替代", cls: "businessStatusinfo" };
    return { label: "当前有效", cls: "businessStatusgood" };
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
    const [resettleTarget, setResettleTarget] = useState<OrderProfitRecord | null>(null);
    const [resettleReason, setResettleReason] = useState("");
    const [resettleSubmitting, setResettleSubmitting] = useState(false);
    const [resettleError, setResettleError] = useState("");
    const [historyTarget, setHistoryTarget] = useState<{ settlement: OrderProfitRecord; history: OrderProfitRecord[] } | null>(null);
    const [historyLoading, setHistoryLoading] = useState(false);
    const [historyError, setHistoryError] = useState("");
    const pendingOrders = useMemo(() => orders.filter((item) => !item.settled), [orders]);
    const impactedCount = useMemo(() => settlements.filter((item) => item.status === "IMPACTED").length, [settlements]);
    const filteredSettlements = useMemo(() => {
        if (statusFilter === "ALL")
            return settlements;
        if (statusFilter === "IMPACTED")
            return settlements.filter((item) => item.status === "IMPACTED");
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
    function openResettle(settlement: OrderProfitRecord) {
        setResettleTarget(settlement);
        setResettleReason("");
        setResettleError("");
    }
    function closeResettle() {
        if (resettleSubmitting)
            return;
        setResettleTarget(null);
        setResettleReason("");
        setResettleError("");
    }
    async function submitResettle() {
        if (!resettleTarget)
            return;
        const reason = resettleReason.trim();
        if (reason.length < 4) {
            setResettleError("重算原因至少需要 4 个字符");
            return;
        }
        setResettleSubmitting(true);
        setResettleError("");
        try {
            const next = await submitResettleOrderProfit({
                salesOrderId: resettleTarget.salesOrderId,
                reason,
                expectedVersion: resettleTarget.version,
            });
            setSettlements((current) => [next, ...current.filter((item) => item.salesOrderId !== next.salesOrderId)]);
            setOrders((current) => current.map((item) => item.salesOrderId === next.salesOrderId ? {
                ...item,
                settled: true,
                settlementId: next.id,
                settlementNumber: next.settlementNumber,
                costStatus: next.costStatus,
            } : item));
            setToast(`${next.orderNumber} 已重算为版本 v${next.settlementVersion}`);
            window.setTimeout(() => setToast(""), 2800);
            setResettleTarget(null);
            setResettleReason("");
        }
        catch (reason) {
            setResettleError(reason instanceof Error ? reason.message : "订单利润重算失败");
        }
        finally {
            setResettleSubmitting(false);
        }
    }
    async function openHistory(settlement: OrderProfitRecord) {
        setHistoryTarget({ settlement, history: [] });
        setHistoryLoading(true);
        setHistoryError("");
        try {
            const history = await fetchOrderProfitHistory(settlement.salesOrderId);
            setHistoryTarget({ settlement, history });
        }
        catch (reason) {
            setHistoryError(reason instanceof Error ? reason.message : "加载历史版本失败");
        }
        finally {
            setHistoryLoading(false);
        }
    }
    function closeHistory() {
        setHistoryTarget(null);
        setHistoryError("");
    }
    return (<div className="businessPage">
      <header className="pageHeading businessPageHeading">
        <div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="account_balance_wallet" size={23}/></span><div><h2>订单利润</h2><p>红字发票、退款、反核销后，当前利润快照会标记为&ldquo;需重算&rdquo;；由财务一键生成新版本，旧版本自动归档。</p></div></div>
        <div className="pageHeadingActions"><RoundedSelect ariaLabel="按状态筛选" value={statusFilter} onValueChange={setStatusFilter} options={[{ value: "ALL", label: "全部结算" }, { value: "IMPACTED", label: "仅需重算" }, { value: "COMPLETE", label: "成本完整" }, { value: "MISSING_COST", label: "缺成本证据" }]}/></div>
      </header>

      <section className="businessMetrics">
        <div><small>当前结算</small><strong>{settlements.length}</strong><em>每个订单仅一条当前版本</em></div>
        <div><small>待重算</small><strong className={impactedCount ? "businessMetricwarn" : "businessMetricgood"}>{impactedCount}</strong><em>后续单据影响利润</em></div>
        <div><small>待结算订单</small><strong className={pendingOrders.length ? "businessMetricwarn" : ""}>{pendingOrders.length}</strong><em>已有发货数量</em></div>
        <div><small>收入 / 毛利</small><strong>{money(totals.revenue)}</strong><em>{money(totals.profit)}</em></div>
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
          <div className="salesOrderTableHeader" role="row"><span>结算单 / 客户</span><span>版本 / 状态</span><span>发货 / 生产证据</span><span>收入</span><span>材料 / 加工</span><span>毛利 / 毛利率</span><span>操作</span></div>
          {filteredSettlements.length ? settlementRows.map((settlement) => <SettlementRow key={settlement.id} settlement={settlement} onResettle={() => openResettle(settlement)} onHistory={() => openHistory(settlement)}/>) : <div className="emptyState"><MaterialIcon name="request_quote" size={28}/><b>暂无利润结算</b><span>从上方待结算订单生成第一笔订单利润。</span></div>}
        </div><footer className="businessLedgerFooter"><span>共 {filteredSettlements.length} 条{missingCount > 0 ? ` · ${missingCount} 条缺成本证据` : ""}</span><GsPagination current={currentPage} pageSize={pageSize} total={filteredSettlements.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer>
      </section>

      <div className="ledgerInsight"><MaterialIcon name="fact_check" size={18}/>红字发票/退款/反核销过账后，当前利润快照会标记为&ldquo;需重算&rdquo;，但不会自动修改金额；财务在确认原因后点&ldquo;重算&rdquo;生成 v+1 版本，旧版本保留为审计历史。</div>
      {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}

      {resettleTarget ? (<GsModalHost onClose={closeResettle}>
        <div className="dialogCard">
          <header className="dialogCardHeader"><div><MaterialIcon name="refresh" size={20}/><h3>重算订单利润</h3></div><GsButton className="iconButton" aria-label="关闭" onClick={closeResettle}><MaterialIcon name="close" size={18}/></GsButton></header>
          <p className="dialogSubtle">销售订单 <b>{resettleTarget.orderNumber}</b> · 当前结算 <b>{resettleTarget.settlementNumber}</b>（v{resettleTarget.settlementVersion}）</p>
          {resettleTarget.status === "IMPACTED" && resettleTarget.impactReason ? (<p className="dialogWarn"><MaterialIcon name="warning" size={16}/>{resettleTarget.impactReason}</p>) : null}
          <div className="dialogGrid">
            <span><small>当前收入</small><strong>{money(resettleTarget.revenue)}</strong></span>
            <span><small>当前总成本</small><strong>{money(resettleTarget.totalCost)}</strong></span>
            <span><small>当前毛利</small><strong className={resettleTarget.grossProfit < 0 ? "businessMetricwarn" : "businessMetricgood"}>{money(resettleTarget.grossProfit)}</strong></span>
            <span><small>毛利率</small><strong>{marginText(resettleTarget.grossMargin)}</strong></span>
          </div>
          <label className="fieldLabel" htmlFor="resettle-reason">重算原因 <span>（至少 4 字符，会写入审计事件）</span></label>
          <GsTextArea id="resettle-reason" rows={3} maxLength={500} value={resettleReason} onChange={(event) => setResettleReason(event.target.value)} placeholder="例如：红字发票 ARCN-... 冲销部分发货，按发票净额重新确认收入"/>
          <p className="fieldHint">{resettleReason.length}/500 · 重算将生成新版本 v{resettleTarget.settlementVersion + 1}，当前版本归档为&ldquo;已替代&rdquo;。</p>
          {resettleError ? <p className="formError" role="alert">{resettleError}</p> : null}
          <footer className="dialogActions">
            <GsButton onClick={closeResettle} disabled={resettleSubmitting}>取消</GsButton>
            <GsButton className="primaryButton" onClick={submitResettle} disabled={resettleSubmitting || resettleReason.trim().length < 4}>{resettleSubmitting ? "重算中..." : "确认重算"}</GsButton>
          </footer>
        </div>
      </GsModalHost>) : null}

      {historyTarget ? (<GsModalHost onClose={closeHistory}>
        <div className="dialogCard dialogCardWide">
          <header className="dialogCardHeader"><div><MaterialIcon name="history" size={20}/><h3>利润历史版本</h3></div><GsButton className="iconButton" aria-label="关闭" onClick={closeHistory}><MaterialIcon name="close" size={18}/></GsButton></header>
          <p className="dialogSubtle">销售订单 <b>{historyTarget.settlement.orderNumber}</b> · 共 {historyTarget.history.length} 个版本（按版本号倒序）</p>
          {historyLoading ? <p className="dialogSubtle">加载中...</p> : null}
          {historyError ? <p className="formError" role="alert">{historyError}</p> : null}
          {!historyLoading && !historyError ? (<div className="historyList">
            {historyTarget.history.map((item) => {
              const badge = statusBadge(item.status);
              return (<article key={item.id} className={`historyItem ${item.id === historyTarget.settlement.id ? "historyItemCurrent" : ""}`}>
                <header>
                  <strong>{item.settlementNumber}<small>v{item.settlementVersion} · {new Date(item.settledAt).toLocaleString("zh-CN")}</small></strong>
                  <em className={`businessStatus ${badge.cls}`}>{badge.label}</em>
                </header>
                <div className="historyGrid">
                  <span><small>收入</small><strong>{money(item.revenue)}</strong></span>
                  <span><small>总成本</small><strong>{money(item.totalCost)}</strong></span>
                  <span><small>毛利</small><strong className={item.grossProfit < 0 ? "businessMetricwarn" : "businessMetricgood"}>{money(item.grossProfit)}</strong></span>
                  <span><small>毛利率</small><strong>{marginText(item.grossMargin)}</strong></span>
                </div>
                {item.impactReason ? <p className="historyReason"><MaterialIcon name="warning" size={14}/>{item.impactReason}</p> : null}
                {item.supersedesId ? <p className="historyHint">替代上一版本（{item.supersedesId.slice(0, 8)}…）</p> : null}
                {item.costStatus === "MISSING_COST" && item.missingItems.length > 0 ? (<ul className="historyMissing">{item.missingItems.slice(0, 3).map((msg, idx) => <li key={idx}>{msg}</li>)}</ul>) : null}
              </article>);
            })}
          </div>) : null}
          <footer className="dialogActions">
            <GsButton className="primaryButton" onClick={closeHistory}>关闭</GsButton>
          </footer>
        </div>
      </GsModalHost>) : null}
    </div>);
}
function SettlementRow({ settlement, onResettle, onHistory }: {
    settlement: OrderProfitRecord;
    onResettle: () => void;
    onHistory: () => void;
}) {
    const missing = settlement.missingItems.length > 0;
    const badge = statusBadge(settlement.status);
    const impacted = settlement.status === "IMPACTED";
    return (<div className={`salesOrderTableRow ${impacted ? "salesOrderTableRowWarn" : ""}`} role="row">
      <strong>{settlement.settlementNumber}<small>{settlement.orderNumber} · {settlement.customerName}</small></strong>
      <span><em className={`businessStatus ${badge.cls}`}>{badge.label}<small>v{settlement.settlementVersion}</small></em>{impacted && settlement.impactReason ? <small className="rowWarnHint">{settlement.impactReason}</small> : null}</span>
      <span>{settlement.lines.map((line) => <b key={line.id}>{line.materialCode} {number.format(line.shippedQuantity)}<small>{line.productionOrderNumber ?? "无生产工单"} · 合格 {line.acceptedQuantity === null ? "—" : number.format(line.acceptedQuantity)}</small></b>)}</span>
      <strong>{money(settlement.revenue)}<small>{settlement.currency} · 未税收入</small></strong>
      <strong>{money(settlement.materialCost)}<small>加工 {money(settlement.processingCost)}</small></strong>
      <strong className={settlement.grossProfit < 0 ? "businessMetricwarn" : "businessMetricgood"}>{money(settlement.grossProfit)}<small>{marginText(settlement.grossMargin)}</small></strong>
      <span className="rowActions">
        <em className={`businessStatus ${missing ? "businessStatuswarn" : "businessStatusgood"}`}>{costStatusLabel(settlement.costStatus)}<small>{missing ? settlement.missingItems[0] : new Date(settlement.settledAt).toLocaleString("zh-CN")}</small></em>
        <GsButton className={impacted ? "primaryButton" : ""} onClick={onResettle}>{impacted ? "重算" : "重新计算"}</GsButton>
        <GsButton onClick={onHistory}>历史</GsButton>
      </span>
    </div>);
}
