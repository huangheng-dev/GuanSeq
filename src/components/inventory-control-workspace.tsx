"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { StockCountTask, TransferTask } from "@/lib/inventory-control-contracts";
import { resolveInventoryControlBalance, resolveInventoryControlTarget } from "@/lib/inventory-control-scan";
import type { InventoryControlPageData } from "@/services/inventory-control-server-service";
import { loadInventoryControlData, submitInventoryControl } from "@/services/inventory-control-client-service";
import { GsButton, GsInput } from "./ui";
import { MaterialIcon } from "./material-icon";

const transferLabels = { OPEN: "待调拨", COMPLETED: "已完成", CANCELLED: "已取消", REVERSED: "已冲回" } as const;
const countLabels = { OPEN: "待实盘", COUNTED: "待审批", APPROVED: "已审批", CANCELLED: "已取消", REVERSED: "已冲回" } as const;
const tones = { OPEN: "warn", COUNTED: "info", COMPLETED: "success", APPROVED: "success", CANCELLED: "neutral", REVERSED: "danger" } as const;

export function InventoryControlWorkspace({ initialData }: { initialData: InventoryControlPageData }) {
  const pathname = usePathname(); const countMode = pathname === "/warehouse/counts";
  const [references, setReferences] = useState(initialData.references);
  const [transfers, setTransfers] = useState(initialData.transfers); const [counts, setCounts] = useState(initialData.counts);
  const [sourceScan, setSourceScan] = useState(""); const [targetScan, setTargetScan] = useState("");
  const [selectedBalanceId, setSelectedBalanceId] = useState(""); const [targetId, setTargetId] = useState("");
  const [quantity, setQuantity] = useState(""); const [transferReason, setTransferReason] = useState("");
  const [actionReason, setActionReason] = useState(""); const [countInputs, setCountInputs] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(""); const [error, setError] = useState(initialData.error ?? ""); const [message, setMessage] = useState("");
  const [online, setOnline] = useState(true); const requestIds = useRef<Record<string, string>>({});

  useEffect(() => { const sync = () => setOnline(navigator.onLine); queueMicrotask(sync); window.addEventListener("online", sync);
    window.addEventListener("offline", sync); return () => { window.removeEventListener("online", sync); window.removeEventListener("offline", sync); }; }, []);

  const balance = references.balances.find((item) => item.id === selectedBalanceId) ?? null;
  const target = references.targetLocations.find((item) => item.id === targetId) ?? null;
  const transferable = useMemo(() => references.balances.filter((item) => item.qualityStatus === "AVAILABLE" && item.locationType === "STORAGE"
    && item.availableQuantity > item.reservedTransferQuantity && !item.activeCount), [references.balances]);
  const countable = useMemo(() => references.balances.filter((item) => !item.activeCount && item.reservedTransferQuantity === 0), [references.balances]);
  const available = balance ? Math.max(0, balance.availableQuantity - balance.reservedTransferQuantity) : 0;

  async function refresh() { const data = await loadInventoryControlData(); setReferences(data.references); setTransfers(data.transfers); setCounts(data.counts); }
  function requestId(key: string) { return requestIds.current[key] ?? (requestIds.current[key] = `inventory-control-${key}-${crypto.randomUUID()}`); }
  function succeed(key: string, text: string) { delete requestIds.current[key]; setMessage(text); setError(""); }
  function resolveBalance() {
    setError(""); const found = resolveInventoryControlBalance(sourceScan, references);
    if (!found) { setSelectedBalanceId(""); return setError("未识别库存。请扫描完整的 STOCK:库存余额UUID 标签。"); }
    if (!countMode && (found.qualityStatus !== "AVAILABLE" || found.locationType !== "STORAGE" || found.activeCount || found.availableQuantity <= found.reservedTransferQuantity))
      return setError("该库存不是可调拨的正式库位合格库存，或正在盘点/已被开放任务占用。");
    if (countMode && (found.activeCount || found.reservedTransferQuantity > 0)) return setError("该库存已有开放盘点或调拨任务，不能重复建立盘点快照。");
    setSelectedBalanceId(found.id); setTargetId(""); setTargetScan(""); setQuantity(String(Math.max(0, found.availableQuantity - found.reservedTransferQuantity)));
    setMessage(`已识别 ${found.materialCode} · ${found.warehouseCode}/${found.locationCode}`);
  }
  function resolveTarget() {
    setError(""); if (!balance) return setError("请先识别源库存。");
    const found = resolveInventoryControlTarget(targetScan, balance.warehouseId, references);
    if (!found || found.id === balance.locationId) { setTargetId(""); return setError("目标必须是同仓库内不同的活动正式存储库位。"); }
    setTargetId(found.id); setMessage(`目标库位 ${found.code} 已确认`);
  }

  async function createTransfer(event: React.FormEvent) {
    event.preventDefault(); setError(""); setMessage(""); if (!online) return setError("当前离线，未提交业务事实。");
    if (!balance || !target) return setError("请依次确认源库存与目标库位。"); const numeric = Number(quantity);
    if (!Number.isFinite(numeric) || numeric <= 0 || numeric > available) return setError(`调拨数量必须大于 0 且不超过 ${available} ${balance.unit}。`);
    if (transferReason.trim().length < 4) return setError("调拨原因至少填写 4 个字符。"); const key = "transfer-create"; setPending(key);
    try { const result = await submitInventoryControl({ action: "TRANSFER_CREATE", sourceBalanceId: balance.id, targetLocationId: target.id,
      quantity: numeric, expectedSourceBalanceVersion: balance.version, reason: transferReason.trim() }, requestId(key)); await refresh();
      succeed(key, `${"taskNumber" in result ? result.taskNumber : "调拨任务"} 已创建，库存尚未过账。`);
      setSourceScan(""); setTargetScan(""); setSelectedBalanceId(""); setTargetId(""); setQuantity(""); setTransferReason("");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "创建调拨任务失败"); } finally { setPending(""); }
  }
  async function actTransfer(task: TransferTask, action: "COMPLETE" | "CANCEL" | "REVERSE") {
    setError(""); setMessage(""); if (!online) return setError("当前离线，未提交业务事实。");
    if ((action === "CANCEL" || action === "REVERSE") && actionReason.trim().length < 4) return setError("取消或冲回原因至少填写 4 个字符。");
    const key = `transfer-${action.toLowerCase()}-${task.id}`; setPending(key);
    try { const payload: Record<string, unknown> = { action: `TRANSFER_${action}`, id: task.id, expectedVersion: task.version };
      if (action === "COMPLETE") payload.expectedSourceBalanceVersion = task.sourceBalanceVersion;
      if (action === "CANCEL") payload.reason = actionReason.trim();
      if (action === "REVERSE") { payload.reason = actionReason.trim(); payload.expectedTargetBalanceVersion = task.targetBalanceVersion; }
      await submitInventoryControl(payload, requestId(key)); await refresh(); succeed(key, `${task.taskNumber} ${action === "COMPLETE" ? "已完成调拨并过账" : action === "CANCEL" ? "已取消" : "已用补偿流水冲回"}。`); setActionReason("");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "调拨操作失败"); } finally { setPending(""); }
  }

  async function createCount() {
    setError(""); setMessage(""); if (!online) return setError("当前离线，未提交业务事实。"); if (!balance) return setError("请先识别需要盘点的库存。");
    const key = "count-create"; setPending(key); try { const result = await submitInventoryControl({ action: "COUNT_CREATE", balanceId: balance.id,
      expectedBalanceVersion: balance.version }, requestId(key)); await refresh(); succeed(key, `${"countNumber" in result ? result.countNumber : "盘点任务"} 已建立账面快照。`);
      setSourceScan(""); setSelectedBalanceId(""); } catch (cause) { setError(cause instanceof Error ? cause.message : "创建盘点失败"); } finally { setPending(""); }
  }
  async function actCount(task: StockCountTask, action: "RECORD" | "APPROVE" | "CANCEL" | "REVERSE") {
    setError(""); setMessage(""); if (!online) return setError("当前离线，未提交业务事实。");
    if (actionReason.trim().length < 4) return setError("实盘说明、审批意见、取消或冲回原因至少填写 4 个字符。");
    const numeric = Number(countInputs[task.id]); if (action === "RECORD" && (!Number.isFinite(numeric) || numeric < 0)) return setError("实盘数量必须是大于等于 0 的数字。");
    const key = `count-${action.toLowerCase()}-${task.id}`; setPending(key);
    try { const payload: Record<string, unknown> = { action: `COUNT_${action}`, id: task.id, expectedVersion: task.version };
      if (action === "RECORD") { payload.expectedBalanceVersion = task.currentBalanceVersion; payload.countedQuantity = numeric; payload.note = actionReason.trim(); }
      if (action === "APPROVE") { payload.expectedBalanceVersion = task.currentBalanceVersion; payload.comment = actionReason.trim(); }
      if (action === "CANCEL") payload.reason = actionReason.trim();
      if (action === "REVERSE") { payload.expectedBalanceVersion = task.currentBalanceVersion; payload.reason = actionReason.trim(); }
      await submitInventoryControl(payload, requestId(key)); await refresh(); succeed(key, `${task.countNumber} ${action === "RECORD" ? "已录入实盘数量" : action === "APPROVE" ? "差异已审批过账" : action === "CANCEL" ? "已取消" : "调整已补偿冲回"}。`);
      setActionReason(""); setCountInputs((current) => ({ ...current, [task.id]: "" }));
    } catch (cause) { setError(cause instanceof Error ? cause.message : "盘点操作失败"); } finally { setPending(""); }
  }

  return <div className="businessPage inventoryControlPage">
    <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name={countMode ? "fact_check" : "swap_horiz"} size={23} /></span>
      <div><h2>{countMode ? "盘点作业" : "库存调拨"}</h2><p>{countMode ? "冻结账面快照，录入实盘数量，差异审批后才形成库存调整。" : "同仓正式库位间受控移动，完成时以成对流水保证数量守恒。"}</p></div></div>
      <div className="pageHeadingActions"><span className={`putawayOnline ${online ? "isOnline" : "isOffline"}`}>{online ? "在线可提交" : "离线未提交"}</span>
        <Link className="secondaryButton" href={countMode ? "/warehouse/inventory-operations/transfers" : "/warehouse/counts"}>{countMode ? "库存调拨" : "盘点作业"}</Link></div></header>
    <section className="businessMetrics"><div><small>可作业库存</small><strong>{countMode ? countable.length : transferable.length}</strong><em>活动库存余额</em></div>
      <div><small>{countMode ? "待实盘" : "开放任务"}</small><strong className="businessMetricwarn">{countMode ? counts.filter((item) => item.status === "OPEN").length : transfers.filter((item) => item.status === "OPEN").length}</strong><em>尚未改变库存</em></div>
      <div><small>{countMode ? "待审批" : "已完成"}</small><strong className="businessMetricinfo">{countMode ? counts.filter((item) => item.status === "COUNTED").length : transfers.filter((item) => item.status === "COMPLETED").length}</strong><em>{countMode ? "等待差异确认" : "成对流水已过账"}</em></div>
      <div><small>并发保护</small><strong>版本快照</strong><em>冲突不静默覆盖</em></div></section>
    {(error || message) ? <div className={`putawayNotice ${error ? "putawayNoticeError" : "putawayNoticeSuccess"}`} role={error ? "alert" : "status"}>{error || message}</div> : null}
    <div className="inventoryControlLayout"><section className="businessLedger inventoryControlCreator"><header className="sectionTitleCompact"><div><h3>{countMode ? "建立盘点快照" : "建立调拨任务"}</h3>
      <p>条码只做精确识别，权限与库存规则由后端确认。</p></div><span>请求幂等</span></header>
      <label><span>库存标签</span><div className="putawayScanRow"><GsInput value={sourceScan} onChange={(event) => setSourceScan(event.target.value)} placeholder="STOCK:库存余额UUID" aria-label={countMode ? "盘点库存标签" : "调拨源库存标签"} />
        <GsButton htmlType="button" onClick={resolveBalance}>识别</GsButton></div></label>
      <div className={`putawayResolved ${balance ? "isResolved" : ""}`}>{balance ? <><strong>{balance.materialCode} · {balance.materialName}</strong><span>{balance.warehouseCode}/{balance.locationCode} · 批次 {balance.lotNumber || "无"}</span>
        <small>现存 {balance.onHandQuantity}，可用 {balance.availableQuantity}，调拨占用 {balance.reservedTransferQuantity} {balance.unit}</small></> : <span>等待扫描正式库存标签</span>}</div>
      {countMode ? <GsButton intent="primary" htmlType="button" onClick={createCount} loading={pending === "count-create"} disabled={!online || !balance}>创建盘点快照</GsButton> :
        <form onSubmit={createTransfer} className="inventoryControlTransferForm"><label><span>目标库位</span><div className="putawayScanRow"><GsInput value={targetScan} onChange={(event) => setTargetScan(event.target.value)} placeholder="LOC:A-01-04 或 A-01-04" aria-label="调拨目标库位" disabled={!balance} />
          <GsButton htmlType="button" onClick={resolveTarget} disabled={!balance}>识别</GsButton></div></label>
          <div className={`putawayResolved ${target ? "isResolved" : ""}`}>{target ? <><strong>{target.code} · {target.name}</strong><span>{target.warehouseCode} · 同仓正式库位</span></> : <span>等待确认不同的同仓正式库位</span>}</div>
          <label><span>调拨数量</span><GsInput type="number" min="0.000001" step="any" value={quantity} onChange={(event) => setQuantity(event.target.value)} aria-label="调拨数量" disabled={!target} /></label>
          <label><span>调拨原因</span><GsInput value={transferReason} onChange={(event) => setTransferReason(event.target.value)} placeholder="至少 4 个字符" aria-label="调拨原因" /></label>
          <GsButton intent="primary" htmlType="submit" loading={pending === "transfer-create"} disabled={!online || !balance || !target}>创建待调拨任务</GsButton></form>}
      <small className="putawayBoundary">{countMode ? "创建只保存快照；审批前库存不会被差异覆盖。" : "创建只占用可调拨额度；完成确认后才过账双向流水。"}</small></section>
      <aside className="businessLedger inventoryControlRules"><header className="sectionTitleCompact"><div><h3>控制边界</h3><p>适合普通制造企业的最小充分流程。</p></div></header>
        <ol><li><b>任务与库存分离</b><span>任务不成为第二套库存。</span></li><li><b>并发显式失败</b><span>版本变化后取消重建，不覆盖新事实。</span></li><li><b>错误可恢复</b><span>只追加补偿流水，原证据永久保留。</span></li></ol></aside></div>
    <section className="businessLedger inventoryControlTasks"><header className="sectionTitleCompact"><div><h3>{countMode ? "盘点任务与差异证据" : "调拨任务与流水证据"}</h3>
      <p>{countMode ? "盘盈入库、盘亏出库；零差异不产生流水。" : "源出库与目标入库必须在同一事务内完成。"}</p></div>
      <GsInput value={actionReason} onChange={(event) => setActionReason(event.target.value)} placeholder={countMode ? "实盘说明 / 审批 / 取消 / 冲回原因" : "取消 / 冲回原因（至少 4 字）"} aria-label="动作原因" /></header>
      <div className="inventoryControlTaskList">{countMode ? (counts.length ? counts.map((task) => <CountCard key={task.id} task={task} pending={pending} value={countInputs[task.id] ?? ""}
        onValue={(value) => setCountInputs((current) => ({ ...current, [task.id]: value }))} onAction={actCount} />) : <div className="putawayEmpty">暂无盘点任务。扫描库存后建立第一张账面快照。</div>) :
        (transfers.length ? transfers.map((task) => <TransferCard key={task.id} task={task} pending={pending} onAction={actTransfer} />) : <div className="putawayEmpty">暂无调拨任务。扫描源库存与目标库位创建第一张任务。</div>)}</div></section>
  </div>;
}

