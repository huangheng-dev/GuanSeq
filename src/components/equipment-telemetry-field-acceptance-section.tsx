"use client";

import { type FormEvent, useEffect, useState } from "react";

import type { EquipmentTelemetryFieldAcceptance, EquipmentTelemetryFieldAcceptanceContext,
  EquipmentTelemetryFieldAcceptanceStatus } from "@/lib/contracts";
import { EquipmentTelemetryFieldAcceptanceClientError, loadEquipmentTelemetryFieldAcceptanceContext,
  submitEquipmentTelemetryFieldAcceptanceMutation } from "@/services/equipment-telemetry-field-acceptance-client-service";
import { MaterialIcon } from "./material-icon";
import { GsButton, GsCheckbox, GsInput, GsModalHost, GsTextArea } from "./ui";

type AcceptanceAction = "SUBMIT" | "APPROVE" | "REJECT";
type CheckKey = "networkApproved" | "securityValidated" | "readOnlyConfirmed"
  | "disconnectRecoveryVerified" | "capacityVerified" | "pointMappingApproved";

const statusLabels: Record<EquipmentTelemetryFieldAcceptanceStatus, string> = {
  DRAFT: "验收草稿", SUBMITTED: "待审批", APPROVED: "现场已验收", REJECTED: "已驳回",
};
const statusTones: Record<EquipmentTelemetryFieldAcceptanceStatus, string> = {
  DRAFT: "warn", SUBMITTED: "info", APPROVED: "good", REJECTED: "risk",
};
const actionLabels: Record<AcceptanceAction, string> = { SUBMIT: "提交验收", APPROVE: "批准验收", REJECT: "驳回验收" };
const checks: Array<{ key: CheckKey; label: string; detail: string }> = [
  { key: "networkApproved", label: "现场网络审批", detail: "网段、防火墙与访问源已经获批" },
  { key: "securityValidated", label: "安全边界验证", detail: "TLS、ACL、凭据或隔离措施已核实" },
  { key: "readOnlyConfirmed", label: "只读权限确认", detail: "设备或 Broker 权限不能执行写入与控制" },
  { key: "disconnectRecoveryVerified", label: "断连恢复验证", detail: "中断、重连、重复与补传行为已有证据" },
  { key: "capacityVerified", label: "现场容量验证", detail: "设备数、点位数、频率和保留满足现场规模" },
  { key: "pointMappingApproved", label: "点位映射确认", detail: "厂商寄存器或 Topic Schema 与业务含义一致" },
];

function errorText(error: unknown) {
  if (error instanceof EquipmentTelemetryFieldAcceptanceClientError && error.requestId)
    return `${error.message}（请求 ${error.requestId}）`;
  return error instanceof Error ? error.message : "现场接入验收操作失败";
}
function dateText(value: string | null) {
  return value ? new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value)) : "未填写";
}
function localInput(value: string | null) { return value ? value.slice(0, 16) : ""; }
function instant(value: string) { return value ? new Date(value).toISOString() : null; }

