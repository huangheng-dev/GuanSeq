"use client";

import { useEffect, useMemo, useState } from "react";
import { MaterialIcon } from "./material-icon";
import { GsButton, GsInput, GsModalHost, GsTextArea } from "./ui";
import { RoundedSelect } from "./rounded-select";
import {
  fetchGrirAccrualPreview,
  fetchGrirAccruals,
  submitReverseGrirAccrual,
  submitRunGrirAccrual,
  type GrirAccrualPage,
} from "@/services/grir-accrual-client-service";
import type { GrirAccrual, GrirAccrualPreview } from "@/lib/contracts";

type Toast = { tone: "good" | "warn"; text: string } | null;

function yuan(value: number | string | null | undefined): string {
  const n = typeof value === "string" ? Number(value) : value ?? 0;
  return `¥${n.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function qty(value: number | string | null | undefined): string {
  const n = typeof value === "string" ? Number(value) : value ?? 0;
  return n.toLocaleString("zh-CN", { maximumFractionDigits: 4 });
}

function periodLabel(year: number, period: number): string {
  return `${year}-${String(period).padStart(2, "0")}`;
}

function lastDayOf(year: number, period: number): string {
  const d = new Date(year, period, 0);
  return `${year}-${String(period).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function GrirAccrualWorkspace({
  initialYear,
  initialPage,
}: {
  initialYear: number;
  initialPage: GrirAccrualPage;
}) {
  const now = new Date();
  const [year, setYear] = useState(initialYear);
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [pageData, setPageData] = useState<GrirAccrualPage>(initialPage);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState("");

  const [runYear, setRunYear] = useState(now.getFullYear());
  const [runPeriod, setRunPeriod] = useState(now.getMonth() + 1);
  const [runNote, setRunNote] = useState("");
  const [runSubmitting, setRunSubmitting] = useState(false);
  const [runError, setRunError] = useState("");
  const [preview, setPreview] = useState<GrirAccrualPreview | null>(null);
  const [previewLoading, setPreviewLoading] = useState(true);

  const [detailTarget, setDetailTarget] = useState<GrirAccrual | null>(null);
  const [reverseTarget, setReverseTarget] = useState<GrirAccrual | null>(null);
  const [toast, setToast] = useState<Toast>(null);

  const yearOptions = useMemo(() => {
    const current = new Date().getFullYear();
    const years: number[] = [];
    for (let y = current - 2; y <= current + 1; y += 1) years.push(y);
    return years;
  }, []);

  async function reloadList(y = year, s = statusFilter) {
    setListLoading(true);
    setListError("");
    try {
      const data = await fetchGrirAccruals({ year: y, status: s, page: 0, size: 50 });
      setPageData(data);
    } catch (error) {
      setListError(error instanceof Error ? error.message : "暂估台账加载失败");
    } finally {
      setListLoading(false);
    }
  }

  function applyYearFilter(value: string) {
    const y = Number(value);
    setYear(y);
    void reloadList(y, statusFilter);
  }

  function applyStatusFilter(value: string) {
    setStatusFilter(value);
    void reloadList(year, value);
  }

  async function loadPreview(y: number, p: number) {
    setPreviewLoading(true);
    setRunError("");
    try {
      const data = await fetchGrirAccrualPreview(y, p);
      setPreview(data);
    } catch (error) {
      setRunError(error instanceof Error ? error.message : "暂估预览加载失败");
      setPreview(null);
    } finally {
      setPreviewLoading(false);
    }
  }

  function changeRunYear(value: string) {
    const y = Number(value);
    setRunYear(y);
    void loadPreview(y, runPeriod);
  }

  function changeRunPeriod(value: string) {
    const p = Number(value);
    setRunPeriod(p);
    void loadPreview(runYear, p);
  }

  useEffect(() => {
    let active = true;
    fetchGrirAccrualPreview(runYear, runPeriod)
      .then((data) => { if (active) setPreview(data); })
      .catch((error) => {
        if (!active) return;
        setRunError(error instanceof Error ? error.message : "暂估预览加载失败");
        setPreview(null);
      })
      .finally(() => { if (active) setPreviewLoading(false); });
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3500);
    return () => window.clearTimeout(timer);
  }, [toast]);

  async function submitRun() {
    setRunError("");
    setRunSubmitting(true);
    try {
      const created = await submitRunGrirAccrual({
        fiscalYear: runYear,
        fiscalPeriod: runPeriod,
        accrualDate: lastDayOf(runYear, runPeriod),
        note: runNote.trim() || undefined,
      });
      setToast({ tone: "good", text: `${periodLabel(runYear, runPeriod)} 暂估已生成：${created.accrualNumber}` });
      setRunNote("");
      setDetailTarget(created);
      await reloadList();
      await loadPreview(runYear, runPeriod);
    } catch (error) {
      setRunError(error instanceof Error ? error.message : "暂估运行失败，请重试");
    } finally {
      setRunSubmitting(false);
    }
  }

  const postedCount = pageData.items.filter((i) => i.status === "POSTED").length;
  const reversedCount = pageData.items.length - postedCount;

  return (
    <div className="businessPage">
      <header className="pageHeading businessPageHeading">
        <div className="pageTitleGroup">
          <span className="pageTitleIcon"><MaterialIcon name="inventory" size={23} /></span>
          <div>
            <h2>暂估应付（GR/IR）</h2>
            <p>货到票未到时，月末按采购订单单价对已收货未开票数量暂估入账；下期运行自动冲回上期暂估。</p>
          </div>
        </div>
        <div className="pageHeadingActions">
          <RoundedSelect ariaLabel="按会计年度切换" value={String(year)}
            onValueChange={applyYearFilter}
            options={yearOptions.map((y) => ({ value: String(y), label: `${y} 年` }))} />
          <RoundedSelect ariaLabel="按状态筛选" value={statusFilter} onValueChange={applyStatusFilter}
            options={[
              { value: "ALL", label: "全部状态" },
              { value: "POSTED", label: "有效暂估" },
              { value: "REVERSED", label: "已冲回" },
            ]} />
        </div>
      </header>

      <section className="businessMetrics">
        <div><small>本期记录</small><strong>{pageData.totalElements}</strong><em>{year} 年</em></div>
        <div><small>有效暂估</small><strong className="businessMetricgood">{postedCount}</strong><em>POSTED</em></div>
        <div><small>已冲回</small><strong>{reversedCount}</strong><em>REVERSED</em></div>
      </section>

      {listError ? <p className="formError" role="alert">{listError}</p> : null}

      <section className="businessLedger">
        <div className="sectionHeading">
          <div><p className="eyebrow">{year} 年度</p><h3>暂估应付台账</h3></div>
          <GsButton className="secondaryButton" onClick={() => { void reloadList(); }}>
            <MaterialIcon name="refresh" size={16} /> 刷新
          </GsButton>
        </div>
        {listLoading ? (
          <div className="emptyState"><MaterialIcon name="hourglass_top" size={28} /><b>正在加载暂估台账</b></div>
        ) : pageData.items.length === 0 ? (
          <div className="emptyState"><MaterialIcon name="inbox" size={28} /><b>该年度暂无暂估单</b><span>在下方选择会计年月并运行月末暂估。</span></div>
        ) : (
          <div className="salesOrderTableWrap">
            <table className="salesOrderTable">
              <thead>
                <tr>
                  <th>暂估单号</th><th>会计期间</th><th>入账日期</th>
                  <th className="amount">暂估净额</th><th>行数</th><th>状态</th><th>冲回信息</th><th>操作</th>
                </tr>
              </thead>
              <tbody>
                {pageData.items.map((item) => {
                  const reversed = item.status === "REVERSED";
                  return (
                    <tr key={item.id} className={reversed ? "salesOrderTableRowWarn" : ""}>
                      <td><strong>{item.accrualNumber}</strong></td>
                      <td>{periodLabel(item.fiscalYear, item.fiscalPeriod)}</td>
                      <td>{item.accrualDate}</td>
                      <td className="amount">{yuan(item.totalNetAmount)}</td>
                      <td>{item.lines.length}</td>
                      <td><span className={`businessStatus ${reversed ? "businessStatuswarn" : "businessStatusgood"}`}>{reversed ? "已冲回" : "有效"}</span></td>
                      <td className="rowWarnHint">
                        {reversed
                          ? (item.reversalReason ? `手动：${item.reversalReason}`
                            : item.reversedByAccrualId ? `下期自动冲回（${item.reversalDate ?? ""}）`
                            : `冲回日期 ${item.reversalDate ?? ""}`)
                          : "—"}
                      </td>
                      <td className="rowActions">
                        <GsButton className="iconButton" aria-label="查看详情" onClick={() => setDetailTarget(item)}>
                          <MaterialIcon name="visibility" size={16} /> 详情
                        </GsButton>
                        {!reversed ? (
                          <GsButton className="iconButton" aria-label="冲回暂估" onClick={() => setReverseTarget(item)}>
                            <MaterialIcon name="undo" size={16} /> 冲回
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

      <section className="businessLedger">
        <div className="sectionHeading">
          <div><p className="eyebrow">月末操作</p><h3>运行暂估</h3></div>
          <strong>{previewLoading ? "加载预览…" : `预览合计 ${yuan(preview?.totalNetAmount ?? 0)}`}</strong>
        </div>
        <div className="dialogGrid">
          <label className="formField">
            <span>会计年度</span>
            <RoundedSelect ariaLabel="运行会计年度" value={String(runYear)}
              onValueChange={changeRunYear}
              options={yearOptions.map((y) => ({ value: String(y), label: `${y} 年` }))} />
          </label>
          <label className="formField">
            <span>会计期间</span>
            <RoundedSelect ariaLabel="运行会计期间" value={String(runPeriod)}
              onValueChange={changeRunPeriod}
              options={Array.from({ length: 12 }, (_, i) => ({ value: String(i + 1), label: `${i + 1} 月` }))} />
          </label>
          <label className="formField formFieldFull">
            <span>备注（可选）</span>
            <GsTextArea maxLength={500} value={runNote}
              onChange={(event) => setRunNote(event.target.value)}
              placeholder="例如：月末按系统收货未开票数据暂估" />
            <small>{runNote.length}/500</small>
          </label>
        </div>
        {preview ? (
          <div className="grirPreview">
            <p className="rowWarnHint">
              {preview.priorAccrualId
                ? `上期 ${preview.priorAccrualNumber ?? ""} 暂估 ${yuan(preview.priorAccrualAmount)} 将被自动冲回。`
                : "本期为首次暂估，无上期可冲回。"}
              {" "}预览合计 {yuan(preview.totalNetAmount)}，{preview.lines.length} 行明细。
            </p>
            {preview.lines.length > 0 ? (
              <div className="salesOrderTableWrap">
                <table className="salesOrderTable">
                  <thead>
                    <tr><th>采购订单</th><th>供应商</th><th>物料</th>
                      <th className="amount">收货数量</th><th className="amount">已开票</th>
                      <th className="amount">暂估数量</th><th className="amount">单价</th>
                      <th className="amount">暂估净额</th></tr>
                  </thead>
                  <tbody>
                    {preview.lines.map((line) => (
                      <tr key={`${line.purchaseOrderId}-${line.purchaseOrderLineId}`}>
                        <td>{line.orderNumber}</td><td>{line.supplierName}</td>
                        <td>{line.materialCode} {line.materialName}</td>
                        <td className="amount">{qty(line.receivedQuantity)}</td>
                        <td className="amount">{qty(line.invoicedQuantity)}</td>
                        <td className="amount">{qty(line.accruedQuantity)}</td>
                        <td className="amount">{yuan(line.unitPrice)}</td>
                        <td className="amount">{yuan(line.netAmount)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="rowWarnHint">本月所有收货均已开票，将生成一张金额为 0 的暂估单，同时冲回上期暂估。</p>
            )}
          </div>
        ) : null}
        {runError ? <p className="formError" role="alert">{runError}</p> : null}
        <div className="dialogActions">
          <GsButton className="primaryButton" disabled={runSubmitting || previewLoading}
            onClick={() => { void submitRun(); }}>
            <MaterialIcon name="play_circle" size={17} />
            {runSubmitting ? "提交中…" : `运行 ${periodLabel(runYear, runPeriod)} 暂估`}
          </GsButton>
        </div>
      </section>

      <div className="ledgerInsight">
        <MaterialIcon name="info" size={18} />
        暂估金额为不含税净额：累计合格收货数量减已开票数量，再乘以采购订单单价。同期间重复提交将幂等返回已有暂估单；冲回仅允许在未关账期间操作。
      </div>

      {detailTarget ? <GrirDetailDrawer item={detailTarget} onClose={() => setDetailTarget(null)} /> : null}
      {reverseTarget ? (
        <ReverseDialog item={reverseTarget} onClose={() => setReverseTarget(null)}
          onSaved={(updated) => {
            setReverseTarget(null);
            setPageData((cur) => ({ ...cur, items: cur.items.map((i) => (i.id === updated.id ? updated : i)) }));
            setToast({ tone: "good", text: `暂估单 ${updated.accrualNumber} 已冲回` });
          }} />
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

function GrirDetailDrawer({ item, onClose }: { item: GrirAccrual; onClose: () => void }) {
  const reversed = item.status === "REVERSED";
  return (
    <GsModalHost onClose={onClose}>
      <section className="businessDialog dialogCardWide" role="dialog" aria-modal="true" onMouseDown={(e) => e.stopPropagation()}>
        <header className="dialogHeader">
          <span className="dialogTitleMark"><MaterialIcon name="receipt_long" size={22} /></span>
          <div>
            <h2>暂估单 {item.accrualNumber}</h2>
            <p>{periodLabel(item.fiscalYear, item.fiscalPeriod)} · 入账日期 {item.accrualDate} · {reversed ? "已冲回" : "有效"}</p>
          </div>
        </header>
        <div className="dialogGrid">
          <div><span className="fieldLabel">暂估净额（不含税）</span><strong>{yuan(item.totalNetAmount)}</strong></div>
          <div><span className="fieldLabel">明细行数</span><strong>{item.lines.length}</strong></div>
          <div><span className="fieldLabel">入账日期</span><strong>{item.accrualDate}</strong></div>
          <div><span className="fieldLabel">状态</span>
            <strong className={reversed ? "businessStatuswarn" : "businessStatusgood"}>{reversed ? "已冲回" : "POSTED"}</strong>
          </div>
          {reversed ? (
            <div className="formFieldFull">
              <span className="fieldLabel">冲回信息</span>
              <p className="historyReason">
                {item.reversalReason
                  ? `手动冲回：${item.reversalReason}（${item.reversalDate ?? ""}）`
                  : item.reversedByAccrualId
                    ? `下期暂估自动冲回（${item.reversalDate ?? ""}）`
                    : `冲回日期：${item.reversalDate ?? ""}`}
              </p>
            </div>
          ) : null}
          {item.note ? (
            <div className="formFieldFull">
              <span className="fieldLabel">备注</span>
              <p className="dialogSubtle">{item.note}</p>
            </div>
          ) : null}
        </div>
        {item.lines.length > 0 ? (
          <div className="salesOrderTableWrap">
            <table className="salesOrderTable">
              <thead>
                <tr><th>#</th><th>采购订单</th><th>供应商</th><th>物料</th>
                  <th className="amount">收货</th><th className="amount">已开票</th>
                  <th className="amount">暂估数量</th><th className="amount">单价</th>
                  <th className="amount">净额</th></tr>
              </thead>
              <tbody>
                {item.lines.map((line) => (
                  <tr key={line.id}>
                    <td>{line.lineNumber}</td><td>{line.orderNumber}</td><td>{line.supplierName}</td>
                    <td>{line.materialCode} {line.materialName}</td>
                    <td className="amount">{qty(line.receivedQuantity)}</td>
                    <td className="amount">{qty(line.invoicedQuantity)}</td>
                    <td className="amount">{qty(line.accruedQuantity)}</td>
                    <td className="amount">{yuan(line.unitPrice)}</td>
                    <td className="amount">{yuan(line.netAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="dialogSubtle">本张暂估单无明细行（本月所有收货均已开票）。</p>
        )}
        <footer className="dialogFooter">
          <div />
          <GsButton className="primaryButton" onClick={onClose}>关闭</GsButton>
        </footer>
      </section>
    </GsModalHost>
  );
}

function ReverseDialog({
  item, onClose, onSaved,
}: {
  item: GrirAccrual;
  onClose: () => void;
  onSaved: (updated: GrirAccrual) => void;
}) {
  const today = new Date().toISOString().slice(0, 10);
  const [reversalDate, setReversalDate] = useState(today);
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (reason.trim().length < 4) { setError("冲回原因至少 4 个字符"); return; }
    setPending(true);
    setError("");
    try {
      const updated = await submitReverseGrirAccrual(item.id, { reversalDate, reason: reason.trim() });
      onSaved(updated);
    } catch (e) {
      setError(e instanceof Error ? e.message : "冲回失败，请刷新后重试");
      setPending(false);
    }
  }

  return (
    <GsModalHost onClose={() => { if (!pending) onClose(); }}>
      <section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(e) => e.stopPropagation()}>
        <header className="dialogHeader">
          <span className="dialogTitleMark"><MaterialIcon name="undo" size={22} /></span>
          <div>
            <h2>手动冲回暂估</h2>
            <p>{item.accrualNumber} · {periodLabel(item.fiscalYear, item.fiscalPeriod)} · 净额 {yuan(item.totalNetAmount)}</p>
          </div>
        </header>
        <form onSubmit={submit}>
          <div className="dialogGrid">
            <label className="formField">
              <span>冲回日期<em>必填</em></span>
              <GsInput type="date" value={reversalDate} required
                onChange={(e) => setReversalDate(e.target.value)} />
            </label>
            <label className="formField formFieldFull">
              <span>冲回原因<em>必填，至少 4 字符</em></span>
              <GsTextArea maxLength={500} value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="例如：暂估金额录入有误，全额冲回后重新运行" />
              <small>{reason.length}/500</small>
            </label>
          </div>
          {error ? <p className="formError" role="alert">{error}</p> : null}
          <footer className="dialogFooter">
            <span><MaterialIcon name="warning" size={17} />冲回后状态不可撤销；如需重新暂估可再次运行。</span>
            <div>
              <GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton>
              <GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "提交中…" : "确认冲回"}</GsButton>
            </div>
          </footer>
        </form>
      </section>
    </GsModalHost>
  );
}