function TransferCard({ task, pending, onAction }: { task: TransferTask; pending: string; onAction: (task: TransferTask, action: "COMPLETE" | "CANCEL" | "REVERSE") => void }) {
  return <article className="putawayTask"><div className="putawayTaskMain"><span><strong>{task.taskNumber}</strong><em className={`businessStatus businessStatus${tones[task.status]}`}>{transferLabels[task.status]}</em></span>
    <h4>{task.materialCode} · {task.materialName}</h4><p>{task.sourceWarehouseCode}/{task.sourceLocationCode} → {task.targetLocationCode} · {task.quantity} {task.unit}</p>
    <small>{task.transferReason} · {task.createdByUsername} · v{task.version}</small></div><div className="putawayEvidence"><span>出库 {task.sourceOutMovementNumber ?? "待过账"}</span><span>入库 {task.targetInMovementNumber ?? "待过账"}</span></div>
    <div className="putawayActions">{task.status === "OPEN" ? <><GsButton intent="primary" loading={pending === `transfer-complete-${task.id}`} onClick={() => onAction(task, "COMPLETE")}>完成调拨</GsButton>
      <GsButton loading={pending === `transfer-cancel-${task.id}`} onClick={() => onAction(task, "CANCEL")}>取消</GsButton></> : null}
      {task.status === "COMPLETED" ? <GsButton intent="danger" loading={pending === `transfer-reverse-${task.id}`} onClick={() => onAction(task, "REVERSE")}>补偿冲回</GsButton> : null}</div></article>;
}

