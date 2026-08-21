"use client";

import { type FormEvent, useMemo, useState } from "react";

import type { SalesOrderRecord, SalesOrderReferenceData } from "@/lib/contracts";
import { submitSalesOrderMutation } from "@/services/sales-order-client-service";
import type { SalesOrderPageData, SalesOrderWritePayload } from "@/services/sales-order-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsCheckbox, GsDrawer, GsInput, GsModal, GsPagination, GsTextArea } from "./ui";

const statusLabels: Record<SalesOrderRecord["status"], string> = {
  DRAFT: "草稿",
  PENDING_APPROVAL: "待审核",
  APPROVED: "已审核",
  REJECTED: "已驳回",
  RELEASED: "已下达",
  PARTIALLY_SHIPPED: "部分发货",
  SHIPPED: "已发货",
};

const actionLabels = { SUBMIT: "提交审核", APPROVE: "通过审核", REJECT: "驳回订单", RELEASE: "下达订单" } as const;
type OrderAction = keyof typeof actionLabels;
type EditableLine = { materialLabel: string; quantity: string; unitPrice: string };

function money(value: number, currency: string) {
  return new Intl.NumberFormat("zh-CN", { style: "currency", currency, minimumFractionDigits: 2 }).format(value);
}

function statusTone(status: SalesOrderRecord["status"]) {
  if (status === "RELEASED") return "good";
  if (status === "PARTIALLY_SHIPPED") return "warn";
  if (status === "SHIPPED") return "good";
  if (status === "REJECTED") return "risk";
  if (status === "PENDING_APPROVAL") return "warn";
  return "info";
}

function customerLabel(customer: SalesOrderReferenceData["customers"][number]) {
  return `${customer.code} · ${customer.name} · ${customer.creditLevel}级`;
}

function materialLabel(material: SalesOrderReferenceData["materials"][number]) {
  return `${material.code} · ${material.name}${material.specification ? ` · ${material.specification}` : ""}`;
}

