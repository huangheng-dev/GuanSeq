"use client";
import { GsButton, GsDrawerHost, GsInput, GsModalHost, GsPagination } from "./ui";
import { type FormEvent, useMemo, useRef, useState } from "react";
import type { OperationLaborEntry, OperationLaborStatus, OperationTaskRecord, OperationTaskStatus } from "@/lib/contracts";
import { fetchOperationTaskPage, submitOperationLaborMutation, submitOperationTaskAction } from "@/services/operation-task-client-service";
import type { OperationTaskPageData } from "@/services/operation-task-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const statusLabels: Record<OperationTaskStatus, string> = { PENDING: "待开工", IN_PROGRESS: "执行中", COMPLETED: "已完工" };
const statusTone: Record<OperationTaskStatus, string> = { PENDING: "info", IN_PROGRESS: "warn", COMPLETED: "good" };
const actionLabels: Record<string, string> = { CREATED: "生成快照", START: "开工", COMPLETE: "完工登记" };
const laborStatusLabels: Record<OperationLaborStatus, string> = { RECORDED: "待审核", APPROVED: "已审核", VOIDED: "已冲销" };
const laborStatusTone: Record<OperationLaborStatus, string> = { RECORDED: "warn", APPROVED: "good", VOIDED: "muted" };
const numberFormatter = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 3 });
const dateFormatter = new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false });
const formatQuantity = (value: number | null | undefined, unit?: string | null) => value == null ? "—" : `${numberFormatter.format(value)}${unit ? ` ${unit}` : ""}`;
const formatDate = (value: string | null | undefined) => value ? dateFormatter.format(new Date(value)) : "—";
const minutesText = (value: number) => value ? `${numberFormatter.format(value)} 分钟` : "—";
type DialogState = {
    type: "start";
    task: OperationTaskRecord;
} | {
    type: "complete";
    task: OperationTaskRecord;
} | {
    type: "detail";
    task: OperationTaskRecord;
} | {
    type: "laborRecord";
    task: OperationTaskRecord;
} | {
    type: "laborAction";
    entry: OperationLaborEntry;
    action: "APPROVE" | "VOID";
};
function useStableRequestId(prefix: string) {
    const [requestId] = useState(() => `web-${prefix}-${crypto.randomUUID()}`);
    return requestId;
}
function RequestEvidence({ requestId }: {
    requestId: string | null | undefined;
}) {
    return <span className="inlineEvidence"><MaterialIcon name="fingerprint" size={14}/>{requestId ?? "无请求号"}</span>;
}
function ActionDialog({ task, mode, onClose, onSaved }: {
    task: OperationTaskRecord;
    mode: "START" | "COMPLETE";
    onClose: () => void;
    onSaved: (task: OperationTaskRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const requestId = useStableRequestId(mode === "START" ? "operation-start" : "operation-complete");
    const [shiftName, setShiftName] = useState(task.shiftName ?? "白班");
    const [operatorName, setOperatorName] = useState(task.operatorName ?? "");
    const [completedQuantity, setCompletedQuantity] = useState(mode === "COMPLETE" ? String(task.completedQuantity ?? task.plannedQuantity) : "");
    const [note, setNote] = useState(task.note ?? "");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        if (!shiftName.trim() || !operatorName.trim()) {
            setError("班次和操作人必须填写。");
            return;
        }
        const quantity = mode === "COMPLETE" ? Number(completedQuantity) : null;
        if (mode === "COMPLETE" && (!Number.isFinite(quantity) || quantity! <= 0)) {
            setError("完工数量必须大于 0。");
            return;
        }
        if (mode === "COMPLETE" && quantity! > task.plannedQuantity) {
            setError(`完工数量不能超过订单计划数量 ${task.plannedQuantity} ${task.unit}。`);
            return;
        }
        setPending(true);
        try {
            onSaved(await submitOperationTaskAction({
                id: task.id,
                action: mode,
                expectedVersion: task.version,
                shiftName: shiftName.trim(),
                operatorName: operatorName.trim(),
                completedQuantity: quantity,
                note: note.trim() || null,
            }, requestId));
        }
        catch (cause) {
            setError(cause instanceof Error ? cause.message : "工序动作未保存");
            setPending(false);
        }
    }
    const title = mode === "START" ? "开始工序" : "完工登记";
    const subtitle = mode === "START" ? "记录班次、操作人和开工时间，订单将自动进入执行中。" : "核对完工数量；全部工序完成后才允许订单级报工。";
    return (<GsModalHost onClose={() => { if (!pending)
        onClose(); }}>
      <section ref={ref} className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="operation-task-action-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={mode === "START" ? "play_circle" : "task_alt"} size={22}/></span><div><h2 id="operation-task-action-title">{title}</h2><p>{subtitle}</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭工序操作表单" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header>
        <form onSubmit={submit}>
          <div className="formGrid">
            <label className="formField formFieldFull"><span>工序 / 工作中心</span><GsInput readOnly value={`${task.sequenceNumber} · ${task.operationCode} · ${task.workCenterName}`}/></label>
            <label className="formField"><span>班次<em>必填</em></span><GsInput value={shiftName} onChange={(event) => setShiftName(event.target.value)} maxLength={80} placeholder="白班"/></label>
            <label className="formField"><span>操作人<em>必填</em></span><GsInput value={operatorName} onChange={(event) => setOperatorName(event.target.value)} maxLength={80} placeholder="陈磊"/></label>
            {mode === "COMPLETE" ? <label className="formField"><span>完工数量<em>必填</em></span><GsInput type="number" inputMode="decimal" min="0.000001" max={task.plannedQuantity} step="0.000001" value={completedQuantity} onChange={(event) => setCompletedQuantity(event.target.value)}/></label> : <label className="formField"><span>计划数量</span><GsInput readOnly value={formatQuantity(task.plannedQuantity, task.unit)}/></label>}
            <label className="formField formFieldFull"><span>作业备注</span><GsInput value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} placeholder="记录异常、交接或自检结论"/></label>
          </div>
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
          <footer className="dialogFooter"><span><MaterialIcon name="fingerprint" size={16}/>同一请求重复提交不会重复记账</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "保存中" : mode === "START" ? "确认开工" : "确认完工"}</GsButton></div></footer>
        </form>
      </section>
    </GsModalHost>);
}