function CountCard({ task, pending, value, onValue, onAction }: { task: StockCountTask; pending: string; value: string; onValue: (value: string) => void;
  onAction: (task: StockCountTask, action: "RECORD" | "APPROVE" | "CANCEL" | "REVERSE") => void }) {
  const difference = task.differenceQuantity == null ? "待实盘" : `${task.differenceQuantity > 0 ? "+" : ""}${task.differenceQuantity} ${task.unit}`;
  return <article className="putawayTask countTask"><div className="putawayTaskMain"><span><strong>{task.countNumber}</strong><em className={`businessStatus businessStatus${tones[task.status]}`}>{countLabels[task.status]}</em></span>
    <h4>{task.materialCode} · {task.materialName}</h4><p>{task.warehouseCode}/{task.locationCode} · 账面 {task.bookOnHand} {task.unit} · 差异 {difference}</p>
    <small>快照 v{task.snapshotBalanceVersion} · 当前 v{task.currentBalanceVersion} · {task.createdByUsername}</small></div>
    <div className="countEntry">{task.status === "OPEN" ? <GsInput type="number" min="0" step="any" value={value} onChange={(event) => onValue(event.target.value)} placeholder="实盘数量" aria-label={`${task.countNumber} 实盘数量`} /> :
      <span>实盘 <b>{task.countedQuantity ?? "—"}</b> {task.unit}</span>}{task.adjustmentMovementNumber ? <small>{task.adjustmentMovementType} · {task.adjustmentMovementNumber}</small> : null}</div>
    <div className="putawayActions">{task.status === "OPEN" ? <><GsButton intent="primary" loading={pending === `count-record-${task.id}`} onClick={() => onAction(task, "RECORD")}>录入实盘</GsButton>
      <GsButton onClick={() => onAction(task, "CANCEL")}>取消</GsButton></> : null}{task.status === "COUNTED" ? <><GsButton intent="primary" loading={pending === `count-approve-${task.id}`} onClick={() => onAction(task, "APPROVE")}>审批差异</GsButton>
      <GsButton onClick={() => onAction(task, "CANCEL")}>取消</GsButton></> : null}{task.status === "APPROVED" && task.adjustmentMovementId ? <GsButton intent="danger" loading={pending === `count-reverse-${task.id}`} onClick={() => onAction(task, "REVERSE")}>补偿冲回</GsButton> : null}</div></article>;
}
