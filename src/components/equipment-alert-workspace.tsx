"use client";

import { type FormEvent, useState } from "react";

import type { EquipmentAlert, EquipmentAlertRule, EquipmentAlertRuleType, EquipmentTelemetryConnection,
  EquipmentWorkOrder } from "@/lib/contracts";
import { EquipmentAlertClientError, loadEquipmentAlertDetail, refreshEquipmentAlertPage,
  submitEquipmentAlertMutation } from "@/services/equipment-alert-client-service";
import type { EquipmentAlertPageData } from "@/services/equipment-alert-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsDrawer, GsInput, GsModalHost, GsTextArea } from "./ui";

type AlertAction = "ACKNOWLEDGE" | "START_PROCESSING" | "RESOLVE" | "CLOSE" | "LINK_REPAIR";

const ruleTypeLabels: Record<EquipmentAlertRuleType, string> = {
  HIGH_LIMIT: "高于或等于上限", LOW_LIMIT: "低于或等于下限", COMMUNICATION_FAILURE: "采集通讯失败",
};
const statusLabels = { OPEN: "待确认", ACKNOWLEDGED: "已确认", IN_PROGRESS: "处理中", RESOLVED: "已解决", CLOSED: "已关闭" } as const;
const actionLabels: Record<AlertAction, string> = {
  ACKNOWLEDGE: "确认报警", START_PROCESSING: "开始处理", RESOLVE: "标记解决", CLOSE: "关闭报警", LINK_REPAIR: "关联维修",
};

function dateText(value: string | null) {
  return value ? new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false }).format(new Date(value)) : "尚无";
}

function errorText(error: unknown) {
  if (error instanceof EquipmentAlertClientError && error.requestId) return `${error.message}（请求 ${error.requestId}）`;
  return error instanceof Error ? error.message : "设备报警操作失败";
}

