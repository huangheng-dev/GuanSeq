"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { encodeCode128B } from "@/lib/code128";
import type { LabelMode, LabelObjectType, LabelPrintRequest } from "@/lib/labeling-contracts";
import type { LabelingPageData } from "@/services/labeling-server-service";
import { submitLabelPrintRequest } from "@/services/labeling-client-service";
import { GsButton, GsInput, GsTextArea } from "./ui";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";

const typeLabels: Record<LabelObjectType, string> = { OPERATION_TASK: "工序任务", EMPLOYEE: "本人身份", STOCK_BALANCE: "库存余额" };

function Code128Barcode({ value }: { value: string }) {
  const encoded = encodeCode128B(value);
  return <svg className="labelBarcode" role="img" aria-label={`条码 ${value}`} viewBox={`0 0 ${encoded.width} 64`} preserveAspectRatio="none">
    <title>{value}</title>{encoded.bars.map((bar, index) => <rect key={`${bar.x}-${index}`} x={bar.x} y="0" width={bar.width} height="48" />)}
    <text x={encoded.width / 2} y="62" textAnchor="middle">{value}</text>
  </svg>;
}

function PrintableLabel({ request }: { request: LabelPrintRequest }) {
  return <article className={`printableLabel printableLabel${request.templateCode}`}>
    <header><div><strong>GuanSeq 贯序</strong><span>{request.templateVersion}</span></div><em>{request.mode === "INITIAL" ? "首次生成" : "受控补打"}</em></header>
    <div className="printableLabelIdentity"><strong>{request.objectCode}</strong><span>{request.objectName}</span></div>
    <Code128Barcode value={request.payload}/>
    <p>{request.objectDetail}</p>
    <footer><span>{request.requestNumber}</span><span>{request.actorUsername}</span><time>{new Date(request.preparedAt).toLocaleString("zh-CN")}</time></footer>
  </article>;
}

