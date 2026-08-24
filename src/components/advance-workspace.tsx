"use client";

import { useEffect, useMemo, useState } from "react";
import { MaterialIcon } from "./material-icon";
import { GsButton, GsInput, GsModalHost, GsTextArea } from "./ui";
import { RoundedSelect } from "./rounded-select";
import {
  fetchAdvances,
  submitRefundAdvance,
  submitRegisterAdvance,
  type Advance,
  type AdvancePage,
} from "@/services/advance-client-service";
import type { AdvancePageData } from "@/services/advance-page-server-service";

type Toast = { tone: "good" | "warn"; text: string } | null;

type PartyOption = { id: string; code: string; name: string };

function yuan(value: number | string | null | undefined): string {
  const n = typeof value === "string" ? Number(value) : value ?? 0;
  return `¥${n.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function typeLabel(type: string): string {
  return type === "RECEIVABLE" ? "预收" : "预付";
}

function statusMeta(status: string): { label: string; cls: string } {
  switch (status) {
    case "OPEN":
      return { label: "未使用", cls: "businessStatusgood" };
    case "PARTIALLY_USED":
      return { label: "部分使用", cls: "businessStatuswarn" };
    case "CLOSED":
      return { label: "已结清", cls: "businessStatusmuted" };
    default:
      return { label: status, cls: "" };
  }
}

function todayStr(): string {
  return new Date().toISOString().slice(0, 10);
}

export function AdvanceWorkspace({ page: initialPage, references }: AdvancePageData) {
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [query, setQuery] = useState("");
  const [pageData, setPageData] = useState<AdvancePage>(initialPage);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState("");

  const [registerOpen, setRegisterOpen] = useState(false);
  const [detailTarget, setDetailTarget] = useState<Advance | null>(null);
  const [refundTarget, setRefundTarget] = useState<Advance | null>(null);
  const [toast, setToast] = useState<Toast>(null);

  const customerOptions = useMemo(
    () => references.customers as PartyOption[],
    [references.customers],
  );
  const supplierOptions = useMemo(
    () => references.suppliers as PartyOption[],
    [references.suppliers],
  );

  async function reloadList(t = typeFilter, s = statusFilter, q = query) {
    setListLoading(true);
    setListError("");
    try {
      const data = await fetchAdvances({ type: t, status: s, query: q || undefined, page: 0, size: 50 });
      setPageData(data);
    } catch (error) {
      setListError(error instanceof Error ? error.message : "预收预付台账加载失败");
    } finally {
      setListLoading(false);
    }
  }

  function applyTypeFilter(value: string) {
    setTypeFilter(value);
    void reloadList(value, statusFilter, query);
  }

  function applyStatusFilter(value: string) {
    setStatusFilter(value);
    void reloadList(typeFilter, value, query);
  }

  function applyQuery() {
    void reloadList(typeFilter, statusFilter, query);
  }

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3500);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const totalReceivable = pageData.items
    .filter((i) => i.type === "RECEIVABLE")
    .reduce((sum, i) => sum + i.availableBalance, 0);
  const totalPayable = pageData.items
    .filter((i) => i.type === "PAYABLE")
    .reduce((sum, i) => sum + i.availableBalance, 0);
  const openCount = pageData.items.filter((i) => i.status !== "CLOSED").length;

  function handleRegisterSaved(advance: Advance) {
    setRegisterOpen(false);
    setDetailTarget(advance);
    setToast({ tone: "good", text: `${typeLabel(advance.type)}单 ${advance.advanceNumber} 已登记` });
    void reloadList();
  }

  function handleRefundSaved(advance: Advance) {
    setRefundTarget(null);
    setPageData((cur) => ({
      ...cur,
      items: cur.items.map((i) => (i.id === advance.id ? advance : i)),
    }));
    setDetailTarget((cur) => (cur?.id === advance.id ? advance : cur));
    setToast({ tone: "good", text: `退款已登记，${advance.advanceNumber} 余额 ${yuan(advance.availableBalance)}` });
  }

  return (
    <div className="businessPage">
      <header className="pageHeading businessPageHeading">
        <div className="pageTitleGroup">
          <span className="pageTitleIcon"><MaterialIcon name="payments" size={23} /></span>
          <div>
            <h2>预收预付</h2>
            <p>登记客户预收与供应商预付款项；开票时自动从余额池抵扣，余额可部分或全额退款。</p>
          </div>
        </div>
        <div className="pageHeadingActions">
          <RoundedSelect ariaLabel="按类型筛选" value={typeFilter} onValueChange={applyTypeFilter}
            options={[
              { value: "ALL", label: "全部类型" },
              { value: "RECEIVABLE", label: "预收（客户）" },
              { value: "PAYABLE", label: "预付（供应商）" },
            ]} />
          <RoundedSelect ariaLabel="按状态筛选" value={statusFilter} onValueChange={applyStatusFilter}
            options={[
              { value: "ALL", label: "全部状态" },
              { value: "OPEN", label: "未使用" },
              { value: "PARTIALLY_USED", label: "部分使用" },
              { value: "CLOSED", label: "已结清" },
            ]} />
          <GsButton className="primaryButton" onClick={() => setRegisterOpen(true)}>
            <MaterialIcon name="add" size={16} /> 登记
          </GsButton>
        </div>
      </header>

      <section className="businessMetrics">
        <div><small>未结单笔数</small><strong>{openCount}</strong><em>非 CLOSED</em></div>
        <div><small>预收可用余额</small><strong className="businessMetricgood">{yuan(totalReceivable)}</strong><em>RECEIVABLE</em></div>
        <div><small>预付可用余额</small><strong>{yuan(totalPayable)}</strong><em>PAYABLE</em></div>
      </section>

      <section className="businessLedger">
        <div className="sectionHeading">
          <div><p className="eyebrow">台账</p><h3>预收预付清单</h3></div>
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <GsInput type="search" placeholder="搜索单号 / 往来单位" value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") applyQuery(); }}
              style={{ maxWidth: 240 }} />
            <GsButton className="secondaryButton" onClick={() => { void reloadList(); }}>
              <MaterialIcon name="refresh" size={16} /> 刷新
            </GsButton>
          </div>
        </div>
        {listError ? <p className="formError" role="alert">{listError}</p> : null}
        {listLoading ? (
          <div className="emptyState"><MaterialIcon name="hourglass_top" size={28} /><b>正在加载台账</b></div>
        ) : pageData.items.length === 0 ? (
          <div className="emptyState">
            <MaterialIcon name="inbox" size={28} />
            <b>暂无预收预付记录</b>
            <span>点击右上角「登记」按钮创建预收或预付单。</span>
          </div>
        ) : (
          <div className="salesOrderTableWrap">
            <table className="salesOrderTable">
              <thead>
                <tr>
                  <th>单号</th><th>类型</th><th>往来单位</th><th>币种</th>
                  <th>登记日期</th>
                  <th className="amount">原始金额</th>
                  <th className="amount">已抵扣</th>
                  <th className="amount">已退款</th>
                  <th className="amount">可用余额</th>
                  <th>状态</th><th>操作</th>
                </tr>
              </thead>
              <tbody>
                {pageData.items.map((item) => {
                  const meta = statusMeta(item.status);
                  return (
                    <tr key={item.id} className={item.status === "CLOSED" ? "salesOrderTableRowMuted" : ""}>
                      <td><strong>{item.advanceNumber}</strong></td>
                      <td>
                        <span className={`businessStatus ${item.type === "RECEIVABLE" ? "businessStatusgood" : "businessStatuswarn"}`}>
                          {typeLabel(item.type)}
                        </span>
                      </td>
                      <td>{item.partyCode} {item.partyName}</td>
                      <td>{item.currency}</td>
                      <td>{item.advanceDate}</td>
                      <td className="amount">{yuan(item.totalAmount)}</td>
                      <td className="amount">{yuan(item.appliedAmount)}</td>
                      <td className="amount">{yuan(item.refundedAmount)}</td>
                      <td className="amount"><strong>{yuan(item.availableBalance)}</strong></td>
                      <td><span className={`businessStatus ${meta.cls}`}>{meta.label}</span></td>
                      <td className="rowActions">
                        <GsButton className="iconButton" aria-label="查看详情" onClick={() => setDetailTarget(item)}>
                          <MaterialIcon name="visibility" size={16} /> 详情
                        </GsButton>
                        {item.status !== "CLOSED" ? (
                          <GsButton className="iconButton" aria-label="退款" onClick={() => setRefundTarget(item)}>
                            <MaterialIcon name="undo" size={16} /> 退款
                          </GsButton>
                        ) : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <div className="ledgerInsight">
        <MaterialIcon name="info" size={18} />
        预收（RECEIVABLE）为客户先付款后开票形成的负债；预付（PAYABLE）为向供应商先付款后供货形成的资产。
        开票时系统自动按最早未结单顺序抵扣可用余额，也可在开票时指定预收/预付单号。已关账期间不可登记或退款。
      </div>

      {registerOpen ? (
        <RegisterDialog
          customers={customerOptions}
          suppliers={supplierOptions}
          onClose={() => setRegisterOpen(false)}
          onSaved={handleRegisterSaved}
        />
      ) : null}
      {detailTarget ? (
        <AdvanceDetailDrawer item={detailTarget} onClose={() => setDetailTarget(null)} />
      ) : null}
      {refundTarget ? (
        <RefundDialog
          item={refundTarget}
          onClose={() => setRefundTarget(null)}
          onSaved={handleRefundSaved}
        />
      ) : null}

      {toast ? (
        <div className={`toastMessage ${toast.tone === "warn" ? "toastWarn" : ""}`} role="status">
          <MaterialIcon name={toast.tone === "warn" ? "warning" : "check_circle"} filled size={18} />
          {toast.text}
        </div>
      ) : null}
    </div>
  );
}

function RegisterDialog({
  customers,
  suppliers,
  onClose,
  onSaved,
}: {
  customers: PartyOption[];
  suppliers: PartyOption[];
  onClose: () => void;
  onSaved: (advance: Advance) => void;
}) {
  const [type, setType] = useState<"RECEIVABLE" | "PAYABLE">("RECEIVABLE");
  const [partyId, setPartyId] = useState("");
  const [advanceDate, setAdvanceDate] = useState(todayStr());
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  const partyList = type === "RECEIVABLE" ? customers : suppliers;

  function changeType(value: string) {
    setType(value as "RECEIVABLE" | "PAYABLE");
    setPartyId("");
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (!partyId) { setError(`请选择${type === "RECEIVABLE" ? "客户" : "供应商"}`); return; }
    const amt = Number(amount);
    if (!Number.isFinite(amt) || amt <= 0) { setError("金额必须为正数"); return; }
    setPending(true);
    try {
      const created = await submitRegisterAdvance({
        type,
        partyId,
        advanceDate,
        totalAmount: Math.round(amt * 100) / 100,
        note: note.trim() || undefined,
      });
      onSaved(created);
    } catch (e) {
      setError(e instanceof Error ? e.message : "登记失败，请刷新后重试");
      setPending(false);
    }
  }

  return (
    <GsModalHost onClose={() => { if (!pending) onClose(); }}>
      <section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(e) => e.stopPropagation()}>
        <header className="dialogHeader">
          <span className="dialogTitleMark"><MaterialIcon name="payments" size={22} /></span>
          <div>
            <h2>登记预收 / 预付</h2>
            <p>先收款后开票记为预收，先付款后供货记为预付。</p>
          </div>
        </header>
        <form onSubmit={submit}>
          <div className="dialogGrid">
            <label className="formField">
              <span>类型<em>必填</em></span>
              <RoundedSelect ariaLabel="预收或预付类型" value={type} onValueChange={changeType}
                options={[
                  { value: "RECEIVABLE", label: "预收（客户）" },
                  { value: "PAYABLE", label: "预付（供应商）" },
                ]} />
            </label>
            <label className="formField">
              <span>{type === "RECEIVABLE" ? "客户" : "供应商"}<em>必填</em></span>
              <RoundedSelect ariaLabel="选择往来单位" value={partyId} onValueChange={setPartyId}
                placeholder={`请选择${type === "RECEIVABLE" ? "客户" : "供应商"}`}
                options={partyList.map((p) => ({ value: p.id, label: `${p.code} ${p.name}` }))} />
            </label>
            <label className="formField">
              <span>登记日期<em>必填</em></span>
              <GsInput type="date" value={advanceDate} required onChange={(e) => setAdvanceDate(e.target.value)} />
            </label>
            <label className="formField">
              <span>金额（含税总额）<em>必填</em></span>
              <GsInput type="number" step="0.01" min="0.01" value={amount} required
                onChange={(e) => setAmount(e.target.value)} placeholder="0.00" />
            </label>
            <label className="formField formFieldFull">
              <span>备注（可选）</span>
              <GsTextArea maxLength={500} value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="例如：合同约定预付款 30%" />
              <small>{note.length}/500</small>
            </label>
          </div>
          {error ? <p className="formError" role="alert">{error}</p> : null}
          <footer className="dialogFooter">
            <span><MaterialIcon name="warning" size={17} />金额为含税总额，登记后不可修改；如有差错请退款后重新登记。</span>
            <div>
              <GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton>
              <GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "提交中…" : "确认登记"}</GsButton>
            </div>
          </footer>
        </form>
      </section>
    </GsModalHost>
  );
}

function RefundDialog({
  item,
  onClose,
  onSaved,
}: {
  item: Advance;
  onClose: () => void;
  onSaved: (advance: Advance) => void;
}) {
  const [refundDate, setRefundDate] = useState(todayStr());
  const [refundAmount, setRefundAmount] = useState("");
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  const available = item.availableBalance;

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const amt = Number(refundAmount);
    if (!Number.isFinite(amt) || amt <= 0) { setError("退款金额必须为正数"); return; }
    if (amt > available + 0.001) { setError(`退款金额不能超过可用余额 ${yuan(available)}`); return; }
    if (reason.trim().length < 4) { setError("退款原因至少 4 个字符"); return; }
    setPending(true);
    try {
      const updated = await submitRefundAdvance(item.id, {
        refundAmount: Math.round(amt * 100) / 100,
        refundDate,
        reason: reason.trim(),
      });
      onSaved(updated);
    } catch (e) {
      setError(e instanceof Error ? e.message : "退款失败，请刷新后重试");
      setPending(false);
    }
  }

  return (
    <GsModalHost onClose={() => { if (!pending) onClose(); }}>
      <section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(e) => e.stopPropagation()}>
        <header className="dialogHeader">
          <span className="dialogTitleMark"><MaterialIcon name="undo" size={22} /></span>
          <div>
            <h2>{typeLabel(item.type)}退款</h2>
            <p>{item.advanceNumber} · {item.partyName} · 可用余额 {yuan(available)}</p>
          </div>
        </header>
        <form onSubmit={submit}>
          <div className="dialogGrid">
            <label className="formField">
              <span>退款日期<em>必填</em></span>
              <GsInput type="date" value={refundDate} required onChange={(e) => setRefundDate(e.target.value)} />
            </label>
            <label className="formField">
              <span>退款金额<em>必填</em></span>
              <GsInput type="number" step="0.01" min="0.01" max={available} value={refundAmount} required
                onChange={(e) => setRefundAmount(e.target.value)} placeholder={yuan(available)} />
            </label>
            <label className="formField formFieldFull">
              <span>退款原因<em>必填，至少 4 字符</em></span>
              <GsTextArea maxLength={500} value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="例如：合同取消，退回剩余预付款" />
              <small>{reason.length}/500</small>
            </label>
          </div>
          {error ? <p className="formError" role="alert">{error}</p> : null}
          <footer className="dialogFooter">
            <span><MaterialIcon name="info" size={17} />退款后余额相应减少；全额退款后单据自动结清。</span>
            <div>
              <GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton>
              <GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "提交中…" : "确认退款"}</GsButton>
            </div>
          </footer>
        </form>
      </section>
    </GsModalHost>
  );
}

function AdvanceDetailDrawer({ item, onClose }: { item: Advance; onClose: () => void }) {
  const meta = statusMeta(item.status);
  return (
    <GsModalHost onClose={onClose}>
      <section className="businessDialog dialogCardWide" role="dialog" aria-modal="true" onMouseDown={(e) => e.stopPropagation()}>
        <header className="dialogHeader">
          <span className="dialogTitleMark"><MaterialIcon name="receipt_long" size={22} /></span>
          <div>
            <h2>{item.advanceNumber}</h2>
            <p>
              <span className={`businessStatus ${item.type === "RECEIVABLE" ? "businessStatusgood" : "businessStatuswarn"}`} style={{ marginRight: 8 }}>
                {typeLabel(item.type)}
              </span>
              {item.partyCode} {item.partyName} · {item.advanceDate} · {item.currency}
            </p>
          </div>
        </header>
        <div className="dialogGrid">
          <div><span className="fieldLabel">原始金额</span><strong>{yuan(item.totalAmount)}</strong></div>
          <div><span className="fieldLabel">已抵扣</span><strong>{yuan(item.appliedAmount)}</strong></div>
          <div><span className="fieldLabel">已退款</span><strong>{yuan(item.refundedAmount)}</strong></div>
          <div><span className="fieldLabel">可用余额</span><strong className="businessMetricgood">{yuan(item.availableBalance)}</strong></div>
          <div><span className="fieldLabel">状态</span>
            <strong><span className={`businessStatus ${meta.cls}`}>{meta.label}</span></strong>
          </div>
          <div><span className="fieldLabel">登记日期</span><strong>{item.advanceDate}</strong></div>
          {item.note ? (
            <div className="formFieldFull">
              <span className="fieldLabel">备注</span>
              <p className="dialogSubtle">{item.note}</p>
            </div>
          ) : null}
        </div>

        {item.applications.length > 0 ? (
          <div className="salesOrderTableWrap" style={{ marginTop: 16 }}>
            <p className="eyebrow">抵扣记录</p>
            <table className="salesOrderTable">
              <thead>
                <tr><th>发票号</th><th className="amount">抵扣金额</th><th>抵扣日期</th></tr>
              </thead>
              <tbody>
                {item.applications.map((app) => (
                  <tr key={app.id}>
                    <td>{app.invoiceNumber}</td>
                    <td className="amount">{yuan(app.appliedAmount)}</td>
                    <td>{app.applicationDate}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {item.refunds.length > 0 ? (
          <div className="salesOrderTableWrap" style={{ marginTop: 16 }}>
            <p className="eyebrow">退款记录</p>
            <table className="salesOrderTable">
              <thead>
                <tr><th className="amount">退款金额</th><th>退款日期</th><th>原因</th></tr>
              </thead>
              <tbody>
                {item.refunds.map((ref) => (
                  <tr key={ref.id}>
                    <td className="amount">{yuan(ref.refundAmount)}</td>
                    <td>{ref.refundDate}</td>
                    <td>{ref.reason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {item.applications.length === 0 && item.refunds.length === 0 ? (
          <p className="dialogSubtle" style={{ marginTop: 16 }}>暂无抵扣或退款记录。</p>
        ) : null}

        <footer className="dialogFooter">
          <div />
          <GsButton className="primaryButton" onClick={onClose}>关闭</GsButton>
        </footer>
      </section>
    </GsModalHost>
  );
}