function OrderFormDialog({ order, references, onClose, onSaved }: { order: SalesOrderRecord | null; references: SalesOrderReferenceData; onClose: () => void; onSaved: (order: SalesOrderRecord) => void }) {
  const customerOptions = references.customers.map(customerLabel);
  const materialOptions = references.materials.map(materialLabel);
  const [customer, setCustomer] = useState(() => order ? customerLabel(references.customers.find((item) => item.id === order.customerId) ?? references.customers[0]) : customerOptions[0] ?? "");
  const [currency, setCurrency] = useState(order?.currency ?? "CNY");
  const [tax, setTax] = useState(`${Math.round((order?.taxRate ?? 0.13) * 100)}%`);
  const [requestedDate, setRequestedDate] = useState(order?.requestedDeliveryDate ?? "2026-08-22");
  const [promisedDate, setPromisedDate] = useState(order?.promisedDeliveryDate ?? "2026-08-22");
  const [owner, setOwner] = useState(order?.owner ?? "沈妍");
  const [lines, setLines] = useState<EditableLine[]>(() => order?.lines.map((line) => ({
    materialLabel: materialLabel(references.materials.find((item) => item.id === line.materialId) ?? references.materials[0]),
    quantity: String(line.quantity),
    unitPrice: String(line.unitPrice),
  })) ?? [{ materialLabel: materialOptions[0] ?? "", quantity: "1", unitPrice: "0" }]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  const taxRate = Number(tax.replace("%", "")) / 100;
  const netTotal = lines.reduce((sum, line) => sum + (Number(line.quantity) || 0) * (Number(line.unitPrice) || 0), 0);
  const grossTotal = netTotal * (1 + taxRate);

  function updateLine(index: number, patch: Partial<EditableLine>) {
    setLines((current) => current.map((line, lineIndex) => lineIndex === index ? { ...line, ...patch } : line));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const customerRecord = references.customers.find((item) => customerLabel(item) === customer);
    const payloadLines = lines.map((line) => ({
      materialId: references.materials.find((item) => materialLabel(item) === line.materialLabel)?.id ?? "",
      quantity: Number(line.quantity),
      unitPrice: Number(line.unitPrice),
    }));
    if (!customerRecord || !requestedDate || !owner.trim() || payloadLines.some((line) => !line.materialId || line.quantity <= 0 || line.unitPrice < 0)) {
      setError("请完整填写客户、交期、负责人和有效的订单明细。");
      return;
    }
    setPending(true);
    const payload: SalesOrderWritePayload = {
      customerId: customerRecord.id,
      currency: currency as SalesOrderWritePayload["currency"],
      taxRate,
      requestedDeliveryDate: requestedDate,
      promisedDeliveryDate: promisedDate || null,
      owner: owner.trim(),
      lines: payloadLines,
    };
    try {
      const saved = await submitSalesOrderMutation(order
        ? { operation: "update", id: order.id, payload: { ...payload, expectedVersion: order.version } }
        : { operation: "create", payload });
      onSaved(saved);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "销售订单保存失败，请重试");
      setPending(false);
    }
  }

  return (
      <GsModal className="gsModal salesOrderDialog" open width={920} title={order ? `编辑 ${order.orderNumber}` : "新建销售订单"} footer={null} closable={!pending} keyboard={!pending} onCancel={pending ? undefined : onClose}>
        <p className="gsModalDescription">客户和物料仅显示当前租户已启用主数据，金额由明细自动计算。</p>
        <form onSubmit={submit}>
          <div className="formGrid salesOrderHeaderFields">
            <label className="formField formFieldFull"><span>客户<em>必填</em></span><RoundedSelect ariaLabel="客户" options={customerOptions} value={customer} onValueChange={setCustomer} size="field" /></label>
            <label className="formField"><span>币种<em>必填</em></span><RoundedSelect ariaLabel="币种" options={["CNY", "USD", "EUR"]} value={currency} onValueChange={(value) => setCurrency(value as "CNY" | "USD" | "EUR")} size="field" /></label>
            <label className="formField"><span>税率<em>必填</em></span><RoundedSelect ariaLabel="税率" options={["13%", "9%", "6%", "0%"]} value={tax} onValueChange={setTax} size="field" /></label>
            <label className="formField"><span>客户要求交期<em>必填</em></span><GsInput type="date" value={requestedDate} onChange={(event) => setRequestedDate(event.target.value)} /></label>
            <label className="formField"><span>承诺交期</span><GsInput type="date" value={promisedDate} onChange={(event) => setPromisedDate(event.target.value)} /></label>
            <label className="formField formFieldFull"><span>负责人<em>必填</em></span><GsInput value={owner} onChange={(event) => setOwner(event.target.value)} /></label>
          </div>
          <section className="salesOrderLines"><header><div><h3>订单明细</h3><p>同一物料只能出现一次，数量与含税前单价必须有效。</p></div><GsButton htmlType="button" icon={<MaterialIcon name="add" size={17} />} onClick={() => setLines((current) => [...current, { materialLabel: materialOptions.find((option) => !current.some((line) => line.materialLabel === option)) ?? materialOptions[0] ?? "", quantity: "1", unitPrice: "0" }])}>添加明细</GsButton></header>
            <div className="salesOrderLineHeader"><span>物料</span><span>数量</span><span>未税单价</span><span>未税金额</span><span>操作</span></div>
            {lines.map((line, index) => <div className="salesOrderLine" key={`${index}-${line.materialLabel}`}><RoundedSelect ariaLabel={`第${index + 1}行物料`} options={materialOptions} value={line.materialLabel} onValueChange={(value) => updateLine(index, { materialLabel: value })} size="field" /><GsInput aria-label={`第${index + 1}行数量`} type="number" min="0.0001" step="0.0001" value={line.quantity} onChange={(event) => updateLine(index, { quantity: event.target.value })} /><GsInput aria-label={`第${index + 1}行未税单价`} type="number" min="0" step="0.0001" value={line.unitPrice} onChange={(event) => updateLine(index, { unitPrice: event.target.value })} /><strong>{money((Number(line.quantity) || 0) * (Number(line.unitPrice) || 0), currency)}</strong><GsButton intent="text" htmlType="button" aria-label={`删除第${index + 1}行`} disabled={lines.length === 1} icon={<MaterialIcon name="delete" size={18} />} onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))} /></div>)}
            <footer><span>未税合计 <strong>{money(netTotal, currency)}</strong></span><span>税额 <strong>{money(grossTotal - netTotal, currency)}</strong></span><span>含税合计 <strong>{money(grossTotal, currency)}</strong></span></footer>
          </section>
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18} />{error}</div> : null}
          <footer className="dialogFooter"><span><MaterialIcon name="shield" size={16} />保存产生版本与审计证据</span><div><GsButton htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton intent="primary" htmlType="submit" loading={pending}>保存订单</GsButton></div></footer>
        </form>
      </GsModal>
  );
}

