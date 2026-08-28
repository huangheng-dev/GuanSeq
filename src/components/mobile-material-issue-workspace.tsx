"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import type { MaterialIssueRecord } from "@/lib/contracts";
import { resolveIssueComponentScan, resolveIssueStockScan, resolveMaterialIssueScan } from "@/lib/mobile-material-issue";
import { submitMaterialIssueAction } from "@/services/material-issue-client-service";
import type { MaterialIssuePageData } from "@/services/material-issue-server-service";
import { GsButton, GsInput } from "./ui";
import { MaterialIcon } from "./material-icon";

const STORAGE_KEY = "guanseq.mobile-material-issue.draft.v1";
type Draft = { issueScan: string; materialScan: string; stockScan: string; quantity: string; note: string; requestId: string };
const blankDraft = (): Draft => ({ issueScan: "", materialScan: "", stockScan: "", quantity: "", note: "", requestId: "" });
const nextRequestId = () => `mobile-material-issue-${crypto.randomUUID()}`;

function safeDraft(value: unknown): Draft | null {
  if (!value || typeof value !== "object") return null;
  const item = value as Partial<Draft>;
  if (typeof item.issueScan !== "string" || typeof item.materialScan !== "string" || typeof item.stockScan !== "string") return null;
  return { issueScan: item.issueScan, materialScan: item.materialScan, stockScan: item.stockScan,
    quantity: typeof item.quantity === "string" ? item.quantity : "", note: typeof item.note === "string" ? item.note : "",
    requestId: typeof item.requestId === "string" && item.requestId.startsWith("mobile-material-issue-") ? item.requestId : "" };
}

