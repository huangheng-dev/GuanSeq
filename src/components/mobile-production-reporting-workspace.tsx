"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import type { OperationTaskRecord, ProductionWorkReportRecord } from "@/lib/contracts";
import { deriveMobileReportingAction, resolveMobileOperationTaskScan, resolveMobileOperatorScan } from "@/lib/mobile-production-reporting";
import { loadMobileProductionReportingData, submitMobileProductionReportingMutation } from "@/services/mobile-production-reporting-client-service";
import type { MobileProductionReportingPageData } from "@/services/mobile-production-reporting-server-service";
import { MaterialIcon } from "./material-icon";
import { GsButton, GsInput } from "./ui";

const STORAGE_KEY = "guanseq.mobile-production-reporting.draft.v1";
type Draft = { taskScan: string; operatorScan: string; shiftName: string; quantity: string; note: string; requestId: string };
const blankDraft = (): Draft => ({ taskScan: "", operatorScan: "", shiftName: "白班", quantity: "", note: "", requestId: "" });
const nextRequestId = () => `mobile-production-reporting-${crypto.randomUUID()}`;

function safeDraft(value: unknown): Draft | null {
  if (!value || typeof value !== "object") return null;
  const item = value as Partial<Draft>;
  if (typeof item.taskScan !== "string" || typeof item.operatorScan !== "string") return null;
  return { taskScan: item.taskScan, operatorScan: item.operatorScan,
    shiftName: typeof item.shiftName === "string" && item.shiftName ? item.shiftName : "白班",
    quantity: typeof item.quantity === "string" ? item.quantity : "", note: typeof item.note === "string" ? item.note : "",
    requestId: typeof item.requestId === "string" && item.requestId.startsWith("mobile-production-reporting-") ? item.requestId : "" };
}

const actionLabels = { START: "正式开工", COMPLETE: "登记工序完工", REPORT: "提交生产报工并送检", WAIT: "等待其他工序", DONE: "订单已无可报数量" } as const;

