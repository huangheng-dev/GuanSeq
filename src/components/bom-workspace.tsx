"use client";

import { type FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";

import type { BomRecord } from "@/lib/contracts";
import { submitBomMutation } from "@/services/bom-client-service";
import type { BomPageData, BomWritePayload } from "@/services/bom-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsCheckbox, GsDrawer, GsInput, GsModal, GsPagination, GsTextArea } from "./ui";

const statusLabels: Record<BomRecord["status"], string> = { DRAFT: "草稿", PUBLISHED: "已发布", INACTIVE: "已停用" };
const statusTones: Record<BomRecord["status"], string> = { DRAFT: "warn", PUBLISHED: "good", INACTIVE: "info" };
const eventLabels: Record<BomRecord["events"][number]["action"], string> = { CREATED: "创建草稿", UPDATED: "更新结构", PUBLISHED: "发布生效", INACTIVATED: "停用版本" };

type FormLine = { key: string; componentCode: string; quantity: string; scrapRate: string; note: string };

function todayText() { return new Date().toISOString().slice(0, 10); }
function formatDateTime(value: string) { return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value)); }
function replaceBom(items: BomRecord[], saved: BomRecord) {
  return items.some((item) => item.id === saved.id) ? items.map((item) => item.id === saved.id ? saved : item) : [saved, ...items];
}