function LaborRecordDialog({ task, onClose, onSaved }: {
    task: OperationTaskRecord;
    onClose: () => void;
    onSaved: (entry: OperationLaborEntry) => void;
}) {
    const requestId = useStableRequestId("operation-labor-record");
    const [workDate, setWorkDate] = useState(new Date().toISOString().slice(0, 10));
    const [shiftName, setShiftName] = useState(task.shiftName ?? "白班");
    const [operatorName, setOperatorName] = useState(task.operatorName ?? "");
    const [actualMinutes, setActualMinutes] = useState("");
    const [note, setNote] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        const minutes = Number(actualMinutes);
        if (!workDate || !shiftName.trim() || !operatorName.trim()) {
            setError("工作日期、班次和操作人必须填写。");
            return;
        }
        if (!Number.isFinite(minutes) || minutes <= 0 || minutes > 1440) {
            setError("实际人工分钟必须大于 0 且不超过 1440。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitOperationLaborMutation({
                kind: "CREATE",
                taskId: task.id,
                workDate,
                shiftName: shiftName.trim(),
                operatorName: operatorName.trim(),
                actualMinutes: minutes,
                note: note.trim() || null,
            }, requestId));
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "实际人工工时未保存");
            setPending(false);
        }
    }
    return (<GsModalHost onClose={() => { if (!pending) onClose(); }}>
      <section className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="labor-record-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="schedule" size={22}/></span><div><h2 id="labor-record-title">登记实际人工工时</h2><p>{task.taskNumber} · {task.operationCode} · {task.operationName}</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭工时登记表单" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header>
        <form onSubmit={submit}>
          <div className="formGrid">
            <label className="formField formFieldFull"><span>生产订单 / 工作中心</span><GsInput readOnly value={`${task.orderNumber} · ${task.workCenterCode} · ${task.workCenterName}`}/></label>
            <label className="formField"><span>工作日期<em>必填</em></span><GsInput type="date" max={new Date().toISOString().slice(0, 10)} value={workDate} onChange={(event) => setWorkDate(event.target.value)}/></label>
            <label className="formField"><span>实际人工分钟<em>必填</em></span><GsInput type="number" min="0.01" max="1440" step="0.01" inputMode="decimal" value={actualMinutes} onChange={(event) => setActualMinutes(event.target.value)} placeholder="例如 95"/></label>
            <label className="formField"><span>班次<em>必填</em></span><GsInput value={shiftName} onChange={(event) => setShiftName(event.target.value)} maxLength={80}/></label>
            <label className="formField"><span>操作人<em>必填</em></span><GsInput value={operatorName} onChange={(event) => setOperatorName(event.target.value)} maxLength={80}/></label>
            <label className="formField formFieldFull"><span>工时说明</span><GsInput value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} placeholder="说明协作、换班或异常投入情况"/></label>
          </div>
          <div className="formHint"><MaterialIcon name="info" size={17}/>登记后进入待审核；审核前不参与订单人工成本。</div>
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
          <footer className="dialogFooter"><span><MaterialIcon name="fingerprint" size={16}/>请求号保证重复提交不会重复记工</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" htmlType="submit" disabled={pending}>{pending ? "登记中" : "确认登记"}</GsButton></div></footer>
        </form>
      </section>
    </GsModalHost>);
}