function OrderActionDialog({ order, action, onClose, onDone }: { order: SalesOrderRecord; action: OrderAction; onClose: () => void; onDone: (order: SalesOrderRecord) => void }) {
  const [comment, setComment] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  async function confirm() {
    if (action === "REJECT" && !comment.trim()) { setError("驳回订单必须填写原因。"); return; }
    setPending(true);
    setError("");
    try {
      onDone(await submitSalesOrderMutation({ operation: "action", id: order.id, action, expectedVersion: order.version, comment: comment.trim() || undefined }));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "订单状态操作失败，请重试");
      setPending(false);
    }
  }
  return <GsModal
    className="gsModal salesOrderActionDialog"
    open
    width={540}
    title={`${actionLabels[action]} · ${order.orderNumber}`}
    closable={!pending}
    keyboard={!pending}
    onCancel={pending ? undefined : onClose}
    footer={[
      <GsButton key="cancel" onClick={onClose} disabled={pending}>取消</GsButton>,
      <GsButton key="confirm" intent={action === "REJECT" ? "danger" : "primary"} onClick={() => void confirm()} loading={pending}>{actionLabels[action]}</GsButton>,
    ]}
  >
    <div className="gsConfirmContent">
      <span className="deleteConfirmIcon"><MaterialIcon name={action === "REJECT" ? "undo" : action === "RELEASE" ? "rocket_launch" : "task_alt"} size={24} /></span>
      <div>
        <p>{action === "SUBMIT" ? "提交后订单进入待审核状态，暂不能继续编辑。" : action === "APPROVE" ? "确认客户、金额、税率与承诺交期均已复核。" : action === "RELEASE" ? "下达后订单成为计划与履约的正式需求来源。" : "驳回后订单回到销售人员修订。"}</p>
        {action === "REJECT" || action === "APPROVE" || action === "RELEASE" ? <GsTextArea aria-label="操作说明" rows={3} value={comment} onChange={(event) => setComment(event.target.value)} placeholder={action === "REJECT" ? "请填写驳回原因" : "可填写审核或下达说明"} /> : null}
        {error ? <p className="deleteConfirmError" role="alert">{error}</p> : null}
      </div>
    </div>
  </GsModal>;
}

function OrderDrawer({ order, onClose, onEdit, onAction }: { order: SalesOrderRecord; onClose: () => void; onEdit: () => void; onAction: (action: OrderAction) => void }) {
  const footer = <div className="recordDrawerFooter">{order.status === "DRAFT" || order.status === "REJECTED" ? <GsButton icon={<MaterialIcon name="edit" size={17} />} onClick={onEdit}>编辑</GsButton> : null}{order.status === "DRAFT" ? <GsButton intent="primary" onClick={() => onAction("SUBMIT")}>提交审核</GsButton> : null}{order.status === "PENDING_APPROVAL" ? <><GsButton intent="danger" onClick={() => onAction("REJECT")}>驳回</GsButton><GsButton intent="primary" onClick={() => onAction("APPROVE")}>通过审核</GsButton></> : null}{order.status === "APPROVED" ? <GsButton intent="primary" icon={<MaterialIcon name="rocket_launch" size={17} />} onClick={() => onAction("RELEASE")}>下达订单</GsButton> : null}</div>;
  return <GsDrawer className="gsDrawer salesOrderDrawer" open size={650} title={<div><strong>{order.orderNumber}</strong><p>{order.customerCode} · {order.customerName}</p></div>} onClose={onClose} footer={footer}><section className="salesOrderSummary"><div><small>订单状态</small><strong className={`businessStatus businessStatus${statusTone(order.status)}`}>{statusLabels[order.status]}</strong></div><div><small>含税金额</small><strong>{money(order.totalGrossAmount, order.currency)}</strong></div><div><small>要求 / 承诺交期</small><strong>{order.requestedDeliveryDate} / {order.promisedDeliveryDate ?? "待承诺"}</strong></div><div><small>负责人</small><strong>{order.owner}</strong></div></section>{order.rejectionReason ? <div className="salesOrderRejection"><MaterialIcon name="warning" size={18} /><span><strong>驳回原因</strong>{order.rejectionReason}</span></div> : null}<section className="salesOrderDrawerLines"><h3>订单明细</h3>{order.lines.map((line) => <article key={line.id}><div><strong>{line.materialCode} · {line.materialName}</strong><small>{line.materialSpecification ?? "无规格"}</small></div><span>{line.quantity} {line.unit} × {money(line.unitPrice, order.currency)}</span><b>{money(line.grossAmount, order.currency)}</b></article>)}</section></GsDrawer>;
}