function BomFormDialog({ record, references, onClose, onSaved }: { record?: BomRecord; references: BomPageData["referenceData"]; onClose: () => void; onSaved: (bom: BomRecord) => void }) {
  const [parentCode, setParentCode] = useState(record?.parentMaterialCode ?? references.parentMaterials[0]?.code ?? "");
  const [versionCode, setVersionCode] = useState(record?.versionCode ?? "V1.0");
  const [baseQuantity, setBaseQuantity] = useState(String(record?.baseQuantity ?? 1));
  const [effectiveFrom, setEffectiveFrom] = useState(record?.effectiveFrom ?? todayText());
  const [owner, setOwner] = useState(record?.owner ?? "");
  const [changeReason, setChangeReason] = useState(record?.changeReason ?? "");
  const [lines, setLines] = useState<FormLine[]>(record?.lines.map((line) => ({ key: line.id, componentCode: line.componentMaterialCode, quantity: String(line.quantity), scrapRate: String(line.scrapRate * 100), note: line.note ?? "" })) ?? [{ key: crypto.randomUUID(), componentCode: references.componentMaterials[0]?.code ?? "", quantity: "1", scrapRate: "0", note: "" }]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  const componentCodes = references.componentMaterials.map((item) => item.code);
  const parentCodes = references.parentMaterials.map((item) => item.code);

  function updateLine(key: string, patch: Partial<FormLine>) { setLines((current) => current.map((line) => line.key === key ? { ...line, ...patch } : line)); }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const parent = references.parentMaterials.find((item) => item.code === parentCode);
    if (!parent || !versionCode.trim() || !owner.trim() || !changeReason.trim() || !effectiveFrom) { setError("请完整填写父项、版本、生效日期、负责人和变更原因。"); return; }
    if (!lines.length) { setError("BOM 至少需要一条组件明细。"); return; }
    const mappedLines = lines.map((line) => ({ source: line, material: references.componentMaterials.find((item) => item.code === line.componentCode), quantity: Number(line.quantity), scrapRate: Number(line.scrapRate) / 100 }));
    if (mappedLines.some((line) => !line.material || !Number.isFinite(line.quantity) || line.quantity <= 0 || !Number.isFinite(line.scrapRate) || line.scrapRate < 0 || line.scrapRate >= 1)) { setError("请检查组件物料、用量和损耗率；损耗率应在 0%（含）到 100%（不含）之间。"); return; }
    if (new Set(mappedLines.map((line) => line.material!.id)).size !== mappedLines.length) { setError("同一组件不能在一个 BOM 中重复出现。"); return; }
    if (mappedLines.some((line) => line.material!.id === parent.id)) { setError("父项物料不能直接作为自身组件。"); return; }
    const payload: BomWritePayload = {
      parentMaterialId: parent.id, usageType: "PRODUCTION", versionCode: versionCode.trim(), baseQuantity: Number(baseQuantity), effectiveFrom,
      owner: owner.trim(), changeReason: changeReason.trim(),
      lines: mappedLines.map((line) => ({ componentMaterialId: line.material!.id, quantity: line.quantity, scrapRate: line.scrapRate, note: line.source.note.trim() || null })),
    };
    if (!Number.isFinite(payload.baseQuantity) || payload.baseQuantity <= 0) { setError("基准数量必须大于 0。"); return; }
    setPending(true);
    try {
      const saved = await submitBomMutation(record ? { operation: "update", id: record.id, payload: { ...payload, expectedVersion: record.version } } : { operation: "create", payload });
      onSaved(saved);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "BOM 保存失败，请重试");
      setPending(false);
    }
  }

  return <GsModal
    className="gsModal bomDialog"
    open
    width={980}
    title={record ? "编辑 BOM 草稿" : "新建 BOM 版本"}
    footer={null}
    closable={!pending}
    keyboard={!pending}
    onCancel={pending ? undefined : onClose}
  >
      <p className="gsModalDescription">维护受控父项、版本、生效日期与组件用量，发布后结构不可直接改写。</p>
      <form onSubmit={submit}>
        <div className="formGrid bomHeaderFields">
          <label className="formField formFieldFull"><span>父项物料<em>必填 · 仅自制物料</em></span><RoundedSelect ariaLabel="选择BOM父项物料" size="field" options={parentCodes} value={parentCode} onValueChange={setParentCode} disabled={Boolean(record)} /></label>
          <label className="formField"><span>版本编码<em>必填</em></span><GsInput value={versionCode} maxLength={32} onChange={(event) => setVersionCode(event.target.value)} /></label>
          <label className="formField"><span>生产用途</span><RoundedSelect ariaLabel="BOM用途" size="field" options={["生产"]} value="生产" disabled /></label>
          <label className="formField"><span>基准数量<em>必填</em></span><GsInput type="number" min="0.000001" step="0.000001" value={baseQuantity} onChange={(event) => setBaseQuantity(event.target.value)} /></label>
          <label className="formField"><span>计划生效日<em>必填</em></span><GsInput type="date" value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} /></label>
          <label className="formField"><span>负责人<em>必填</em></span><GsInput value={owner} maxLength={80} onChange={(event) => setOwner(event.target.value)} /></label>
          <label className="formField formFieldFull"><span>变更原因<em>必填 · 纳入审计</em></span><GsTextArea value={changeReason} maxLength={500} rows={2} onChange={(event) => setChangeReason(event.target.value)} /></label>
        </div>
        <section className="bomLineEditor"><header><div><h3>组件明细</h3><p>损耗率按百分比录入，系统保存为精确比例。</p></div><GsButton htmlType="button" icon={<MaterialIcon name="add" size={17} />} onClick={() => setLines((current) => [...current, { key: crypto.randomUUID(), componentCode: componentCodes[0] ?? "", quantity: "1", scrapRate: "0", note: "" }])}>添加组件</GsButton></header><div className="bomLineEditorHeader"><span>行</span><span>组件物料</span><span>用量</span><span>损耗率</span><span>备注</span><span>操作</span></div>{lines.map((line, index) => <div className="bomLineEditorRow" key={line.key}><strong>{(index + 1) * 10}</strong><RoundedSelect ariaLabel={`第${index + 1}行组件物料`} size="field" options={componentCodes} value={line.componentCode} onValueChange={(value) => updateLine(line.key, { componentCode: value })} /><GsInput aria-label={`第${index + 1}行用量`} type="number" min="0.000001" step="0.000001" value={line.quantity} onChange={(event) => updateLine(line.key, { quantity: event.target.value })} /><label className="bomRateInput"><GsInput aria-label={`第${index + 1}行损耗率`} type="number" min="0" max="99.9999" step="0.01" value={line.scrapRate} onChange={(event) => updateLine(line.key, { scrapRate: event.target.value })} /><span>%</span></label><GsInput aria-label={`第${index + 1}行备注`} value={line.note} maxLength={240} onChange={(event) => updateLine(line.key, { note: event.target.value })} /><GsButton intent="text" htmlType="button" aria-label={`删除第${index + 1}行`} disabled={lines.length === 1} icon={<MaterialIcon name="delete" size={18} />} onClick={() => setLines((current) => current.filter((item) => item.key !== line.key))} /></div>)}</section>
        {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18} />{error}</div> : null}
        <footer className="dialogFooter"><span><MaterialIcon name="verified_user" size={16} />保存为草稿，发布需再次确认</span><div><GsButton htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton intent="primary" htmlType="submit" loading={pending}>保存草稿</GsButton></div></footer>
      </form>
  </GsModal>;
}

