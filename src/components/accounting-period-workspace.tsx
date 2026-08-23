"use client";

import { useEffect, useState } from "react";
import { MaterialIcon } from "./material-icon";
import { GsButton, GsModalHost, GsTextArea } from "./ui";
import { RoundedSelect } from "./rounded-select";
import {
  fetchAccountingPeriods,
  submitCloseAccountingPeriod,
  submitReopenAccountingPeriod,
} from "@/services/accounting-period-client-service";
import type { AccountingPeriod } from "@/lib/contracts";

const MONTH_LABELS = [
  "一月", "二月", "三月", "四月", "五月", "六月",
  "七月", "八月", "九月", "十月", "十一月", "十二月",
];

function formatInstant(value: string | null): string {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString("zh-CN", {
      year: "numeric", month: "2-digit", day: "2-digit",
      hour: "2-digit", minute: "2-digit",
    });
  } catch {
    return value;
  }
}

type Toast = { tone: "good" | "warn"; text: string } | null;

export function AccountingPeriodWorkspace({
  initialYear,
  initialPeriods,
}: {
  initialYear: number;
  initialPeriods: AccountingPeriod[];
}) {
  const now = new Date();
  const currentLabel = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;

  const [year, setYear] = useState<number>(initialYear);
  const [periods, setPeriods] = useState<AccountingPeriod[]>(initialPeriods);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState("");
  const [toast, setToast] = useState<Toast>(null);
  const [reopenTarget, setReopenTarget] = useState<AccountingPeriod | null>(null);

  const yearOptions = (() => {
    const currentYear = new Date().getFullYear();
    const years: number[] = [];
    for (let y = currentYear - 2; y <= currentYear + 1; y += 1) years.push(y);
    return years;
  })();

  async function changeYear(nextYear: number) {
    if (nextYear === year) return;
    setYear(nextYear);
    setLoading(true);
    setLoadError("");
    try {
      const data = await fetchAccountingPeriods(nextYear);
      setPeriods(data);
    } catch (reason) {
      setPeriods([]);
      setLoadError(reason instanceof Error ? reason.message : "会计期间加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const closedCount = periods.filter((p) => p.status === "CLOSED").length;
  const openCount = periods.length - closedCount;

  async function closePeriod(period: AccountingPeriod) {
    setActionError("");
    setPendingId(period.id);
    try {
      const updated = await submitCloseAccountingPeriod(period.id);
      setPeriods((current) => current.map((p) => (p.id === updated.id ? updated : p)));
      setToast({ tone: "good", text: `${period.periodLabel} 已关账，该月财务写入已冻结` });
    } catch (reason) {
      setActionError(reason instanceof Error ? reason.message : "关账失败，请重试");
    } finally {
      setPendingId(null);
    }
  }

  function handleReopenSaved(updated: AccountingPeriod) {
    setPeriods((current) => current.map((p) => (p.id === updated.id ? updated : p)));
    setReopenTarget(null);
    setToast({ tone: "good", text: `${updated.periodLabel} 已重开，可补录该月业务` });
  }

  return (
    <div className="businessPage">
      <header className="pageHeading businessPageHeading">
        <div className="pageTitleGroup">
          <span className="pageTitleIcon"><MaterialIcon name="event_lock" size={23} /></span>
          <div>
            <h2>会计期间与关账</h2>
            <p>按月冻结财务过账：开票、收付款、红字、退款、反核销与订单利润结算均按单据日期校验期间状态。</p>
          </div>
        </div>
        <div className="pageHeadingActions">
          <RoundedSelect
            ariaLabel="按会计年度切换"
            value={String(year)}
            onValueChange={(value) => { void changeYear(Number(value)); }}
            options={yearOptions.map((y) => ({ value: String(y), label: `${y} 年` }))}
          />
        </div>
      </header>

      <section className="businessMetrics">
        <div><small>开放期间</small><strong className="businessMetricgood">{openCount}</strong><em>允许财务写入</em></div>
        <div><small>已关账</small><strong className={closedCount ? "businessMetricwarn" : ""}>{closedCount}</strong><em>该月写入被拒绝</em></div>
        <div><small>当前月</small><strong>{currentLabel}</strong><em>系统日期</em></div>
        <div><small>重开权限</small><strong>仅管理员</strong><em>财务经理可关账不可重开</em></div>
      </section>

      {loadError ? <p className="formError" role="alert">{loadError}</p> : null}
      {actionError ? <p className="formError" role="alert">{actionError}</p> : null}

      <section className="businessLedger">
        <div className="sectionHeading">
          <div><p className="eyebrow">{year} 年度</p><h3>月度期间状态</h3></div>
          <strong>{loading ? "加载中…" : `${periods.length} / 12`}</strong>
        </div>

        {loading ? (
          <div className="emptyState"><MaterialIcon name="hourglass_top" size={28} /><b>正在加载会计期间</b><span>按年度查询期间状态与关账审计。</span></div>
        ) : periods.length === 0 ? (
          <div className="emptyState"><MaterialIcon name="event_busy" size={28} /><b>该年度暂无期间</b><span>系统会在首次访问时自动补建，请稍后重试或联系管理员。</span></div>
        ) : (
          <div className="periodGrid" role="grid" aria-label={`${year} 年度会计期间`}>
            {periods.map((period) => {
              const closed = period.status === "CLOSED";
              return (
                <article
                  key={period.id}
                  className={`periodCard${closed ? " periodCardClosed" : " periodCardOpen"}`}
                  role="row"
                >
                  <header>
                    <div>
                      <strong>{MONTH_LABELS[period.fiscalPeriod - 1]}</strong>
                      <span>{period.periodLabel}</span>
                    </div>
                    <em className={`businessStatus ${closed ? "businessStatuswarn" : "businessStatusgood"}`}>
                      {closed ? "已关账" : "开放"}
                    </em>
                  </header>
                  <dl>
                    <dt>关账时间</dt>
                    <dd>{closed ? formatInstant(period.closedAt) : "—"}</dd>
                    <dt>最近重开</dt>
                    <dd>{period.reopenedAt ? formatInstant(period.reopenedAt) : "—"}</dd>
                    {period.reopenReason ? (
                      <>
                        <dt>重开原因</dt>
                        <dd className="periodReason">{period.reopenReason}</dd>
                      </>
                    ) : null}
                  </dl>
                  <footer>
                    {closed ? (
                      <GsButton
                        className="secondaryButton"
                        disabled={pendingId === period.id}
                        onClick={() => setReopenTarget(period)}
                      >
                        重开
                      </GsButton>
                    ) : (
                      <GsButton
                        className="primaryButton"
                        disabled={pendingId === period.id}
                        onClick={() => { void closePeriod(period); }}
                      >
                        {pendingId === period.id ? "关账中…" : "关账"}
                      </GsButton>
                    )}
                  </footer>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <div className="ledgerInsight">
        <MaterialIcon name="info" size={18} />
        关账只冻结财务过账（开票、收付款、红字、退款、反核销、订单利润结算），不影响销售、采购、生产、仓储等业务操作。若关账后发现错误，管理员可重开并补录，重开必须填写原因。
      </div>

      {reopenTarget ? (
        <ReopenDialog
          period={reopenTarget}
          onClose={() => setReopenTarget(null)}
          onSaved={handleReopenSaved}
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

function ReopenDialog({
  period,
  onClose,
  onSaved,
}: {
  period: AccountingPeriod;
  onClose: () => void;
  onSaved: (period: AccountingPeriod) => void;
}) {
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (reason.trim().length < 4) {
      setError("重开原因不能少于 4 个字符");
      return;
    }
    setPending(true);
    try {
      const updated = await submitReopenAccountingPeriod(period.id, {
        reason: reason.trim(),
        expectedVersion: period.version,
      });
      onSaved(updated);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "重开失败，请刷新后重试");
      setPending(false);
    }
  }

  return (
    <GsModalHost onClose={() => { if (!pending) onClose(); }}>
      <section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader">
          <span className="dialogTitleMark"><MaterialIcon name="lock_open" size={22} /></span>
          <div>
            <h2>重开会计期间</h2>
            <p>{period.periodLabel} · 重开后该月恢复财务写入，操作将记入审计</p>
          </div>
        </header>
        <form onSubmit={submit}>
          <label className="formField formFieldFull">
            <span>重开原因<em>必填</em></span>
            <GsTextArea
              maxLength={500}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="例如：补录上月遗漏凭证，经财务主管确认后重开。至少 4 个字符"
            />
            <small>{reason.length}/500</small>
          </label>
          {error ? <p className="formError" role="alert">{error}</p> : null}
          <footer className="dialogFooter">
            <span><MaterialIcon name="warning" size={17} />重开已关账期间会允许修改该月财务事实，请谨慎操作。</span>
            <div>
              <GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton>
              <GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "提交中…" : "确认重开"}</GsButton>
            </div>
          </footer>
        </form>
      </section>
    </GsModalHost>
  );
}