export function LabelingWorkspace({ initialData }: { initialData: LabelingPageData }) {
  const [references, setReferences] = useState(initialData.references);
  const [requests, setRequests] = useState(initialData.requests);
  const [objectType, setObjectType] = useState<LabelObjectType>(initialData.references.allowedObjectTypes[0] ?? "EMPLOYEE");
  const initialCandidates = initialData.references.candidates.filter((item) => item.objectType === (initialData.references.allowedObjectTypes[0] ?? "EMPLOYEE"));
  const [candidateId, setCandidateId] = useState(initialCandidates[0]?.objectId ?? "");
  const [mode, setMode] = useState<LabelMode>(initialCandidates[0]?.hasPreparedRequest ? "REPRINT" : "INITIAL");
  const [copies, setCopies] = useState("1");
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [prepared, setPrepared] = useState<LabelPrintRequest | null>(null);
  const [requestId, setRequestId] = useState("");

  const candidates = useMemo(() => references.candidates.filter((item) => item.objectType === objectType), [references, objectType]);
  const selected = candidates.find((item) => item.objectId === candidateId) ?? null;
  const template = references.templates.find((item) => item.objectType === objectType);

  function changeType(next: LabelObjectType) {
    const nextCandidates = references.candidates.filter((item) => item.objectType === next);
    setObjectType(next); setCandidateId(nextCandidates[0]?.objectId ?? "");
    setMode(nextCandidates[0]?.hasPreparedRequest ? "REPRINT" : "INITIAL"); setReason(""); setPrepared(null); setError("");
  }

  function changeCandidate(id: string) {
    const next = candidates.find((item) => item.objectId === id);
    setCandidateId(id); setMode(next?.hasPreparedRequest ? "REPRINT" : "INITIAL"); setReason(""); setPrepared(null); setError("");
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault(); setError("");
    if (!selected) return setError("请选择一个仍然有效的原业务对象。");
    const numericCopies = Number(copies);
    if (!Number.isInteger(numericCopies) || numericCopies < 1 || numericCopies > 10) return setError("打印份数必须为 1–10 的整数。");
    if (mode === "REPRINT" && reason.trim().length < 4) return setError("补打原因至少填写 4 个字符。");
    const activeRequestId = requestId || `label-print-${crypto.randomUUID()}`;
    if (!requestId) setRequestId(activeRequestId);
    setPending(true);
    try {
      const result = await submitLabelPrintRequest({ objectType, objectId: selected.objectId, expectedObjectVersion: selected.version,
        mode, copies: numericCopies, reason: mode === "REPRINT" ? reason.trim() : null }, activeRequestId);
      setRequests((current) => [result, ...current.filter((item) => item.id !== result.id)]);
      setReferences((current) => ({ ...current, candidates: current.candidates.map((item) => item.objectId === result.objectId && item.objectType === result.objectType
        ? { ...item, hasPreparedRequest: true } : item) }));
      setPrepared(result); setMode("REPRINT"); setReason(""); setRequestId(`label-print-${crypto.randomUUID()}`);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "标签打印准备失败"); }
    finally { setPending(false); }
  }

  function openBrowserPrint() {
    document.body.classList.add("label-print-mode");
    const cleanup = () => document.body.classList.remove("label-print-mode");
    window.addEventListener("afterprint", cleanup, { once: true });
    window.print();
    window.setTimeout(cleanup, 1500);
  }

  return <div className="businessPage labelingPage">
    <header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="print" size={23}/></span><div><h2>受控标签生成与补打</h2><p>从正式工序、本人和库存事实生成可扫描标签，补打必须留下原因与责任证据。</p></div></div><div className="pageHeadingActions"><Link className="secondaryButton" href="/production/mobile-operations/reporting-scan"><MaterialIcon name="barcode_scanner" size={18}/>扫码报工</Link><Link className="secondaryButton" href="/warehouse/inventory/on-hand">库存现存量</Link></div></header>
    <section className="businessMetrics"><div><small>打印准备记录</small><strong className="businessMetricinfo">{requests.length}</strong><em>当前工作区不可变证据</em></div><div><small>首次生成</small><strong>{requests.filter((item) => item.mode === "INITIAL").length}</strong><em>每个对象只允许一次</em></div><div><small>受控补打</small><strong className="businessMetricwarn">{requests.filter((item) => item.mode === "REPRINT").length}</strong><em>原因必填</em></div><div><small>物理打印回执</small><strong>未接入</strong><em>PREPARED 不等于出纸成功</em></div></section>
    <div className="labelingLayout">
      <section className="businessLedger labelingComposer"><header className="sectionTitleCompact"><div><h3>生成打印凭证</h3><p>条码内容由原对象确定，不能任意修改。</p></div><span>请求编号稳定幂等</span></header>
        <nav className="labelTypeTabs" aria-label="标签对象类型">{references.allowedObjectTypes.map((type) => <GsButton key={type} className={type === objectType ? "primaryButton" : "secondaryButton"} htmlType="button" onClick={() => changeType(type)}>{typeLabels[type]}</GsButton>)}</nav>
        {candidates.length ? <form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>原业务对象<em>必填</em></span><RoundedSelect ariaLabel="选择标签原业务对象" size="field" value={candidateId} onValueChange={changeCandidate} options={candidates.map((item) => ({ value: item.objectId, label: `${item.code} · ${item.name}` }))}/></label>
          <label className="formField"><span>打印模式<em>后端校验</em></span><RoundedSelect ariaLabel="标签打印模式" size="field" value={mode} onValueChange={(value) => { setMode(value as LabelMode); setReason(""); }} options={[{ value: "INITIAL", label: "首次生成" }, { value: "REPRINT", label: "受控补打" }]}/></label>
          <label className="formField"><span>打印份数<em>1–10</em></span><GsInput aria-label="标签打印份数" type="number" min="1" max="10" step="1" value={copies} onChange={(event) => setCopies(event.target.value)}/></label>
          {mode === "REPRINT" ? <label className="formField formFieldFull"><span>补打原因<em>必填 · 至少 4 字符</em></span><GsTextArea aria-label="标签补打原因" maxLength={300} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="例如：现场标签污损无法识别"/></label> : null}</div>
          {selected ? <div className="labelObjectPreview"><MaterialIcon name="verified" filled size={20}/><div><strong>{selected.code} · {selected.name}</strong><span>{selected.detail}</span><code>{selected.payload}</code></div><em>{template?.version} · {template?.paperSize}</em></div> : null}
          <div className="truthNotice"><MaterialIcon name="info" size={18}/><span>提交只形成“打印准备”事实。点击浏览器打印后，实际出纸结果仍由现场人员确认。</span></div>
          {error ? <p className="formError" role="alert">{error}</p> : null}<footer className="labelComposerFooter"><span>当前请求：{requestId || "提交时生成稳定编号"}</span><GsButton className="primaryButton" disabled={pending || !selected} htmlType="submit"><MaterialIcon name="task_alt" size={18}/>{pending ? "正在生成..." : mode === "INITIAL" ? "生成首次打印凭证" : "生成补打凭证"}</GsButton></footer>
        </form> : <div className="businessEmptyState"><MaterialIcon name="inventory_2" size={28}/><strong>当前类型没有可打印对象</strong><p>先建立对应的正式业务对象，再生成标签。</p></div>}
      </section>
      <aside className="labelPreviewPanel"><header><div><h3>可打印标签</h3><p>{prepared ? `${prepared.requestNumber} · ${prepared.status}` : "提交后在此生成正式凭证"}</p></div>{prepared ? <GsButton className="primaryButton" htmlType="button" onClick={openBrowserPrint}><MaterialIcon name="print" size={18}/>打开浏览器打印</GsButton> : null}</header>
        {prepared ? <><PrintableLabel request={prepared}/><div className="labelEvidence"><span><b>模式</b>{prepared.mode === "INITIAL" ? "首次生成" : "受控补打"}</span><span><b>份数</b>{prepared.copies}</span><span><b>责任人</b>{prepared.actorUsername}</span><span><b>模板</b>{prepared.templateVersion}</span>{prepared.reason ? <p><b>补打原因</b>{prepared.reason}</p> : null}</div></> : <div className="businessEmptyState"><MaterialIcon name="print_disabled" size={30}/><strong>尚未形成打印准备事实</strong><p>预览不是业务证据；提交成功后才会出现可打印标签。</p></div>}
      </aside>
    </div>
    <section className="businessLedger labelHistory"><header className="sectionTitleCompact"><div><h3>打印准备证据</h3><p>记录只追加、不覆盖；状态不冒充打印机硬件回执。</p></div></header><div className="labelHistoryTable" role="table" aria-label="标签打印准备记录"><div className="labelHistoryHeader" role="row"><span>请求 / 对象</span><span>载荷 / 模板</span><span>模式 / 份数</span><span>责任人 / 时间</span><span>状态</span><span>操作</span></div>{requests.length ? requests.map((item) => <div className="labelHistoryRow" role="row" key={item.id}><span><strong>{item.requestNumber}</strong><small>{typeLabels[item.objectType]} · {item.objectCode}</small></span><span><code>{item.payload}</code><small>{item.templateVersion}</small></span><span><strong>{item.mode === "INITIAL" ? "首次生成" : "受控补打"}</strong><small>{item.copies} 份{item.reason ? ` · ${item.reason}` : ""}</small></span><span><strong>{item.actorUsername}</strong><small>{new Date(item.preparedAt).toLocaleString("zh-CN")}</small></span><em className="businessStatus businessStatusinfo">已准备</em><GsButton intent="text" htmlType="button" aria-label={`查看${item.requestNumber}打印凭证`} onClick={() => setPrepared(item)}><MaterialIcon name="visibility" size={18}/></GsButton></div>) : <div className="businessEmptyState"><MaterialIcon name="history" size={28}/><strong>还没有打印准备记录</strong><p>首次成功生成后，证据会出现在这里。</p></div>}</div></section>
    {prepared ? <section className="labelPrintSheet" aria-hidden="true">{Array.from({ length: prepared.copies }, (_, index) => <PrintableLabel key={index} request={prepared}/>)}</section> : null}
  </div>;
}