export function SalesOrderWorkspace({ initialData }: { initialData: SalesOrderPageData }) {
  const [orders, setOrders] = useState(initialData.orders);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("全部状态");
  const [sortAscending, setSortAscending] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [detail, setDetail] = useState<SalesOrderRecord | null>(null);
  const [editing, setEditing] = useState<SalesOrderRecord | null | undefined>(undefined);
  const [action, setAction] = useState<OrderAction | null>(null);
  const [toast, setToast] = useState("");

  const filtered = useMemo(() => orders.filter((order) => (!query.trim() || `${order.orderNumber}${order.customerCode}${order.customerName}${order.owner}`.toLowerCase().includes(query.trim().toLowerCase())) && (status === "全部状态" || statusLabels[order.status] === status)).sort((a, b) => (sortAscending ? 1 : -1) * a.orderNumber.localeCompare(b.orderNumber, "zh-CN", { numeric: true })), [orders, query, sortAscending, status]);
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const selectedOrders = orders.filter((order) => selectedIds.has(order.id));
  const currentIds = pageRows.map((order) => order.id);
  const allCurrent = currentIds.length > 0 && currentIds.every((id) => selectedIds.has(id));
  const totalAmount = orders.filter((order) => order.currency === "CNY").reduce((sum, order) => sum + order.totalGrossAmount, 0);
  const statusCount = (value: SalesOrderRecord["status"]) => orders.filter((order) => order.status === value).length;

  function replaceOrder(order: SalesOrderRecord, message: string) {
    setOrders((current) => current.some((item) => item.id === order.id) ? current.map((item) => item.id === order.id ? order : item) : [order, ...current]);
    setDetail(order);
    setEditing(undefined);
    setAction(null);
    setToast(message);
    window.setTimeout(() => setToast(""), 2600);
  }

  function exportOrders(rows: SalesOrderRecord[]) {
    const content = ["订单号,客户,状态,币种,含税金额,要求交期,承诺交期,负责人", ...rows.map((order) => [order.orderNumber, order.customerName, statusLabels[order.status], order.currency, order.totalGrossAmount, order.requestedDeliveryDate, order.promisedDeliveryDate ?? "", order.owner].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))].join("\n");
    const href = URL.createObjectURL(new Blob([`\uFEFF${content}`], { type: "text/csv;charset=utf-8" }));
    const anchor = document.createElement("a"); anchor.href = href; anchor.download = `销售订单-${new Date().toISOString().slice(0, 10)}.csv`; anchor.click(); URL.revokeObjectURL(href);
  }

  return <div className="businessPage salesOrderPage">
    <header className="pageHeading businessPageHeading">
      <div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="receipt_long" size={23} /></span><div><h2>销售订单</h2><p>围绕客户、物料、成交价格、税率与交期建立受控订单，并完成审核和正式下达。</p></div></div>
      <div className="pageHeadingActions"><GsButton icon={<MaterialIcon name="download" size={18} />} onClick={() => exportOrders(filtered)}>导出</GsButton><GsButton intent="primary" icon={<MaterialIcon name="add" size={18} />} onClick={() => setEditing(null)}>新建销售订单</GsButton></div>
    </header>
    <section className="businessMetrics"><div><small>订单总数</small><strong className="businessMetricinfo">{orders.length}</strong><em>当前租户可见范围</em></div><div><small>含税订单额</small><strong className="businessMetricgood">{money(totalAmount, "CNY")}</strong><em>按当前演示币种汇总</em></div><div><small>待审核</small><strong className="businessMetricwarn">{statusCount("PENDING_APPROVAL")}</strong><em>需要及时复核</em></div><div><small>已下达</small><strong className="businessMetricgood">{statusCount("RELEASED")}</strong><em>已成为正式需求</em></div></section>
    <section className="businessLedger salesOrderLedger">
      <div className="businessToolbar">
        <div className="businessSearch"><MaterialIcon name="search" size={18} /><GsInput variant="borderless" aria-label="搜索销售订单" placeholder="搜索订单号、客户或负责人" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} /></div>
        <RoundedSelect ariaLabel="订单状态" options={["全部状态", ...Object.values(statusLabels)]} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }} />
        <div className="businessTableTools"><GsButton icon={<MaterialIcon name={sortAscending ? "arrow_upward" : "arrow_downward"} size={17} />} onClick={() => setSortAscending((value) => !value)}>订单号</GsButton><GsButton icon={<MaterialIcon name="download" size={17} />} onClick={() => exportOrders(selectedOrders.length ? selectedOrders : filtered)}>{selectedOrders.length ? `导出所选（${selectedOrders.length}）` : "导出当前"}</GsButton></div>
      </div>
      {selectedOrders.length ? <div className="businessBulkBar"><div><strong>已选择 {selectedOrders.length} 张订单</strong><span>订单状态必须逐单校验，批量区仅提供导出与取消选择。</span></div><nav><GsButton intent="text" icon={<MaterialIcon name="download" size={17} />} onClick={() => exportOrders(selectedOrders)}>导出所选</GsButton><GsButton intent="text" onClick={() => setSelectedIds(new Set())}>取消选择</GsButton></nav></div> : null}
      <div className="salesOrderTable" role="table" aria-label="销售订单列表">
        <div className="salesOrderTableHeader" role="row"><GsCheckbox ariaLabel="选择当前页全部订单" checked={allCurrent} onCheckedChange={(checked) => setSelectedIds((current) => { const next = new Set(current); currentIds.forEach((id) => checked ? next.add(id) : next.delete(id)); return next; })} /><GsButton intent="text" icon={<MaterialIcon name={sortAscending ? "arrow_upward" : "arrow_downward"} size={15} />} onClick={() => setSortAscending((value) => !value)}>订单号</GsButton><span>客户</span><span>明细</span><span>金额 / 币种</span><span>要求 / 承诺交期</span><span>状态</span><span>操作</span></div>
        {pageRows.length ? pageRows.map((order) => <div className="salesOrderTableRow" role="row" key={order.id} onClick={() => setDetail(order)}><span><GsCheckbox ariaLabel={`选择${order.orderNumber}`} checked={selectedIds.has(order.id)} onCheckedChange={(checked) => setSelectedIds((current) => { const next = new Set(current); if (checked) next.add(order.id); else next.delete(order.id); return next; })} /></span><strong>{order.orderNumber}</strong><span><b>{order.customerName}</b><small>{order.customerCode}</small></span><span><b>{order.lines[0].materialName}</b><small>{order.lines.length} 项 · {order.lines.reduce((sum, line) => sum + line.quantity, 0)} 件/单位</small></span><span><b>{money(order.totalGrossAmount, order.currency)}</b><small>税率 {Math.round(order.taxRate * 100)}%</small></span><span><b>{order.requestedDeliveryDate}</b><small>{order.promisedDeliveryDate ?? "待承诺"}</small></span><em className={`businessStatus businessStatus${statusTone(order.status)}`}>{statusLabels[order.status]}</em><span className="businessRowActions"><GsButton intent="text" aria-label={`查看${order.orderNumber}详情`} icon={<MaterialIcon name="chevron_right" size={19} />} onClick={(event) => { event.stopPropagation(); setDetail(order); }} /></span></div>) : <div className="businessEmptyState"><MaterialIcon name="receipt_long" size={28} /><strong>没有符合条件的销售订单</strong><p>调整搜索或状态筛选，也可以新建一张销售订单草稿。</p></div>}
      </div>
      <footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer>
    </section>
    {detail ? <OrderDrawer order={detail} onClose={() => setDetail(null)} onEdit={() => { setEditing(detail); setDetail(null); }} onAction={setAction} /> : null}
    {editing !== undefined ? <OrderFormDialog order={editing} references={initialData.references} onClose={() => setEditing(undefined)} onSaved={(order) => replaceOrder(order, editing ? `${order.orderNumber} 已更新` : `${order.orderNumber} 已创建为草稿`)} /> : null}
    {detail && action ? <OrderActionDialog order={detail} action={action} onClose={() => setAction(null)} onDone={(order) => replaceOrder(order, `${order.orderNumber} 已${actionLabels[action]}`)} /> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18} />{toast}</div> : null}
  </div>;
}