function LaborActionDialog({ entry, action, onClose, onSaved }: {
    entry: OperationLaborEntry;
    action: "APPROVE" | "VOID";
    onClose: () => void;
    onSaved: (entry: OperationLaborEntry) => void;
}) {
    const requestId = useStableRequestId(action === "APPROVE" ? "operation-labor-approve" : "operation-labor-void");
    const [reason, setReason] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        if (action === "VOID" && !reason.trim()) {
            setError("冲销工时必须填写原因。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitOperationLaborMutation({ kind: "ACTION", id: entry.id, action, expectedVersion: entry.version, reason: reason.trim() || null }, requestId));
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "工时动作未保存");
            setPending(false);
        }
    }
    const approving = action === "APPROVE";
    return (<GsModalHost onClose={() => { if (!pending) onClose(); }}>
      <section className="businessDialog businessConfirmDialog" role="dialog" aria-modal="true" aria-labelledby="labor-action-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={approving ? "verified" : "undo"} size={22}/></span><div><h2 id="labor-action-title">{approving ? "审核实际人工工时" : "冲销实际人工工时"}</h2><p>{entry.entryNumber} · {entry.operatorName} · {numberFormatter.format(entry.actualMinutes)} 分钟</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭工时操作表单" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header>
        <form onSubmit={submit}>
          <div className="formGrid"><label className="formField formFieldFull"><span>{approving ? "审核说明" : <>冲销原因<em>必填</em></>}</span><GsInput value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} placeholder={approving ? "可填写核对依据" : "说明错误原因和后续处理"}/></label></div>
          <div className="formHint"><MaterialIcon name="info" size={17}/>{approving ? "审核通过后该工时将进入订单人工成本归集。" : "冲销保留原始工时和全部审计证据，不可恢复；如需更正请重新登记。"}</div>
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
          <footer className="dialogFooter"><span><MaterialIcon name="fingerprint" size={16}/>动作使用版本号与请求号控制并发</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" htmlType="submit" disabled={pending}>{pending ? "处理中" : approving ? "确认审核" : "确认冲销"}</GsButton></div></footer>
        </form>
      </section>
    </GsModalHost>);
}

