"use client";
import { GsButton, GsCheckbox, GsDrawerHost, GsInput, GsModalHost, GsTextArea, GsPagination } from "./ui";
import { type FormEvent, useMemo, useState } from "react";
import type { PayableInvoiceRecord, PayableReferenceData } from "@/lib/contracts";
import {
  submitCreatePayableCreditNote,
  submitCreatePayableInvoice,
  submitPostPayablePayment,
  submitPostPayableRefund,
  submitReversePayablePayment,
} from "@/services/payable-client-service";
import type { PayablePageData } from "@/services/payable-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const statusLabels = { OPEN: "待付款", PARTIALLY_PAID: "部分付款", PAID: "已付讫", CREDIT_PENDING: "待退款", SETTLED: "已结清" } as const;
const paymentLabels = { BANK_TRANSFER: "银行转账", CASH: "现金", BILL: "票据", OTHER: "其他" } as const;
const moneyFormatter = new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY", maximumFractionDigits: 2 });
const pagePaths = {
    "/finance/payables": { title: "应付管理", description: "以已收货、已开票和已付款事实管理供应商应付余额、账期与逾期风险。", icon: "account_balance_wallet" },
    "/finance/purchase-settlement/invoices": { title: "采购发票", description: "按采购订单已收数量登记采购发票，控制累计开票不超过实际收货数量。", icon: "request_quote" },
    "/finance/purchase-settlement/payments": { title: "付款核销", description: "登记向供应商实付款并核销到具体应付发票，保留金额、日期、方式和并发版本证据。", icon: "payments" },
} as const;
function today() { return new Date().toISOString().slice(0, 10); }
function plusDays(days: number) { const date = new Date(); date.setDate(date.getDate() + days); return date.toISOString().slice(0, 10); }
function money(value: number, currency = "CNY") { return currency === "CNY" ? moneyFormatter.format(value) : `${currency} ${value.toLocaleString("zh-CN", { minimumFractionDigits: 2 })}`; }
function tone(status: PayableInvoiceRecord["status"]) {
  if (status === "PAID" || status === "SETTLED") return "good";
  if (status === "PARTIALLY_PAID") return "info";
  if (status === "CREDIT_PENDING") return "warn";
  return "warn";
}
function InvoiceDialog({ orders, initialOrderId, onClose, onSaved }: {
    orders: PayableReferenceData["orders"];
    initialOrderId?: string;
    onClose: () => void;
    onSaved: (invoice: PayableInvoiceRecord) => void;
}) {
    const available = orders.filter((order) => order.lines.some((line) => line.remainingQuantity > 0));
    const [orderId, setOrderId] = useState(available.some((order) => order.purchaseOrderId === initialOrderId) ? initialOrderId! : available[0]?.purchaseOrderId ?? "");
    const order = available.find((item) => item.purchaseOrderId === orderId);
    const [supplierInvoiceNumber, setSupplierInvoiceNumber] = useState("");
    const [invoiceDate, setInvoiceDate] = useState(today());
    const [dueDate, setDueDate] = useState(plusDays(30));
    const [quantities, setQuantities] = useState<Record<string, string>>({});
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        if (!supplierInvoiceNumber.trim()) return setError("请填写供应商发票号码。");
        if (!order || !invoiceDate || !dueDate)
            return setError("请选择采购订单并填写开票日期和到期日。");
        if (dueDate < invoiceDate)
            return setError("到期日不能早于开票日期。");
        const lines = order.lines.map((line) => ({ purchaseOrderLineId: line.purchaseOrderLineId, invoiceQuantity: Number(quantities[line.purchaseOrderLineId] ?? line.remainingQuantity) })).filter((line) => line.invoiceQuantity > 0);
        if (!lines.length)
            return setError("至少填写一行本次开票数量。");
        if (lines.some((line) => line.invoiceQuantity > (order.lines.find((item) => item.purchaseOrderLineId === line.purchaseOrderLineId)?.remainingQuantity ?? 0)))
            return setError("本次开票数量不能超过未开票的已收数量。");
        setPending(true);
        try {
            onSaved(await submitCreatePayableInvoice({ purchaseOrderId: order.purchaseOrderId, supplierInvoiceNumber: supplierInvoiceNumber.trim(), invoiceDate, dueDate, lines }));
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "采购发票创建失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}><section className="businessDialog payableDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="request_quote" size={22}/></span><div><h2>登记采购发票</h2><p>开票上限来自采购订单累计已收数量，税率、币种和单价按订单快照取值。</p></div></header>
    <form onSubmit={submit}>
      <div className="formGrid"><label className="formField formFieldFull"><span>已收货采购订单<em>必填</em></span><RoundedSelect ariaLabel="已收货采购订单" size="field" value={orderId} onValueChange={(value) => { setOrderId(value); setQuantities({}); }} options={available.map((item) => ({ value: item.purchaseOrderId, label: `${item.orderNumber} · ${item.supplierName} · 待开 ${money(item.remainingAmount, item.currency)}` }))}/></label><label className="formField"><span>供应商发票号<em>必填</em></span><GsInput maxLength={80} value={supplierInvoiceNumber} onChange={(event) => setSupplierInvoiceNumber(event.target.value)} placeholder="供应商开具的发票号码"/></label><label className="formField"><span>开票日期<em>必填</em></span><GsInput type="date" value={invoiceDate} onChange={(event) => setInvoiceDate(event.target.value)}/></label><label className="formField"><span>到期日<em>必填</em></span><GsInput type="date" min={invoiceDate} value={dueDate} onChange={(event) => setDueDate(event.target.value)}/></label></div>
      <div className="dialogLineList">{order?.lines.map((line) => <div className="dialogLineRow" key={line.purchaseOrderLineId}><span><strong>{line.materialCode} · {line.materialName}</strong><small>已发 {line.acceptedQuantity} · 已开 {line.invoicedQuantity} · 剩余 {line.remainingQuantity} {line.unit}</small></span><span><GsInput aria-label={`${line.materialCode}开票数量`} type="number" min="0" max={line.remainingQuantity} step="0.0001" value={quantities[line.purchaseOrderLineId] ?? line.remainingQuantity} onChange={(event) => setQuantities((current) => ({ ...current, [line.purchaseOrderLineId]: event.target.value }))}/><small>{money(line.unitPrice, order.currency)} / {line.unit}</small></span></div>)}</div>
      {error ? <p className="formError" role="alert">{error}</p> : null}<footer className="dialogFooter"><span><MaterialIcon name="verified_user" size={17}/>提交后形成财务事实，不直接修改采购订单。</span><div><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton><GsButton className="primaryButton" disabled={pending || !available.length} htmlType="submit">{pending ? "开票中..." : "确认开票"}</GsButton></div></footer>
    </form>
  </section></GsModalHost>;
}
function PaymentDialog({ invoice, onClose, onSaved }: {
    invoice: PayableInvoiceRecord;
    onClose: () => void;
    onSaved: (invoice: PayableInvoiceRecord) => void;
}) {
    const [paymentDate, setPaymentDate] = useState(today());
    const [amount, setAmount] = useState(String(invoice.outstandingAmount));
    const [paymentMethod, setPaymentMethod] = useState<keyof typeof paymentLabels>("BANK_TRANSFER");
    const [bankReference, setBankReference] = useState("");
    const [note, setNote] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        const numericAmount = Number(amount);
        if (!paymentDate || numericAmount <= 0 || numericAmount > invoice.outstandingAmount)
            return setError(`付款金额应大于 0 且不超过 ${money(invoice.outstandingAmount, invoice.currency)}。`);
        setPending(true);
        try {
            onSaved(await submitPostPayablePayment({ invoiceId: invoice.id, expectedVersion: invoice.version, paymentDate, amount: numericAmount, paymentMethod, bankReference: bankReference.trim() || null, note: note.trim() || null }));
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "付款核销失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}><section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="payments" size={22}/></span><div><h2>登记付款核销</h2><p>{invoice.invoiceNumber} · {invoice.supplierName} · 当前待付 {money(invoice.outstandingAmount, invoice.currency)}</p></div></header><form onSubmit={submit}><div className="formGrid"><label className="formField"><span>付款日期<em>必填</em></span><GsInput type="date" min={invoice.invoiceDate} value={paymentDate} onChange={(event) => setPaymentDate(event.target.value)}/></label><label className="formField"><span>本次付款<em>必填</em></span><GsInput type="number" min="0.01" max={invoice.outstandingAmount} step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)}/></label><label className="formField"><span>付款方式<em>必填</em></span><RoundedSelect ariaLabel="付款方式" size="field" value={paymentMethod} onValueChange={(value) => setPaymentMethod(value as keyof typeof paymentLabels)} options={Object.entries(paymentLabels).map(([value, label]) => ({ value, label }))}/></label><label className="formField"><span>银行流水 / 票据号</span><GsInput maxLength={120} value={bankReference} onChange={(event) => setBankReference(event.target.value)} placeholder="用于银行对账和审计追溯"/></label><label className="formField formFieldFull"><span>付款备注</span><GsTextArea maxLength={500} value={note} onChange={(event) => setNote(event.target.value)} placeholder="记录收款主体、差异说明或核销依据"/></label></div>{error ? <p className="formError" role="alert">{error}</p> : null}<footer className="dialogFooter"><span><MaterialIcon name="sync_lock" size={17}/>按发票版本提交，并发变化会要求刷新。</span><div><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "核销中..." : "登记并核销"}</GsButton></div></footer></form></section></GsModalHost>;
}
function CreditNoteDialog({ invoice, onClose, onSaved }: {
    invoice: PayableInvoiceRecord;
    onClose: () => void;
    onSaved: () => void;
}) {
    const [creditNoteDate, setCreditNoteDate] = useState(today());
    const [dueDate, setDueDate] = useState(plusDays(15));
    const [reason, setReason] = useState("");
    const [supplierCreditNoteNumber, setSupplierCreditNoteNumber] = useState("");
    const [taxNoticeNumber, setTaxNoticeNumber] = useState("");
    const [quantities, setQuantities] = useState<Record<string, string>>({});
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        if (reason.trim().length < 4) return setError("红冲原因至少填写 4 个字。");
        if (dueDate < creditNoteDate) return setError("到期日不能早于红字发票日期。");
        const lines = invoice.lines
            .map((line) => ({ originalInvoiceLineId: line.id, creditQuantity: Number(quantities[line.id] ?? 0) }))
            .filter((line) => line.creditQuantity > 0);
        if (!lines.length) return setError("至少填写一行红冲数量。");
        setPending(true);
        try {
            await submitCreatePayableCreditNote({
                originalInvoiceId: invoice.id,
                supplierCreditNoteNumber: supplierCreditNoteNumber.trim() || null,
                taxNoticeNumber: taxNoticeNumber.trim() || null,
                creditNoteDate, dueDate,
                reason: reason.trim(),
                lines,
            });
            onSaved();
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "红字发票开具失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="request_quote" size={22}/></span><div><h2>开具红字发票</h2><p>{invoice.invoiceNumber} · {invoice.supplierName} · 红冲后形成应退供应商余额</p></div></header>
        <form onSubmit={submit}>
            <div className="formGrid">
                <label className="formField"><span>红字发票日期<em>必填</em></span><GsInput type="date" value={creditNoteDate} onChange={(event) => setCreditNoteDate(event.target.value)}/></label>
                <label className="formField"><span>到期日<em>必填</em></span><GsInput type="date" min={creditNoteDate} value={dueDate} onChange={(event) => setDueDate(event.target.value)}/></label>
                <label className="formField"><span>供应商红字单号</span><GsInput maxLength={80} value={supplierCreditNoteNumber} onChange={(event) => setSupplierCreditNoteNumber(event.target.value)} placeholder="供应商提供的红字单号（可空）"/></label><label className="formField"><span>红字信息表编号</span><GsInput maxLength={80} value={taxNoticeNumber} onChange={(event) => setTaxNoticeNumber(event.target.value)} placeholder="增值税红字信息表编号（可空）"/></label>
            </div>
            <div className="dialogLineList">{invoice.lines.map((line) => <div className="dialogLineRow" key={line.id}><span><strong>{line.materialCode} · {line.materialName}</strong><small>已开 {line.invoiceQuantity} {line.unit} × {money(line.unitPrice, invoice.currency)}</small></span><span><GsInput aria-label={`${line.materialCode}红冲数量`} type="number" min="0" max={line.invoiceQuantity} step="0.0001" value={quantities[line.id] ?? ""} onChange={(event) => setQuantities((current) => ({ ...current, [line.id]: event.target.value }))} placeholder="0"/><small>{line.unit}</small></span></div>)}</div>
            <label className="formField formFieldFull" style={{ marginTop: 12 }}><span>红冲原因<em>必填</em></span><GsTextArea maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="如：质量问题退货、价格折让等，至少 4 个字"/></label>
            {error ? <p className="formError" role="alert">{error}</p> : null}
            <footer className="dialogFooter"><span><MaterialIcon name="warning" size={17}/>红字发票过账后不可修改，金额以负数表达。</span><div><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "处理中..." : "确认红冲"}</GsButton></div></footer>
        </form>
    </section></GsModalHost>;
}
function RefundDialog({ invoice, onClose, onSaved }: {
    invoice: PayableInvoiceRecord;
    onClose: () => void;
    onSaved: (invoice: PayableInvoiceRecord) => void;
}) {
    const [refundDate, setRefundDate] = useState(today());
    const [amount, setAmount] = useState(String(invoice.creditBalance));
    const [paymentMethod, setPaymentMethod] = useState<keyof typeof paymentLabels>("BANK_TRANSFER");
    const [bankReference, setBankReference] = useState("");
    const [note, setNote] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        const numericAmount = Number(amount);
        if (!refundDate || numericAmount <= 0 || numericAmount > invoice.creditBalance)
            return setError(`退款金额应大于 0 且不超过待退余额 ${money(invoice.creditBalance, invoice.currency)}。`);
        setPending(true);
        try {
            onSaved(await submitPostPayableRefund({
                invoiceId: invoice.id, expectedVersion: invoice.version,
                refundDate, amount: numericAmount, paymentMethod,
                bankReference: bankReference.trim() || null, note: note.trim() || null,
            }));
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "退款登记失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="payments" size={22}/></span><div><h2>登记退款</h2><p>{invoice.invoiceNumber} · {invoice.supplierName} · 待退余额 {money(invoice.creditBalance, invoice.currency)}</p></div></header>
        <form onSubmit={submit}>
            <div className="formGrid">
                <label className="formField"><span>退款日期<em>必填</em></span><GsInput type="date" value={refundDate} onChange={(event) => setRefundDate(event.target.value)}/></label>
                <label className="formField"><span>退款金额<em>必填</em></span><GsInput type="number" min="0.01" max={invoice.creditBalance} step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)}/></label>
                <label className="formField"><span>退款方式<em>必填</em></span><RoundedSelect ariaLabel="退款方式" size="field" value={paymentMethod} onValueChange={(value) => setPaymentMethod(value as keyof typeof paymentLabels)} options={Object.entries(paymentLabels).map(([value, label]) => ({ value, label }))}/></label>
                <label className="formField"><span>银行流水 / 票据号</span><GsInput maxLength={120} value={bankReference} onChange={(event) => setBankReference(event.target.value)} placeholder="退款凭证号"/></label>
                <label className="formField formFieldFull"><span>退款备注</span><GsTextArea maxLength={500} value={note} onChange={(event) => setNote(event.target.value)} placeholder="退款原因或审批依据"/></label>
            </div>
            {error ? <p className="formError" role="alert">{error}</p> : null}
            <footer className="dialogFooter"><span><MaterialIcon name="sync_lock" size={17}/>退款将减少待退余额，全部退完后发票结清。</span><div><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "退款中..." : "确认退款"}</GsButton></div></footer>
        </form>
    </section></GsModalHost>;
}
function ReverseDialog({ payment, invoice, onClose, onSaved }: {
    payment: PayableInvoiceRecord["payments"][number];
    invoice: PayableInvoiceRecord;
    onClose: () => void;
    onSaved: (invoice: PayableInvoiceRecord) => void;
}) {
    const [reversalDate, setReversalDate] = useState(today());
    const [reason, setReason] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const isRefund = payment.direction === "REFUND";
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        if (reason.trim().length < 4) return setError("反核销原因至少填写 4 个字。");
        setPending(true);
        try {
            onSaved(await submitReversePayablePayment({
                paymentId: payment.id, reversalDate, reason: reason.trim(),
            }));
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "反核销失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="undo" size={22}/></span><div><h2>反核销{isRefund ? "退款" : "付款"}</h2><p>{payment.paymentNumber} · {money(payment.amount, invoice.currency)} · 操作后原记录标记为已反核销</p></div></header>
        <form onSubmit={submit}>
            <div className="formGrid">
                <label className="formField"><span>反核销日期<em>必填</em></span><GsInput type="date" min={payment.paymentDate} value={reversalDate} onChange={(event) => setReversalDate(event.target.value)}/></label>
            </div>
            <label className="formField formFieldFull" style={{ marginTop: 12 }}><span>反核销原因<em>必填</em></span><GsTextArea maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="说明为什么需要冲销这笔记录，至少 4 个字"/></label>
            {error ? <p className="formError" role="alert">{error}</p> : null}
            <footer className="dialogFooter"><span><MaterialIcon name="warning" size={17}/>反核销不删除原记录，而是回补金额并保留审计链。</span><div><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton><GsButton intent="danger" className="primaryButton" disabled={pending} htmlType="submit">{pending ? "处理中..." : "确认反核销"}</GsButton></div></footer>
        </form>
    </section></GsModalHost>;
}
function InvoiceDrawer({ invoice, onClose, onPayment, onCreditNote, onRefund, onReverse }: {
    invoice: PayableInvoiceRecord;
    onClose: () => void;
    onPayment: () => void;
    onCreditNote: () => void;
    onRefund: () => void;
    onReverse: (payment: PayableInvoiceRecord["payments"][number]) => void;
}) {
    const canPayment = invoice.outstandingAmount > 0 && invoice.status !== "CREDIT_PENDING";
    const canRefund = invoice.creditBalance > 0;
    const canCreditNote = invoice.status !== "OPEN";
    return <GsDrawerHost onClose={onClose}><aside className="businessDrawer" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
        <header className="recordDrawerHeader"><div><h2>{invoice.invoiceNumber}</h2><p>{invoice.supplierName} · {invoice.orderNumber}</p></div><GsButton className="iconButton" aria-label="关闭详情" onClick={onClose} htmlType="submit"><MaterialIcon name="close" size={20}/></GsButton></header>
        <div className="recordDrawerBody">
            <section className="detailSummary">
                <div><small>含税金额</small><strong>{money(invoice.grossAmount, invoice.currency)}</strong></div>
                <div><small>已付 / 待付</small><strong>{money(invoice.paidAmount, invoice.currency)} / {money(invoice.outstandingAmount, invoice.currency)}</strong></div>
                {invoice.creditBalance > 0 ? <div><small>待退余额</small><strong className="businessMetricwarn">{money(invoice.creditBalance, invoice.currency)}</strong></div> : null}
                <div><small>账期</small><strong>{invoice.invoiceDate} → {invoice.dueDate}</strong></div>
            </section>
            <section className="drawerSection"><h3>开票明细</h3>{invoice.lines.map((line) => <article className="payableDetailLine" key={line.id}><span><strong>{line.materialCode} · {line.materialName}</strong><small>{line.invoiceQuantity} {line.unit} × {money(line.unitPrice, invoice.currency)}</small></span><b>{money(line.grossAmount, invoice.currency)}</b></article>)}</section>
            <section className="drawerSection"><h3>付款记录</h3>{invoice.payments.length ? invoice.payments.map((payment) => <article className="payableDetailLine" key={payment.id}><span><strong>{payment.direction === "REFUND" ? "退款" : "付款"} · {payment.paymentNumber}</strong><small>{payment.paymentDate} · {paymentLabels[payment.paymentMethod]}{payment.status === "REVERSED" ? " · 已反核销" : ""}</small></span><span><b style={{ color: payment.direction === "REFUND" ? "var(--color-danger, #c62828)" : undefined }}>{payment.direction === "REFUND" ? "-" : ""}{money(payment.amount, invoice.currency)}</b>{payment.status === "POSTED" ? <GsButton className="textButton" aria-label={`反核销${payment.paymentNumber}`} onClick={() => onReverse(payment)} htmlType="submit"><MaterialIcon name="undo" size={16}/></GsButton> : null}</span></article>) : <p className="drawerEmptyText">尚无付款记录。</p>}</section>
        </div>
        <footer className="recordDrawerFooter">
            <GsButton className="secondaryButton" onClick={onClose} htmlType="submit">关闭</GsButton>
            {canCreditNote ? <GsButton className="secondaryButton" onClick={onCreditNote} htmlType="submit"><MaterialIcon name="request_quote" size={17}/>红冲</GsButton> : null}
            {canRefund ? <GsButton className="secondaryButton" onClick={onRefund} htmlType="submit"><MaterialIcon name="payments" size={17}/>退款</GsButton> : null}
            {canPayment ? <GsButton className="primaryButton" onClick={onPayment} htmlType="submit"><MaterialIcon name="payments" size={17}/>登记付款</GsButton> : null}
        </footer>
    </aside></GsDrawerHost>;
}
export function PayableWorkspace({ initialData, pathname }: {
    initialData: PayablePageData;
    pathname: keyof typeof pagePaths;
}) {
    const heading = pagePaths[pathname];
    const [invoices, setInvoices] = useState(initialData.invoices);
    const [orders, setOrders] = useState(initialData.references.orders);
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState("全部状态");
    const [sortNewest, setSortNewest] = useState(true);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [selectedIds, setSelectedIds] = useState(new Set<string>());
    const [detail, setDetail] = useState<PayableInvoiceRecord | null>(null);
    const [creatingFor, setCreatingFor] = useState<string | null>(null);
    const [paying, setPaying] = useState<PayableInvoiceRecord | null>(null);
    const [crediting, setCrediting] = useState<PayableInvoiceRecord | null>(null);
    const [refunding, setRefunding] = useState<PayableInvoiceRecord | null>(null);
    const [reversing, setReversing] = useState<{ payment: PayableInvoiceRecord["payments"][number]; invoice: PayableInvoiceRecord } | null>(null);
    const [toast, setToast] = useState("");
    const filtered = useMemo(() => invoices.filter((invoice) => (!query.trim() || `${invoice.invoiceNumber} ${invoice.orderNumber} ${invoice.supplierCode} ${invoice.supplierName}`.toLowerCase().includes(query.trim().toLowerCase())) && (status === "全部状态" || statusLabels[invoice.status] === status)).sort((a, b) => sortNewest ? b.createdAt.localeCompare(a.createdAt) : a.createdAt.localeCompare(b.createdAt)), [invoices, query, status, sortNewest]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const rows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const allCurrent = rows.length > 0 && rows.every((row) => selectedIds.has(row.id));
    const totals = invoices.reduce((sum, invoice) => ({ gross: sum.gross + invoice.grossAmount, paid: sum.paid + invoice.paidAmount, outstanding: sum.outstanding + invoice.outstandingAmount }), { gross: 0, paid: 0, outstanding: 0 });
    const overdue = invoices.filter((invoice) => invoice.status !== "PAID" && invoice.status !== "SETTLED" && invoice.dueDate < today()).length;
    function saved(invoice: PayableInvoiceRecord, message: string) { setInvoices((current) => [invoice, ...current.filter((item) => item.id !== invoice.id)]); setDetail(invoice); setCreatingFor(null); setPaying(null); setOrders((current) => current.map((order) => order.purchaseOrderId !== invoice.purchaseOrderId ? order : { ...order, lines: order.lines.map((line) => { const billed = invoice.lines.filter((item) => item.purchaseOrderLineId === line.purchaseOrderLineId).reduce((sum, item) => sum + item.invoiceQuantity, 0); return { ...line, invoicedQuantity: line.invoicedQuantity + billed, remainingQuantity: Math.max(0, line.remainingQuantity - billed) }; }), invoicedAmount: order.invoicedAmount + invoice.netAmount, remainingAmount: Math.max(0, order.remainingAmount - invoice.netAmount) })); setToast(message); window.setTimeout(() => setToast(""), 2800); }
    function replace(invoice: PayableInvoiceRecord, message?: string) { setInvoices((current) => current.map((item) => item.id === invoice.id ? invoice : item)); setDetail(invoice); setPaying(null); setRefunding(null); setReversing(null); setToast(message ?? `${invoice.invoiceNumber} 已更新`); window.setTimeout(() => setToast(""), 2800); }
    function handleCreditNoteSaved() { setCrediting(null); setToast("红字发票已开具，请刷新查看最新余额"); window.setTimeout(() => setToast(""), 2800); window.location.reload(); }
    function exportRows() { const chosen = selectedIds.size ? filtered.filter((item) => selectedIds.has(item.id)) : filtered; const csv = ["发票号,采购订单,供应商,开票日期,到期日,含税金额,已付金额,待付金额,状态", ...chosen.map((item) => [item.invoiceNumber, item.orderNumber, item.supplierName, item.invoiceDate, item.dueDate, item.grossAmount, item.paidAmount, item.outstandingAmount, statusLabels[item.status]].join(","))].join("\n"); const link = document.createElement("a"); link.href = URL.createObjectURL(new Blob([`\ufeff${csv}`], { type: "text/csv;charset=utf-8" })); link.download = `应付台账-${today()}.csv`; link.click(); URL.revokeObjectURL(link.href); }
    function toggleCurrentPage(checked: boolean) {
        setSelectedIds((current) => {
            const next = new Set(current);
            rows.forEach((row) => {
                if (checked)
                    next.add(row.id);
                else
                    next.delete(row.id);
            });
            return next;
        });
    }
    function toggleInvoice(id: string, checked: boolean) {
        setSelectedIds((current) => {
            const next = new Set(current);
            if (checked)
                next.add(id);
            else
                next.delete(id);
            return next;
        });
    }
    const canInvoice = orders.some((order) => order.lines.some((line) => line.remainingQuantity > 0));
    return <div className="businessPage payablePage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name={heading.icon} size={23}/></span><div><h2>{heading.title}</h2><p>{heading.description}</p></div></div><div className="pageHeadingActions">{pathname !== "/finance/purchase-settlement/payments" ? <GsButton className="primaryButton" disabled={!canInvoice} onClick={() => setCreatingFor("")} htmlType="submit"><MaterialIcon name="add" size={18}/>开具发票</GsButton> : null}</div></header>
    <section className="businessMetrics"><div><small>应付含税额</small><strong>{money(totals.gross)}</strong><em>{invoices.length} 张发票</em></div><div><small>已付金额</small><strong className="businessMetricgood">{money(totals.paid)}</strong><em>已过账付款</em></div><div><small>待付余额</small><strong className={totals.outstanding ? "businessMetricwarn" : "businessMetricgood"}>{money(totals.outstanding)}</strong><em>逐发票核销</em></div><div><small>逾期发票</small><strong className={overdue ? "businessMetricrisk" : "businessMetricgood"}>{overdue}</strong><em>按到期日识别</em></div></section>
    {pathname === "/finance/purchase-settlement/invoices" ? <section className="payableQueue"><div className="sectionHeading"><div><h3>可开票收货订单</h3><p>仅展示已收货且仍有未开票数量的订单。</p></div><strong>{orders.filter((order) => order.remainingAmount > 0).length} 单</strong></div>{orders.filter((order) => order.remainingAmount > 0).slice(0, 4).map((order) => <article key={order.purchaseOrderId}><span><strong>{order.orderNumber} · {order.supplierName}</strong><small>已付金额 {money(order.acceptedAmount, order.currency)} · 税率 {(order.taxRate * 100).toFixed(0)}%</small></span><b>待开 {money(order.remainingAmount, order.currency)}</b><GsButton className="secondaryButton" onClick={() => setCreatingFor(order.purchaseOrderId)} htmlType="submit">开票</GsButton></article>)}</section> : null}
    <section className="businessLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索采购发票" placeholder="搜索发票号、采购订单或供应商" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }}/></div><RoundedSelect ariaLabel="应收状态" options={["全部状态", ...Object.values(statusLabels)]} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }}/><div className="businessTableTools"><GsButton className="secondaryButton" onClick={() => setSortNewest((value) => !value)} htmlType="submit"><MaterialIcon name={sortNewest ? "south" : "north"} size={17}/>开票时间</GsButton><GsButton className="secondaryButton" onClick={exportRows} htmlType="submit"><MaterialIcon name="download" size={17}/>{selectedIds.size ? `导出所选（${selectedIds.size}）` : "导出当前"}</GsButton></div></div>
      <div className="salesOrderTable payableTable" role="table" aria-label="采购发票台账"><div className="salesOrderTableHeader" role="row"><GsCheckbox className="selectionCheckbox" aria-label="选择当前页全部采购发票" checked={allCurrent} onChange={(event) => toggleCurrentPage(event.target.checked)}/><span>发票号</span><span>供应商 / 采购订单</span><span>开票 / 到期</span><span>含税 / 已付</span><span>待付余额</span><span>状态</span><span>操作</span></div>{rows.length ? rows.map((invoice) => <div className="salesOrderTableRow" role="row" key={invoice.id} onClick={() => setDetail(invoice)}><GsCheckbox className="selectionCheckbox" onClick={(event) => event.stopPropagation()} aria-label={`选择${invoice.invoiceNumber}`} checked={selectedIds.has(invoice.id)} onChange={(event) => toggleInvoice(invoice.id, event.target.checked)}/><strong>{invoice.invoiceNumber}</strong><span><b>{invoice.supplierName}</b><small>{invoice.orderNumber} · {invoice.supplierCode}</small></span><span><b>{invoice.invoiceDate}</b><small>到期 {invoice.dueDate}</small></span><span><b>{money(invoice.grossAmount, invoice.currency)}</b><small>已付 {money(invoice.paidAmount, invoice.currency)}</small></span><strong>{money(invoice.outstandingAmount, invoice.currency)}</strong><em className={`businessStatus businessStatus${tone(invoice.status)}`}>{statusLabels[invoice.status]}</em><span className="businessRowActions">{invoice.status !== "PAID" ? <GsButton aria-label={`为${invoice.invoiceNumber}登记付款`} onClick={(event) => { event.stopPropagation(); setPaying(invoice); }} htmlType="submit"><MaterialIcon name="payments" size={18}/></GsButton> : null}<GsButton aria-label={`查看${invoice.invoiceNumber}详情`} onClick={(event) => { event.stopPropagation(); setDetail(invoice); }} htmlType="submit"><MaterialIcon name="chevron_right" size={19}/></GsButton></span></div>) : <div className="businessEmptyState"><MaterialIcon name="request_quote" size={28}/><strong>没有符合条件的采购发票</strong><p>从已收货采购订单开具第一张采购发票。</p></div>}</div>
      <footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => {
        setPage(nextPage);
        setPageSize(nextPageSize);
    }}/></footer>
    </section><div className="ledgerInsight"><MaterialIcon name="fact_check" size={18}/>当前闭环覆盖开票、付款、红字发票、退款与反核销；总账凭证和税务平台对接尚未接入。</div>
    {creatingFor !== null ? <InvoiceDialog orders={orders} initialOrderId={creatingFor || undefined} onClose={() => setCreatingFor(null)} onSaved={(invoice) => saved(invoice, `${invoice.invoiceNumber} 已开具`)}/> : null}
    {paying ? <PaymentDialog invoice={paying} onClose={() => setPaying(null)} onSaved={(inv) => replace(inv, `${inv.invoiceNumber} 付款已登记`)}/> : null}
    {crediting ? <CreditNoteDialog invoice={crediting} onClose={() => setCrediting(null)} onSaved={handleCreditNoteSaved}/> : null}
    {refunding ? <RefundDialog invoice={refunding} onClose={() => setRefunding(null)} onSaved={(inv) => replace(inv, `${inv.invoiceNumber} 退款已登记`)}/> : null}
    {reversing ? <ReverseDialog payment={reversing.payment} invoice={reversing.invoice} onClose={() => setReversing(null)} onSaved={(inv) => replace(inv, "反核销已完成")}/> : null}
    {detail ? <InvoiceDrawer invoice={detail} onClose={() => setDetail(null)} onPayment={() => { setPaying(detail); setDetail(null); }} onCreditNote={() => { setCrediting(detail); setDetail(null); }} onRefund={() => { setRefunding(detail); setDetail(null); }} onReverse={(payment) => setReversing({ payment, invoice: detail })}/> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}</div>;
}

