"use client";
import { GsButton, GsCheckbox, GsDrawerHost, GsInput, GsModalHost, GsTextArea, GsPagination } from "./ui";
/* eslint-disable @typescript-eslint/no-unused-expressions -- collection selection callbacks use add/delete as branch actions */
import { type FormEvent, useMemo, useRef, useState } from "react";
import Link from "next/link";
import type { ProductionOrderRecord, ProductionWorkReportRecord } from "@/lib/contracts";
import { submitProductionExecutionMutation } from "@/services/production-execution-client-service";
import type { ProductionExecutionPageData } from "@/services/production-execution-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const statusLabels: Record<ProductionWorkReportRecord["status"], string> = { PENDING_INSPECTION: "待检验", READY_FOR_RECEIPT: "待入库", READY_TO_CLOSE: "待关闭", RECEIVED: "已入库", REJECTED_CLOSED: "不合格关闭" };
const statusTones: Record<ProductionWorkReportRecord["status"], string> = { PENDING_INSPECTION: "warn", READY_FOR_RECEIPT: "info", READY_TO_CLOSE: "risk", RECEIVED: "good", REJECTED_CLOSED: "risk" };
const qualityLabels = { PASSED: "合格", PARTIALLY_PASSED: "部分合格", FAILED: "不合格" } as const;
function todayText() { return new Date().toISOString().slice(0, 10); }
function ReportForm({ orders, onClose, onSaved }: {
    orders: ProductionOrderRecord[];
    onClose: () => void;
    onSaved: (item: ProductionWorkReportRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const available = orders.filter((item) => item.status === "IN_PROGRESS" && item.reportableQuantity > 0);
    const [orderText, setOrderText] = useState(available[0] ? `${available[0].orderNumber} · ${available[0].materialCode}` : "暂无可报工订单");
    const order = available.find((item) => `${item.orderNumber} · ${item.materialCode}` === orderText);
    const [quantity, setQuantity] = useState(String(order?.reportableQuantity ?? 1));
    const [shiftName, setShiftName] = useState("白班");
    const [operatorName, setOperatorName] = useState("陈磊");
    const [note, setNote] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        const amount = Number(quantity);
        if (!order || !Number.isFinite(amount) || amount <= 0 || amount > order.reportableQuantity || !shiftName.trim() || !operatorName.trim()) {
            setError("请选择执行中订单，并核对报工数量、班次和操作人。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitProductionExecutionMutation({ operation: "report", orderId: order.id, quantity: amount, shiftName: shiftName.trim(), operatorName: operatorName.trim(), note: note.trim() || null, expectedOrderVersion: order.version }));
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "生产报工失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}><section ref={ref} className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="report-form-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="fact_check" size={22}/></span><div><h2 id="report-form-title">提交生产报工</h2><p>报工后自动建立完工检验任务，检验前不增加完工库存。</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭生产报工表单" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>生产订单<em>必填</em></span><RoundedSelect ariaLabel="报工生产订单" options={available.map((item) => `${item.orderNumber} · ${item.materialCode}`)} value={orderText} onValueChange={(value) => {
            setOrderText(value);
            const selected = available.find((item) => `${item.orderNumber} · ${item.materialCode}` === value);
            if (selected)
                setQuantity(String(selected.reportableQuantity));
        }}/></label><label className="formField"><span>报工数量<em>必填</em></span><GsInput type="number" min="0.0001" max={order?.reportableQuantity ?? undefined} step="0.0001" value={quantity} onChange={(event) => setQuantity(event.target.value)}/></label><label className="formField"><span>当前可报</span><GsInput readOnly value={order ? `${order.reportableQuantity} ${order.unit}` : "—"}/></label><label className="formField"><span>班次<em>必填</em></span><GsInput maxLength={80} value={shiftName} onChange={(event) => setShiftName(event.target.value)}/></label><label className="formField"><span>操作人<em>必填</em></span><GsInput maxLength={80} value={operatorName} onChange={(event) => setOperatorName(event.target.value)}/></label><label className="formField formFieldFull"><span>报工说明</span><GsTextArea rows={3} maxLength={500} value={note} onChange={(event) => setNote(event.target.value)} placeholder="记录班组、工艺异常或交接说明"/></label></div>{error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="verified" size={16}/>提交后进入质量完工检验</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" disabled={pending || !order} htmlType="submit">{pending ? "正在提交" : "提交并送检"}</GsButton></div></footer></form></section></GsModalHost>;
}
function SettleDialog({ report, data, onClose, onSaved }: {
    report: ProductionWorkReportRecord;
    data: ProductionExecutionPageData;
    onClose: () => void;
    onSaved: (item: ProductionWorkReportRecord) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const needsReceipt = (report.acceptedQuantity ?? 0) > 0;
    const warehouses = data.inventoryReferences.warehouses;
    const [warehouseText, setWarehouseText] = useState(warehouses[0] ? `${warehouses[0].code} · ${warehouses[0].name}` : "暂无可用仓库");
    const warehouse = warehouses.find((item) => `${item.code} · ${item.name}` === warehouseText);
    const locations = data.inventoryReferences.locations.filter((item) => item.warehouseId === warehouse?.id);
    const [locationText, setLocationText] = useState(locations[0] ? `${locations[0].code} · ${locations[0].name}` : "暂无可用库位");
    const location = locations.find((item) => `${item.code} · ${item.name}` === locationText);
    const [lotNumber, setLotNumber] = useState(`LOT-${report.materialCode}-${todayText().replaceAll("-", "")}`);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        if (needsReceipt && (!warehouse || !location || !lotNumber.trim())) {
            setError("合格数量入库必须选择仓库、库位并填写批次。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitProductionExecutionMutation({ operation: "settle", id: report.id, warehouseId: needsReceipt ? warehouse?.id ?? null : null, locationId: needsReceipt ? location?.id ?? null : null, lotNumber: needsReceipt ? lotNumber.trim() : null, expectedVersion: report.version }));
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "报工结算失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => {
            if (!pending)
                onClose();
        }}><section ref={ref} className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="settle-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={needsReceipt ? "inventory" : "block"} size={22}/></span><div><h2 id="settle-title">{needsReceipt ? "检验放行并入库" : "关闭不合格报工"}</h2><p>{report.reportNumber} · {report.materialCode}</p></div><GsButton className="iconButton" htmlType="button" aria-label="关闭报工结算" onClick={onClose} disabled={pending}><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="mrpRunTruthNotice"><MaterialIcon name="verified" size={18}/><span><strong>{report.qualityResult ? qualityLabels[report.qualityResult] : "检验已完成"}</strong>合格 {report.acceptedQuantity ?? 0} {report.unit} · 不合格 {report.rejectedQuantity ?? 0} {report.unit}。{needsReceipt ? "仅合格数量进入成品库存。" : "本次不产生库存入账。"}</span></div>{needsReceipt ? <div className="formGrid"><label className="formField"><span>成品仓库<em>必填</em></span><RoundedSelect ariaLabel="成品入库仓库" options={warehouses.map((item) => `${item.code} · ${item.name}`)} value={warehouseText} onValueChange={(value) => { setWarehouseText(value); const selected = warehouses.find((item) => `${item.code} · ${item.name}` === value); const next = data.inventoryReferences.locations.find((item) => item.warehouseId === selected?.id); setLocationText(next ? `${next.code} · ${next.name}` : "暂无可用库位"); }}/></label><label className="formField"><span>入库库位<em>必填</em></span><RoundedSelect ariaLabel="成品入库库位" options={locations.map((item) => `${item.code} · ${item.name}`)} value={locationText} onValueChange={setLocationText}/></label><label className="formField formFieldFull"><span>生产批次<em>必填</em></span><GsInput maxLength={80} value={lotNumber} onChange={(event) => setLotNumber(event.target.value)}/></label></div> : null}{error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}<footer className="dialogFooter"><span>结算操作按请求编号幂等执行</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>返回</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "正在结算" : needsReceipt ? "确认入库" : "确认关闭"}</GsButton></div></footer></form></section></GsModalHost>;
}
function ReportDrawer({ report, onClose, onSettle }: {
    report: ProductionWorkReportRecord;
    onClose: () => void;
    onSettle: () => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const canSettle = ["READY_FOR_RECEIPT", "READY_TO_CLOSE"].includes(report.status);
    return <GsDrawerHost onClose={onClose}><aside ref={ref} className="recordDrawer" role="dialog" aria-modal="true" aria-labelledby="report-detail-title" onMouseDown={(event) => event.stopPropagation()}><header className="recordDrawerHeader"><div><h2 id="report-detail-title">{report.reportNumber}</h2><p>{report.orderNumber} · {report.materialCode} {report.materialName}</p></div><GsButton className="iconButton" aria-label="关闭报工详情" onClick={onClose} htmlType="submit"><MaterialIcon name="close"/></GsButton></header><div className="recordDrawerBody"><section className="salesOrderSummary"><div><small>状态</small><strong className={`businessStatus businessStatus${statusTones[report.status]}`}>{statusLabels[report.status]}</strong></div><div><small>报工数量</small><strong>{report.reportedQuantity} {report.unit}</strong></div><div><small>合格数量</small><strong>{report.acceptedQuantity ?? "—"} {report.acceptedQuantity === null ? "" : report.unit}</strong></div><div><small>不合格</small><strong>{report.rejectedQuantity ?? "—"} {report.rejectedQuantity === null ? "" : report.unit}</strong></div></section><section className="drawerSection"><header><h3>执行与送检</h3></header><div className="detailLedger"><div><span>执行车间</span><strong>{report.workshop}</strong></div><div><span>班次</span><strong>{report.shiftName}</strong></div><div><span>操作人</span><strong>{report.operatorName}</strong></div><div><span>完工检验</span><strong>{report.inspectionNumber}</strong></div><div><span>质量判定</span><strong>{report.qualityResult ? qualityLabels[report.qualityResult] : "待检验"}</strong></div><div><span>报工时间</span><strong>{new Date(report.createdAt).toLocaleString("zh-CN", { hour12: false })}</strong></div></div></section><section className="drawerSection"><header><h3>入库证据</h3></header>{report.receiptMovementId ? <div className="detailLedger"><div><span>仓库</span><strong>{report.receiptWarehouse}</strong></div><div><span>库位</span><strong>{report.receiptLocation}</strong></div><div><span>批次</span><strong>{report.lotNumber}</strong></div><div><span>库存流水</span><strong>{report.receiptMovementId.slice(0, 8)}</strong></div></div> : <div className="mrpRunTruthNotice"><MaterialIcon name="inventory" size={18}/><span><strong>{canSettle ? "等待生产结算" : "尚未形成库存流水"}</strong>完工检验放行后，合格数量才进入成品库存。</span></div>}</section>{report.note ? <section className="drawerSection"><header><h3>报工说明</h3></header><p>{report.note}</p></section> : null}</div><footer className="recordDrawerFooter"><Link className="secondaryButton" href="/quality/final"><MaterialIcon name="verified" size={17}/>完工检验</Link>{canSettle ? <GsButton className="primaryButton" onClick={onSettle} htmlType="submit">{report.status === "READY_TO_CLOSE" ? "关闭不合格报工" : "检验放行并入库"}</GsButton> : null}</footer></aside></GsDrawerHost>;
}
export function ProductionExecutionWorkspace({ initialData }: {
    initialData: ProductionExecutionPageData;
}) {
    const [reports, setReports] = useState(initialData.reports);
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState("全部状态");
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [newOpen, setNewOpen] = useState(false);
    const [detail, setDetail] = useState<ProductionWorkReportRecord | null>(null);
    const [settle, setSettle] = useState<ProductionWorkReportRecord | null>(null);
    const [toast, setToast] = useState("");
    const filtered = useMemo(() => reports.filter((item) => (!query.trim() || `${item.reportNumber}${item.orderNumber}${item.materialCode}${item.materialName}${item.operatorName}`.toLowerCase().includes(query.trim().toLowerCase())) && (status === "全部状态" || statusLabels[item.status] === status)).sort((a, b) => b.createdAt.localeCompare(a.createdAt)), [query, reports, status]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const rows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const currentIds = rows.map((item) => item.id);
    const allCurrent = currentIds.length > 0 && currentIds.every((id) => selectedIds.has(id));
    function saved(item: ProductionWorkReportRecord, message: string) { setReports((current) => current.some((row) => row.id === item.id) ? current.map((row) => row.id === item.id ? item : row) : [item, ...current]); setNewOpen(false); setSettle(null); setDetail(item); setToast(message); window.setTimeout(() => setToast(""), 3200); }
    function exportRows() { const chosen = reports.filter((item) => selectedIds.has(item.id)); const source = chosen.length ? chosen : filtered; const csv = ["报工单,生产订单,物料,报工数量,检验单,合格数量,不合格数量,状态", ...source.map((item) => [item.reportNumber, item.orderNumber, item.materialCode, item.reportedQuantity, item.inspectionNumber, item.acceptedQuantity ?? "", item.rejectedQuantity ?? "", statusLabels[item.status]].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))].join("\n"); const href = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" })); const anchor = document.createElement("a"); anchor.href = href; anchor.download = `生产报工-${todayText()}.csv`; anchor.click(); URL.revokeObjectURL(href); }
    const reportableOrders = initialData.orders.filter((item) => item.status === "IN_PROGRESS" && item.reportableQuantity > 0);
    return <div className="businessPage executionPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="fact_check" size={23}/></span><div><h2>生产报工</h2><p>记录完工数量，联动完工检验与成品入库，保留从订单到库存流水的完整证据。</p></div></div><div className="pageHeadingActions"><Link className="secondaryButton" href="/production/orders/list"><MaterialIcon name="assignment" size={18}/>生产订单</Link><GsButton className="primaryButton" disabled={!reportableOrders.length} onClick={() => setNewOpen(true)} htmlType="submit"><MaterialIcon name="add" size={18}/>提交报工</GsButton></div></header><section className="businessMetrics"><div><small>报工记录</small><strong className="businessMetricinfo">{reports.length}</strong><em>当前租户可见范围</em></div><div><small>待完工检验</small><strong className="businessMetricwarn">{reports.filter((item) => item.status === "PENDING_INSPECTION").length}</strong><em>质量模块负责判定</em></div><div><small>待入库结算</small><strong>{reports.filter((item) => ["READY_FOR_RECEIPT", "READY_TO_CLOSE"].includes(item.status)).length}</strong><em>按检验结论执行</em></div><div><small>已形成库存流水</small><strong className="businessMetricgood">{reports.filter((item) => item.status === "RECEIVED").length}</strong><em>合格数量已入账</em></div></section><section className="businessLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索生产报工" placeholder="搜索报工单、订单、物料或操作人" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }}/></div><RoundedSelect ariaLabel="生产报工状态" options={["全部状态", ...Object.values(statusLabels)]} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }}/><div className="businessTableTools"><GsButton className="secondaryButton" onClick={exportRows} htmlType="submit"><MaterialIcon name="download" size={17}/>{selectedIds.size ? `导出所选（${selectedIds.size}）` : "导出当前"}</GsButton></div></div><div className="salesOrderTable" role="table" aria-label="生产报工列表"><div className="salesOrderTableHeader" role="row"><GsCheckbox className="selectionCheckbox" aria-label="选择当前页全部报工" checked={allCurrent} onChange={(event) => setSelectedIds((current) => { const next = new Set(current); currentIds.forEach((id) => event.target.checked ? next.add(id) : next.delete(id)); return next; })}/><span>报工单 / 生产订单</span><span>物料 / 车间</span><span>报工数量</span><span>完工检验</span><span>质量结果</span><span>状态</span><span>操作</span></div>{rows.length ? rows.map((item) => <div className="salesOrderTableRow" role="row" key={item.id} onClick={() => setDetail(item)}><GsCheckbox className="selectionCheckbox" onClick={(event) => event.stopPropagation()} aria-label={`选择${item.reportNumber}`} checked={selectedIds.has(item.id)} onChange={(event) => setSelectedIds((current) => { const next = new Set(current); event.target.checked ? next.add(item.id) : next.delete(item.id); return next; })}/><span><strong>{item.reportNumber}</strong><small>{item.orderNumber}</small></span><span><strong>{item.materialCode} · {item.materialName}</strong><small>{item.workshop} · {item.operatorName}</small></span><span><strong>{item.reportedQuantity} {item.unit}</strong><small>{item.shiftName}</small></span><span><strong>{item.inspectionNumber}</strong><small>{item.inspectionStatus === "PENDING" ? "等待检验" : "检验完成"}</small></span><span><strong>{item.qualityResult ? qualityLabels[item.qualityResult] : "—"}</strong><small>合格 {item.acceptedQuantity ?? "—"} · 不合格 {item.rejectedQuantity ?? "—"}</small></span><em className={`businessStatus businessStatus${statusTones[item.status]}`}>{statusLabels[item.status]}</em><span className="businessRowActions"><GsButton aria-label={`查看${item.reportNumber}详情`} onClick={(event) => { event.stopPropagation(); setDetail(item); }} htmlType="submit"><MaterialIcon name="chevron_right" size={19}/></GsButton></span></div>) : <div className="businessEmptyState"><MaterialIcon name="fact_check" size={28}/><strong>没有符合条件的生产报工</strong><p>先将生产订单开工，再提交第一笔真实报工。</p></div>}</div><footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => {
        setPage(nextPage);
        setPageSize(nextPageSize);
    }}/></footer></section>{newOpen ? <ReportForm orders={initialData.orders} onClose={() => setNewOpen(false)} onSaved={(item) => saved(item, `${item.reportNumber} 已提交并自动送检`)}/> : null}{detail ? <ReportDrawer report={detail} onClose={() => setDetail(null)} onSettle={() => { setSettle(detail); setDetail(null); }}/> : null}{settle ? <SettleDialog report={settle} data={initialData} onClose={() => setSettle(null)} onSaved={(item) => saved(item, item.status === "RECEIVED" ? `${item.reportNumber} 合格数量已入库` : `${item.reportNumber} 已按不合格结论关闭`)}/> : null}{toast ? <div className="toastMessage" role="status"><MaterialIcon name="task_alt" filled size={18}/>{toast}</div> : null}</div>;
}