function RuleCreateDialog({ connections, onClose, onSaved }: { connections: EquipmentTelemetryConnection[];
  onClose: () => void; onSaved: () => void }) {
  const candidates = connections.filter((connection) => connection.status === "ACTIVE");
  const connectionOptions = candidates.map((connection) => ({ value: connection.id, label: `${connection.connectionCode} · ${connection.name}` }));
  const [connectionId, setConnectionId] = useState(candidates[0]?.id ?? "");
  const [ruleType, setRuleType] = useState<EquipmentAlertRuleType>("HIGH_LIMIT");
  const selectedConnection = candidates.find((connection) => connection.id === connectionId);
  const numericPoints = selectedConnection?.points.filter((point) => point.valueType !== "BOOLEAN") ?? [];
  const [pointId, setPointId] = useState(numericPoints[0]?.id ?? "");
  const [ruleCode, setRuleCode] = useState("SPINDLE_LOAD_HIGH"); const [name, setName] = useState("主轴负载过高");
  const [threshold, setThreshold] = useState("80"); const [severity, setSeverity] = useState<"WARNING" | "CRITICAL">("WARNING");
  const [assignee, setAssignee] = useState("设备主管"); const [reason, setReason] = useState("建立可追踪的设备报警责任");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  const communication = ruleType === "COMMUNICATION_FAILURE";

  function chooseConnection(value: string) {
    setConnectionId(value);
    const next = candidates.find((connection) => connection.id === value);
    setPointId(next?.points.find((point) => point.valueType !== "BOOLEAN")?.id ?? "");
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setPending(true); setError("");
    try {
      await submitEquipmentAlertMutation({ operation: "createRule", ruleCode: ruleCode.trim().toUpperCase(), name,
        connectionId, pointId: communication ? null : pointId, ruleType,
        thresholdValue: communication ? null : Number(threshold), severity, defaultAssignee: assignee, reason });
      onSaved();
    } catch (caught) { setError(errorText(caught)); setPending(false); }
  }

  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentAlertDialog" role="dialog" aria-modal="true" aria-labelledby="alert-rule-create-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="notification_add" size={22}/></span><div><h2 id="alert-rule-create-title">建立设备报警规则</h2><p>v1 仅支持即时数值阈值与通讯失败，不使用复杂规则引擎。</p></div><GsButton className="iconButton" aria-label="关闭报警规则表单" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    {candidates.length ? <form onSubmit={submit}><div className="formGrid">
      <label className="formField formFieldFull"><span>采集连接<em>必填</em></span><RoundedSelect ariaLabel="报警采集连接" size="field" options={connectionOptions} value={connectionId} onValueChange={chooseConnection}/></label>
      <label className="formField"><span>规则编码<em>必填</em></span><GsInput value={ruleCode} maxLength={40} onChange={(event) => setRuleCode(event.target.value.toUpperCase())}/></label>
      <label className="formField"><span>规则名称<em>必填</em></span><GsInput value={name} maxLength={120} onChange={(event) => setName(event.target.value)}/></label>
      <label className="formField"><span>触发类型<em>必填</em></span><RoundedSelect ariaLabel="报警触发类型" size="field" options={Object.entries(ruleTypeLabels).map(([value, label]) => ({ value, label }))} value={ruleType} onValueChange={(value) => { const next = value as EquipmentAlertRuleType; setRuleType(next); if (next === "COMMUNICATION_FAILURE") { setPointId(""); setName("采集通讯失败"); } else if (!pointId) setPointId(numericPoints[0]?.id ?? ""); }}/></label>
      {!communication ? <><label className="formField"><span>采集点位<em>必填</em></span><RoundedSelect ariaLabel="报警采集点位" size="field" options={numericPoints.map((point) => ({ value: point.id, label: `${point.pointCode} · ${point.name}` }))} value={pointId} onValueChange={setPointId}/></label><label className="formField"><span>阈值<em>必填</em></span><GsInput type="number" step="any" value={threshold} onChange={(event) => setThreshold(event.target.value)}/></label></> : null}
      <label className="formField"><span>报警级别<em>必填</em></span><RoundedSelect ariaLabel="报警级别" size="field" options={[{ value: "WARNING", label: "警告" }, { value: "CRITICAL", label: "严重" }]} value={severity} onValueChange={(value) => setSeverity(value as "WARNING" | "CRITICAL")}/></label>
      <label className="formField"><span>默认责任人<em>必填</em></span><GsInput value={assignee} maxLength={80} onChange={(event) => setAssignee(event.target.value)}/></label>
      <label className="formField formFieldFull"><span>创建原因<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label>
    </div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="shield" size={16}/>报警只建立责任，不自动修改设备状态或维修事实</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">建立活动规则</GsButton></div></footer></form> : <div className="businessEmptyState"><strong>没有可用采集连接</strong><p>请先完成连接技术预检并启用采集。</p></div>}
  </section></GsModalHost>;
}

function RuleActionDialog({ rule, action, onClose, onSaved }: { rule: EquipmentAlertRule; action: "ACTIVATE" | "PAUSE";
  onClose: () => void; onSaved: () => void }) {
  const [reason, setReason] = useState(action === "PAUSE" ? "暂停规则并保留已有报警责任" : "恢复规则评估");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); setPending(true); setError(""); try {
    await submitEquipmentAlertMutation({ operation: "actOnRule", id: rule.id, action, reason, expectedVersion: rule.version }); onSaved();
  } catch (caught) { setError(errorText(caught)); setPending(false); } }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentGenerateDialog" role="dialog" aria-modal="true" aria-labelledby="alert-rule-action-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action === "PAUSE" ? "pause_circle" : "play_circle"} size={22}/></span><div><h2 id="alert-rule-action-title">{action === "PAUSE" ? "暂停报警规则" : "启用报警规则"}</h2><p>{rule.ruleCode} · 当前版本 {rule.version}</p></div><GsButton className="iconButton" aria-label="关闭报警规则操作" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>操作原因<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>暂停规则不会关闭已有报警。</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent={action === "PAUSE" ? "danger" : "primary"} loading={pending} htmlType="submit">确认{action === "PAUSE" ? "暂停" : "启用"}</GsButton></div></footer></form></section></GsModalHost>;
}