function DetailDrawer({ task, laborEntries, onClose, onAction }: {
    task: OperationTaskRecord;
    laborEntries: OperationLaborEntry[];
    onClose: () => void;
    onAction: (dialog: DialogState) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const approvedMinutes = laborEntries.filter((entry) => entry.status === "APPROVED").reduce((sum, entry) => sum + entry.actualMinutes, 0);
    return (<GsDrawerHost onClose={onClose}><aside ref={ref} className="recordDrawer" role="dialog" aria-modal="true" aria-labelledby="operation-task-detail-title" onMouseDown={(event) => event.stopPropagation()}>
      <header className="recordDrawerHeader"><div><h2 id="operation-task-detail-title">{task.taskNumber}</h2><p>{task.orderNumber} · {task.materialCode} · {task.materialName}</p></div><GsButton className="iconButton" aria-label="关闭工序详情" onClick={onClose} htmlType="submit"><MaterialIcon name="close"/></GsButton></header>
      <div className="recordDrawerBody">
        <section className="salesOrderSummary"><div><small>状态</small><strong className={`businessStatus businessStatus${statusTone[task.status]}`}>{statusLabels[task.status]}</strong></div><div><small>工序顺序</small><strong>{task.sequenceNumber}</strong></div><div><small>完工数量</small><strong>{formatQuantity(task.completedQuantity, task.unit)}</strong></div><div><small>检验点</small><strong>{task.inspectionRequired ? "需要" : "不需要"}</strong></div></section>
        <section className="drawerSection"><header><h3>路线与工作中心</h3></header><div className="detailLedger"><div><span>工艺路线</span><strong>{task.routingNumber} · {task.routingVersionCode}</strong></div><div><span>工序编码</span><strong>{task.operationCode}</strong></div><div><span>工序名称</span><strong>{task.operationName}</strong></div><div><span>工作中心</span><strong>{task.workCenterCode} · {task.workCenterName}</strong></div><div><span>准备工时</span><strong>{minutesText(task.setupMinutes)}</strong></div><div><span>单件工时</span><strong>{minutesText(task.runMinutesPerUnit)}</strong></div><div><span>排队工时</span><strong>{minutesText(task.queueMinutes)}</strong></div><div><span>计划数量</span><strong>{formatQuantity(task.plannedQuantity, task.unit)}</strong></div></div></section>
        {task.instructionSummary ? <section className="drawerSection"><header><h3>作业摘要</h3></header><p>{task.instructionSummary}</p></section> : null}
        <section className="drawerSection"><header><h3>执行证据</h3></header><div className="detailLedger"><div><span>班次</span><strong>{task.shiftName ?? "—"}</strong></div><div><span>操作人</span><strong>{task.operatorName ?? "—"}</strong></div><div><span>开工时间</span><strong>{formatDate(task.startedAt)}</strong></div><div><span>完工时间</span><strong>{formatDate(task.completedAt)}</strong></div><div><span>开工请求</span><RequestEvidence requestId={task.events.find((event) => event.action === "START")?.requestId}/></div><div><span>完工请求</span><RequestEvidence requestId={task.events.find((event) => event.action === "COMPLETE")?.requestId}/></div></div></section>
        <section className="drawerSection laborEntrySection"><header><div><h3>实际人工工时</h3><p>已审核 {numberFormatter.format(approvedMinutes)} 分钟，只有已审核记录参与订单人工成本。</p></div>{task.status !== "PENDING" ? <GsButton className="secondaryButton" onClick={() => onAction({ type: "laborRecord", task })} htmlType="button"><MaterialIcon name="add" size={16}/>登记工时</GsButton> : null}</header>
          <div className="laborEntryList">{laborEntries.length ? laborEntries.map((entry) => <article key={entry.id}><div><strong>{entry.operatorName} · {numberFormatter.format(entry.actualMinutes)} 分钟</strong><small>{entry.workDate} · {entry.shiftName} · {entry.entryNumber}</small></div><em className={`businessStatus businessStatus${laborStatusTone[entry.status]}`}>{laborStatusLabels[entry.status]}</em><div className="laborEntryActions">{entry.status === "RECORDED" && task.status === "COMPLETED" ? <GsButton onClick={() => onAction({ type: "laborAction", entry, action: "APPROVE" })} htmlType="button">审核</GsButton> : null}{entry.status !== "VOIDED" ? <GsButton onClick={() => onAction({ type: "laborAction", entry, action: "VOID" })} htmlType="button">冲销</GsButton> : null}</div></article>) : <div className="businessEmptyState compactEmptyState"><MaterialIcon name="schedule" size={22}/><strong>尚未登记实际人工工时</strong><p>工序开工后可按操作人分别登记，完工后由生产负责人审核。</p></div>}</div>
        </section>
        <section className="drawerSection"><header><h3>审计事件</h3></header><div className="eventList">{task.events.length ? task.events.map((event) => <div key={event.id}><strong>{actionLabels[event.action] ?? event.action}</strong><span>{formatDate(event.occurredAt)}</span><RequestEvidence requestId={event.requestId}/></div>) : <p>暂无事件</p>}</div></section>
      </div>
      <footer className="recordDrawerFooter">{task.status === "PENDING" ? <GsButton className="primaryButton" onClick={() => onAction({ type: "start", task })} htmlType="button"><MaterialIcon name="play_circle" size={17}/>开始工序</GsButton> : null}{task.status === "IN_PROGRESS" ? <GsButton className="primaryButton" onClick={() => onAction({ type: "complete", task })} htmlType="button"><MaterialIcon name="task_alt" size={17}/>完工登记</GsButton> : null}{task.status !== "PENDING" ? <GsButton className="secondaryButton" onClick={() => onAction({ type: "laborRecord", task })} htmlType="button"><MaterialIcon name="schedule" size={17}/>登记工时</GsButton> : null}<GsButton className="secondaryButton" onClick={onClose} htmlType="button">关闭</GsButton></footer>
    </aside></GsDrawerHost>);
}
export function OperationTaskWorkspace({ initialData }: {
    initialData: OperationTaskPageData;
}) {
    const [data, setData] = useState(initialData);
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState("全部状态");
    const [loading, setLoading] = useState(false);
    const [dialog, setDialog] = useState<DialogState | null>(null);
    const [toast, setToast] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const metrics = useMemo(() => ({
        pending: data.tasks.filter((item) => item.status === "PENDING").length,
        inProgress: data.tasks.filter((item) => item.status === "IN_PROGRESS").length,
        completed: data.tasks.filter((item) => item.status === "COMPLETED").length,
        laborPending: data.laborEntries.filter((item) => item.status === "RECORDED").length,
    }), [data.tasks, data.laborEntries]);
    const filtered = useMemo(() => {
        const keyword = query.trim().toLowerCase();
        return data.tasks.filter((item) => {
            if (status !== "全部状态" && statusLabels[item.status] !== status)
                return false;
            if (!keyword)
                return true;
            return [item.taskNumber, item.orderNumber, item.materialCode, item.materialName, item.operationCode, item.operationName, item.workCenterCode, item.workCenterName].some((value) => value.toLowerCase().includes(keyword));
        });
    }, [data.tasks, query, status]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    async function refresh() {
        setLoading(true);
        try {
            setData(await fetchOperationTaskPage());
        }
        catch (error) {
            setData({ source: "unavailable", tasks: [], laborEntries: [], error: error instanceof Error ? error.message : "刷新失败" });
        }
        finally {
            setLoading(false);
        }
    }
    function saved(task: OperationTaskRecord, message: string) {
        setData((current) => ({ ...current, source: "backend", tasks: current.tasks.map((item) => item.id === task.id ? task : item), error: undefined }));
        setDialog(null);
        setToast(message);
        window.setTimeout(() => setToast(""), 3500);
    }
    function laborSaved(entry: OperationLaborEntry, message: string) {
        setData((current) => {
            const exists = current.laborEntries.some((item) => item.id === entry.id);
            return { ...current, source: "backend", laborEntries: exists ? current.laborEntries.map((item) => item.id === entry.id ? entry : item) : [entry, ...current.laborEntries], error: undefined };
        });
        const task = data.tasks.find((item) => item.id === entry.taskId);
        setDialog(task ? { type: "detail", task } : null);
        setToast(message);
        window.setTimeout(() => setToast(""), 3500);
    }
    const approvedMinutesFor = (taskId: string) => data.laborEntries.filter((entry) => entry.taskId === taskId && entry.status === "APPROVED").reduce((sum, entry) => sum + entry.actualMinutes, 0);
    const currentTask = dialog?.type === "detail" ? data.tasks.find((item) => item.id === dialog.task.id) ?? dialog.task : null;
    return (<div className="businessPage inventoryPage">
      <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="route" size={23}/></span><div><h2>车间工序执行</h2><p>下达订单即冻结工艺路线快照，逐序记录开工、完工和责任证据，全部完成后方可订单报工。</p></div></div><div className="pageHeadingActions"><GsButton className="secondaryButton" onClick={refresh} disabled={loading} htmlType="submit"><MaterialIcon name="refresh" size={17}/>{loading ? "刷新中" : "刷新"}</GsButton></div></header>
      {data.error ? <div className="formError pageError" role="alert"><MaterialIcon name="error" size={18}/>{data.error}</div> : null}
      <section className="businessMetrics"><div><small>待开工</small><strong className="businessMetricinfo">{metrics.pending}</strong><em>等待班次和操作人</em></div><div><small>执行中</small><strong className="businessMetricwarn">{metrics.inProgress}</strong><em>正在加工或测试</em></div><div><small>已完工</small><strong className="businessMetricgood">{metrics.completed}</strong><em>可进入订单报工</em></div><div><small>待审工时</small><strong className={metrics.laborPending ? "businessMetricwarn" : "businessMetricgood"}>{metrics.laborPending}</strong><em>审核后进入人工成本</em></div></section>
      <section className="businessLedger inventoryLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索工序任务" placeholder="搜索任务号、订单、物料、工序或工作中心" value={query} onChange={(event) => setQuery(event.target.value)}/></div><RoundedSelect ariaLabel="工序任务状态" options={["全部状态", ...Object.values(statusLabels)]} value={status} onValueChange={setStatus}/></div>
        <div className="salesOrderTable" role="table" aria-label="车间工序任务列表"><div className="salesOrderTableHeader" role="row"><span>工序任务 / 订单</span><span>产品 / 工作中心</span><span>工时证据</span><span>完工数量</span><span>状态</span><span>操作</span></div>{filtered.length ? pageRows.map((task) => <div className="salesOrderTableRow" role="row" key={task.id} onClick={() => setDialog({ type: "detail", task })}><span><strong>{task.taskNumber}</strong><small>{task.orderNumber} · {task.sequenceNumber} {task.operationCode}</small></span><span><strong>{task.materialCode} · {task.materialName}</strong><small>{task.workCenterCode} · {task.workCenterName}{task.inspectionRequired ? " · 需检验" : ""}</small></span><span><strong>已审人工 {numberFormatter.format(approvedMinutesFor(task.id))} 分</strong><small>标准：准备 {numberFormatter.format(task.setupMinutes)} / 单件 {numberFormatter.format(task.runMinutesPerUnit)} 分</small></span><span><strong>{formatQuantity(task.completedQuantity, task.unit)}</strong><small>计划 {numberFormatter.format(task.plannedQuantity)} {task.unit}</small></span><em className={`businessStatus businessStatus${statusTone[task.status]}`}>{statusLabels[task.status]}</em><span className="businessRowActions">{task.status === "PENDING" ? <GsButton onClick={(event) => { event.stopPropagation(); setDialog({ type: "start", task }); }} htmlType="button">开工</GsButton> : null}{task.status === "IN_PROGRESS" ? <GsButton onClick={(event) => { event.stopPropagation(); setDialog({ type: "complete", task }); }} htmlType="button">完工</GsButton> : null}{task.status !== "PENDING" ? <GsButton aria-label={`为${task.taskNumber}登记工时`} onClick={(event) => { event.stopPropagation(); setDialog({ type: "laborRecord", task }); }} htmlType="button"><MaterialIcon name="schedule" size={17}/></GsButton> : null}<GsButton aria-label={`查看${task.taskNumber}详情`} onClick={(event) => { event.stopPropagation(); setDialog({ type: "detail", task }); }} htmlType="button"><MaterialIcon name="chevron_right" size={19}/></GsButton></span></div>) : <div className="businessEmptyState"><MaterialIcon name="route" size={28}/><strong>{data.source === "unavailable" ? "暂无可显示的工序数据" : "没有符合条件的工序任务"}</strong><p>{data.source === "unavailable" ? "请检查后端服务、登录状态或当前账号权限。" : "下达生产订单后，系统会按已发布工艺路线生成工序任务。"}</p></div>}</div><footer className="businessLedgerFooter"><span>共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer>
      </section>
      {dialog?.type === "start" ? <ActionDialog mode="START" task={dialog.task} onClose={() => setDialog(null)} onSaved={(task) => saved(task, `${task.taskNumber} 已开工`)}/> : null}
      {dialog?.type === "complete" ? <ActionDialog mode="COMPLETE" task={dialog.task} onClose={() => setDialog(null)} onSaved={(task) => saved(task, `${task.taskNumber} 完工已登记`)}/> : null}
      {dialog?.type === "laborRecord" ? <LaborRecordDialog task={dialog.task} onClose={() => setDialog({ type: "detail", task: dialog.task })} onSaved={(entry) => laborSaved(entry, `${entry.entryNumber} 已登记，等待审核`)}/> : null}
      {dialog?.type === "laborAction" ? <LaborActionDialog entry={dialog.entry} action={dialog.action} onClose={() => { const task = data.tasks.find((item) => item.id === dialog.entry.taskId); setDialog(task ? { type: "detail", task } : null); }} onSaved={(entry) => laborSaved(entry, entry.status === "APPROVED" ? `${entry.entryNumber} 已审核` : `${entry.entryNumber} 已冲销`)}/> : null}
      {currentTask ? <DetailDrawer task={currentTask} laborEntries={data.laborEntries.filter((entry) => entry.taskId === currentTask.id)} onClose={() => setDialog(null)} onAction={setDialog}/> : null}
      {toast ? <div className="toastMessage" role="status"><MaterialIcon name="task_alt" filled size={18}/>{toast}</div> : null}
    </div>);
}

