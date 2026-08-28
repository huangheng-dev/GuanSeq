"use client";

import { type FormEvent, useEffect, useMemo, useState } from "react";

import type { EquipmentAsset, EquipmentTelemetryConnection, EquipmentTelemetryConnectionPage,
  EquipmentTelemetryEndpointType, EquipmentTelemetryProtocol, EquipmentTelemetryQuality, EquipmentTelemetryRegisterType,
  EquipmentTelemetryRetentionPolicy, EquipmentTelemetrySamplePage, EquipmentTelemetryValueType } from "@/lib/contracts";
import { EquipmentTelemetryClientError, loadEquipmentTelemetryConnectionDetail,
  refreshEquipmentTelemetryConnections, submitEquipmentTelemetryMutation } from "@/services/equipment-telemetry-client-service";
import { loadEquipmentTelemetryRetention, loadEquipmentTelemetrySampleHistory,
  submitEquipmentTelemetryLifecycleMutation } from "@/services/equipment-telemetry-lifecycle-client-service";
import type { EquipmentTelemetryPageData, EquipmentTelemetryPointInput } from "@/services/equipment-telemetry-server-service";
import { EquipmentTelemetryFieldAcceptanceSection } from "./equipment-telemetry-field-acceptance-section";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsCheckbox, GsDrawer, GsInput, GsModalHost, GsTextArea } from "./ui";

type ConnectionAction = "test" | "activate" | "pause" | "poll";
type DraftPoint = { pointCode: string; name: string; registerType: EquipmentTelemetryRegisterType;
  address: string; valueType: EquipmentTelemetryValueType; scale: string; valueOffset: string;
  engineeringUnit: string; validMin: string; validMax: string; mqttTopic: string; mqttValuePointer: string };

const protocolLabels: Record<EquipmentTelemetryProtocol, string> = { MODBUS_TCP: "Modbus TCP", MQTT_3_1_1: "MQTT 3.1.1" };
const endpointLabels: Record<EquipmentTelemetryEndpointType, string> = {
  SIMULATOR: "开发仿真端点", PHYSICAL_DEVICE: "物理设备", EXTERNAL_BROKER: "外部 Broker",
};
const statusLabels = { DRAFT: "待测试", ACTIVE: "采集中", PAUSED: "已暂停" } as const;
const communicationLabels = { UNKNOWN: "未测试", ONLINE: "通讯正常", OFFLINE: "通讯失败" } as const;
const actionLabels: Record<ConnectionAction, string> = { test: "执行技术预检", activate: "启用采集", pause: "暂停采集", poll: "立即采集" };

function errorText(error: unknown) {
  if (error instanceof EquipmentTelemetryClientError && error.requestId) return `${error.message}（请求 ${error.requestId}）`;
  return error instanceof Error ? error.message : "设备采集操作失败";
}

function dateText(value: string | null) {
  return value ? new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false }).format(new Date(value)) : "尚无";
}

function TelemetryVerificationPanel({ connection }: { connection: EquipmentTelemetryConnection }) {
  const event = connection.events.find((item) => item.verification !== null);
  const verification = event?.verification;
  if (!verification || !event) return <section className="drawerSection telemetryVerificationSection"><div className="sectionTitleCompact"><div><h3>接入技术预检</h3><small>尚未形成结构化预检证据</small></div><em className="businessStatus businessStatuswarn">待预检</em></div><p className="telemetryVerificationEmpty">执行技术预检后，系统会记录协议读取、点位覆盖、值类型、安全提示和现场待办；预检不会生成业务样本。</p></section>;
  const passed = verification.technicalPassed;
  const simulated = verification.evidenceLevel === "SIMULATION_TECHNICAL";
  const statusLabels = { PASSED: "通过", WARNING: "警告", FAILED: "失败", INFO: "说明" } as const;
  return <section className="drawerSection telemetryVerificationSection" aria-label="接入技术预检证据"><div className="sectionTitleCompact"><div><h3>接入技术预检</h3><small>{dateText(event.occurredAt)} · 请求 {event.requestId}</small></div><em className={`businessStatus businessStatus${passed ? "good" : "risk"}`}>{passed ? simulated ? "仿真预检通过" : "现场候选预检通过" : "技术预检失败"}</em></div>
    <div className={`telemetryVerificationBoundary ${simulated ? "telemetryVerificationBoundarySimulation" : ""}`}><MaterialIcon name={passed ? "verified_user" : "error"} size={19}/><span><strong>{verification.fieldAccepted ? "现场已验收" : "现场尚未验收"}</strong><small>{simulated ? "本次是生产协议链路的仿真技术证据，正式接入时只替换端点、凭据和映射。" : "当前端点只完成技术预检，仍需现场责任人完成网络、安全、恢复和容量验收。"}</small></span></div>
    <div className="telemetryVerificationMetrics"><span><small>协议</small><strong>{protocolLabels[verification.protocol]}</strong></span><span><small>点位覆盖</small><strong>{verification.returnedPointCount} / {verification.pointCount}</strong></span><span><small>警告</small><strong>{verification.warningCount}</strong></span></div>
    <div className="telemetryVerificationChecks">{verification.checks.map((check) => <div key={check.code}><em className={`telemetryVerificationCheck telemetryVerificationCheck${check.status}`}>{statusLabels[check.status]}</em><span><strong>{check.code}</strong><small>{check.message}</small></span></div>)}</div>
    <div className="telemetryPendingChecks"><strong>正式接入仍需完成</strong><div>{verification.pendingFieldChecks.map((item) => <span key={item}>{item}</span>)}</div></div>
  </section>;
}

function emptyPoint(index: number): DraftPoint {
  return { pointCode: `POINT_${String(index).padStart(2, "0")}`, name: "", registerType: "HOLDING_REGISTER",
    address: String(index - 1), valueType: "UINT16", scale: "1", valueOffset: "0", engineeringUnit: "",
    validMin: "", validMax: "", mqttTopic: "", mqttValuePointer: "" };
}

