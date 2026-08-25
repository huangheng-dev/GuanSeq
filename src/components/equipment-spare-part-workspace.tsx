"use client";

import { type FormEvent, useMemo, useState } from "react";

import type { EquipmentSparePart, EquipmentSparePartPage, EquipmentSparePartReference } from "@/lib/contracts";
import { EquipmentSparePartClientError, refreshEquipmentSpareParts, submitEquipmentSparePart } from "@/services/equipment-spare-part-client-service";
import type { EquipmentSparePartPageData } from "@/services/equipment-spare-part-server-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsInput, GsModalHost, GsPagination, GsTextArea } from "./ui";

function errorText(error: unknown) {
  if (error instanceof EquipmentSparePartClientError) return error.requestId ? `${error.message}（请求编号：${error.requestId}）` : error.message;
  return error instanceof Error ? error.message : "设备备件操作失败";
}
function money(value: number | null, currency: string | null) { return value == null ? "缺少标准成本" : `${currency ?? "CNY"} ${value.toFixed(2)}`; }

function CreateDialog({ references, onClose, onSaved }: { references: EquipmentSparePartReference; onClose: () => void; onSaved: (item: EquipmentSparePart) => void }) {
  const materialLabels = references.materials.map((item) => `${item.code} · ${item.name} · ${item.unit}`);
  const warehouseLabels = references.warehouses.map((item) => `${item.code} · ${item.name}`);
  const [materialLabel, setMaterialLabel] = useState(materialLabels[0] ?? ""); const [warehouseLabel, setWarehouseLabel] = useState(warehouseLabels[0] ?? "");
  const [reorderPoint, setReorderPoint] = useState("0"); const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault(); const material = references.materials[materialLabels.indexOf(materialLabel)];
    const warehouse = references.warehouses[warehouseLabels.indexOf(warehouseLabel)]; const point = Number(reorderPoint);
    if (!material || !warehouse || !Number.isFinite(point) || point < 0 || reason.trim().length < 4) { setError("请选择物料和默认仓库，并填写非负安全库存与至少 4 个字符的建档原因。"); return; }
    setPending(true); setError("");
    try { onSaved(await submitEquipmentSparePart({ materialId: material.id, preferredWarehouseId: warehouse.id, reorderPoint: point, reason: reason.trim() })); }
    catch (failure) { setError(errorText(failure)); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="spare-part-create-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="inventory_2" size={22}/></span><div><h2 id="spare-part-create-title">建立备件台账</h2><p>关联现有物料和默认仓库，不复制库存主事实。</p></div><GsButton className="iconButton" onClick={onClose} disabled={pending} aria-label="关闭备件表单" htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    {references.materials.length && references.warehouses.length ? <form onSubmit={submit}><div className="formGrid">
      <label className="formField formFieldFull"><span>物料<em>必填</em></span><RoundedSelect ariaLabel="备件物料" options={materialLabels} value={materialLabel} onValueChange={setMaterialLabel}/></label>
      <label className="formField formFieldFull"><span>默认仓库<em>必填</em></span><RoundedSelect ariaLabel="备件默认仓库" options={warehouseLabels} value={warehouseLabel} onValueChange={setWarehouseLabel}/></label>
      <label className="formField"><span>安全库存<em>必填</em></span><GsInput type="number" min="0" step="0.0001" value={reorderPoint} onChange={(event) => setReorderPoint(event.target.value)}/></label>
      <label className="formField formFieldFull"><span>建档原因<em>必填</em></span><GsTextArea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)}/></label>
    </div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>重复物料或请求编号会被后端拒绝</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">建立台账</GsButton></div></footer></form> : <div className="businessEmptyState"><strong>暂无可用物料或仓库</strong><p>请先完成主数据和仓储基础数据。</p></div>}
  </section></GsModalHost>;
}

