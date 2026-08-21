"use client";
import { GsButton, GsInput, GsModalHost, GsPagination, GsTextArea } from "./ui";
import { type FormEvent, useState } from "react";
import type { IncomingInspectionRecord } from "@/lib/contracts";
import { submitCompleteIncomingInspection } from "@/services/incoming-inspection-client-service";
import type { IncomingInspectionPageData } from "@/services/incoming-inspection-server-service";
import { MaterialIcon } from "./material-icon";
const resultLabels: Record<NonNullable<IncomingInspectionRecord["result"]>, string> = { PASSED: "合格", PARTIALLY_PASSED: "部分合格", FAILED: "不合格" };
const resultTone: Record<NonNullable<IncomingInspectionRecord["result"]>, string> = { PASSED: "good", PARTIALLY_PASSED: "warn", FAILED: "risk" };
function CompleteDialog({ inspection, onClose, onSaved }: {
    inspection: IncomingInspectionRecord;
    onClose: () => void;
    onSaved: (value: IncomingInspectionRecord) => void;
}) {
    const [accepted, setAccepted] = useState(String(inspection.inspectionQuantity));
    const [rejected, setRejected] = useState("0");
    const [inspector, setInspector] = useState("吴倩");
    const [defect, setDefect] = useState("");
    const [conclusion, setConclusion] = useState("尺寸与外观符合来料要求");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        const acceptedValue = Number(accepted);
        const rejectedValue = Number(rejected);
        if (Math.abs(acceptedValue + rejectedValue - inspection.inspectionQuantity) > 0.000001) {
            setError("合格与不合格数量之和必须等于送检数量。");
            return;
        }
        setPending(true);
        try {
            onSaved(await submitCompleteIncomingInspection({ id: inspection.id, acceptedQuantity: acceptedValue, rejectedQuantity: rejectedValue, inspector, defectDescription: rejectedValue > 0 ? defect : null, conclusion, expectedVersion: inspection.version }));
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "来料检验判定失败");
            setPending(false);
        }
    }
    return <GsModalHost onClose={() => { if (!pending)
        onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="fact_check" size={22}/></span><div><h2>来料检验判定</h2><p>{inspection.inspectionNumber} · {inspection.materialName}</p></div></header>
    <form onSubmit={submit}>
      <div className="formGrid two"><label>送检数量<GsInput value={inspection.inspectionQuantity} readOnly/></label><label>批号 / 单号<small>{inspection.sourceNumber}</small></label></div>
      <div className="formGrid two"><label>合格数量<GsInput type="number" min="0" step="0.0001" value={accepted} onChange={(event) => setAccepted(event.target.value)}/></label><label>不合格数量<GsInput type="number" min="0" step="0.0001" value={rejected} onChange={(event) => setRejected(event.target.value)}/></label></div>
      <label>检验员<GsInput value={inspector} onChange={(event) => setInspector(event.target.value)}/></label>
      <label>缺陷说明<GsTextArea value={defect} onChange={(event) => setDefect(event.target.value)} placeholder="存在不合格数量时必填"/></label>
      <label>检验结论<GsTextArea value={conclusion} onChange={(event) => setConclusion(event.target.value)}/></label>
      {error ? <p className="formError">{error}</p> : null}
      <footer className="dialogFooter"><GsButton htmlType="button" className="secondaryButton" disabled={pending} onClick={onClose}>取消</GsButton><GsButton className="primaryButton" disabled={pending} htmlType="submit">{pending ? "提交中..." : "提交判定"}</GsButton></footer>
    </form>
  </section></GsModalHost>;
}
export function IncomingInspectionWorkspace({ initialData }: {
    initialData: IncomingInspectionPageData;
}) {
    const [items, setItems] = useState(initialData.inspections);
    const [active, setActive] = useState<IncomingInspectionRecord | null>(null);
    const [toast, setToast] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const pageRows = items.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const pending = items.filter((item) => item.status === "PENDING").length;
    const completed = items.length - pending;
    function saved(inspection: IncomingInspectionRecord) { setItems((current) => current.map((item) => item.id === inspection.id ? inspection : item)); setActive(null); setToast(`${inspection.inspectionNumber} 已完成判定`); window.setTimeout(() => setToast(""), 2600); }
    return <div className="businessPage">
    <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="verified" size={23}/></span><div><h2>来料检验 IQC</h2><p>对采购到货执行检验，合格数量自动入合格库存，不合格数量进入隔离库存。</p></div></div></header>
    <section className="businessMetrics"><div><small>检验任务</small><strong>{items.length}</strong><em>采购到货触发</em></div><div><small>待检</small><strong className="businessMetricwarn">{pending}</strong><em>需要质量结论</em></div><div><small>已完成</small><strong className="businessMetricgood">{completed}</strong><em>已驱动库存结算</em></div></section>
    <section className="businessLedger"><div className="salesOrderTable" role="table" aria-label="来料检验任务"><div className="salesOrderTableHeader" role="row"><span>检验单号</span><span>采购单 / 供应商</span><span>物料</span><span>数量</span><span>结果</span><span>操作</span></div>{items.length ? pageRows.map((item) => <div className="salesOrderTableRow" role="row" key={item.id}><strong>{item.inspectionNumber}<small>{new Date(item.createdAt).toLocaleString("zh-CN")}</small></strong><span><b>{item.purchaseOrderNumber}</b><small>{item.supplierName}</small></span><span><b>{item.materialName}</b><small>{item.materialCode} · {item.unit}</small></span><strong>{item.inspectionQuantity}</strong>{item.result ? <em className={`businessStatus businessStatus${resultTone[item.result]}`}>{resultLabels[item.result]} {item.acceptedQuantity}/{item.rejectedQuantity}</em> : <em className="businessStatus businessStatuswarn">待检</em>}<span className="businessRowActions">{item.status === "PENDING" ? <GsButton className="secondaryButton" onClick={() => setActive(item)} htmlType="submit">录入判定</GsButton> : <span>已完成</span>}</span></div>) : <div className="emptyState"><MaterialIcon name="verified" size={28}/><b>暂无来料检验任务</b><span>需检验的采购物料到货后会自动进入这里。</span></div>}</div><footer className="businessLedgerFooter"><span>共 {items.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={items.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer></section>
    {active ? <CompleteDialog inspection={active} onClose={() => setActive(null)} onSaved={saved}/> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" filled size={18}/>{toast}</div> : null}
  </div>;
}