function defaultPoints(protocol: EquipmentTelemetryProtocol): DraftPoint[] {
  if (protocol === "MQTT_3_1_1") return [
    { ...emptyPoint(1), pointCode: "RUN_STATE", name: "运行状态", registerType: "MQTT_JSON", address: "0",
      valueType: "DECIMAL", engineeringUnit: "状态", validMin: "0", validMax: "4",
      mqttTopic: "factory/cnc/telemetry", mqttValuePointer: "/values/runState" },
    { ...emptyPoint(2), pointCode: "SPINDLE_LOAD", name: "主轴负载", registerType: "MQTT_JSON", address: "0",
      valueType: "DECIMAL", engineeringUnit: "%", validMin: "0", validMax: "120",
      mqttTopic: "factory/cnc/telemetry", mqttValuePointer: "/values/spindleLoad" },
    { ...emptyPoint(3), pointCode: "DOOR_CLOSED", name: "防护门关闭", registerType: "MQTT_JSON", address: "0",
      valueType: "BOOLEAN", mqttTopic: "factory/cnc/telemetry", mqttValuePointer: "/values/doorClosed" },
  ];
  return [
    { ...emptyPoint(1), pointCode: "RUN_STATE", name: "运行状态", address: "0", engineeringUnit: "状态", validMin: "0", validMax: "4" },
    { ...emptyPoint(2), pointCode: "SPINDLE_LOAD", name: "主轴负载", address: "2", scale: "0.1", engineeringUnit: "%", validMin: "0", validMax: "120" },
    { ...emptyPoint(3), pointCode: "DOOR_CLOSED", name: "防护门关闭", registerType: "COIL", address: "0", valueType: "BOOLEAN" },
  ];
}

