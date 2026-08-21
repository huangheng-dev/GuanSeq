"use client";
import { GsButton, GsInput, GsModalHost, GsPagination } from "./ui";
import { type FormEvent, useMemo, useRef, useState } from "react";
import type { MaterialPlanningParameter } from "@/lib/contracts";
import type { PlanningParameterPageData } from "@/services/planning-parameter-server-service";
import { savePlanningParameter } from "@/services/planning-parameter-client-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
const typeLabels: Record<MaterialPlanningParameter["procurementType"], string> = { MAKE: "自制", BUY: "采购", OUTSOURCE: "委外" };
function ParameterDialog({ parameter, onClose, onSaved }: {
    parameter: MaterialPlanningParameter;
    onClose: () => void;
    onSaved: (value: MaterialPlanningParameter) => void;
}) {
    const ref = useRef<HTMLElement>(null);
    const [days, setDays] = useState(String(parameter.leadTimeDays ?? 1));
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) { event.preventDefault(); const value = Number(days); if (!Number.isInteger(value) || value < 1 || value > 3650) {
        setError("提前期必须是 1–3650 之间的整数天。");
        return;
    } setPending(true); setError(""); try {
        onSaved(await savePlanningParameter(parameter.materialId, value, parameter.version));
    }
    catch (reason) {
        setError(reason instanceof Error ? reason.message : "计划参数保存失败");
        setPending(false);
    } }
    return <GsModalHost onClose={() => { if (!pending)
        onClose(); }}><section ref={ref} className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="parameter-form-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="schedule" size={22}/></span><div><h2 id="parameter-form-title">维护提前期</h2><p>{parameter.materialCode} · {parameter.materialName} · {typeLabels[parameter.procurementType]}</p></div><GsButton className="iconButton" onClick={onClose} aria-label="关闭计划参数表单" htmlType="submit"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>提前期（自然日）<em>必填</em></span><GsInput type="number" min={1} max={3650} step={1} value={days} onChange={(event) => setDays(event.target.value)}/></label></div><div className="mrpRunTruthNotice"><MaterialIcon name="info" size={18}/><span><strong>用于 MRP 日期反推</strong>本切片只维护已证明必要的提前期，不预置缺乏业务定义的批量、安全库存或日历参数。</span></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span><MaterialIcon name="history" size={16}/>使用乐观锁防止并发覆盖</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "正在保存" : "保存提前期"}</GsButton></div></footer></form></section></GsModalHost>;
}
export function PlanningParameterWorkspace({ initialData }: {
    initialData: PlanningParameterPageData;
}) {
    const [parameters, setParameters] = useState(initialData.parameters);
    const [query, setQuery] = useState("");
    const [type, setType] = useState("全部类型");
    const [editing, setEditing] = useState<MaterialPlanningParameter | null>(null);
    const [toast, setToast] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const filtered = useMemo(() => parameters.filter((item) => (!query.trim() || `${item.materialCode}${item.materialName}`.toLowerCase().includes(query.toLowerCase())) && (type === "全部类型" || typeLabels[item.procurementType] === type)), [parameters, query, type]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const configured = parameters.filter((x) => x.configured).length;
    return <div className="businessPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="tune" size={23}/></span><div><h2>计划参数</h2><p>按物料维护受控提前期，为 MRP 建议日期反推提供可信依据。</p></div></div></header><section className="businessMetrics"><div><small>有效物料</small><strong className="businessMetricinfo">{parameters.length}</strong><em>当前租户启用范围</em></div><div><small>已配置</small><strong className="businessMetricgood">{configured}</strong><em>可用于准备检查</em></div><div><small>待配置</small><strong className="businessMetricwarn">{parameters.length - configured}</strong><em>会形成明确阻断</em></div><div><small>参数范围</small><strong className="businessMetricinfo">1</strong><em>当前仅提前期</em></div></section><section className="businessLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索计划参数" placeholder="搜索物料编码或名称" value={query} onChange={(event) => setQuery(event.target.value)}/></div><RoundedSelect ariaLabel="物料采购类型" options={["全部类型", ...Object.values(typeLabels)]} value={type} onValueChange={setType}/></div><div className="inventoryTable" role="table" aria-label="物料计划参数列表"><div className="inventoryTableHeader" role="row"><span>物料</span><span>采购类型</span><span>单位</span><span>提前期</span><span>配置状态</span><span>更新时间</span><span>操作</span></div>{pageRows.map((item) => <div className="inventoryTableRow" role="row" key={item.materialId}><span><strong>{item.materialCode} · {item.materialName}</strong><small>{item.materialSpecification ?? "无规格"}</small></span><span><strong>{typeLabels[item.procurementType]}</strong><small>{item.procurementType}</small></span><span><strong>{item.unit}</strong></span><span><strong>{item.leadTimeDays ? `${item.leadTimeDays} 天` : "未配置"}</strong><small>自然日</small></span><em className={`businessStatus businessStatus${item.configured ? "good" : "warn"}`}>{item.configured ? "已配置" : "待配置"}</em><span><strong>{item.updatedAt ? new Date(item.updatedAt).toLocaleDateString("zh-CN") : "—"}</strong><small>版本 {item.version}</small></span><span className="businessRowActions"><GsButton aria-label={`编辑${item.materialCode}提前期`} onClick={() => setEditing(item)} htmlType="submit"><MaterialIcon name="edit" size={18}/></GsButton></span></div>)}</div><footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer></section>{editing ? <ParameterDialog parameter={editing} onClose={() => setEditing(null)} onSaved={(value) => { setParameters((current) => current.map((x) => x.materialId === value.materialId ? value : x)); setEditing(null); setToast(`${value.materialCode} 提前期已更新为 ${value.leadTimeDays} 天`); window.setTimeout(() => setToast(""), 2600); }}/> : null}{toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}</div>;
}