function BomActionDialog({ record, action, onClose, onDone }: { record: BomRecord; action: "PUBLISH" | "INACTIVATE"; onClose: () => void; onDone: (bom: BomRecord) => void }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const publishing = action === "PUBLISH";
  async function confirm() { setPending(true); setError(""); try { onDone(await submitBomMutation({ operation: "action", id: record.id, action, expectedVersion: record.version })); } catch (reason) { setError(reason instanceof Error ? reason.message : "BOM 状态操作失败"); setPending(false); } }
  return <GsModal
    className="gsModal bomActionDialog"
    open
    width={520}
    title={publishing ? "确认发布此 BOM？" : "确认停用此 BOM？"}
    closable={!pending}
    keyboard={!pending}
    onCancel={pending ? undefined : onClose}
    footer={[
      <GsButton key="cancel" onClick={onClose} disabled={pending}>取消</GsButton>,
      <GsButton key="confirm" intent={publishing ? "primary" : "danger"} onClick={confirm} loading={pending}>{publishing ? "确认发布" : "确认停用"}</GsButton>,
    ]}
  >
    <div className="gsConfirmContent">
      <span className="deleteConfirmIcon"><MaterialIcon name={publishing ? "publish" : "block"} size={23} /></span>
      <div>
        <p>{publishing ? "发布后将成为计划模块可引用的有效结构，且不能直接编辑。" : "停用后，新的计划需求将不能再引用此版本；历史证据仍会保留。"}</p>
        <strong>{record.bomNumber} · {record.parentMaterialCode} · {record.versionCode}</strong>
        {error ? <p className="deleteConfirmError" role="alert">{error}</p> : null}
      </div>
    </div>
  </GsModal>;
}

function BomDrawer({ record, onClose, onEdit, onAction }: { record: BomRecord; onClose: () => void; onEdit: () => void; onAction: (action: "PUBLISH" | "INACTIVATE") => void }) {
  const footer = <div className="recordDrawerFooter"><Link className="secondaryButton" href="/product/materials/list"><MaterialIcon name="inventory_2" size={17} />物料档案</Link>{record.status === "DRAFT" ? <><GsButton icon={<MaterialIcon name="edit" size={17} />} onClick={onEdit}>编辑</GsButton><GsButton intent="primary" icon={<MaterialIcon name="publish" size={17} />} onClick={() => onAction("PUBLISH")}>发布版本</GsButton></> : record.status === "PUBLISHED" ? <GsButton intent="danger" icon={<MaterialIcon name="block" size={17} />} onClick={() => onAction("INACTIVATE")}>停用版本</GsButton> : null}</div>;
  return <GsDrawer className="gsDrawer bomDrawer" open title={<div><strong>{record.bomNumber}</strong><p>{record.parentMaterialCode} · {record.parentMaterialName} · {record.versionCode}</p></div>} onClose={onClose} footer={footer}><section className="salesOrderSummary bomSummary"><div><small>版本状态</small><strong className={`businessStatus businessStatus${statusTones[record.status]}`}>{statusLabels[record.status]}</strong></div><div><small>用途 / 基准</small><strong>生产 · {record.baseQuantity} {record.parentUnit}</strong></div><div><small>生效窗口</small><strong>{record.effectiveFrom} 至 {record.effectiveTo ?? "持续有效"}</strong></div><div><small>负责人</small><strong>{record.owner}</strong></div></section><section className="bomDrawerReason"><MaterialIcon name="history_edu" size={19} /><div><strong>变更原因</strong><p>{record.changeReason}</p></div></section><section className="bomDrawerSection"><header><div><h3>组件结构</h3><p>发布版本的组件快照不会随物料名称后续变化而改写。</p></div><strong>{record.lines.length} 项</strong></header><div className="bomDetailTable"><div className="bomDetailTableHeader"><span>行</span><span>组件</span><span>规格</span><span>单位用量</span><span>损耗率</span></div>{record.lines.map((line) => <div className="bomDetailTableRow" key={line.id}><strong>{line.lineNumber}</strong><span><strong>{line.componentMaterialCode}</strong><small>{line.componentMaterialName}</small></span><span>{line.componentMaterialSpecification ?? "—"}</span><span>{line.quantity} {line.unit}</span><span>{(line.scrapRate * 100).toFixed(2)}%</span></div>)}</div></section><section className="bomDrawerSection"><header><div><h3>版本证据</h3><p>每次创建、修改、发布和停用均保留请求号。</p></div><strong>{record.events.length} 条</strong></header><ol className="recordAudit bomAudit">{record.events.map((event) => <li key={event.id}><span /><div><strong>{eventLabels[event.action]}</strong><p>{event.fromStatus ? `${statusLabels[event.fromStatus]} → ` : ""}{statusLabels[event.toStatus]} · 请求号 {event.requestId ?? "未记录"}</p><time>{formatDateTime(event.occurredAt)}</time></div></li>)}</ol></section></GsDrawer>;
}