function ConnectionCreateDialog({ assets, onClose, onSaved }: { assets: EquipmentAsset[]; onClose: () => void;
  onSaved: (connection: EquipmentTelemetryConnection) => void }) {
  const candidates = assets.filter((asset) => asset.operatingStatus !== "INACTIVE");
  const assetOptions = candidates.map((asset) => `${asset.assetCode} · ${asset.assetName}`);
  const [assetLabel, setAssetLabel] = useState(assetOptions[0] ?? "");
  const [protocol, setProtocol] = useState<EquipmentTelemetryProtocol>("MODBUS_TCP");
  const [endpointLabel, setEndpointLabel] = useState(endpointLabels.SIMULATOR);
  const [connectionCode, setConnectionCode] = useState("TEL-MODBUS-001");
  const [name, setName] = useState("加工中心只读采集连接");
  const [host, setHost] = useState("127.0.0.1"); const [port, setPort] = useState("1502");
  const [unitId, setUnitId] = useState("1"); const [pollInterval, setPollInterval] = useState("5");
  const [mqttTransport, setMqttTransport] = useState<"TCP" | "TLS">("TCP");
  const [mqttClientId, setMqttClientId] = useState("guanseq-dev-mqtt"); const [mqttQos, setMqttQos] = useState("0");
  const [credentialReference, setCredentialReference] = useState("");
  const [messageIdPointer, setMessageIdPointer] = useState("/messageId");
  const [deviceTimePointer, setDeviceTimePointer] = useState("/deviceTime");
  const [reason, setReason] = useState(""); const [pending, setPending] = useState(false); const [error, setError] = useState("");
  const [points, setPoints] = useState<DraftPoint[]>(defaultPoints("MODBUS_TCP"));
  function changeProtocol(label: string) {
    const next: EquipmentTelemetryProtocol = label === protocolLabels.MQTT_3_1_1 ? "MQTT_3_1_1" : "MODBUS_TCP";
    setProtocol(next); setPoints(defaultPoints(next)); setEndpointLabel(endpointLabels.SIMULATOR);
    setConnectionCode(next === "MQTT_3_1_1" ? "TEL-MQTT-001" : "TEL-MODBUS-001");
    setName(next === "MQTT_3_1_1" ? "加工中心外部 Broker 只读连接" : "加工中心只读采集连接");
    setPort(next === "MQTT_3_1_1" ? "1883" : "1502"); setUnitId(next === "MQTT_3_1_1" ? "0" : "1");
  }
  function updatePoint(index: number, changes: Partial<DraftPoint>) {
    setPoints((current) => current.map((point, itemIndex) => itemIndex === index ? { ...point, ...changes } : point));
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); setError("");
    const asset = candidates[assetOptions.indexOf(assetLabel)];
    if (!asset || !connectionCode.trim() || !name.trim() || !host.trim() || reason.trim().length < 4) {
      setError("请完整填写设备、连接、端点和至少 4 个字符的创建原因。"); return;
    }
    try {
      const normalizedPoints: EquipmentTelemetryPointInput[] = points.map((point, index) => ({
        pointCode: point.pointCode.trim().toUpperCase(), name: point.name.trim(), registerType: point.registerType,
        address: Number(point.address), valueType: point.valueType, scale: Number(point.scale), valueOffset: Number(point.valueOffset),
        mqttTopic: point.mqttTopic.trim() || null, mqttValuePointer: point.mqttValuePointer.trim() || null,
        engineeringUnit: point.engineeringUnit.trim() || null, validMin: point.validMin === "" ? null : Number(point.validMin),
        validMax: point.validMax === "" ? null : Number(point.validMax), sortOrder: index + 1,
      }));
      if (normalizedPoints.some((point) => !point.pointCode || !point.name || !Number.isInteger(point.address)
        || !Number.isFinite(point.scale) || !Number.isFinite(point.valueOffset)
        || (protocol === "MQTT_3_1_1" && (!point.mqttTopic || !point.mqttValuePointer)))) {
        setError(protocol === "MQTT_3_1_1" ? "请检查点位编码、名称、精确 Topic、JSON Pointer、比例和偏移。" : "请检查点位编码、名称、零基地址、比例和偏移。"); return;
      }
      setPending(true);
      const result = await submitEquipmentTelemetryMutation({ operation: "create", connectionCode: connectionCode.trim().toUpperCase(),
        name: name.trim(), assetId: asset.id, protocol,
        endpointType: endpointLabel === endpointLabels.SIMULATOR ? "SIMULATOR"
          : endpointLabel === endpointLabels.EXTERNAL_BROKER ? "EXTERNAL_BROKER" : "PHYSICAL_DEVICE",
        host: host.trim(), port: Number(port), unitId: Number(unitId), connectTimeoutMs: 1000, readTimeoutMs: 1000,
        mqtt: protocol === "MQTT_3_1_1" ? { transport: mqttTransport, clientId: mqttClientId.trim(), qos: Number(mqttQos),
          credentialReference: credentialReference.trim() || null, messageIdPointer: messageIdPointer.trim(),
          deviceTimePointer: deviceTimePointer.trim() || null } : null,
        pollIntervalSeconds: Number(pollInterval), points: normalizedPoints, reason: reason.trim() });
      if ("connectionCode" in result) onSaved(result);
    } catch (failure) { setError(errorText(failure)); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog telemetryCreateDialog" role="dialog" aria-modal="true" aria-labelledby="telemetry-create-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="sensors" size={22}/></span><div><h2 id="telemetry-create-title">建立{protocolLabels[protocol]}只读连接</h2><p>{protocol === "MQTT_3_1_1" ? "连接用户选择的外部 Broker；系统不保存密码，也不内置 Broker。" : "仿真与真机走同一生产适配器；地址使用协议零基偏移。"}</p></div><GsButton className="iconButton" aria-label="关闭设备采集连接表单" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    {candidates.length ? <form onSubmit={submit}><div className="formGrid equipmentFormGrid">
      <label className="formField formFieldFull"><span>关联设备<em>必填</em></span><RoundedSelect ariaLabel="关联设备" options={assetOptions} value={assetLabel} onValueChange={setAssetLabel}/></label>
      <label className="formField"><span>采集协议<em>必填</em></span><RoundedSelect ariaLabel="采集协议" options={Object.values(protocolLabels)} value={protocolLabels[protocol]} onValueChange={changeProtocol}/></label>
      <label className="formField"><span>端点类型<em>必填</em></span><RoundedSelect ariaLabel="端点类型" options={protocol === "MQTT_3_1_1" ? [endpointLabels.SIMULATOR, endpointLabels.EXTERNAL_BROKER] : [endpointLabels.SIMULATOR, endpointLabels.PHYSICAL_DEVICE]} value={endpointLabel} onValueChange={setEndpointLabel}/></label>
      <label className="formField"><span>连接编码<em>必填</em></span><GsInput value={connectionCode} maxLength={40} onChange={(event) => setConnectionCode(event.target.value.toUpperCase())}/></label>
      <label className="formField formFieldFull"><span>连接名称<em>必填</em></span><GsInput value={name} maxLength={120} onChange={(event) => setName(event.target.value)}/></label>
      <label className="formField"><span>主机地址<em>必填</em></span><GsInput value={host} maxLength={253} onChange={(event) => setHost(event.target.value)} placeholder="192.168.1.20"/></label>
      <label className="formField"><span>TCP 端口<em>必填</em></span><GsInput type="number" min="1" max="65535" value={port} onChange={(event) => setPort(event.target.value)}/></label>
      {protocol === "MODBUS_TCP" ? <label className="formField"><span>单元标识<em>必填</em></span><GsInput type="number" min="0" max="247" value={unitId} onChange={(event) => setUnitId(event.target.value)}/></label> : <>
        <label className="formField"><span>传输方式<em>必填</em></span><RoundedSelect ariaLabel="MQTT 传输方式" options={["TCP", "TLS"]} value={mqttTransport} onValueChange={(value) => setMqttTransport(value as "TCP" | "TLS")}/></label>
        <label className="formField"><span>客户端标识<em>必填</em></span><GsInput value={mqttClientId} maxLength={128} onChange={(event) => setMqttClientId(event.target.value)} placeholder="guanseq-site-a"/></label>
        <label className="formField"><span>订阅 QoS<em>必填</em></span><RoundedSelect ariaLabel="MQTT QoS" options={["0", "1"]} value={mqttQos} onValueChange={setMqttQos}/></label>
        <label className="formField"><span>凭据别名</span><GsInput value={credentialReference} maxLength={80} onChange={(event) => setCredentialReference(event.target.value)} placeholder="site_a（不填写用户名或密码）"/></label>
        <label className="formField"><span>消息编号 Pointer<em>必填</em></span><GsInput value={messageIdPointer} maxLength={253} onChange={(event) => setMessageIdPointer(event.target.value)} placeholder="/messageId"/></label>
        <label className="formField"><span>设备时间 Pointer</span><GsInput value={deviceTimePointer} maxLength={253} onChange={(event) => setDeviceTimePointer(event.target.value)} placeholder="/deviceTime"/></label>
      </>}
      <label className="formField"><span>采集周期（秒）<em>必填</em></span><GsInput type="number" min="1" max="3600" value={pollInterval} onChange={(event) => setPollInterval(event.target.value)}/></label>
    </div><section className="telemetryPointEditor"><header><div><strong>只读点位</strong><small>{protocol === "MQTT_3_1_1" ? "每个点位映射一个精确 Topic 与 JSON Pointer，不允许通配订阅" : "首期支持线圈、16/32 位保持寄存器"}</small></div><GsButton htmlType="button" onClick={() => setPoints((current) => [...current, protocol === "MQTT_3_1_1" ? { ...emptyPoint(current.length + 1), registerType: "MQTT_JSON", address: "0", valueType: "DECIMAL", mqttTopic: "factory/cnc/telemetry", mqttValuePointer: `/values/point${current.length + 1}` } : emptyPoint(current.length + 1)])}><MaterialIcon name="add" size={16}/>添加点位</GsButton></header>{points.map((point, index) => <div className={`telemetryPointRow${protocol === "MQTT_3_1_1" ? " telemetryMqttPointRow" : ""}`} key={`${index}-${point.pointCode}`}>
      <GsInput aria-label={`点位 ${index + 1} 编码`} value={point.pointCode} onChange={(event) => updatePoint(index, { pointCode: event.target.value.toUpperCase() })} placeholder="POINT_CODE"/>
      <GsInput aria-label={`点位 ${index + 1} 名称`} value={point.name} onChange={(event) => updatePoint(index, { name: event.target.value })} placeholder="业务名称"/>
      {protocol === "MQTT_3_1_1" ? <><GsInput aria-label={`点位 ${index + 1} Topic`} value={point.mqttTopic} maxLength={512} onChange={(event) => updatePoint(index, { mqttTopic: event.target.value })} placeholder="factory/cnc/telemetry"/><GsInput aria-label={`点位 ${index + 1} JSON Pointer`} value={point.mqttValuePointer} maxLength={253} onChange={(event) => updatePoint(index, { mqttValuePointer: event.target.value })} placeholder="/values/load"/></> : <><RoundedSelect ariaLabel={`点位 ${index + 1} 区域`} options={["保持寄存器", "线圈"]} value={point.registerType === "COIL" ? "线圈" : "保持寄存器"} onValueChange={(value) => updatePoint(index, value === "线圈" ? { registerType: "COIL", valueType: "BOOLEAN" } : { registerType: "HOLDING_REGISTER", valueType: "UINT16" })}/><GsInput aria-label={`点位 ${index + 1} 地址`} type="number" min="0" max="65535" value={point.address} onChange={(event) => updatePoint(index, { address: event.target.value })}/></>}
      <RoundedSelect ariaLabel={`点位 ${index + 1} 类型`} options={protocol === "MQTT_3_1_1" ? ["DECIMAL", "BOOLEAN"] : point.registerType === "COIL" ? ["BOOLEAN"] : ["UINT16", "INT16", "UINT32", "INT32"]} value={point.valueType} onValueChange={(value) => updatePoint(index, { valueType: value as EquipmentTelemetryValueType })}/>
      <GsInput aria-label={`点位 ${index + 1} 比例`} type="number" step="any" value={point.scale} onChange={(event) => updatePoint(index, { scale: event.target.value })}/>
      <GsInput aria-label={`点位 ${index + 1} 单位`} value={point.engineeringUnit} onChange={(event) => updatePoint(index, { engineeringUnit: event.target.value })} placeholder="单位"/>
      <GsButton aria-label={`删除点位 ${index + 1}`} htmlType="button" disabled={points.length === 1} onClick={() => setPoints((current) => current.filter((_, itemIndex) => itemIndex !== index))}><MaterialIcon name="delete" size={17}/></GsButton>
    </div>)}</section><label className="formField formFieldFull telemetryReason"><span>创建原因<em>必填</em></span><GsTextArea rows={2} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="说明设备、协议和只读用途"/></label>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="lock" size={16}/>{protocol === "MQTT_3_1_1" ? "只订阅外部 Broker；凭据从服务端环境变量解析" : "仅实现读取功能码，不允许远程写入"}</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">建立待测试连接</GsButton></div></footer></form> : <div className="businessEmptyState"><strong>没有可关联设备</strong><p>请先在设备台账建立一台未停用设备。</p></div>}
  </section></GsModalHost>;
}

function ConnectionActionDialog({ connection, action, onClose, onSaved }: { connection: EquipmentTelemetryConnection;
  action: ConnectionAction; onClose: () => void; onSaved: (connection: EquipmentTelemetryConnection, message: string, success: boolean) => void }) {
  const [reason, setReason] = useState(""); const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault(); if (reason.trim().length < 4) { setError("请填写至少 4 个字符的操作原因。"); return; }
    setPending(true); setError("");
    try {
      const result = await submitEquipmentTelemetryMutation({ operation: action, id: connection.id,
        reason: reason.trim(), expectedVersion: connection.version });
      if ("success" in result) onSaved(result.connection, result.message, result.success);
      else onSaved(result, `${actionLabels[action]}完成`, true);
    } catch (failure) { setError(errorText(failure)); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentGenerateDialog" role="dialog" aria-modal="true" aria-labelledby="telemetry-action-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={action === "pause" ? "pause_circle" : action === "poll" ? "sync" : "settings_input_antenna"} size={22}/></span><div><h2 id="telemetry-action-title">{actionLabels[action]}</h2><p>{connection.connectionCode} · 版本 {connection.version} · {endpointLabels[connection.endpointType]}</p></div><GsButton className="iconButton" aria-label="关闭设备采集操作" onClick={onClose} disabled={pending} htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>操作原因<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="说明本次测试、启停或立即采集原因"/></label></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>{action === "test" ? "测试会读取全部点位但不生成样本。" : action === "pause" ? "暂停不会删除历史样本。" : "采集结果不会修改人工设备状态。"}</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent={action === "pause" ? "danger" : "primary"} loading={pending} htmlType="submit">确认{actionLabels[action]}</GsButton></div></footer></form></section></GsModalHost>;
}

function RetentionDialog({ policy, onClose, onSaved }: { policy: EquipmentTelemetryRetentionPolicy;
  onClose: () => void; onSaved: (policy: EquipmentTelemetryRetentionPolicy, message: string) => void }) {
  const [retentionDays, setRetentionDays] = useState(String(policy.retentionDays));
  const [automaticCleanupEnabled, setAutomaticCleanupEnabled] = useState(policy.automaticCleanupEnabled);
  const [cleanupIntervalHours, setCleanupIntervalHours] = useState(String(policy.cleanupIntervalHours));
  const [reason, setReason] = useState(""); const [pending, setPending] = useState<string | null>(null);
  const [error, setError] = useState("");
  function valid() {
    const days = Number(retentionDays);
    if (!Number.isInteger(days) || days < 7 || days > 3650) { setError("保留天数必须是 7—3650 的整数。"); return false; }
    const interval = Number(cleanupIntervalHours);
    if (!Number.isInteger(interval) || interval < 1 || interval > 720) { setError("自动清理间隔必须是 1—720 小时的整数。"); return false; }
    if (reason.trim().length < 4) { setError("请填写至少 4 个字符的操作原因。"); return false; }
    return true;
  }
  async function update(event: FormEvent) {
    event.preventDefault(); if (!valid()) return; setPending("update"); setError("");
    try {
      const result = await submitEquipmentTelemetryLifecycleMutation({ operation: "updatePolicy",
        retentionDays: Number(retentionDays), automaticCleanupEnabled,
        cleanupIntervalHours: Number(cleanupIntervalHours), expectedVersion: policy.version, reason: reason.trim() });
      if ("retentionDays" in result) onSaved(result, `原始样本保留期已调整为 ${result.retentionDays} 天`);
    } catch (failure) { setError(errorText(failure)); setPending(null); }
  }
  async function cleanup() {
    if (!valid()) return; setPending("cleanup"); setError("");
    try {
      const result = await submitEquipmentTelemetryLifecycleMutation({ operation: "cleanup",
        expectedVersion: policy.version, reason: reason.trim() });
      if ("deletedSampleCount" in result) onSaved(result.policy,
        `${result.replayed ? "幂等返回" : "清理完成"}：删除 ${result.deletedSampleCount} 条过期样本`);
    } catch (failure) { setError(errorText(failure)); setPending(null); }
  }
  async function runNow() {
    if (!valid()) return; setPending("run"); setError("");
    try {
      const result = await submitEquipmentTelemetryLifecycleMutation({ operation: "runNow",
        expectedVersion: policy.version, reason: reason.trim() });
      if ("run" in result) onSaved(result.policy,
        `${result.replayed ? "幂等返回" : "自动清理完成"}：删除 ${result.run.deletedSampleCount} 条，剩余 ${result.run.remainingExpiredCount} 条`);
    } catch (failure) { setError(errorText(failure)); setPending(null); }
  }
  async function acknowledge(runId: string) {
    if (reason.trim().length < 4) { setError("请在操作原因中填写至少 4 个字符的确认与跟踪说明。"); return; }
    setPending(`ack-${runId}`); setError("");
    try {
      const result = await submitEquipmentTelemetryLifecycleMutation({ operation: "acknowledge", runId,
        note: reason.trim() });
      if ("run" in result) onSaved(result.policy, "自动清理失败责任已确认，跟踪证据已保存");
    } catch (failure) { setError(errorText(failure)); setPending(null); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog equipmentGenerateDialog telemetryRetentionDialog" role="dialog" aria-modal="true" aria-labelledby="telemetry-retention-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="inventory_2" size={22}/></span><div><h2 id="telemetry-retention-title">原始样本保留与自动清理</h2><p>截止时间之前有 {policy.expiredSampleCount} 条过期样本；单次自动任务最多删除 10,000 条。</p></div><GsButton className="iconButton" aria-label="关闭设备样本保留管理" onClick={onClose} disabled={Boolean(pending)} htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={update}><div className="formGrid"><label className="formField"><span>保留天数<em>必填</em></span><GsInput aria-label="原始样本保留天数" type="number" min="7" max="3650" value={retentionDays} onChange={(event) => setRetentionDays(event.target.value)}/></label><label className="formField"><span>自动间隔（小时）<em>必填</em></span><GsInput aria-label="自动样本清理间隔小时" type="number" min="1" max="720" disabled={!automaticCleanupEnabled || !policy.schedulerAvailable} value={cleanupIntervalHours} onChange={(event) => setCleanupIntervalHours(event.target.value)}/></label><label className="telemetryAutomationToggle formFieldFull"><GsCheckbox ariaLabel="启用自动样本清理" checked={automaticCleanupEnabled} disabled={!policy.schedulerAvailable} onCheckedChange={setAutomaticCleanupEnabled}/><span><strong>启用受控自动清理</strong><small>{policy.schedulerAvailable ? "保存后从下一计划时间开始，不会立即删除样本。" : "当前部署未启用自动运行器，只能手动清理。"}</small></span></label><label className="formField formFieldFull"><span>操作原因 / 失败确认说明<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="说明策略调整、立即运行或失败确认原因"/></label></div>{policy.automaticCleanupEnabled ? <div className={`telemetryAutomationState telemetryAutomationState${policy.lastAutomationStatus === "FAILED" ? "risk" : "info"}`}><MaterialIcon name={policy.lastAutomationStatus === "FAILED" ? "error" : "schedule"} size={18}/><span><strong>{policy.lastAutomationStatus === "FAILED" ? `连续失败 ${policy.consecutiveFailures} 次` : `下次计划 ${dateText(policy.nextCleanupAt)}`}</strong><small>最近运行 {policy.lastAutomationStatus ?? "尚无"} · {dateText(policy.lastAutomationCompletedAt)}</small></span></div> : null}{policy.automationRuns.length ? <section className="telemetryAutomationRuns" aria-label="自动清理运行证据"><header><strong>最近运行证据</strong><small>保留最近 {policy.automationRuns.length} 条</small></header>{policy.automationRuns.slice(0, 5).map((run) => <div key={run.id}><span><strong>{run.status} · 删除 {run.deletedSampleCount} 条</strong><small>{run.triggerType === "SCHEDULED" ? "计划运行" : "人工触发"} · {dateText(run.completedAt)} · 请求 {run.requestId}</small>{run.failureSummary ? <em>{run.failureSummary}</em> : null}</span>{run.status === "FAILED" && run.attentionStatus === "OPEN" ? <GsButton loading={pending === `ack-${run.id}`} disabled={Boolean(pending) && pending !== `ack-${run.id}`} onClick={() => acknowledge(run.id)} htmlType="button">确认责任</GsButton> : <em className={`businessStatus businessStatus${run.attentionStatus === "ACKNOWLEDGED" ? "info" : run.status === "FAILED" ? "risk" : "good"}`}>{run.attentionStatus === "ACKNOWLEDGED" ? "已确认" : run.status === "PARTIAL" ? "待续跑" : run.status === "FAILED" ? "待确认" : "完成"}</em>}</div>)}</section> : null}{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="shield" size={16}/>按工作区租约串行执行，连接、事件和未过期样本不会删除</span><div><GsButton onClick={onClose} disabled={Boolean(pending)} htmlType="button">取消</GsButton><GsButton intent="danger" loading={pending === "cleanup"} disabled={Boolean(pending) && pending !== "cleanup"} onClick={cleanup} htmlType="button">手动清理 {policy.expiredSampleCount} 条</GsButton>{policy.schedulerAvailable && policy.automaticCleanupEnabled ? <GsButton loading={pending === "run"} disabled={Boolean(pending) && pending !== "run"} onClick={runNow} htmlType="button">立即运行</GsButton> : null}<GsButton intent="primary" loading={pending === "update"} disabled={Boolean(pending) && pending !== "update"} htmlType="submit">保存策略</GsButton></div></footer></form></section></GsModalHost>;
}

function TelemetryLifecyclePanel({ connection }: { connection: EquipmentTelemetryConnection }) {
  const [history, setHistory] = useState<EquipmentTelemetrySamplePage | null>(null);
  const [policy, setPolicy] = useState<EquipmentTelemetryRetentionPolicy | null>(null);
  const [pointLabel, setPointLabel] = useState("全部点位"); const [qualityLabel, setQualityLabel] = useState("全部质量");
  const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [manageOpen, setManageOpen] = useState(false);
  const [notice, setNotice] = useState("");
  const pointOptions = ["全部点位", ...connection.points.map((point) => `${point.pointCode} · ${point.name}`)];
  const qualityOptions = ["全部质量", "正常 GOOD", "存疑 UNCERTAIN", "异常 BAD"];
  const pointCode = pointLabel === "全部点位" ? undefined : pointLabel.split(" · ")[0];
  const quality = qualityLabel === "全部质量" ? undefined : qualityLabel.split(" ").at(-1) as EquipmentTelemetryQuality;
  async function reloadHistory() {
    setLoading(true); setError("");
    try { setHistory(await loadEquipmentTelemetrySampleHistory({ connectionId: connection.id, pointCode, quality })); }
    catch (failure) { setError(errorText(failure)); }
    finally { setLoading(false); }
  }
  useEffect(() => {
    let active = true;
    Promise.all([loadEquipmentTelemetrySampleHistory({ connectionId: connection.id }), loadEquipmentTelemetryRetention()])
      .then(([nextHistory, nextPolicy]) => { if (active) { setHistory(nextHistory); setPolicy(nextPolicy); } })
      .catch((failure) => { if (active) setError(errorText(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [connection.id]);
  function display(sample: EquipmentTelemetrySamplePage["items"][number]) {
    const point = connection.points.find((item) => item.id === sample.pointId);
    if (sample.booleanValue !== null) return sample.booleanValue ? "是" : "否";
    return `${sample.numericValue ?? "—"}${point?.engineeringUnit ? ` ${point.engineeringUnit}` : ""}`;
  }
  return <section className="drawerSection telemetryLifecycleSection"><div className="sectionTitleCompact"><div><h3>有限历史与保留</h3><small>原始样本默认查询最近 24 小时，单次最多 31 天</small></div>{policy?.canManage ? <GsButton onClick={() => setManageOpen(true)} htmlType="button"><MaterialIcon name="tune" size={16}/>管理保留</GsButton> : null}</div>
    {policy ? <div className="telemetryRetentionSummary"><span><small>当前策略</small><strong>{policy.retentionDays} 天{policy.defaultPolicy ? " · 默认" : ""}</strong></span><span><small>自动清理</small><strong>{policy.automaticCleanupEnabled ? `每 ${policy.cleanupIntervalHours} 小时` : policy.schedulerAvailable ? "未启用" : "运行器未部署"}</strong></span><span><small>待清理</small><strong>{policy.expiredSampleCount} 条</strong></span><span><small>最近运行</small><strong>{policy.lastAutomationStatus ? `${policy.lastAutomationStatus} · ${dateText(policy.lastAutomationCompletedAt)}` : policy.events[0]?.action === "CLEANUP_COMPLETED" ? `手动清理 ${policy.events[0].deletedSampleCount} 条` : "尚无"}</strong></span></div> : null}
    <div className="telemetryHistoryFilters"><RoundedSelect ariaLabel="历史点位筛选" options={pointOptions} value={pointLabel} onValueChange={setPointLabel}/><RoundedSelect ariaLabel="历史质量筛选" options={qualityOptions} value={qualityLabel} onValueChange={setQualityLabel}/><GsButton onClick={reloadHistory} loading={loading} htmlType="button"><MaterialIcon name="filter_alt" size={16}/>应用筛选</GsButton></div>
    {error ? <div className="formError" role="alert">{error}</div> : null}{notice ? <div className="businessInlineNotice" role="status">{notice}</div> : null}
    {loading && !history ? <p className="telemetryHistoryState">正在读取有限历史与保留策略…</p> : history?.items.length ? <div className="telemetryHistoryTable" role="table" aria-label="设备原始样本历史"><div role="row"><span>接收时间</span><span>点位</span><span>值</span><span>质量</span><span>序列</span></div>{history.items.map((sample) => <div role="row" key={sample.id}><span>{dateText(sample.receivedAt)}</span><span>{sample.pointCode}</span><strong>{display(sample)}</strong><em className={sample.quality === "GOOD" ? "telemetryQualityGood" : sample.quality === "UNCERTAIN" ? "telemetryQualityWarn" : "telemetryQualityBad"}>{sample.quality}</em><span>#{sample.sequenceNumber}</span></div>)}</div> : !loading ? <div className="businessEmptyState telemetryHistoryState"><strong>当前筛选没有原始样本</strong><p>可以调整点位或质量筛选，也可以先执行一次只读采集。</p></div> : null}
    {history ? <small className="telemetryHistoryMeta">共 {history.totalElements} 条 · {dateText(history.windowFrom)} 至 {dateText(history.windowTo)}</small> : null}
    {manageOpen && policy ? <RetentionDialog policy={policy} onClose={() => setManageOpen(false)} onSaved={(nextPolicy, message) => { setPolicy(nextPolicy); setManageOpen(false); setNotice(message); void reloadHistory(); window.setTimeout(() => setNotice(""), 4200); }}/>: null}
  </section>;
}

function ConnectionDrawer({ connection, loading, error, onClose }: { connection: EquipmentTelemetryConnection;
  loading: boolean; error: string; onClose: () => void }) {
  const values = new Map(connection.currentValues.map((value) => [value.pointId, value]));
  const mqtt = connection.protocol === "MQTT_3_1_1";
  return <GsDrawer open onClose={onClose} ariaLabel="设备采集连接详情"><header className="recordDrawerHeader"><div><h2>{connection.name}</h2><p>{connection.connectionCode} · {endpointLabels[connection.endpointType]}</p></div><GsButton className="iconButton" aria-label="关闭设备采集连接详情" onClick={onClose} htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <section className="drawerSection"><div className="telemetryTruthBanner"><MaterialIcon name={connection.endpointType === "SIMULATOR" ? "science" : mqtt ? "hub" : "precision_manufacturing"} size={21}/><div><strong>{connection.endpointType === "SIMULATOR" ? "开发仿真协议数据" : mqtt ? "外部 Broker 配置" : "物理设备配置"}</strong><p>{connection.endpointType === "SIMULATOR" ? `已经过真实 ${protocolLabels[connection.protocol]} 协议链路，但不代表现场验收。` : mqtt ? "GuanSeq 只订阅用户选择的 Broker，不接管 Broker 和设备。" : "仍需结合厂商资料、网络和现场验收确认。"}</p></div></div><dl className="detailLedger"><div><dt>关联设备</dt><dd>{connection.assetName} · {connection.assetCode}</dd></div><div><dt>连接状态</dt><dd>{statusLabels[connection.status]}</dd></div><div><dt>通讯状态</dt><dd>{communicationLabels[connection.communicationStatus]}</dd></div><div><dt>端点</dt><dd>{connection.host ? `${connection.host}:${connection.port}` : `受限字段 · 端口 ${connection.port}`}</dd></div>{mqtt ? <><div><dt>传输 / QoS</dt><dd>{connection.mqtt?.transport ?? "受限"} / {connection.mqtt?.qos ?? "—"}</dd></div><div><dt>客户端标识</dt><dd>{connection.mqtt?.clientId ?? "受限字段"}</dd></div><div><dt>凭据</dt><dd>{connection.mqtt?.credentialConfigured ? `已配置别名 ${connection.mqtt.credentialReference ?? ""}` : "匿名连接"}</dd></div></> : <div><dt>单元标识</dt><dd>{connection.unitId}</dd></div>}<div><dt>采集周期</dt><dd>{connection.pollIntervalSeconds} 秒</dd></div><div><dt>最后成功</dt><dd>{dateText(connection.lastSuccessAt)}</dd></div><div><dt>最后错误</dt><dd>{connection.lastErrorMessage ?? "无"}</dd></div></dl>{loading ? <p>正在刷新详情…</p> : null}{error ? <div className="formError" role="alert">{error}</div> : null}</section>
    {!loading && !error ? <TelemetryVerificationPanel connection={connection}/> : null}
    {!loading && !error ? <EquipmentTelemetryFieldAcceptanceSection key={`acceptance-${connection.id}`} connectionId={connection.id}/> : null}
    <section className="drawerSection"><div className="sectionTitleCompact"><h3>点位与当前值</h3><span>{connection.points.length} 个</span></div><div className="telemetryValueList">{connection.points.map((point) => { const current = values.get(point.id); const display = current ? current.booleanValue !== null ? (current.booleanValue ? "是" : "否") : `${current.numericValue ?? "—"}${point.engineeringUnit ? ` ${point.engineeringUnit}` : ""}` : "尚未采集"; return <div key={point.id}><span><strong>{point.name}</strong><small>{mqtt ? `${point.pointCode} · ${point.mqttTopic ?? "受限 Topic"} ${point.mqttValuePointer ?? ""}` : `${point.pointCode} · ${point.registerType === "COIL" ? "COIL" : "HR"} ${point.address}（零基）`}</small></span><span><strong>{display}</strong><small className={current?.quality === "UNCERTAIN" ? "telemetryQualityWarn" : current ? "telemetryQualityGood" : ""}>{current?.quality ?? "NO_DATA"} · {dateText(current?.receivedAt ?? null)}</small></span></div>; })}</div></section>
    {!loading && !error && connection.points.length ? <TelemetryLifecyclePanel key={`lifecycle-${connection.id}`} connection={connection}/> : null}
  </GsDrawer>;
}

export function EquipmentTelemetryWorkspace({ initialData }: { initialData: EquipmentTelemetryPageData }) {
  const [page, setPage] = useState<EquipmentTelemetryConnectionPage | null>(initialData.page);
  const [message, setMessage] = useState(initialData.source === "unavailable" ? initialData.message : "");
  const [refreshing, setRefreshing] = useState(false); const [createOpen, setCreateOpen] = useState(false);
  const [selected, setSelected] = useState<EquipmentTelemetryConnection | null>(null); const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(""); const [action, setAction] = useState<ConnectionAction | null>(null);
  const [toast, setToast] = useState("");
  const assets = initialData.assets?.items ?? [];
  const metrics = useMemo(() => ({ active: page?.items.filter((item) => item.status === "ACTIVE").length ?? 0,
    online: page?.items.filter((item) => item.communicationStatus === "ONLINE").length ?? 0,
    simulator: page?.items.filter((item) => item.endpointType === "SIMULATOR").length ?? 0,
    field: page?.items.filter((item) => item.endpointType !== "SIMULATOR").length ?? 0 }), [page]);
  async function refresh() { setRefreshing(true); try { setPage(await refreshEquipmentTelemetryConnections()); setMessage(""); } catch (failure) { setMessage(errorText(failure)); } finally { setRefreshing(false); } }
  function replace(connection: EquipmentTelemetryConnection, text: string, success = true) {
    setPage((current) => current ? { ...current, items: current.items.some((item) => item.id === connection.id)
      ? current.items.map((item) => item.id === connection.id ? { ...connection, points: [], currentValues: [], events: [] } : item)
      : [{ ...connection, points: [], currentValues: [], events: [] }, ...current.items], totalElements: current.items.some((item) => item.id === connection.id) ? current.totalElements : current.totalElements + 1 } : current);
    setSelected(connection); setCreateOpen(false); setAction(null); setToast(`${success ? "完成" : "未通过"}：${text}`);
    window.setTimeout(() => setToast(""), 4200);
  }
  async function openDetail(connection: EquipmentTelemetryConnection) { setSelected(connection); setDetailLoading(true); setDetailError(""); try { setSelected(await loadEquipmentTelemetryConnectionDetail(connection.id)); } catch (failure) { setDetailError(errorText(failure)); } finally { setDetailLoading(false); } }
  if (!page) return <section className="backendUnavailableState" role="alert"><MaterialIcon name="cloud_off" size={26}/><strong>设备采集连接暂时不可用</strong><p>{message || "尚未取得设备采集连接数据。"}</p><GsButton onClick={refresh} loading={refreshing} htmlType="button">重新检查</GsButton></section>;
  return <section className="telemetryWorkspace"><section className="telemetryTruthBanner"><MaterialIcon name="developer_board" size={23}/><div><strong>可替换协议闭环</strong><p>Modbus TCP 与 MQTT 3.1.1 均使用生产只读适配器；MQTT 连接用户选择的外部 Broker，系统不内置或强制 EMQX。技术预检会保留证据，但仿真或连通通过都不等于现场验收。</p></div><span>只读采集</span></section>
    <header className="telemetryHeader"><div><span>设备与资产</span><h2>设备采集连接</h2><p>连接配置、通讯状态、数据质量和人工设备状态相互独立。</p></div><div><GsButton onClick={refresh} loading={refreshing} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新</GsButton>{page.canManage ? <GsButton intent="primary" onClick={() => setCreateOpen(true)} htmlType="button"><MaterialIcon name="add" size={17}/>接入设备</GsButton> : null}</div></header>
    <div className="telemetryMetrics"><div><small>全部连接</small><strong>{page.totalElements}</strong></div><div><small>启用采集</small><strong>{metrics.active}</strong></div><div><small>通讯正常</small><strong className={metrics.online ? "businessMetricgood" : ""}>{metrics.online}</strong></div><div><small>仿真 / 现场</small><strong>{metrics.simulator} / {metrics.field}</strong></div></div>
    <div className="telemetryConnectionTable" role="table" aria-label="设备采集连接列表"><div className="telemetryConnectionHeader" role="row"><span>连接 / 来源</span><span>设备</span><span>配置状态</span><span>通讯与最后成功</span><span>操作</span></div>{page.items.length ? page.items.map((connection) => <div className="telemetryConnectionRow" role="row" key={connection.id}><span><strong>{connection.name}</strong><small>{connection.connectionCode} · {endpointLabels[connection.endpointType]}</small></span><span><strong>{connection.assetName}</strong><small>{connection.assetCode} · {protocolLabels[connection.protocol]}</small></span><span><em className={`businessStatus businessStatus${connection.status === "ACTIVE" ? "good" : connection.status === "DRAFT" ? "warn" : "info"}`}>{statusLabels[connection.status]}</em><small>每 {connection.pollIntervalSeconds} 秒 · {connection.protocol === "MQTT_3_1_1" ? `QoS ${connection.mqtt?.qos ?? "—"}` : `单元 ${connection.unitId}`}</small></span><span><em className={`businessStatus businessStatus${connection.communicationStatus === "ONLINE" ? "good" : connection.communicationStatus === "OFFLINE" ? "risk" : "info"}`}>{communicationLabels[connection.communicationStatus]}</em><small>{dateText(connection.lastSuccessAt)}</small></span><span className="businessRowActions"><GsButton aria-label={`查看${connection.connectionCode}`} onClick={() => openDetail(connection)} htmlType="button"><MaterialIcon name="visibility" size={18}/></GsButton>{connection.canManage ? <><GsButton aria-label={`预检${connection.connectionCode}`} onClick={() => { setSelected(connection); setAction("test"); }} htmlType="button"><MaterialIcon name="fact_check" size={18}/></GsButton>{connection.status !== "ACTIVE" ? <GsButton aria-label={`启用${connection.connectionCode}`} onClick={() => { setSelected(connection); setAction("activate"); }} htmlType="button"><MaterialIcon name="play_arrow" size={18}/></GsButton> : <GsButton aria-label={`暂停${connection.connectionCode}`} onClick={() => { setSelected(connection); setAction("pause"); }} htmlType="button"><MaterialIcon name="pause" size={18}/></GsButton>}<GsButton aria-label={`采集${connection.connectionCode}`} onClick={() => { setSelected(connection); setAction("poll"); }} htmlType="button"><MaterialIcon name="sync" size={18}/></GsButton></> : null}</span></div>) : <div className="businessEmptyState"><MaterialIcon name="sensors_off" size={28}/><strong>尚未建立采集连接</strong><p>可以先使用开发仿真端点；取得真机或 Broker 后只替换端点配置。</p></div>}</div>
    {message ? <div className="formError" role="alert">{message}</div> : null}{selected && !action ? <ConnectionDrawer connection={selected} loading={detailLoading} error={detailError} onClose={() => setSelected(null)}/> : null}{createOpen ? <ConnectionCreateDialog assets={assets} onClose={() => setCreateOpen(false)} onSaved={(connection) => replace(connection, `${connection.connectionCode} 已建立`)}/> : null}{selected && action ? <ConnectionActionDialog connection={selected} action={action} onClose={() => setAction(null)} onSaved={replace}/> : null}{toast ? <div className="toastMessage" role="status"><MaterialIcon name="info" size={18}/>{toast}</div> : null}
  </section>;
}
