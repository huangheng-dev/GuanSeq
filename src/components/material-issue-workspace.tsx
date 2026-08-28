"use client";
import { GsButton, GsInput, GsModalHost, GsPagination, GsTextArea } from "./ui";
import { type FormEvent, useMemo, useRef, useState } from "react";
import type { MaterialIssueRecord, MaterialIssueStatus } from "@/lib/contracts";
import { createMaterialIssue, fetchMaterialIssuePage, submitMaterialIssueAction, submitMaterialIssueReturn, } from "@/services/material-issue-client-service";
import type { MaterialIssuePageData } from "@/services/material-issue-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const statusLabels: Record<MaterialIssueStatus, string> = { DRAFT: "待发料", PARTIAL: "部分发料", ISSUED: "已发齐", CANCELLED: "已取消" };
const statusTone: Record<MaterialIssueStatus, string> = { DRAFT: "info", PARTIAL: "warn", ISSUED: "good", CANCELLED: "risk" };
const actionLabels: Record<string, string> = { CREATED: "生成领料单", ISSUE: "生产发料", RETURN: "组件退料", CANCEL: "取消领料单" };
const quantityFormatter = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 3 });
const formatQuantity = (value: number) => quantityFormatter.format(value);
const formatDate = (value: string) => new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value));
const sum = (items: MaterialIssueRecord["lines"], field: "requiredQuantity" | "issuedQuantity" | "returnedQuantity") => items.reduce((total, item) => total + item[field], 0);
const issueProgress = (issue: MaterialIssueRecord) => {
    const required = sum(issue.lines, "requiredQuantity");
    return required <= 0 ? 0 : Math.min(100, Math.round((sum(issue.lines, "issuedQuantity") / required) * 100));
};
type DialogState = {
    type: "create";
} | {
    type: "issue";
    issue: MaterialIssueRecord;
} | {
    type: "return";
    issue: MaterialIssueRecord;
} | {
    type: "cancel";
    issue: MaterialIssueRecord;
} | {
    type: "detail";
    issue: MaterialIssueRecord;
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
function CreateIssueDialog({ data, onClose, onSaved }: {
    data: MaterialIssuePageData;
    onClose: () => void;
    onSaved: (issue: MaterialIssueRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const requestId = useStableRequestId("material-issue-create");
    const orders = data.reference.productionOrders;
    const warehouses = data.reference.warehouses;
    const [orderId, setOrderId] = useState(orders[0]?.id ?? "");
    const [warehouseId, setWarehouseId] = useState(warehouses.find((item) => item.code === "WH-RM")?.id ?? warehouses[0]?.id ?? "");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const selectedOrder = orders.find((item) => item.id === orderId);
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        if (!selectedOrder || !warehouseId) {
            setError("暂无可备料生产订单或启用仓库，请先下达生产订单并维护仓库。");
            return;
        }
        setPending(true);
        try {
            onSaved(await createMaterialIssue({ productionOrderId: orderId, warehouseId }, requestId));
        }
        catch (cause) {
            setError(cause instanceof Error ? cause.message : "生产领料单生成失败");
            setPending(false);
        }
    }
    return (<GsModalHost onClose={() => { if (!pending)
        onClose(); }}>
      <section ref={ref} className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="material-issue-create-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="inventory_2" size={22}/></span><div><h2 id="material-issue-create-title">生成生产领料单</h2><p>按已发布 BOM、订单数量和损耗率自动计算组件需求。</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭生成领料单表单" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header>
        <form onSubmit={submit}>
          <div className="formGrid">
            <label className="formField formFieldFull"><span>生产订单<em>必填</em></span><RoundedSelect ariaLabel="选择生产订单" options={orders.map((item) => `${item.orderNumber} · ${item.materialCode}`)} value={selectedOrder ? `${selectedOrder.orderNumber} · ${selectedOrder.materialCode}` : ""} onValueChange={(value) => setOrderId(orders.find((item) => `${item.orderNumber} · ${item.materialCode}` === value)?.id ?? "")}/></label>
            <label className="formField"><span>计划数量</span><GsInput readOnly value={selectedOrder ? `${formatQuantity(selectedOrder.plannedQuantity)} ${selectedOrder.unit}` : "—"}/></label>
            <label className="formField"><span>计划开工</span><GsInput readOnly value={selectedOrder?.plannedStartDate ?? "—"}/></label>
            <label className="formField formFieldFull"><span>发料仓库<em>必填</em></span><RoundedSelect ariaLabel="选择发料仓库" options={warehouses.map((item) => `${item.code} · ${item.name}`)} value={warehouses.find((item) => item.id === warehouseId) ? `${warehouses.find((item) => item.id === warehouseId)?.code} · ${warehouses.find((item) => item.id === warehouseId)?.name}` : ""} onValueChange={(value) => setWarehouseId(warehouses.find((item) => `${item.code} · ${item.name}` === value)?.id ?? "")}/></label>
          </div>
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
          <footer className="dialogFooter"><span><MaterialIcon name="fingerprint" size={16}/>重复提交同一请求不会重复生成</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" disabled={pending || !selectedOrder || !warehouseId} htmlType="submit">{pending ? "正在生成" : "生成领料单"}</GsButton></div></footer>
        </form>
      </section>
    </GsModalHost>);
}
function IssueDialog({ issue, onClose, onSaved }: {
    issue: MaterialIssueRecord;
    onClose: () => void;
    onSaved: (issue: MaterialIssueRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const requestId = useStableRequestId("material-issue-action");
    const issuableLines = issue.lines.filter((line) => line.issuableQuantity > 0);
    const [quantities, setQuantities] = useState<Record<string, string>>(() => Object.fromEntries(issuableLines.map((line) => [line.id, String(line.issuableQuantity)])));
    const [comment, setComment] = useState(`按 ${issue.orderNumber} 生产进度发料`);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        const lines = issuableLines.map((line) => ({ lineId: line.id, quantity: Number(quantities[line.id] ?? 0), expectedLineVersion: line.version }));
        if (lines.length === 0 || lines.some((line) => !Number.isFinite(line.quantity) || line.quantity <= 0)) {
            setError("请至少填写一行大于 0 的本次发料数量。");
            return;
        }
        const overflow = lines.find((line) => line.quantity > (issuableLines.find((item) => item.id === line.lineId)?.issuableQuantity ?? 0));
        if (overflow) {
            setError("发料数量不能超过可领数量；若页面数据已变化，请刷新后重试。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitMaterialIssueAction({ id: issue.id, action: "ISSUE", expectedVersion: issue.version, comment: comment.trim() || "生产领料", source: "DESKTOP_FORM", lines }, requestId));
        }
        catch (cause) {
            setError(cause instanceof Error ? cause.message : "生产发料失败");
            setPending(false);
        }
    }
    return (<GsModalHost onClose={() => { if (!pending)
        onClose(); }}>
      <section ref={ref} className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="material-issue-issue-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="output" size={22}/></span><div><h2 id="material-issue-issue-title">生产发料</h2><p>{issue.issueNumber} · 扣减 {issue.warehouseName} 合格库存</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭生产发料表单" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header>
        <form onSubmit={submit}>
          <div className="dialogLineList">{issuableLines.map((line) => <label key={line.id} className="dialogLineRow"><span><strong>{line.componentMaterialCode}</strong><small>{line.componentMaterialName}</small></span><span><b>可领 {formatQuantity(line.issuableQuantity)} {line.unit}</b><GsInput type="number" min="0.000001" max={line.issuableQuantity} step="0.000001" value={quantities[line.id] ?? ""} onChange={(event) => setQuantities((current) => ({ ...current, [line.id]: event.target.value }))}/></span></label>)}</div>
          <label className="formField formFieldFull"><span>发料说明</span><GsTextArea maxLength={500} value={comment} onChange={(event) => setComment(event.target.value)}/></label>
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
          <footer className="dialogFooter"><span><RequestEvidence requestId={requestId}/></span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "正在过账" : "确认发料"}</GsButton></div></footer>
        </form>
      </section>
    </GsModalHost>);
}
function ReturnDialog({ issue, locations, onClose, onSaved }: {
    issue: MaterialIssueRecord;
    locations: MaterialIssuePageData["reference"]["locations"];
    onClose: () => void;
    onSaved: (issue: MaterialIssueRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const requestId = useStableRequestId("material-issue-return");
    const returnableLines = issue.lines.filter((line) => line.issuedQuantity - line.returnedQuantity > 0.0000001);
    const issueLocations = locations.filter((item) => item.warehouseId === issue.warehouseId);
    const [locationId, setLocationId] = useState(issueLocations.find((item) => item.code === "A-01-03")?.id ?? issueLocations[0]?.id ?? "");
    const [quantities, setQuantities] = useState<Record<string, string>>(() => Object.fromEntries(returnableLines.map((line) => [line.id, String(line.issuedQuantity - line.returnedQuantity)])));
    const [reason, setReason] = useState("未使用组件退回原材料仓");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        if (!locationId) {
            setError("请选择属于发料仓库的退料库位。");
            return;
        }
        const lines = returnableLines.map((line) => ({ lineId: line.id, quantity: Number(quantities[line.id] ?? 0), expectedLineVersion: line.version, reason: reason.trim() }));
        if (!reason.trim() || lines.some((line) => !Number.isFinite(line.quantity) || line.quantity <= 0)) {
            setError("请填写退料原因和大于 0 的退料数量。");
            return;
        }
        const overflow = lines.find((line) => line.quantity > (returnableLines.find((item) => item.id === line.lineId)?.issuedQuantity ?? 0) - (returnableLines.find((item) => item.id === line.lineId)?.returnedQuantity ?? 0));
        if (overflow) {
            setError("退料数量不能超过累计已领未退数量。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitMaterialIssueReturn({ id: issue.id, locationId, reason: reason.trim(), lines }, requestId));
        }
        catch (cause) {
            setError(cause instanceof Error ? cause.message : "组件退料失败");
            setPending(false);
        }
    }
    return (<GsModalHost onClose={() => { if (!pending)
        onClose(); }}>
      <section ref={ref} className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="material-issue-return-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="undo" size={22}/></span><div><h2 id="material-issue-return-title">组件退料</h2><p>退料只会回补已领组件，不能撤销下游生产或成本事实。</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭组件退料表单" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header>
        <form onSubmit={submit}>
          <div className="formGrid"><label className="formField formFieldFull"><span>退回库位<em>必填</em></span><RoundedSelect ariaLabel="选择退回库位" options={issueLocations.map((item) => `${item.code} · ${item.name}`)} value={issueLocations.find((item) => item.id === locationId) ? `${issueLocations.find((item) => item.id === locationId)?.code} · ${issueLocations.find((item) => item.id === locationId)?.name}` : ""} onValueChange={(value) => setLocationId(issueLocations.find((item) => `${item.code} · ${item.name}` === value)?.id ?? "")}/></label></div>
          <div className="dialogLineList">{returnableLines.map((line) => <label key={line.id} className="dialogLineRow"><span><strong>{line.componentMaterialCode}</strong><small>已领 {formatQuantity(line.issuedQuantity)} · 已退 {formatQuantity(line.returnedQuantity)}</small></span><span><b>可退 {formatQuantity(line.issuedQuantity - line.returnedQuantity)} {line.unit}</b><GsInput type="number" min="0.000001" max={line.issuedQuantity - line.returnedQuantity} step="0.000001" value={quantities[line.id] ?? ""} onChange={(event) => setQuantities((current) => ({ ...current, [line.id]: event.target.value }))}/></span></label>)}</div>
          <label className="formField formFieldFull"><span>退料原因<em>必填</em></span><GsTextArea maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label>
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
          <footer className="dialogFooter"><span><RequestEvidence requestId={requestId}/></span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "正在回补库存" : "确认退料"}</GsButton></div></footer>
        </form>
      </section>
    </GsModalHost>);
}
function CancelDialog({ issue, onClose, onSaved }: {
    issue: MaterialIssueRecord;
    onClose: () => void;
    onSaved: (issue: MaterialIssueRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const requestId = useStableRequestId("material-issue-cancel");
    const [reason, setReason] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        if (!reason.trim()) {
            setError("取消领料单必须填写原因。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitMaterialIssueAction({ id: issue.id, action: "CANCEL", expectedVersion: issue.version, comment: reason.trim() }, requestId));
        }
        catch (cause) {
            setError(cause instanceof Error ? cause.message : "取消领料单失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => { if (!pending)
        onClose(); }}><section ref={ref} className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="material-issue-cancel-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="block" size={22}/></span><div><h2 id="material-issue-cancel-title">取消领料单</h2><p>只有尚未发料的草稿领料单可以取消。</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭取消领料单表单" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><label className="formField formFieldFull"><span>取消原因<em>必填</em></span><GsTextArea maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label>{error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}<footer className="dialogFooter"><span><RequestEvidence requestId={requestId}/></span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>返回</GsButton><GsButton className="primaryButton" htmlType="submit" disabled={pending}>{pending ? "正在取消" : "确认取消"}</GsButton></div></footer></form></section></GsModalHost>;
}
function DetailDrawer({ issue, onClose, onAction }: {
    issue: MaterialIssueRecord;
    onClose: () => void;
    onAction: (dialog: DialogState) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const canIssue = issue.status === "DRAFT" || issue.status === "PARTIAL";
    const canReturn = issue.status === "PARTIAL" || issue.status === "ISSUED";
    const canCancel = issue.status === "DRAFT";
    return <GsModalHost onClose={onClose}><section ref={ref} className="businessDrawer" role="dialog" aria-modal="true" aria-labelledby="material-issue-detail-title" onMouseDown={(event) => event.stopPropagation()}><header className="drawerHeader"><span className="dialogTitleMark"><MaterialIcon name="fact_check" size={22}/></span><div><p className="eyebrow">{issue.orderNumber} · {issue.materialCode}</p><h2 id="material-issue-detail-title">{issue.issueNumber}</h2><small>仓库 {issue.warehouseCode} · 计划 {formatQuantity(issue.plannedQuantity)} {issue.unit} · 更新于 {formatDate(issue.updatedAt)}</small></div><GsButton className="iconButton" htmlType="button" aria-label="关闭领料单详情" onClick={onClose}><MaterialIcon name="close"/></GsButton></header><div className="drawerBody"><div className="drawerStatusRow"><em className={`businessStatus businessStatus${statusTone[issue.status]}`}>{statusLabels[issue.status]}</em><div className="tableProgress"><i><b style={{ width: `${issueProgress(issue)}%` }}/></i><em>{issueProgress(issue)}% 已领</em></div>{canIssue ? <GsButton className="primaryButton" onClick={() => onAction({ type: "issue", issue })} htmlType="submit">生产发料</GsButton> : null}{canReturn ? <GsButton className="secondaryButton" onClick={() => onAction({ type: "return", issue })} htmlType="submit">组件退料</GsButton> : null}{canCancel ? <GsButton className="secondaryButton" onClick={() => onAction({ type: "cancel", issue })} htmlType="submit">取消</GsButton> : null}</div><h3>需求与执行</h3><div className="drawerTable" role="table" aria-label="领料组件明细"><div className="drawerTableHead" role="row"><span>组件</span><span>需求</span><span>已领</span><span>已退</span><span>可领</span></div>{issue.lines.map((line) => <div className="drawerTableRow" role="row" key={line.id}><span><strong>{line.componentMaterialCode}</strong><small>{line.componentMaterialName}</small></span><b>{formatQuantity(line.requiredQuantity)} {line.unit}</b><b>{formatQuantity(line.issuedQuantity)}</b><b>{formatQuantity(line.returnedQuantity)}</b><b>{formatQuantity(line.issuableQuantity)}</b></div>)}</div><h3>库存流水证据</h3>{issue.stockTransactions.length ? <div className="evidenceList">{issue.stockTransactions.map((txn) => <article key={txn.id}><span className={txn.movementType === "ISSUE" ? "evidenceIssue" : "evidenceReturn"}>{txn.movementType === "ISSUE" ? "发料" : "退料"}</span><div><strong>{txn.movementNumber} · {formatQuantity(txn.quantity)} {issue.lines.find((line) => line.id === (txn.issueLineId ?? txn.returnLineId))?.unit ?? ""}</strong><small>{txn.componentMaterialCode} · {txn.locationCode} · {formatDate(txn.occurredAt)}</small><RequestEvidence requestId={txn.requestId}/></div></article>)}</div> : <p className="drawerEmpty">尚无库存流水；发料后会在这里展示不可篡改的仓库证据。</p>}<h3>退料记录</h3>{issue.returns.length ? issue.returns.map((item) => <article className="returnRecord" key={item.id}><strong>{item.returnNumber}</strong><span>{item.locationCode} · {formatDate(item.createdAt)}</span><p>{item.reason}</p><small>{item.lines.map((line) => `${line.componentMaterialCode} ${formatQuantity(line.quantity)}`).join(" · ")}</small></article>) : <p className="drawerEmpty">暂无退料记录。</p>}<h3>操作审计</h3><div className="eventList">{issue.events.map((event) => <div key={event.id}><strong>{actionLabels[event.action] ?? event.action}</strong><span>{formatDate(event.occurredAt)}</span><RequestEvidence requestId={event.requestId}/></div>)}</div></div></section></GsModalHost>;
}
export function MaterialIssueWorkspace({ initialData }: {
    initialData: MaterialIssuePageData;
}) {
    const [data, setData] = useState(initialData);
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState("全部状态");
    const [loading, setLoading] = useState(false);
    const [dialog, setDialog] = useState<DialogState | null>(null);
    const [toast, setToast] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const filtered = useMemo(() => {
        const keyword = query.trim().toLowerCase();
        return data.issues.filter((item) => (status === "全部状态" || statusLabels[item.status] === status) && (!keyword || [item.issueNumber, item.orderNumber, item.materialCode, item.materialName, item.warehouseCode].some((value) => value.toLowerCase().includes(keyword))));
    }, [data.issues, query, status]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    async function refresh() {
        setLoading(true);
        try {
            setData(await fetchMaterialIssuePage());
        }
        finally {
            setLoading(false);
        }
    }
    function saved(issue: MaterialIssueRecord, message: string) {
        setData((current) => ({ ...current, source: "backend", issues: [issue, ...current.issues.filter((item) => item.id !== issue.id)] }));
        setDialog({ type: "detail", issue });
        setToast(message);
        window.setTimeout(() => setToast(""), 2800);
    }
    const metrics = {
        draft: data.issues.filter((item) => item.status === "DRAFT").length,
        partial: data.issues.filter((item) => item.status === "PARTIAL").length,
        issued: data.issues.filter((item) => item.status === "ISSUED").length,
        pendingReturn: data.issues.filter((item) => item.lines.some((line) => line.issuedQuantity - line.returnedQuantity > 0.0000001)).length,
    };
    return (<div className="businessPage inventoryPage">
      <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="inventory_2" size={23}/></span><div><h2>生产备料与领退料</h2><p>按生产订单和 BOM 生成需求，仓库发料扣减合格库存，余料退回形成可追溯流水。</p></div></div><div className="pageHeadingActions"><GsButton className="secondaryButton" onClick={refresh} disabled={loading} htmlType="submit"><MaterialIcon name="refresh" size={17}/>{loading ? "刷新中" : "刷新"}</GsButton><GsButton className="primaryButton" onClick={() => setDialog({ type: "create" })} disabled={data.source === "unavailable"} htmlType="submit"><MaterialIcon name="add" size={18}/>生成领料单</GsButton></div></header>
      {data.error ? <div className="formError pageError" role="alert"><MaterialIcon name="error" size={18}/>{data.error}</div> : null}
      <section className="businessMetrics"><div><small>待发料</small><strong className="businessMetricinfo">{metrics.draft}</strong><em>草稿领料单</em></div><div><small>部分发料</small><strong className="businessMetricwarn">{metrics.partial}</strong><em>仍有待领组件</em></div><div><small>已发齐</small><strong className="businessMetricgood">{metrics.issued}</strong><em>可继续生产执行</em></div><div><small>存在已领未退</small><strong className="businessMetricwarn">{metrics.pendingReturn}</strong><em>关注余料退回</em></div></section>
      <section className="businessLedger inventoryLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索生产备料" placeholder="搜索领料单号、生产订单、物料或仓库" value={query} onChange={(event) => setQuery(event.target.value)}/></div><RoundedSelect ariaLabel="生产领料状态" options={["全部状态", ...Object.values(statusLabels)]} value={status} onValueChange={setStatus}/></div>
        <div className="materialIssueTable" role="table" aria-label="生产领料单列表"><div className="materialIssueTableHeader" role="row"><span>领料单 / 生产订单</span><span>产品 / 仓库</span><span>需求与已领</span><span>退料</span><span>状态</span><span>操作</span></div>{filtered.length ? pageRows.map((item) => <div className="materialIssueTableRow" role="row" key={item.id} onClick={() => setDialog({ type: "detail", issue: item })}><span><strong>{item.issueNumber}</strong><small>{item.orderNumber}</small></span><span><strong>{item.materialCode} · {item.materialName}</strong><small>{item.warehouseCode} · {item.warehouseName}</small></span><span className="issueProgressCell"><b>{formatQuantity(sum(item.lines, "issuedQuantity"))} / {formatQuantity(sum(item.lines, "requiredQuantity"))}</b><span className="tableProgress"><i><b style={{ width: `${issueProgress(item)}%` }}/></i></span></span><b>{formatQuantity(sum(item.lines, "returnedQuantity"))}</b><em className={`businessStatus businessStatus${statusTone[item.status]}`}>{statusLabels[item.status]}</em><span className="businessRowActions"><GsButton aria-label={`查看${item.issueNumber}详情`} onClick={(event) => { event.stopPropagation(); setDialog({ type: "detail", issue: item }); }} htmlType="submit"><MaterialIcon name="chevron_right" size={19}/></GsButton></span></div>) : <div className="businessEmptyState"><MaterialIcon name="inventory_2" size={28}/><strong>{data.source === "unavailable" ? "暂无可显示的备料数据" : "没有符合条件的领料单"}</strong><p>{data.source === "unavailable" ? "请检查后端服务或当前账号权限。" : "可从已下达的生产订单生成第一张领料单。"}</p></div>}</div><footer className="businessLedgerFooter"><span>共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer>
      </section>
      {dialog?.type === "create" ? <CreateIssueDialog data={data} onClose={() => setDialog(null)} onSaved={(issue) => saved(issue, `${issue.issueNumber} 已按 BOM 生成需求`)}/> : null}
      {dialog?.type === "issue" ? <IssueDialog issue={dialog.issue} onClose={() => setDialog({ type: "detail", issue: dialog.issue })} onSaved={(issue) => saved(issue, `${issue.issueNumber} 发料已过账`)}/> : null}
      {dialog?.type === "return" ? <ReturnDialog issue={dialog.issue} locations={data.reference.locations} onClose={() => setDialog({ type: "detail", issue: dialog.issue })} onSaved={(issue) => saved(issue, "组件退料已回补库存")}/> : null}
      {dialog?.type === "cancel" ? <CancelDialog issue={dialog.issue} onClose={() => setDialog({ type: "detail", issue: dialog.issue })} onSaved={(issue) => saved(issue, `${issue.issueNumber} 已取消`)}/> : null}
      {dialog?.type === "detail" ? <DetailDrawer issue={data.issues.find((item) => item.id === dialog.issue.id) ?? dialog.issue} onClose={() => setDialog(null)} onAction={setDialog}/> : null}
      {toast ? <div className="toastMessage" role="status"><MaterialIcon name="task_alt" filled size={18}/>{toast}</div> : null}
    </div>);
}