function AlertActionDialog({ alert, action, repairs, onClose, onSaved }: { alert: EquipmentAlert; action: AlertAction;
  repairs: EquipmentWorkOrder[]; onClose: () => void; onSaved: (next: EquipmentAlert) => void }) {
  const candidates = repairs.filter((order) => order.assetId === alert.assetId && order.workType === "REPAIR" && !["COMPLETED", "CANCELLED"].includes(order.status));
  const [reason, setReason] = useState(`${actionLabels[action]}并保留处置证据`); const [assignee, setAssignee] = useState(alert.assignee);
  const [notes, setNotes] = useState(""); const [workOrderId, setWorkOrderId] = useState(candidates[0]?.id ?? "");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); setPending(true); setError(""); try {
    const saved = await submitEquipmentAlertMutation({ operation: "actOnAlert", id: alert.id, action, reason,
      expectedVersion: alert.version, assignee: action === "ACKNOWLEDGE" ? assignee : null,
      resolutionNotes: action === "RESOLVE" ? notes : null, workOrderId: action === "LINK_REPAIR" ? workOrderId : null });
    if ("alertNumber" in saved) onSaved(saved);
  } catch (caught) { setError(errorText(caught)); setPending(false); } }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentGenerateDialog equipmentAlertDialog" role="dialog" aria-modal="true" aria-labelledby="alert-action-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action === "LINK_REPAIR" ? "build" : action === "CLOSE" ? "task_alt" : "notification_important"} size={22}/></span><div><h2 id="alert-action-title">{actionLabels[action]}</h2><p>{alert.alertNumber} · 版本 {alert.version} · 条件{alert.conditionActive ? "仍存在" : "已恢复"}</p></div><GsButton className="iconButton" aria-label="关闭报警操作" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid">
    {action === "ACKNOWLEDGE" ? <label className="formField formFieldFull"><span>责任人</span><GsInput value={assignee} maxLength={80} onChange={(event) => setAssignee(event.target.value)}/></label> : null}
    {action === "RESOLVE" ? <label className="formField formFieldFull"><span>解决说明<em>必填</em></span><GsTextArea rows={3} maxLength={1000} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="说明原因、措施和验证结果"/></label> : null}
    {action === "LINK_REPAIR" ? <label className="formField formFieldFull"><span>同设备未关闭维修工单<em>必填</em></span><RoundedSelect ariaLabel="关联维修工单" size="field" options={candidates.map((order) => ({ value: order.id, label: `${order.workOrderNumber} · ${order.title}` }))} value={workOrderId} onValueChange={setWorkOrderId}/></label> : null}
    <label className="formField formFieldFull"><span>操作原因<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label>
  </div>{action === "LINK_REPAIR" && !candidates.length ? <div className="formError" role="alert">当前设备没有可关联的未关闭维修工单。</div> : null}{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>{action === "RESOLVE" ? "只有条件恢复后才能解决报警。" : "所有动作保留责任人、时间和请求编号。"}</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} disabled={action === "LINK_REPAIR" && !candidates.length} htmlType="submit">确认{actionLabels[action]}</GsButton></div></footer></form></section></GsModalHost>;
}

function AlertDrawer({ alert, canManage, onClose, onAction }: { alert: EquipmentAlert; canManage: boolean;
  onClose: () => void; onAction: (action: AlertAction) => void }) {
  return <GsDrawer open onClose={onClose} ariaLabel="设备报警详情"><header className="recordDrawerHeader"><div><h2>{alert.ruleName}</h2><p>{alert.alertNumber} · {alert.assetCode} · {alert.assetName}</p></div><GsButton className="iconButton" aria-label="关闭设备报警详情" onClick={onClose} htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <section className="drawerSection"><div className={`equipmentAlertBoundary equipmentAlertBoundary${alert.conditionActive ? "active" : "clear"}`}><MaterialIcon name={alert.conditionActive ? "warning" : "check_circle"} size={20}/><span><strong>{alert.conditionActive ? "报警条件仍存在" : "报警条件已恢复"}</strong><small>{alert.conditionActive ? "不能标记为已解决；请先处理现场原因。" : "恢复不等于责任闭环，仍需完成解决和关闭。"}</small></span></div><dl className="detailLedger"><div><dt>处置状态</dt><dd>{statusLabels[alert.status]}</dd></div><div><dt>级别</dt><dd>{alert.severity === "CRITICAL" ? "严重" : "警告"}</dd></div><div><dt>责任人</dt><dd>{alert.assignee}</dd></div><div><dt>连接 / 点位</dt><dd>{alert.connectionCode} / {alert.pointCode ?? "通讯"}</dd></div><div><dt>观测值</dt><dd>{alert.observedValue ?? alert.failureCode ?? "—"}</dd></div><div><dt>首次 / 最近</dt><dd>{dateText(alert.firstOccurredAt)} / {dateText(alert.lastOccurredAt)}</dd></div><div><dt>恢复时间</dt><dd>{dateText(alert.recoveredAt)}</dd></div><div><dt>维修关联</dt><dd>{alert.linkedWorkOrderNumber ?? "尚未关联"}</dd></div></dl></section>
    {canManage && alert.availableActions.length ? <section className="drawerSection"><div className="sectionTitleCompact"><div><h3>可执行动作</h3><small>后端状态机与版本校验是可信边界</small></div></div><div className="equipmentAlertActions">{alert.availableActions.map((action) => <GsButton key={action} intent={action === "CLOSE" ? "primary" : "secondary"} onClick={() => onAction(action)} htmlType="button">{actionLabels[action]}</GsButton>)}</div></section> : null}
    <section className="drawerSection"><div className="sectionTitleCompact"><div><h3>不可变证据</h3><small>{alert.events.length} 条触发、恢复与处置记录</small></div></div><div className="equipmentAlertTimeline">{alert.events.map((event) => <div key={event.id}><span className="timelineDot"/><span><strong>{event.action}</strong><small>{dateText(event.occurredAt)} · {event.actorUserId ? "人工动作" : "系统证据"}</small><p>{event.reason}</p><em>请求 {event.requestId}</em></span></div>)}</div></section>
  </GsDrawer>;
}