export function MobileMaterialIssueWorkspace({ initialData }: { initialData: MaterialIssuePageData }) {
  const [issues, setIssues] = useState(initialData.issues);
  const [draft, setDraft] = useState<Draft>(blankDraft);
  const [hydrated, setHydrated] = useState(false);
  const [online, setOnline] = useState(true);
  const [attempted, setAttempted] = useState({ issue: false, material: false, stock: false });
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState<MaterialIssueRecord | null>(null);
  const skipNextPersist = useRef(false);

  const issueResolution = useMemo(() => resolveMaterialIssueScan(issues, draft.issueScan), [draft.issueScan, issues]);
  const lineResolution = useMemo(() => issueResolution.issue
    ? resolveIssueComponentScan(issueResolution.issue, draft.materialScan)
    : { line: null, error: "请先确认生产领料单。" }, [draft.materialScan, issueResolution.issue]);
  const stockResolution = useMemo(() => issueResolution.issue && lineResolution.line
    ? resolveIssueStockScan(initialData.reference.availableStocks, issueResolution.issue, lineResolution.line, draft.stockScan)
    : { stock: null, error: "请先确认组件。" }, [draft.stockScan, initialData.reference.availableStocks, issueResolution.issue, lineResolution.line]);
  const limit = Math.min(lineResolution.line?.issuableQuantity ?? 0, stockResolution.stock?.availableQuantity ?? 0);
  const savedTxn = saved?.stockTransactions.find((item) => item.source === "MOBILE_SCAN" && item.requestId === draft.requestId)
    ?? saved?.stockTransactions.find((item) => item.source === "MOBILE_SCAN");

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setOnline(navigator.onLine);
      try {
        const stored = localStorage.getItem(STORAGE_KEY);
        const restored = stored ? safeDraft(JSON.parse(stored)) : null;
        setDraft({ ...(restored ?? blankDraft()), requestId: restored?.requestId || nextRequestId() });
        setAttempted({ issue: Boolean(restored?.issueScan), material: Boolean(restored?.materialScan), stock: Boolean(restored?.stockScan) });
      } catch { setDraft({ ...blankDraft(), requestId: nextRequestId() }); }
      setHydrated(true);
    });
    const updateNetwork = () => setOnline(navigator.onLine);
    window.addEventListener("online", updateNetwork); window.addEventListener("offline", updateNetwork);
    return () => { active = false; window.removeEventListener("online", updateNetwork); window.removeEventListener("offline", updateNetwork); };
  }, []);

  useEffect(() => {
    if (!hydrated || !draft.requestId) return;
    if (skipNextPersist.current) { skipNextPersist.current = false; return; }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(draft));
  }, [draft, hydrated]);

  function update(patch: Partial<Draft>) { setDraft((current) => ({ ...current, ...patch })); setError(""); }
  function confirmIssue(event: FormEvent) { event.preventDefault(); setAttempted((value) => ({ ...value, issue: true })); if (issueResolution.issue) window.setTimeout(() => document.getElementById("issue-material-scan")?.focus(), 0); }
  function confirmMaterial(event: FormEvent) { event.preventDefault(); setAttempted((value) => ({ ...value, material: true })); if (lineResolution.line) window.setTimeout(() => document.getElementById("issue-stock-scan")?.focus(), 0); }
  function confirmStock(event: FormEvent) { event.preventDefault(); setAttempted((value) => ({ ...value, stock: true })); if (stockResolution.stock) update({ quantity: draft.quantity || String(limit) }); }
  function reset() {
    skipNextPersist.current = true; localStorage.removeItem(STORAGE_KEY);
    setDraft({ ...blankDraft(), requestId: nextRequestId() }); setAttempted({ issue: false, material: false, stock: false });
    setSaved(null); setError("");
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); setAttempted({ issue: true, material: true, stock: true }); setError("");
    const issue = issueResolution.issue, line = lineResolution.line, stock = stockResolution.stock, quantity = Number(draft.quantity);
    if (!issue || !line || !stock) return;
    if (!Number.isFinite(quantity) || quantity <= 0 || quantity > limit) { setError(`发料数量必须大于 0 且不超过本行/本批可发上限 ${limit}。`); return; }
    if (!online) { setError("当前离线：草稿已保存在本机，但没有创建任何领料或库存事实。恢复网络后请重新确认提交。"); return; }
    setPending(true);
    try {
      const result = await submitMaterialIssueAction({ id: issue.id, action: "ISSUE", expectedVersion: issue.version,
        comment: draft.note.trim() || "移动扫码领料", source: "MOBILE_SCAN",
        lines: [{ lineId: line.id, quantity, expectedLineVersion: line.version, stockBalanceId: stock.id, expectedStockVersion: stock.version }] }, draft.requestId);
      skipNextPersist.current = true; localStorage.removeItem(STORAGE_KEY);
      setIssues((current) => current.map((item) => item.id === result.id ? result : item)); setSaved(result);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "移动领料提交失败；草稿和请求编号已保留，可核对后重试。"); }
    finally { setPending(false); }
  }

  const heading = <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="barcode_scanner" size={23}/></span><div><h2>生产领料扫码作业</h2><p>扫描领料单、组件和库存标签，在线确认后复用正式领料与仓库扣减。</p></div></div><div className="pageHeadingActions"><Link className="secondaryButton" href="/warehouse/material-issues"><MaterialIcon name="arrow_back" size={18}/>返回领料台账</Link></div></header>;
  if (!initialData.reference.canControl) return <div className="businessPage mobileReceivingPage">{heading}<div className="emptyState"><MaterialIcon name="lock" size={30}/><b>当前角色无权执行生产领料</b><span>扫码只改变输入方式，后端领料权限仍是唯一可信边界。</span></div></div>;
  if (!issues.some((item) => ["DRAFT", "PARTIAL"].includes(item.status) && item.lines.some((line) => line.issuableQuantity > 0))) return <div className="businessPage mobileReceivingPage">{heading}<div className="emptyState"><MaterialIcon name="inventory_2" size={30}/><b>没有可扫码发料的生产领料单</b><span>请先从已下达生产订单生成领料需求，或检查是否已经全部发料。</span></div></div>;

  return <div className="businessPage mobileReceivingPage">{heading}
    <div className={`mobileNetworkState ${online ? "isOnline" : "isOffline"}`} role="status"><MaterialIcon name={online ? "wifi" : "wifi_off"} size={18}/><strong>{online ? "在线，可提交正式发料" : "当前离线，仅保存本机草稿"}</strong><span>请求编号 {draft.requestId || "初始化中"}</span></div>
    {saved ? <section className="mobileReceiptSuccess" aria-live="polite"><MaterialIcon name="task_alt" size={42}/><h3>{saved.issueNumber} 已正式发料</h3><p>{savedTxn ? `${savedTxn.componentMaterialCode} · ${savedTxn.locationCode} · 批次 ${savedTxn.lotNumber || "无批次"} · ${savedTxn.quantity}` : "后端已确认领料与库存扣减证据。"}</p><div className="mobileSuccessActions"><Link className="secondaryButton" href="/warehouse/material-issues">查看领料证据</Link><GsButton className="primaryButton" htmlType="button" onClick={reset}>继续下一笔</GsButton></div></section> : <form className="mobileReceivingFlow" onSubmit={submit}>
      <section className="mobileScanStep"><header><span>1</span><div><h3>扫描生产领料单</h3><p>支持原始单号或 `PI:` 前缀条码。</p></div></header><div className="mobileScanInput"><GsInput autoFocus aria-label="扫描生产领料单" value={draft.issueScan} onChange={(event) => { update({ issueScan: event.target.value, materialScan: "", stockScan: "", quantity: "" }); setAttempted({ issue: false, material: false, stock: false }); }} onPressEnter={confirmIssue}/><GsButton className="secondaryButton" htmlType="button" onClick={confirmIssue}>确认</GsButton></div>{attempted.issue && issueResolution.error ? <p className="formError">{issueResolution.error}</p> : null}{issueResolution.issue ? <div className="mobileScanMatch"><MaterialIcon name="check_circle" filled size={18}/><div><strong>{issueResolution.issue.issueNumber} · {issueResolution.issue.orderNumber}</strong><span>{issueResolution.issue.warehouseCode} · 待领组件 {issueResolution.issue.lines.filter((line) => line.issuableQuantity > 0).length} 项</span></div></div> : null}</section>
      <section className={`mobileScanStep ${issueResolution.issue ? "" : "isLocked"}`}><header><span>2</span><div><h3>扫描组件</h3><p>组件必须属于当前领料单且仍有可发数量。</p></div></header><div className="mobileScanInput"><GsInput id="issue-material-scan" aria-label="扫描领料组件" disabled={!issueResolution.issue} value={draft.materialScan} onChange={(event) => { update({ materialScan: event.target.value, stockScan: "", quantity: "" }); setAttempted((value) => ({ ...value, material: false, stock: false })); }} onPressEnter={confirmMaterial}/><GsButton className="secondaryButton" disabled={!issueResolution.issue} htmlType="button" onClick={confirmMaterial}>确认</GsButton></div>{attempted.material && lineResolution.error ? <p className="formError">{lineResolution.error}</p> : null}{lineResolution.line ? <div className="mobileScanMatch"><MaterialIcon name="check_circle" filled size={18}/><div><strong>{lineResolution.line.componentMaterialCode} · {lineResolution.line.componentMaterialName}</strong><span>本行可发 {lineResolution.line.issuableQuantity} {lineResolution.line.unit}</span></div></div> : null}</section>
      <section className={`mobileScanStep ${lineResolution.line ? "" : "isLocked"}`}><header><span>3</span><div><h3>扫描库存标签 / 批次</h3><p>`STOCK:` 精确定位库存；`LOT:` 仅在批次唯一时接受。</p></div></header><div className="mobileScanInput"><GsInput id="issue-stock-scan" aria-label="扫描库存标签或批次" disabled={!lineResolution.line} value={draft.stockScan} onChange={(event) => { update({ stockScan: event.target.value, quantity: "" }); setAttempted((value) => ({ ...value, stock: false })); }} onPressEnter={confirmStock}/><GsButton className="secondaryButton" disabled={!lineResolution.line} htmlType="button" onClick={confirmStock}>确认</GsButton></div>{attempted.stock && stockResolution.error ? <p className="formError">{stockResolution.error}</p> : null}{stockResolution.stock ? <div className="mobileScanMatch"><MaterialIcon name="check_circle" filled size={18}/><div><strong>{stockResolution.stock.locationCode} · 批次 {stockResolution.stock.lotNumber || "无批次"}</strong><span>实时可用 {stockResolution.stock.availableQuantity} · 库存版本 {stockResolution.stock.version}</span></div></div> : null}</section>
      <section className={`mobileScanStep ${stockResolution.stock ? "" : "isLocked"}`}><header><span>4</span><div><h3>确认正式发料</h3><p>后端将再次校验权限、领料行版本、库存版本和可用数量。</p></div></header><div className="formGrid two"><label className="formField"><span>本次数量<em>必填</em></span><GsInput aria-label="本次发料数量" type="number" min="0.000001" max={limit || undefined} step="0.000001" disabled={!stockResolution.stock} value={draft.quantity} onChange={(event) => update({ quantity: event.target.value })}/></label><label className="formField"><span>作业备注</span><GsInput aria-label="移动领料备注" maxLength={500} disabled={!stockResolution.stock} value={draft.note} onChange={(event) => update({ note: event.target.value })}/></label></div>{error ? <p className="formError" role="alert">{error}</p> : null}<footer className="mobileReceivingActions"><GsButton className="secondaryButton" htmlType="button" disabled={pending} onClick={reset}>清空草稿</GsButton><GsButton className="primaryButton" htmlType="submit" disabled={!stockResolution.stock || pending || !hydrated}>{pending ? "正在过账..." : online ? "确认并正式发料" : "离线，不能过账"}</GsButton></footer></section>
    </form>}
    <div className="ledgerInsight"><MaterialIcon name="verified_user" size={18}/>扫描不等于发料：只有后端返回领料单、库存流水与来源证据后，业务事实才生效。</div>
  </div>;
}