export function BomWorkspace({ initialData }: { initialData: BomPageData }) {
  const [boms, setBoms] = useState(initialData.boms);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("全部状态");
  const [sortNewest, setSortNewest] = useState(true);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [editing, setEditing] = useState<BomRecord | "new" | null>(null);
  const [detail, setDetail] = useState<BomRecord | null>(null);
  const [confirmAction, setConfirmAction] = useState<"PUBLISH" | "INACTIVATE" | null>(null);
  const [toast, setToast] = useState("");
  useEffect(() => { if (!toast) return; const timer = window.setTimeout(() => setToast(""), 2600); return () => window.clearTimeout(timer); }, [toast]);

  const filtered = useMemo(() => boms.filter((bom) => (!query.trim() || `${bom.bomNumber}${bom.parentMaterialCode}${bom.parentMaterialName}${bom.versionCode}${bom.owner}`.toLowerCase().includes(query.trim().toLowerCase())) && (status === "全部状态" || statusLabels[bom.status] === status)).sort((a, b) => (sortNewest ? -1 : 1) * a.updatedAt.localeCompare(b.updatedAt)), [boms, query, sortNewest, status]);
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const currentIds = pageRows.map((bom) => bom.id);
  const allCurrent = currentIds.length > 0 && currentIds.every((id) => selectedIds.has(id));
  const selected = boms.filter((bom) => selectedIds.has(bom.id));
  function saveResult(saved: BomRecord, message: string) { setBoms((current) => replaceBom(current, saved)); setDetail(saved); setEditing(null); setConfirmAction(null); setToast(message); }
  function exportRows(rows: BomRecord[]) { const csv = ["BOM编号,父项编码,父项名称,版本,用途,生效日期,失效日期,组件数,状态,负责人", ...rows.map((bom) => [bom.bomNumber, bom.parentMaterialCode, bom.parentMaterialName, bom.versionCode, "生产", bom.effectiveFrom, bom.effectiveTo ?? "", bom.lines.length, statusLabels[bom.status], bom.owner].map((value) => `"${String(value).replaceAll('"', '""')}"`).join(","))].join("\n"); const href = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" })); const anchor = document.createElement("a"); anchor.href = href; anchor.download = `BOM版本-${todayText()}.csv`; anchor.click(); URL.revokeObjectURL(href); }

  if (initialData.source === "unavailable") return <div className="businessPage"><section className="routeState"><MaterialIcon name="cloud_off" size={34} /><h2>BOM 服务暂时不可用</h2><p>{initialData.error}</p><GsButton intent="primary" icon={<MaterialIcon name="refresh" size={18} />} onClick={() => window.location.reload()}>重新加载</GsButton></section></div>;

  const statusBadges = { "全部状态": boms.length, "草稿": boms.filter((bom) => bom.status === "DRAFT").length, "已发布": boms.filter((bom) => bom.status === "PUBLISHED").length, "已停用": boms.filter((bom) => bom.status === "INACTIVE").length };
  return <div className="businessPage bomPage">
    <header className="pageHeading businessPageHeading">
      <div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="account_tree" size={23} /></span><div><h2>BOM 版本</h2><p>以受控版本维护产品结构、生效窗口、组件用量和审计证据，为计划与生产提供同一结构事实。</p></div></div>
      <div className="pageHeadingActions"><Link className="secondaryButton" href="/product/materials/list"><MaterialIcon name="inventory_2" size={18} />物料档案</Link><GsButton intent="primary" icon={<MaterialIcon name="add" size={18} />} onClick={() => { setDetail(null); setEditing("new"); }}>新建 BOM 版本</GsButton></div>
    </header>
    <section className="businessMetrics"><div><small>版本总数</small><strong className="businessMetricinfo">{boms.length}</strong><em>当前租户可见范围</em></div><div><small>已发布</small><strong className="businessMetricgood">{statusBadges["已发布"]}</strong><em>可供计划按日期引用</em></div><div><small>草稿待处理</small><strong className="businessMetricwarn">{statusBadges["草稿"]}</strong><em>发布前仍可编辑</em></div><div><small>结构组件</small><strong className="businessMetricinfo">{boms.reduce((sum, bom) => sum + bom.lines.length, 0)}</strong><em>受控版本行项目</em></div></section>
    <section className="businessLedger bomLedger">
      <div className="businessToolbar">
        <div className="businessSearch"><MaterialIcon name="search" size={18} /><GsInput variant="borderless" aria-label="搜索BOM版本" placeholder="搜索 BOM 编号、父项、版本或负责人" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} /></div>
        <div className="businessFilters"><RoundedSelect ariaLabel="BOM状态筛选" options={Object.keys(statusBadges)} optionBadges={statusBadges} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }} /></div>
        <div className="businessTableTools"><GsButton icon={<MaterialIcon name={sortNewest ? "south" : "north"} size={17} />} onClick={() => setSortNewest((value) => !value)}>更新时间</GsButton><GsButton icon={<MaterialIcon name="download" size={17} />} onClick={() => exportRows(selected.length ? selected : filtered)}>{selected.length ? `导出所选（${selected.length}）` : "导出当前"}</GsButton></div>
      </div>
      {selected.length ? <div className="businessBulkBar"><div><strong>已选择 {selected.length} 个版本</strong><span>批量状态变更会破坏逐版本校验，本页仅支持批量导出。</span></div><nav><GsButton intent="text" icon={<MaterialIcon name="download" size={17} />} onClick={() => exportRows(selected)}>导出所选</GsButton><GsButton intent="text" onClick={() => setSelectedIds(new Set())}>取消选择</GsButton></nav></div> : null}
      <div className="bomTable" role="table" aria-label="BOM版本列表">
        <div className="bomTableHeader" role="row"><GsCheckbox ariaLabel="选择当前页全部BOM版本" checked={allCurrent} onCheckedChange={(checked) => setSelectedIds((current) => { const next = new Set(current); currentIds.forEach((id) => checked ? next.add(id) : next.delete(id)); return next; })} /><span>BOM 编号 / 父项</span><span>版本 / 用途</span><span>生效窗口</span><span>组件</span><span>负责人 / 更新</span><span>状态</span><span>操作</span></div>
        {pageRows.length ? pageRows.map((bom) => <div className="bomTableRow" role="row" key={bom.id} onClick={() => setDetail(bom)}><span><GsCheckbox ariaLabel={`选择${bom.bomNumber}`} checked={selectedIds.has(bom.id)} onCheckedChange={(checked) => setSelectedIds((current) => { const next = new Set(current); if (checked) next.add(bom.id); else next.delete(bom.id); return next; })} /></span><span><strong>{bom.bomNumber}</strong><small>{bom.parentMaterialCode} · {bom.parentMaterialName}</small></span><span><strong>{bom.versionCode}</strong><small>生产 · 基准 {bom.baseQuantity} {bom.parentUnit}</small></span><span><strong>{bom.effectiveFrom}</strong><small>至 {bom.effectiveTo ?? "持续有效"}</small></span><span><strong>{bom.lines.length} 项</strong><small>{bom.lines.filter((line) => line.scrapRate > 0).length} 项含损耗</small></span><span><strong>{bom.owner}</strong><small>{formatDateTime(bom.updatedAt)}</small></span><em className={`businessStatus businessStatus${statusTones[bom.status]}`}>{statusLabels[bom.status]}</em><span className="businessRowActions"><GsButton intent="text" aria-label={`查看${bom.bomNumber}`} icon={<MaterialIcon name="chevron_right" size={20} />} onClick={(event) => { event.stopPropagation(); setDetail(bom); }} />{bom.status === "DRAFT" ? <GsButton intent="text" aria-label={`编辑${bom.bomNumber}`} icon={<MaterialIcon name="edit" size={18} />} onClick={(event) => { event.stopPropagation(); setDetail(null); setEditing(bom); }} /> : null}</span></div>) : <div className="businessEmptyState"><MaterialIcon name="account_tree" size={28} /><strong>没有符合条件的 BOM 版本</strong><p>调整筛选条件，或新建一个受控 BOM 草稿。</p></div>}
      </div>
      <footer className="businessPagination"><span>共 {filtered.length} 条 · 第 {currentPage} / {totalPages} 页</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }} /></footer>
    </section>
    {editing ? <BomFormDialog record={editing === "new" ? undefined : editing} references={initialData.referenceData} onClose={() => setEditing(null)} onSaved={(saved) => saveResult(saved, editing === "new" ? "BOM 草稿已创建" : "BOM 草稿已更新")} /> : null}
    {detail ? <BomDrawer record={detail} onClose={() => setDetail(null)} onEdit={() => { setEditing(detail); setDetail(null); }} onAction={setConfirmAction} /> : null}
    {detail && confirmAction ? <BomActionDialog record={detail} action={confirmAction} onClose={() => setConfirmAction(null)} onDone={(saved) => saveResult(saved, confirmAction === "PUBLISH" ? "BOM 已发布并可供计划引用" : "BOM 已停用")} /> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="check_circle" size={18} />{toast}</div> : null}
  </div>;
}