export function EquipmentAlertWorkspace({ initialData }: { initialData: EquipmentAlertPageData }) {
  const [data, setData] = useState<EquipmentAlertPageData>(initialData); const [view, setView] = useState<"alerts" | "rules">("alerts");
  const [refreshing, setRefreshing] = useState(false); const [error, setError] = useState(""); const [createOpen, setCreateOpen] = useState(false);
  const [selectedAlert, setSelectedAlert] = useState<EquipmentAlert | null>(null); const [alertAction, setAlertAction] = useState<AlertAction | null>(null);
  const [selectedRule, setSelectedRule] = useState<EquipmentAlertRule | null>(null); const [ruleAction, setRuleAction] = useState<"ACTIVATE" | "PAUSE" | null>(null);
  const backend = data.source === "backend" ? data : null;

  async function refresh(closeOverlays = false) { setRefreshing(true); setError(""); try { const next = await refreshEquipmentAlertPage(); setData(next); if (closeOverlays) { setCreateOpen(false); setSelectedRule(null); setRuleAction(null); } } catch (caught) { setError(errorText(caught)); } finally { setRefreshing(false); } }
  async function openAlert(alert: EquipmentAlert) { setError(""); try { setSelectedAlert(await loadEquipmentAlertDetail(alert.id)); } catch (caught) { setError(errorText(caught)); } }
  async function afterAlertAction(next: EquipmentAlert) { setSelectedAlert(next); setAlertAction(null); await refresh(); }

  if (!backend) return <section className="backendUnavailableState" role="alert"><MaterialIcon name="cloud_off" size={26}/><strong>设备报警服务暂时不可用</strong><p>{data.source === "unavailable" ? data.message : "尚未取得设备报警数据。"}</p><GsButton onClick={() => refresh()} loading={refreshing} htmlType="button">重新检查</GsButton></section>;
  const activeRules = backend.rulePage.items.filter((rule) => rule.status === "ACTIVE").length;
  return <section className="equipmentAlertWorkspace"><header className="telemetryHeader"><div><span>设备与资产</span><h2>设备报警处置</h2><p>把采集异常变成责任闭环；条件恢复与人工结案严格分离。</p></div><div><GsButton onClick={() => refresh()} loading={refreshing} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新</GsButton>{backend.rulePage.canManage ? <GsButton intent="primary" onClick={() => setCreateOpen(true)} htmlType="button"><MaterialIcon name="add_alert" size={17}/>建立规则</GsButton> : null}</div></header>
    <section className="telemetryTruthBanner equipmentAlertTruth"><MaterialIcon name="policy" size={23}/><div><strong>最小充分报警闭环</strong><p>v1 只支持即时上/下限和通讯失败；不自动改设备状态、不自动建维修单，也不冒充真实现场阈值验收。</p></div><span>生产采集触发</span></section>
    <div className="equipmentAlertMetrics"><span><small>活动条件</small><strong>{backend.alertPage.activeConditionCount}</strong><em>仍在触发</em></span><span><small>未关闭责任</small><strong>{backend.alertPage.unclosedCount}</strong><em>含已恢复待结案</em></span><span><small>活动规则</small><strong>{activeRules}</strong><em>共 {backend.rulePage.totalElements} 条</em></span></div>
    <div className="equipmentAlertViewSwitch"><GsButton intent={view === "alerts" ? "primary" : "secondary"} onClick={() => setView("alerts")} htmlType="button">报警责任</GsButton><GsButton intent={view === "rules" ? "primary" : "secondary"} onClick={() => setView("rules")} htmlType="button">规则管理</GsButton></div>
    {error ? <div className="formError" role="alert">{error}</div> : null}
    {view === "alerts" ? <div className="equipmentAlertTable" role="table" aria-label="设备报警责任列表"><div className="equipmentAlertHeader" role="row"><span>报警 / 设备</span><span>触发证据</span><span>条件</span><span>处置责任</span><span>操作</span></div>{backend.alertPage.items.length ? backend.alertPage.items.map((alert) => <div className="equipmentAlertRow" role="row" key={alert.id}><span><strong>{alert.ruleName}</strong><small>{alert.alertNumber} · {alert.assetCode} · {alert.assetName}</small></span><span><strong>{alert.pointCode ?? "通讯状态"}</strong><small>{alert.observedValue ?? alert.failureCode ?? "—"} · {dateText(alert.lastOccurredAt)}</small></span><span><em className={`businessStatus businessStatus${alert.conditionActive ? "risk" : "good"}`}>{alert.conditionActive ? "仍存在" : "已恢复"}</em><small>{alert.severity === "CRITICAL" ? "严重" : "警告"}</small></span><span><em className={`businessStatus businessStatus${alert.status === "CLOSED" ? "good" : alert.status === "RESOLVED" ? "info" : "warn"}`}>{statusLabels[alert.status]}</em><small>{alert.assignee}</small></span><span className="businessRowActions"><GsButton aria-label={`查看${alert.alertNumber}`} onClick={() => openAlert(alert)} htmlType="button"><MaterialIcon name="visibility" size={18}/></GsButton></span></div>) : <div className="businessEmptyState"><MaterialIcon name="notifications_off" size={28}/><strong>当前没有报警责任</strong><p>活动规则会由生产采集链路触发；仿真结果不代表现场阈值已验收。</p></div>}</div>
      : <div className="equipmentAlertTable equipmentAlertRuleTable" role="table" aria-label="设备报警规则列表"><div className="equipmentAlertHeader" role="row"><span>规则</span><span>连接 / 设备</span><span>触发条件</span><span>责任与状态</span><span>操作</span></div>{backend.rulePage.items.length ? backend.rulePage.items.map((rule) => <div className="equipmentAlertRow" role="row" key={rule.id}><span><strong>{rule.name}</strong><small>{rule.ruleCode}</small></span><span><strong>{rule.connectionName}</strong><small>{rule.connectionCode} · {rule.assetCode}</small></span><span><strong>{ruleTypeLabels[rule.ruleType]}</strong><small>{rule.pointCode ? `${rule.pointCode} · 阈值 ${rule.thresholdValue}` : "整个连接"}</small></span><span><em className={`businessStatus businessStatus${rule.status === "ACTIVE" ? "good" : "info"}`}>{rule.status === "ACTIVE" ? "活动" : "暂停"}</em><small>{rule.defaultAssignee}</small></span><span className="businessRowActions">{backend.rulePage.canManage ? <GsButton aria-label={`${rule.status === "ACTIVE" ? "暂停" : "启用"}${rule.ruleCode}`} onClick={() => { setSelectedRule(rule); setRuleAction(rule.status === "ACTIVE" ? "PAUSE" : "ACTIVATE"); }} htmlType="button"><MaterialIcon name={rule.status === "ACTIVE" ? "pause" : "play_arrow"} size={18}/></GsButton> : null}</span></div>) : <div className="businessEmptyState"><strong>尚未建立报警规则</strong><p>先选择已启用连接，再为已评审点位设置即时阈值。</p></div>}</div>}
    {createOpen ? <RuleCreateDialog connections={backend.connections.items} onClose={() => setCreateOpen(false)} onSaved={() => refresh(true)}/> : null}
    {selectedRule && ruleAction ? <RuleActionDialog rule={selectedRule} action={ruleAction} onClose={() => { setSelectedRule(null); setRuleAction(null); }} onSaved={() => refresh(true)}/> : null}
    {selectedAlert ? <AlertDrawer alert={selectedAlert} canManage={backend.alertPage.canManage} onClose={() => { setSelectedAlert(null); setAlertAction(null); }} onAction={setAlertAction}/> : null}
    {selectedAlert && alertAction ? <AlertActionDialog alert={selectedAlert} action={alertAction} repairs={backend.workOrders.items} onClose={() => setAlertAction(null)} onSaved={afterAlertAction}/> : null}
  </section>;
}