export function EquipmentSparePartWorkspace({ initialData }: { initialData: EquipmentSparePartPageData }) {
  const [pageData, setPageData] = useState<EquipmentSparePartPage | null>(initialData.page);
  const [references, setReferences] = useState<EquipmentSparePartReference | null>(initialData.source === "backend" ? initialData.references : null);
  const [unavailable, setUnavailable] = useState(initialData.source === "unavailable" ? initialData : null);
  const [query, setQuery] = useState(""); const [page, setPage] = useState(1); const [pageSize, setPageSize] = useState(10);
  const [refreshing, setRefreshing] = useState(false); const [createOpen, setCreateOpen] = useState(false); const [toast, setToast] = useState("");
  const filtered = useMemo(() => !pageData ? [] : pageData.items.filter((item) => !query.trim() || `${item.materialCode}${item.materialName}${item.preferredWarehouseCode}`.toLowerCase().includes(query.trim().toLowerCase())), [pageData, query]);
  async function refresh() { setRefreshing(true); try { const result = await refreshEquipmentSpareParts(); setPageData(result.page); setReferences(result.references); setUnavailable(null); setToast("备件库存与成本口径已刷新"); window.setTimeout(() => setToast(""), 2200); } catch (error) { setToast(errorText(error)); } finally { setRefreshing(false); } }
  if (!pageData || !references) return <div className="businessPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="inventory_2" size={23}/></span><div><h2>备件管理</h2><p>备件用途、仓库可用量和标准成本。</p></div></div></header><section className="backendUnavailableState" role="alert"><MaterialIcon name="cloud_off" size={30}/><strong>设备备件服务暂时不可用</strong><p>{unavailable?.message ?? "未取得备件台账"}</p>{unavailable ? <small>请求编号：{unavailable.requestId}</small> : null}<GsButton onClick={refresh} loading={refreshing} htmlType="button">重新检查</GsButton></section></div>;
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize)); const current = Math.min(page, totalPages); const rows = filtered.slice((current - 1) * pageSize, current * pageSize);
  const below = filtered.filter((item) => item.stockStatus === "BELOW_REORDER_POINT").length; const missing = filtered.filter((item) => item.costStatus === "MISSING_COST").length;
  function saved(item: EquipmentSparePart) { setPageData((currentPage) => currentPage ? { ...currentPage, items: [item, ...currentPage.items], totalElements: currentPage.totalElements + 1 } : currentPage); setCreateOpen(false); setToast(`${item.materialCode} 已建立备件台账`); }
  return <div className="businessPage equipmentSparePartPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="inventory_2" size={23}/></span><div><h2>备件管理</h2><p>把物料、默认仓可用量和维修成本口径放在同一台账。</p></div></div><div className="pageHeadingActions"><GsButton onClick={refresh} loading={refreshing} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新</GsButton>{pageData.canMaintain ? <GsButton intent="primary" onClick={() => setCreateOpen(true)} htmlType="button"><MaterialIcon name="add" size={17}/>新增备件</GsButton> : null}</div></header>
    <section className="equipmentTruthBanner"><MaterialIcon name="account_balance_wallet" size={21}/><div><strong>跨模块事实实时装配</strong><p>库存来自仓储，标准成本来自财务；此处只维护设备备件用途。</p></div><span>非财务凭证</span></section>
    <section className="businessMetrics"><div><small>备件项</small><strong className="businessMetricinfo">{filtered.length}</strong><em>当前工作区</em></div><div><small>低于安全库存</small><strong className={below ? "businessMetricwarn" : "businessMetricgood"}>{below}</strong><em>默认仓可用量</em></div><div><small>缺少标准成本</small><strong className={missing ? "businessMetricdanger" : "businessMetricgood"}>{missing}</strong><em>缺失时禁止领用</em></div><div><small>可领用</small><strong className="businessMetricgood">{filtered.filter((item) => item.availableQuantity > 0 && item.costStatus === "READY").length}</strong><em>库存与成本均就绪</em></div></section>
    <section className="businessLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索备件" value={query} placeholder="搜索物料或默认仓库" onChange={(event) => { setQuery(event.target.value); setPage(1); }}/></div></div>
      <div className="equipmentWorkOrderTable" role="table" aria-label="备件台账列表"><div className="equipmentWorkOrderTableHeader" role="row"><span>备件 / 规格</span><span>默认仓库</span><span>可用量 / 安全库存</span><span>标准成本</span><span>状态</span></div>{rows.length ? rows.map((item) => <div className="equipmentWorkOrderTableRow" role="row" key={item.id}><span><strong>{item.materialName}</strong><small>{item.materialCode} · {item.materialSpecification ?? "无规格"}</small></span><span><strong>{item.preferredWarehouseName}</strong><small>{item.preferredWarehouseCode}</small></span><span><strong>{item.availableQuantity} {item.unit}</strong><small>安全库存 {item.reorderPoint} {item.unit}</small></span><span><strong>{money(item.standardUnitCost, item.currency)}</strong><small>{item.costEffectiveDate ? `${item.costEffectiveDate} 起生效` : "请由财务维护成本"}</small></span><span><em className={`businessStatus businessStatus${item.costStatus === "MISSING_COST" ? "risk" : item.stockStatus === "BELOW_REORDER_POINT" ? "warn" : "good"}`}>{item.costStatus === "MISSING_COST" ? "缺成本" : item.stockStatus === "BELOW_REORDER_POINT" ? "需关注" : "可领用"}</em><small>库存实时查询</small></span></div>) : <div className="businessEmptyState"><MaterialIcon name="inventory_2" size={28}/><strong>没有符合条件的备件</strong><p>{pageData.canMaintain ? "可从有效物料建立第一条备件台账。" : "当前工作区暂无可见备件。"}</p></div>}</div>
      <footer className="businessLedgerFooter"><span>第 {filtered.length ? (current - 1) * pageSize + 1 : 0}–{Math.min(current * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={current} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(next, size) => { setPage(next); setPageSize(size); }}/></footer></section>
    {!pageData.canMaintain ? <section className="workspaceRoleBoundary"><MaterialIcon name="shield_lock" size={20}/><div><strong>当前角色只读</strong><p>设备经理、生产经理和管理员可以建立备件台账。</p></div></section> : null}
    {createOpen ? <CreateDialog references={references} onClose={() => setCreateOpen(false)} onSaved={saved}/> : null}{toast ? <div className="toastMessage" role="status"><MaterialIcon name="info" size={18}/>{toast}</div> : null}
  </div>;
}
