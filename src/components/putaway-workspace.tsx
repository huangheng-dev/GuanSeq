"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { PutawayTask } from "@/lib/putaway-contracts";
import { resolvePutawaySource, resolvePutawayTarget } from "@/lib/putaway-scan";
import type { PutawayPageData } from "@/services/putaway-server-service";
import { loadPutawayData, submitPutaway } from "@/services/putaway-client-service";
import { GsButton, GsInput } from "./ui";
import { MaterialIcon } from "./material-icon";

const statusLabel = { OPEN: "待上架", COMPLETED: "已完成", CANCELLED: "已取消", REVERSED: "已冲回" } as const;
const statusTone = { OPEN: "warn", COMPLETED: "success", CANCELLED: "neutral", REVERSED: "danger" } as const;

export function PutawayWorkspace({ initialData }: { initialData: PutawayPageData }) {
  const pathname = usePathname();
  const scannerMode = pathname === "/warehouse/barcodes/scanning";
  const [references, setReferences] = useState(initialData.references);
  const [tasks, setTasks] = useState(initialData.tasks);
  const [sourceScan, setSourceScan] = useState("");
  const [targetScan, setTargetScan] = useState("");
  const [quantity, setQuantity] = useState("");
  const [sourceId, setSourceId] = useState("");
  const [targetId, setTargetId] = useState("");
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState("");
  const [error, setError] = useState(initialData.error ?? "");
  const [message, setMessage] = useState("");
  const [online, setOnline] = useState(true);
  const [createRequestId, setCreateRequestId] = useState("");
  const [actionRequestId, setActionRequestId] = useState("");

  useEffect(() => {
    const sync = () => setOnline(navigator.onLine);
    queueMicrotask(sync);
    window.addEventListener("online", sync);
    window.addEventListener("offline", sync);
    return () => { window.removeEventListener("online", sync); window.removeEventListener("offline", sync); };
  }, []);

  const source = references.sourceBalances.find((item) => item.id === sourceId) ?? null;
  const target = references.targetLocations.find((item) => item.id === targetId) ?? null;
  const compatibleTargets = useMemo(() => references.targetLocations.filter((item) => !source || item.warehouseId === source.warehouseId), [references.targetLocations, source]);
  const available = source ? Math.max(0, source.availableQuantity - source.reservedOpenQuantity) : 0;

  async function refresh() {
    const data = await loadPutawayData();
    setReferences(data.references);
    setTasks(data.tasks);
  }

  function scanSource() {
    setError("");
    const found = resolvePutawaySource(sourceScan, references);
    if (!found) { setSourceId(""); return setError("未识别到可上架库存。请扫描完整的 STOCK:库存UUID 标签。"); }
    setSourceId(found.id);
    setTargetId("");
    setTargetScan("");
    setQuantity(String(Math.max(0, found.availableQuantity - found.reservedOpenQuantity)));
    setMessage(`已识别 ${found.materialCode} · ${found.locationCode}`);
  }

  function scanTarget() {
    setError("");
    if (!source) return setError("请先扫描源库存标签。");
    const found = resolvePutawayTarget(targetScan, source.warehouseId, references);
    if (!found) { setTargetId(""); return setError("目标库位不存在、不是同仓正式存储库位，或条码不完整。"); }
    setTargetId(found.id);
    setMessage(`目标库位 ${found.code} 已确认`);
  }

  async function create(event: React.FormEvent) {
    event.preventDefault(); setError(""); setMessage("");
    if (!online) return setError("当前离线，未提交业务事实。恢复网络后请确认并重试。");
    if (!source || !target) return setError("请依次确认源库存和目标库位。");
    const numeric = Number(quantity);
    if (!Number.isFinite(numeric) || numeric <= 0 || numeric > available) return setError(`上架数量必须大于 0 且不超过 ${available} ${source.unit}。`);
    const requestId = createRequestId || `putaway-create-${crypto.randomUUID()}`;
    if (!createRequestId) setCreateRequestId(requestId);
    setPending("CREATE");
    try {
      const result = await submitPutaway({ action: "CREATE", sourceBalanceId: source.id, targetLocationId: target.id, quantity: numeric, expectedSourceBalanceVersion: source.version }, requestId);
      await refresh();
      setMessage(`${result.taskNumber} 已创建，库存尚未过账。`);
      setSourceScan(""); setTargetScan(""); setSourceId(""); setTargetId(""); setQuantity(""); setCreateRequestId("");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "创建上架任务失败"); }
    finally { setPending(""); }
  }

  async function act(task: PutawayTask, action: "COMPLETE" | "CANCEL" | "REVERSE") {
    setError(""); setMessage("");
    if (!online) return setError("当前离线，未提交业务事实。");
    if ((action === "CANCEL" || action === "REVERSE") && reason.trim().length < 4) return setError("取消或冲回原因至少填写 4 个字符。");
    const requestId = actionRequestId || `putaway-${action.toLowerCase()}-${crypto.randomUUID()}`;
    if (!actionRequestId) setActionRequestId(requestId);
    setPending(`${action}:${task.id}`);
    try {
      const payload: Record<string, unknown> = { action, id: task.id, expectedVersion: task.version };
      if (action === "COMPLETE") payload.expectedSourceBalanceVersion = task.sourceBalanceVersion;
      if (action === "CANCEL") payload.reason = reason.trim();
      if (action === "REVERSE") { payload.reason = reason.trim(); payload.expectedTargetBalanceVersion = task.targetBalanceVersion; }
      const result = await submitPutaway(payload, requestId);
      await refresh();
      setReason(""); setActionRequestId("");
      setMessage(`${result.taskNumber} ${action === "COMPLETE" ? "已完成上架并过账" : action === "CANCEL" ? "已取消" : "已用补偿流水冲回"}。`);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "上架任务操作失败"); }
    finally { setPending(""); }
  }

  const open = tasks.filter((item) => item.status === "OPEN").length;
  const completed = tasks.filter((item) => item.status === "COMPLETED").length;
  return <div className="businessPage putawayPage">
    <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="barcode_scanner" size={23} /></span><div><h2>{scannerMode ? "库内扫码作业" : "收货上架"}</h2><p>扫描源库存与目标库位，创建任务后再确认过账；数量守恒、动作可追溯。</p></div></div><div className="pageHeadingActions"><span className={`putawayOnline ${online ? "isOnline" : "isOffline"}`}>{online ? "在线可提交" : "离线未提交"}</span><Link className="secondaryButton" href="/warehouse/inventory/on-hand">库存现存量</Link></div></header>
    <section className="businessMetrics"><div><small>待上架库存</small><strong>{references.sourceBalances.length}</strong><em>收货/待检区合格库存</em></div><div><small>开放任务</small><strong className="businessMetricwarn">{open}</strong><em>已占用可上架额度</em></div><div><small>已完成</small><strong className="businessMetricinfo">{completed}</strong><em>源出库 + 目标入库</em></div><div><small>真实设备</small><strong>可选接入</strong><em>键盘与扫码枪使用同一契约</em></div></section>
    {(error || message) ? <div className={`putawayNotice ${error ? "putawayNoticeError" : "putawayNoticeSuccess"}`} role={error ? "alert" : "status"}>{error || message}</div> : null}
    <div className="putawayLayout"><form className="businessLedger putawayScanner" onSubmit={create}><header className="sectionTitleCompact"><div><h3>三步建立上架任务</h3><p>条码只负责精确识别，业务规则由后端确认。</p></div><span>请求幂等</span></header>
      <label><span>1. 源库存标签</span><div className="putawayScanRow"><GsInput value={sourceScan} onChange={(e) => setSourceScan(e.target.value)} placeholder="STOCK:库存余额UUID" aria-label="源库存标签" /><GsButton htmlType="button" onClick={scanSource}>识别</GsButton></div></label>
      <div className={`putawayResolved ${source ? "isResolved" : ""}`}>{source ? <><strong>{source.materialCode} · {source.materialName}</strong><span>{source.warehouseCode}/{source.locationCode} · 批次 {source.lotNumber || "无"}</span><small>可上架 {available} {source.unit}（可用 {source.availableQuantity}，开放任务占用 {source.reservedOpenQuantity}）</small></> : <span>等待扫描合格库存标签</span>}</div>
      <label><span>2. 目标正式库位</span><div className="putawayScanRow"><GsInput value={targetScan} onChange={(e) => setTargetScan(e.target.value)} placeholder="LOC:A-01-03 或 A-01-03" aria-label="目标库位条码" disabled={!source} /><GsButton htmlType="button" onClick={scanTarget} disabled={!source}>识别</GsButton></div></label>
      <div className={`putawayResolved ${target ? "isResolved" : ""}`}>{target ? <><strong>{target.code} · {target.name}</strong><span>{target.warehouseCode} · 正式存储库位</span></> : <span>{source ? `可选 ${compatibleTargets.length} 个同仓正式库位` : "先确认源库存"}</span>}</div>
      <label><span>3. 上架数量</span><GsInput type="number" min="0.000001" step="any" value={quantity} onChange={(e) => setQuantity(e.target.value)} aria-label="上架数量" disabled={!target} /></label>
      <GsButton intent="primary" htmlType="submit" loading={pending === "CREATE"} disabled={!online || !source || !target}>创建待上架任务</GsButton><small className="putawayBoundary">创建不会改库存；仓管确认“完成上架”后才产生两条不可变流水。</small>
    </form><aside className="businessLedger putawayGuide"><header className="sectionTitleCompact"><div><h3>正式版接入边界</h3><p>硬件输入与核心业务解耦。</p></div></header><ol><li><b>扫码枪 / 键盘</b><span>直接输入标准条码，不需要 Broker。</span></li><li><b>PDA / 移动应用</b><span>以后调用同一 BFF/API，无需重写库存规则。</span></li><li><b>自动输送与设备</b><span>可通过独立适配器提交，不能绕过授权与版本校验。</span></li></ol></aside></div>
    <section className="businessLedger putawayTasks"><header className="sectionTitleCompact"><div><h3>任务与过账证据</h3><p>原流水不覆盖；错误完成只能填写原因并形成反向补偿流水。</p></div><GsInput value={reason} onChange={(e) => setReason(e.target.value)} placeholder="取消/冲回原因（至少 4 字）" aria-label="取消或冲回原因" /></header>
      <div className="putawayTaskList">{tasks.length ? tasks.map((task) => <article key={task.id} className="putawayTask"><div className="putawayTaskMain"><span><strong>{task.taskNumber}</strong><em className={`businessStatus businessStatus${statusTone[task.status]}`}>{statusLabel[task.status]}</em></span><h4>{task.materialCode} · {task.materialName}</h4><p>{task.sourceWarehouseCode}/{task.sourceLocationCode} → {task.targetLocationCode} · {task.quantity} {task.unit} · 批次 {task.lotNumber || "无"}</p><small>{task.createdByUsername} · {new Date(task.createdAt).toLocaleString("zh-CN")} · v{task.version}</small></div><div className="putawayEvidence"><span><b>源流水</b>{task.sourceOutMovementNumber ?? "待过账"}</span><span><b>目标流水</b>{task.targetInMovementNumber ?? "待过账"}</span>{task.reversalReason ? <span><b>冲回原因</b>{task.reversalReason}</span> : task.cancellationReason ? <span><b>取消原因</b>{task.cancellationReason}</span> : null}</div><div className="putawayActions">{task.status === "OPEN" ? <><GsButton intent="primary" htmlType="button" loading={pending === `COMPLETE:${task.id}`} onClick={() => act(task, "COMPLETE")}>完成上架</GsButton><GsButton intent="danger" htmlType="button" loading={pending === `CANCEL:${task.id}`} onClick={() => act(task, "CANCEL")}>取消</GsButton></> : task.status === "COMPLETED" ? <GsButton intent="danger" htmlType="button" loading={pending === `REVERSE:${task.id}`} onClick={() => act(task, "REVERSE")}>冲回</GsButton> : null}</div></article>) : <div className="businessEmptyState"><MaterialIcon name="inventory_2" size={30} /><strong>还没有上架任务</strong><p>扫描源库存与目标库位即可创建第一条任务。</p></div>}</div>
    </section>
  </div>;
}