export function MobileProductionReportingWorkspace({ initialData }: { initialData: MobileProductionReportingPageData }) {
  const [tasks, setTasks] = useState(initialData.tasks);
  const [orders, setOrders] = useState(initialData.orders);
  const [draft, setDraft] = useState<Draft>(blankDraft);
  const [hydrated, setHydrated] = useState(false);
  const [online, setOnline] = useState(true);
  const [attempted, setAttempted] = useState({ task: false, operator: false });
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [evidence, setEvidence] = useState("");
  const [savedReport, setSavedReport] = useState<ProductionWorkReportRecord | null>(null);
  const skipNextPersist = useRef(false);
  const operator = initialData.operator;
  const taskResolution = useMemo(() => resolveMobileOperationTaskScan(tasks, draft.taskScan), [draft.taskScan, tasks]);
  const operatorResolution = useMemo(() => operator ? resolveMobileOperatorScan(draft.operatorScan, operator.username)
    : { username: null, error: "当前登录人员信息不可用。" }, [draft.operatorScan, operator]);
  const stage = useMemo(() => taskResolution.task
    ? deriveMobileReportingAction(taskResolution.task, orders, tasks)
    : { action: "WAIT" as const, order: null, error: "请先确认工序任务。" }, [orders, taskResolution.task, tasks]);
  const quantityLimit = stage.action === "REPORT" ? stage.order?.reportableQuantity ?? 0 : taskResolution.task?.plannedQuantity ?? 0;

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setOnline(navigator.onLine);
      try {
        const stored = localStorage.getItem(STORAGE_KEY);
        const restored = stored ? safeDraft(JSON.parse(stored)) : null;
        setDraft({ ...(restored ?? blankDraft()), requestId: restored?.requestId || nextRequestId() });
        setAttempted({ task: Boolean(restored?.taskScan), operator: Boolean(restored?.operatorScan) });
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

  function update(patch: Partial<Draft>) { setDraft((current) => ({ ...current, ...patch })); setError(""); setEvidence(""); }
  function confirmTask(event: FormEvent) {
    event.preventDefault(); setAttempted((value) => ({ ...value, task: true }));
    if (taskResolution.task) window.setTimeout(() => document.getElementById("mobile-reporting-operator")?.focus(), 0);
  }
  function confirmOperator(event: FormEvent) {
    event.preventDefault(); setAttempted((value) => ({ ...value, operator: true }));
    if (operatorResolution.username && !draft.quantity) update({ quantity: String(quantityLimit) });
  }
  function reset() {
    skipNextPersist.current = true; localStorage.removeItem(STORAGE_KEY);
    setDraft({ ...blankDraft(), requestId: nextRequestId() }); setAttempted({ task: false, operator: false });
    setSavedReport(null); setEvidence(""); setError("");
  }

  async function refreshAfterAction(task: OperationTaskRecord) {
    setTasks((current) => current.map((item) => item.id === task.id ? task : item));
    try {
      const refreshed = await loadMobileProductionReportingData();
      setTasks(refreshed.tasks); setOrders(refreshed.orders);
      const currentTask = refreshed.tasks.find((item) => item.id === task.id) ?? task;
      const nextStage = deriveMobileReportingAction(currentTask, refreshed.orders, refreshed.tasks);
      setDraft((current) => ({ ...current, quantity: nextStage.action === "REPORT"
        ? String(nextStage.order?.reportableQuantity ?? "") : nextStage.action === "COMPLETE" ? String(currentTask.plannedQuantity) : "",
        requestId: nextRequestId() }));
    } catch {
      setError("工序动作已经成功，但最新订单状态刷新失败；请刷新页面后继续，避免使用旧版本提交。");
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); setAttempted({ task: true, operator: true }); setError(""); setEvidence("");
    const task = taskResolution.task, username = operatorResolution.username, order = stage.order;
    if (!task || !username || !order) return;
    if (stage.action === "WAIT" || stage.action === "DONE") { setError(stage.error); return; }
    const quantity = Number(draft.quantity);
    if (stage.action !== "START" && (!Number.isFinite(quantity) || quantity <= 0 || quantity > quantityLimit)) {
      setError(`数量必须大于 0 且不超过当前上限 ${quantityLimit} ${task.unit}。`); return;
    }
    if (!draft.shiftName.trim()) { setError("请选择或填写当前班次。") ; return; }
    if (!online) { setError("当前离线：草稿已保存在本机，但没有创建工序动作或生产报工事实。恢复网络后请重新确认提交。"); return; }
    setPending(true);
    try {
      const result = stage.action === "REPORT"
        ? await submitMobileProductionReportingMutation({ kind: "WORK_REPORT", orderId: order.id, operationTaskId: task.id,
          quantity, shiftName: draft.shiftName.trim(), note: draft.note.trim() || null,
          expectedOrderVersion: order.version, operatorBadge: username }, draft.requestId)
        : await submitMobileProductionReportingMutation({ kind: "TASK_ACTION", id: task.id, action: stage.action,
          expectedVersion: task.version, shiftName: draft.shiftName.trim(), completedQuantity: stage.action === "COMPLETE" ? quantity : null,
          note: draft.note.trim() || null, operatorBadge: username }, draft.requestId);
      if (result.kind === "WORK_REPORT") {
        skipNextPersist.current = true; localStorage.removeItem(STORAGE_KEY); setSavedReport(result.report);
      } else {
        setEvidence(`${result.task.taskNumber} 已${result.task.status === "IN_PROGRESS" ? "正式开工" : "登记工序完工"}，来源 MOBILE_SCAN。`);
        await refreshAfterAction(result.task);
      }
    } catch (reason) { setError(reason instanceof Error ? reason.message : "扫码报工失败；草稿和请求编号已保留，可核对后重试。"); }
    finally { setPending(false); }
  }

  const heading = <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="barcode_scanner" size={23}/></span><div><h2>生产扫码报工</h2><p>扫描工序和当前人员，连续复用正式工序执行、生产报工与完工检验事实。</p></div></div><div className="pageHeadingActions"><Link className="secondaryButton" href="/production/work-orders/operations"><MaterialIcon name="arrow_back" size={18}/>工序任务</Link><Link className="secondaryButton" href="/production/reporting/reports">报工台账</Link></div></header>;
  if (!initialData.canControl || !operator) return <div className="businessPage mobileReceivingPage">{heading}<div className="emptyState"><MaterialIcon name="lock" size={30}/><b>当前账号不能执行生产扫码报工</b><span>{initialData.error ?? "扫码只改变输入方式，后端生产权限仍是唯一可信边界。"}</span></div></div>;
  if (!tasks.length) return <div className="businessPage mobileReceivingPage">{heading}<div className="emptyState"><MaterialIcon name="precision_manufacturing" size={30}/><b>没有可扫描的工序任务</b><span>请先下达包含有效工艺路线的生产订单。</span></div></div>;

  return <div className="businessPage mobileReceivingPage">{heading}
    <div className={`mobileNetworkState ${online ? "isOnline" : "isOffline"}`} role="status"><MaterialIcon name={online ? "wifi" : "wifi_off"} size={18}/><strong>{online ? "在线，可提交正式工序与报工事实" : "当前离线，仅保存本机草稿"}</strong><span>请求编号 {draft.requestId || "初始化中"}</span></div>
    {savedReport ? <section className="mobileReceiptSuccess" aria-live="polite"><MaterialIcon name="task_alt" size={42}/><h3>{savedReport.reportNumber} 已正式报工并送检</h3><p>{savedReport.operationTaskNumber} · {savedReport.orderNumber} · {savedReport.reportedQuantity} {savedReport.unit} · {savedReport.inspectionNumber}</p><div className="mobileSuccessActions"><Link className="secondaryButton" href="/production/reporting/reports">查看报工证据</Link><GsButton className="primaryButton" htmlType="button" onClick={reset}>继续下一笔</GsButton></div></section> : <form className="mobileReceivingFlow" onSubmit={submit}>
      <section className="mobileScanStep"><header><span>1</span><div><h3>扫描工序任务</h3><p>支持任务号或 `OT:` 前缀的精确任务标签。</p></div></header><div className="mobileScanInput"><GsInput autoFocus aria-label="扫描工序任务" value={draft.taskScan} onChange={(event) => { update({ taskScan: event.target.value, quantity: "" }); setAttempted({ task: false, operator: false }); }} onPressEnter={confirmTask}/><GsButton className="secondaryButton" htmlType="button" onClick={confirmTask}>确认</GsButton></div>{attempted.task && taskResolution.error ? <p className="formError">{taskResolution.error}</p> : null}{taskResolution.task ? <div className="mobileScanMatch"><MaterialIcon name="check_circle" filled size={18}/><div><strong>{taskResolution.task.taskNumber} · {taskResolution.task.operationName}</strong><span>{taskResolution.task.orderNumber} · {taskResolution.task.workCenterCode} · {taskResolution.task.status}</span></div></div> : null}</section>
      <section className={`mobileScanStep ${taskResolution.task ? "" : "isLocked"}`}><header><span>2</span><div><h3>扫描当前操作人员</h3><p>人员标签必须匹配已认证账号 {operator.displayName}（{operator.username}）。</p></div></header><div className="mobileScanInput"><GsInput id="mobile-reporting-operator" aria-label="扫描当前操作人员" disabled={!taskResolution.task} value={draft.operatorScan} onChange={(event) => { update({ operatorScan: event.target.value }); setAttempted((value) => ({ ...value, operator: false })); }} onPressEnter={confirmOperator}/><GsButton className="secondaryButton" disabled={!taskResolution.task} htmlType="button" onClick={confirmOperator}>确认</GsButton></div>{attempted.operator && operatorResolution.error ? <p className="formError">{operatorResolution.error}</p> : null}{operatorResolution.username ? <div className="mobileScanMatch"><MaterialIcon name="verified_user" filled size={18}/><div><strong>{operator.displayName}</strong><span>认证账号 {operatorResolution.username}，后端将再次核验。</span></div></div> : null}</section>
      <section className={`mobileScanStep ${operatorResolution.username ? "" : "isLocked"}`}><header><span>3</span><div><h3>{actionLabels[stage.action]}</h3><p>{stage.action === "START" ? "开工后生产订单进入执行，继续使用新请求编号登记完工。" : stage.action === "COMPLETE" ? "登记当前工序完工；全部工序完成后扫描最后工序提交正式报工。" : stage.action === "REPORT" ? "报工后自动生成完工检验，检验前不会增加成品库存。" : stage.error}</p></div></header><div className="formGrid two"><label className="formField"><span>班次<em>必填</em></span><GsInput aria-label="移动报工班次" maxLength={80} disabled={!operatorResolution.username} value={draft.shiftName} onChange={(event) => update({ shiftName: event.target.value })}/></label><label className="formField"><span>{stage.action === "REPORT" ? "本次报工数量" : "工序完工数量"}{stage.action !== "START" ? <em>必填</em> : null}</span><GsInput aria-label="移动报工数量" type="number" min="0.000001" max={quantityLimit || undefined} step="0.000001" disabled={!operatorResolution.username || stage.action === "START"} value={stage.action === "START" ? "" : draft.quantity} onChange={(event) => update({ quantity: event.target.value })}/></label><label className="formField formFieldFull"><span>作业备注</span><GsInput aria-label="移动报工备注" maxLength={500} disabled={!operatorResolution.username} value={draft.note} onChange={(event) => update({ note: event.target.value })}/></label></div>{evidence ? <p className="mobileScanMatch" role="status">{evidence}</p> : null}{error ? <p className="formError" role="alert">{error}</p> : null}<footer className="mobileReceivingActions"><GsButton className="secondaryButton" htmlType="button" disabled={pending} onClick={reset}>清空草稿</GsButton><GsButton className="primaryButton" htmlType="submit" disabled={!operatorResolution.username || pending || !hydrated || ["WAIT", "DONE"].includes(stage.action)}>{pending ? "正在提交..." : online ? actionLabels[stage.action] : "离线，不能提交"}</GsButton></footer></section>
    </form>}
    <div className="ledgerInsight"><MaterialIcon name="verified_user" size={18}/>扫描只负责识别：只有后端返回工序事件或报工单与完工检验号后，正式业务事实才生效。</div>
  </div>;
}