function AcceptanceEditDialog({ context, onClose, onSaved }: { context: EquipmentTelemetryFieldAcceptanceContext;
  onClose: () => void; onSaved: (next: EquipmentTelemetryFieldAcceptanceContext) => void }) {
  const initial = context.acceptance;
  const [values, setValues] = useState<Record<CheckKey, boolean>>({
    networkApproved: initial?.networkApproved ?? false, securityValidated: initial?.securityValidated ?? false,
    readOnlyConfirmed: initial?.readOnlyConfirmed ?? false,
    disconnectRecoveryVerified: initial?.disconnectRecoveryVerified ?? false,
    capacityVerified: initial?.capacityVerified ?? false, pointMappingApproved: initial?.pointMappingApproved ?? false,
  });
  const [owner, setOwner] = useState(initial?.responsibleOwner ?? "");
  const [windowStart, setWindowStart] = useState(localInput(initial?.testWindowStart ?? null));
  const [windowEnd, setWindowEnd] = useState(localInput(initial?.testWindowEnd ?? null));
  const [evidenceReference, setEvidenceReference] = useState(initial?.evidenceReference ?? "");
  const [notes, setNotes] = useState(initial?.notes ?? "");
  const [reason, setReason] = useState(initial ? "更新现场验收证据" : "建立待现场核验的验收草稿");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault(); setError("");
    if (reason.trim().length < 4) { setError("请填写至少 4 个字符的修改原因。"); return; }
    if (Boolean(windowStart) !== Boolean(windowEnd)) { setError("测试窗口开始与结束必须同时填写或同时留空。"); return; }
    if (windowStart && windowEnd && new Date(windowEnd) <= new Date(windowStart)) {
      setError("测试窗口结束时间必须晚于开始时间。"); return;
    }
    try {
      setPending(true);
      const next = await submitEquipmentTelemetryFieldAcceptanceMutation({ operation: "save",
        connectionId: context.connectionId, ...values, responsibleOwner: owner.trim() || null,
        testWindowStart: instant(windowStart), testWindowEnd: instant(windowEnd),
        evidenceReference: evidenceReference.trim() || null, notes: notes.trim() || null,
        expectedVersion: initial?.version ?? null, reason: reason.trim() });
      onSaved(next);
    } catch (failure) { setError(errorText(failure)); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog fieldAcceptanceDialog" role="dialog" aria-modal="true" aria-labelledby="field-acceptance-edit-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="assignment_turned_in" size={22}/></span><div><h2 id="field-acceptance-edit-title">{initial ? "维护现场验收单" : "建立现场验收草稿"}</h2><p>{context.connectionCode} · 只在取得真实现场证据后勾选，不得使用仿真结果代替。</p></div><GsButton className="iconButton" aria-label="关闭现场验收表单" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <form onSubmit={submit}><section className="fieldAcceptanceChecklist" aria-label="现场验收六项检查">{checks.map((check) => <label key={check.key}><GsCheckbox ariaLabel={check.label} checked={values[check.key]} onCheckedChange={(checked) => setValues((current) => ({ ...current, [check.key]: checked }))}/><span><strong>{check.label}</strong><small>{check.detail}</small></span></label>)}</section>
      <div className="formGrid"><label className="formField"><span>现场责任人</span><GsInput value={owner} maxLength={80} onChange={(event) => setOwner(event.target.value)} placeholder="提交前必填"/></label><label className="formField"><span>证据引用</span><GsInput value={evidenceReference} maxLength={240} onChange={(event) => setEvidenceReference(event.target.value)} placeholder="测试报告、工单或归档地址"/></label><label className="formField"><span>测试开始</span><GsInput type="datetime-local" value={windowStart} onChange={(event) => setWindowStart(event.target.value)}/></label><label className="formField"><span>测试结束</span><GsInput type="datetime-local" value={windowEnd} onChange={(event) => setWindowEnd(event.target.value)}/></label><label className="formField formFieldFull"><span>现场说明</span><GsTextArea rows={3} maxLength={1000} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="记录设备、网络、规模、异常与结论"/></label><label className="formField formFieldFull"><span>保存原因<em>必填</em></span><GsTextArea rows={2} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label></div>
      {error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="verified_user" size={16}/>草稿允许不完整；提交时后端重新核对预检与全部证据。</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">保存验收草稿</GsButton></div></footer>
    </form></section></GsModalHost>;
}

function AcceptanceActionDialog({ context, action, onClose, onSaved }: { context: EquipmentTelemetryFieldAcceptanceContext;
  action: AcceptanceAction; onClose: () => void; onSaved: (next: EquipmentTelemetryFieldAcceptanceContext) => void }) {
  const [reason, setReason] = useState(action === "SUBMIT" ? "提交真实现场验收证据审核"
    : action === "APPROVE" ? "复核真实现场证据并批准验收" : "现场证据不完整，驳回补充");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!context.acceptance || reason.trim().length < 4) { setError("请填写至少 4 个字符的责任说明。"); return; }
    try { setPending(true); setError(""); onSaved(await submitEquipmentTelemetryFieldAcceptanceMutation({
      operation: "act", connectionId: context.connectionId, action,
      expectedVersion: context.acceptance.version, reason: reason.trim(),
    })); } catch (failure) { setError(errorText(failure)); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentGenerateDialog" role="dialog" aria-modal="true" aria-labelledby="field-acceptance-action-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action === "APPROVE" ? "verified" : action === "REJECT" ? "undo" : "send"} size={22}/></span><div><h2 id="field-acceptance-action-title">{actionLabels[action]}</h2><p>{context.acceptance?.acceptanceNumber} · 当前版本 {context.acceptance?.version}</p></div><GsButton className="iconButton" aria-label="关闭现场验收操作" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>责任说明<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>{action === "APPROVE" ? "批准后验收事实冻结；新的接入变化不得覆盖本次证据。" : "动作将记录责任人、时间和请求编号。"}</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent={action === "REJECT" ? "danger" : "primary"} loading={pending} htmlType="submit">确认{actionLabels[action]}</GsButton></div></footer></form></section></GsModalHost>;
}

function AcceptanceEvidence({ acceptance }: { acceptance: EquipmentTelemetryFieldAcceptance }) {
  const completed = checks.filter((check) => acceptance[check.key]).length;
  return <><div className="fieldAcceptanceProgress"><span><small>核验项</small><strong>{completed} / 6</strong></span><span><small>现场责任人</small><strong>{acceptance.responsibleOwner ?? "待填写"}</strong></span><span><small>测试窗口</small><strong>{acceptance.testWindowStart ? `${dateText(acceptance.testWindowStart)} — ${dateText(acceptance.testWindowEnd)}` : "待安排"}</strong></span></div>
    <div className="fieldAcceptanceEvidenceGrid">{checks.map((check) => <div key={check.key} className={acceptance[check.key] ? "isComplete" : ""}><MaterialIcon name={acceptance[check.key] ? "check_circle" : "radio_button_unchecked"} size={17}/><span><strong>{check.label}</strong><small>{acceptance[check.key] ? "已有责任证据" : "待现场核验"}</small></span></div>)}</div>
    <dl className="detailLedger fieldAcceptanceLedger"><div><dt>证据引用</dt><dd>{acceptance.evidenceReference ?? "待填写"}</dd></div><div><dt>更新时间</dt><dd>{dateText(acceptance.updatedAt)}</dd></div></dl>
    {acceptance.notes ? <p className="fieldAcceptanceNotes">{acceptance.notes}</p> : null}
    {acceptance.rejectionReason ? <div className="formError" role="status">驳回原因：{acceptance.rejectionReason}</div> : null}
    {acceptance.events.length ? <div className="fieldAcceptanceEvents"><strong>验收责任证据</strong>{acceptance.events.slice(0, 5).map((event) => <span key={event.id}><em>{event.action}</em><small>{dateText(event.occurredAt)} · {event.reason} · 请求 {event.requestId}</small></span>)}</div> : null}</>;
}

export function EquipmentTelemetryFieldAcceptanceSection({ connectionId }: { connectionId: string }) {
  const [context, setContext] = useState<EquipmentTelemetryFieldAcceptanceContext | null>(null);
  const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  const [editOpen, setEditOpen] = useState(false); const [action, setAction] = useState<AcceptanceAction | null>(null);
  const [notice, setNotice] = useState("");
  useEffect(() => {
    let active = true;
    loadEquipmentTelemetryFieldAcceptanceContext(connectionId)
      .then((next) => { if (active) setContext(next); })
      .catch((failure) => { if (active) setError(errorText(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [connectionId]);
  function saved(next: EquipmentTelemetryFieldAcceptanceContext) {
    setContext(next); setEditOpen(false); setAction(null);
    setNotice(next.fieldAccepted ? "现场验收已批准并冻结" : "现场验收证据已保存");
    window.setTimeout(() => setNotice(""), 4200);
  }
  if (loading) return <section className="drawerSection fieldAcceptanceSection"><div className="sectionTitleCompact"><h3>现场接入验收</h3><span>加载中</span></div><p className="telemetryHistoryState">正在核对现场验收事实…</p></section>;
  if (error || !context) return <section className="drawerSection fieldAcceptanceSection"><div className="sectionTitleCompact"><h3>现场接入验收</h3><em className="businessStatus businessStatusrisk">加载失败</em></div><div className="formError" role="alert">{error || "未取得现场验收上下文"}</div><GsButton onClick={() => { setLoading(true); setError(""); loadEquipmentTelemetryFieldAcceptanceContext(connectionId).then(setContext).catch((failure) => setError(errorText(failure))).finally(() => setLoading(false)); }} htmlType="button">重新加载</GsButton></section>;
  if (!context.fieldEligible) return <section className="drawerSection fieldAcceptanceSection" aria-label="现场接入验收"><div className="sectionTitleCompact"><div><h3>现场接入验收</h3><small>仿真端点不具备现场验收资格</small></div><em className="businessStatus businessStatusinfo">不适用</em></div><div className="fieldAcceptanceBoundary fieldAcceptanceBoundarySimulation"><MaterialIcon name="science" size={20}/><span><strong>不会为模拟器生成“现场已验收”</strong><small>替换为物理设备或用户真实 Broker 后，完成现场候选预检和六项核验再建立验收单。</small></span></div></section>;
  const acceptance = context.acceptance;
  const precheckTone = context.latestTechnicalPrecheckPassed ? "good" : "warn";
  return <section className="drawerSection fieldAcceptanceSection" aria-label="现场接入验收"><div className="sectionTitleCompact"><div><h3>现场接入验收</h3><small>{acceptance?.acceptanceNumber ?? "尚未建立验收单"}</small></div><em className={`businessStatus businessStatus${acceptance ? statusTones[acceptance.status] : "warn"}`}>{acceptance ? statusLabels[acceptance.status] : "待建立"}</em></div>
    <div className={`fieldAcceptanceBoundary fieldAcceptanceBoundary${context.fieldAccepted ? "Accepted" : "Pending"}`}><MaterialIcon name={context.fieldAccepted ? "verified" : "assignment_late"} size={20}/><span><strong>{context.fieldAccepted ? "真实现场验收已经批准" : "当前部署仍未完成现场验收"}</strong><small>{context.latestTechnicalPrecheckPassed ? "最新现场候选技术预检已通过；仍以验收单状态为正式接入依据。" : "尚无成功的最新现场候选预检，或最近一次预检已经失败。"}</small></span><em className={`businessStatus businessStatus${precheckTone}`}>{context.latestTechnicalPrecheckPassed ? "预检有效" : "预检待完成"}</em></div>
    {acceptance ? <AcceptanceEvidence acceptance={acceptance}/> : <p className="telemetryVerificationEmpty">可先建立不完整草稿安排责任人与测试窗口；只有真实现场证据完整且最新技术预检成功后才能提交。</p>}
    {notice ? <div className="businessInlineNotice" role="status">{notice}</div> : null}
    <div className="fieldAcceptanceActions">{context.canMaintain && (!acceptance || acceptance.availableActions.includes("UPDATE")) ? <GsButton onClick={() => setEditOpen(true)} htmlType="button"><MaterialIcon name="edit_note" size={17}/>{acceptance ? "维护验收单" : "建立验收草稿"}</GsButton> : null}{acceptance?.availableActions.includes("SUBMIT") ? <GsButton intent="primary" disabled={!context.latestTechnicalPrecheckPassed} onClick={() => setAction("SUBMIT")} htmlType="button"><MaterialIcon name="send" size={17}/>提交验收</GsButton> : null}{acceptance?.availableActions.includes("APPROVE") ? <GsButton intent="primary" disabled={!context.latestTechnicalPrecheckPassed} onClick={() => setAction("APPROVE")} htmlType="button"><MaterialIcon name="verified" size={17}/>批准验收</GsButton> : null}{acceptance?.availableActions.includes("REJECT") ? <GsButton intent="danger" onClick={() => setAction("REJECT")} htmlType="button">驳回补证</GsButton> : null}</div>
    {acceptance?.availableActions.some((item) => item === "SUBMIT" || item === "APPROVE") && !context.latestTechnicalPrecheckPassed ? <small className="fieldAcceptanceBlocked">提交或批准已锁定：请先对真实端点执行并通过最新技术预检。</small> : null}
    {editOpen ? <AcceptanceEditDialog context={context} onClose={() => setEditOpen(false)} onSaved={saved}/> : null}
    {action ? <AcceptanceActionDialog context={context} action={action} onClose={() => setAction(null)} onSaved={saved}/> : null}
  </section>;
}
