"use client";

import { type FormEvent, useState } from "react";
import type { OrganizationMember, OrganizationStructurePage, OrganizationUnit } from "@/lib/contracts";
import type { OrganizationStructurePageData } from "@/services/organization-structure-server-service";
import { mutateOrganizationStructure, OrganizationClientError, refreshOrganizationStructure } from "@/services/organization-structure-client-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsInput, GsModalHost, GsTextArea } from "./ui";

function errorText(error: unknown) {
  if (error instanceof OrganizationClientError) return error.requestId ? `${error.message}（请求编号：${error.requestId}）` : error.message;
  return error instanceof Error ? error.message : "组织操作失败";
}

type Dialog = { type: "create" } | { type: "editUnit"; unit: OrganizationUnit }
  | { type: "status"; unit: OrganizationUnit } | { type: "workspace" }
  | { type: "assign"; member: OrganizationMember };

function EditDialog({ page, dialog, onClose, onSaved }: { page: OrganizationStructurePage; dialog: Dialog; onClose: () => void; onSaved: (page: OrganizationStructurePage) => void }) {
  const unit = dialog.type === "editUnit" ? dialog.unit : null;
  const workspace = dialog.type === "workspace" ? page.workspace : null;
  const [code, setCode] = useState("");
  const [name, setName] = useState(unit?.name ?? workspace?.name ?? "");
  const [ownerId, setOwnerId] = useState<string | null>(unit?.responsibleUserId ?? workspace?.responsibleUserId ?? null);
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  const owners = page.members.filter((member) => member.membershipStatus === "ACTIVE");
  const ownerLabels = ["暂不指定", ...owners.map((owner) => `${owner.displayName} · ${owner.username}`)];
  const ownerLabel = ownerId ? ownerLabels.find((label) => label.endsWith(`· ${owners.find((owner) => owner.userId === ownerId)?.username}`)) ?? "暂不指定" : "暂不指定";
  async function submit(event: FormEvent) {
    event.preventDefault(); if (!name.trim() || (dialog.type === "create" && !/^[A-Z0-9][A-Z0-9_-]*$/.test(code))) { setError("请填写名称，并使用大写字母、数字、下划线或连字符作为编码。"); return; }
    setPending(true); setError("");
    try {
      if (dialog.type === "create") onSaved(await mutateOrganizationStructure({ action: "createSite", code, name: name.trim(), responsibleUserId: ownerId }));
      else if (dialog.type === "editUnit") onSaved(await mutateOrganizationStructure({ action: "updateUnit", unitId: dialog.unit.id, name: name.trim(), responsibleUserId: ownerId, expectedVersion: dialog.unit.version }));
      else if (dialog.type === "workspace") onSaved(await mutateOrganizationStructure({ action: "updateWorkspace", name: name.trim(), responsibleUserId: ownerId, expectedVersion: page.workspace.version }));
    } catch (reason) { setError(errorText(reason)); setPending(false); }
  }
  const title = dialog.type === "create" ? "建立直属现场单元" : dialog.type === "workspace" ? "维护当前工作区" : "维护组织单元";
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="organization-edit-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name="account_tree" size={22}/></span><div><h2 id="organization-edit-title">{title}</h2><p>编码和层级一经建立不可在本用例中移动，避免业务事实漂移。</p></div><GsButton className="iconButton" onClick={onClose} disabled={pending} aria-label="关闭" htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <form onSubmit={submit}><div className="formGrid">{dialog.type === "create" ? <label className="formField"><span>现场编码<em>必填</em></span><GsInput value={code} maxLength={40} placeholder="例如 EAST-ASM" onChange={(event) => setCode(event.target.value.toUpperCase())}/></label> : null}<label className="formField"><span>名称<em>必填</em></span><GsInput value={name} maxLength={120} onChange={(event) => setName(event.target.value)}/></label><label className="formField formFieldFull"><span>负责人</span><RoundedSelect ariaLabel="选择负责人" options={ownerLabels} value={ownerLabel} onValueChange={(label) => setOwnerId(label === "暂不指定" ? null : owners.find((owner) => label.endsWith(`· ${owner.username}`))?.userId ?? null)}/><small>只能选择当前工作区启用成员。</small></label></div>
      <div className="mrpRunTruthNotice"><MaterialIcon name="verified_user" size={18}/><span><strong>权限边界</strong>负责人是责任事实，不会自动获得角色或跨组织权限。</span></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>保存由服务端版本与审计保护</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">{pending ? "正在保存" : "保存"}</GsButton></div></footer>
    </form></section></GsModalHost>;
}

function ReasonDialog({ page, dialog, onClose, onSaved }: { page: OrganizationStructurePage; dialog: Extract<Dialog, { type: "status" | "assign" }>; onClose: () => void; onSaved: (page: OrganizationStructurePage) => void }) {
  const assign = dialog.type === "assign"; const member = dialog.type === "assign" ? dialog.member : null; const unit = dialog.type === "status" ? dialog.unit : null;
  const units = [page.operatingUnit, ...page.siteUnits.filter((candidate) => candidate.status === "ACTIVE")];
  const [unitId, setUnitId] = useState(member?.organizationUnitId ?? page.operatingUnit.id); const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault(); if (reason.trim().length < 4) { setError("请填写至少 4 个字符的变更原因。"); return; }
    setPending(true); setError("");
    try {
      onSaved(assign ? await mutateOrganizationStructure({ action: "assignMember", userId: member!.userId, organizationUnitId: unitId, expectedMembershipVersion: member!.membershipVersion, reason: reason.trim() })
        : await mutateOrganizationStructure({ action: "changeUnitStatus", unitId: unit!.id, nextStatus: unit!.status === "ACTIVE" ? "INACTIVE" : "ACTIVE", expectedVersion: unit!.version, reason: reason.trim() }));
    } catch (cause) { setError(errorText(cause)); setPending(false); }
  }
  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="organization-reason-title" onMouseDown={(event) => event.stopPropagation()}><header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={assign ? "move_down" : "rule"} size={22}/></span><div><h2 id="organization-reason-title">{assign ? "调整成员组织归属" : unit?.status === "ACTIVE" ? "停用现场单元" : "恢复现场单元"}</h2><p>{assign ? `${member?.displayName} · ${member?.username}` : `${unit?.name} · ${unit?.code}`}</p></div><GsButton className="iconButton" onClick={onClose} disabled={pending} aria-label="关闭" htmlType="button"><MaterialIcon name="close"/></GsButton></header><form onSubmit={submit}><div className="formGrid">{assign ? <label className="formField formFieldFull"><span>目标组织<em>必填</em></span><RoundedSelect ariaLabel="目标组织" options={units.map((candidate) => `${candidate.name} · ${candidate.code}`)} value={`${units.find((candidate) => candidate.id === unitId)?.name} · ${units.find((candidate) => candidate.id === unitId)?.code}`} onValueChange={(label) => setUnitId(units.find((candidate) => label === `${candidate.name} · ${candidate.code}`)?.id ?? page.operatingUnit.id)}/></label> : null}<label className="formField formFieldFull"><span>变更原因<em>必填</em></span><GsTextArea rows={4} maxLength={300} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="说明岗位或现场责任变化"/></label></div><div className="mrpRunTruthNotice"><MaterialIcon name="info" size={18}/><span><strong>{assign ? "归属不是授权" : "停用保护"}</strong>{assign ? "调整归属不会改变成员角色。" : "仍有启用成员或工作区时，后端会拒绝停用。"}</span></div>{error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>原因会写入审计事件</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">确认提交</GsButton></div></footer></form></section></GsModalHost>;
}

function Unavailable({ data, onRecovered }: { data: Extract<OrganizationStructurePageData, { source: "unavailable" }>; onRecovered: (page: OrganizationStructurePage) => void }) {
  const [pending, setPending] = useState(false); const [message, setMessage] = useState(data.message);
  async function retry() { setPending(true); try { onRecovered(await refreshOrganizationStructure()); } catch (error) { setMessage(errorText(error)); setPending(false); } }
  return <div className="businessPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="account_tree" size={23}/></span><div><h2>组织架构</h2><p>治理当前工作区组织、负责人和成员归属。</p></div></div></header><section className="backendUnavailableState" role="alert"><MaterialIcon name={data.status === 403 ? "lock" : "cloud_off"} size={30}/><strong>{data.status === 403 ? "当前角色无权治理组织" : "组织治理服务暂时不可用"}</strong><p>{message}</p><small>请求编号：{data.requestId}</small><GsButton onClick={retry} loading={pending} htmlType="button">重新检查</GsButton></section></div>;
}

export function OrganizationStructureWorkspace({ initialData }: { initialData: OrganizationStructurePageData }) {
  const [page, setPage] = useState(initialData.page); const [unavailable, setUnavailable] = useState(initialData.source === "unavailable" ? initialData : null);
  const [dialog, setDialog] = useState<Dialog | null>(null); const [refreshing, setRefreshing] = useState(false); const [toast, setToast] = useState("");
  if (!page && unavailable) return <Unavailable data={unavailable} onRecovered={(next) => { setPage(next); setUnavailable(null); }}/>; if (!page) return null;
  const activeSites = page.siteUnits.filter((unit) => unit.status === "ACTIVE").length;
  const save = (next: OrganizationStructurePage) => { setPage(next); setDialog(null); setToast("组织事实已更新"); window.setTimeout(() => setToast(""), 2400); };
  async function refresh() { setRefreshing(true); try { setPage(await refreshOrganizationStructure()); setToast("组织事实已刷新"); } catch (error) { setToast(errorText(error)); } finally { setRefreshing(false); window.setTimeout(() => setToast(""), 2400); } }
  return <div className="businessPage organizationStructurePage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="account_tree" size={23}/></span><div><h2>组织架构</h2><p>{page.company.name} · {page.workspace.name} 的最小组织治理</p></div></div><div className="pageHeadingActions"><GsButton onClick={refresh} loading={refreshing} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新</GsButton>{page.operatingUnit.unitType === "PLANT" ? <GsButton intent="primary" onClick={() => setDialog({ type: "create" })} htmlType="button"><MaterialIcon name="add_business" size={17}/>建立现场单元</GsButton> : null}</div></header>
    <section className="businessMetrics"><div><small>公司根组织</small><strong className="businessMetricinfo">1</strong><em>当前工作区只读</em></div><div><small>所在组织</small><strong className="businessMetricinfo">1</strong><em>{page.operatingUnit.unitType === "PLANT" ? "工厂" : "现场"}</em></div><div><small>启用现场单元</small><strong className="businessMetricgood">{activeSites}</strong><em>共 {page.siteUnits.length} 个</em></div><div><small>工作区成员</small><strong className="businessMetricinfo">{page.members.length}</strong><em>归属不等于授权</em></div></section>
    <section className="workspaceRoleBoundary"><MaterialIcon name="shield_lock" size={20}/><div><strong>治理范围由当前工作区锁定</strong><p>{page.scopeDescription}</p></div></section>
    <section className="businessLedger"><div className="businessToolbar"><div><strong>组织与工作区</strong><p className="organizationSectionNote">COMPANY → {page.operatingUnit.unitType} → SITE；不提供任意层级拖拽。</p></div><GsButton onClick={() => setDialog({ type: "workspace" })} htmlType="button"><MaterialIcon name="edit" size={17}/>维护工作区</GsButton></div><div className="workspaceUserTable" role="table" aria-label="组织单元列表"><div className="workspaceUserTableHeader" role="row"><span>组织 / 编码</span><span>类型</span><span>负责人</span><span>状态 / 版本</span><span>操作</span></div>{[page.company, page.operatingUnit, ...page.siteUnits].map((unit) => <div className="workspaceUserTableRow" role="row" key={unit.id}><span><strong>{unit.name}</strong><small>{unit.code}</small></span><span><strong>{unit.unitType === "COMPANY" ? "公司" : unit.unitType === "PLANT" ? "工厂" : "现场单元"}</strong><small>{unit.unitType}</small></span><span><strong>{unit.responsibleUserName ?? "暂未指定"}</strong><small>{unit.unitType === "COMPANY" ? "当前工作区只读" : "当前工作区启用成员"}</small></span><span><em className={`businessStatus businessStatus${unit.status === "ACTIVE" ? "good" : "info"}`}>{unit.status === "ACTIVE" ? "已启用" : "已停用"}</em><small>V{unit.version}</small></span><span className="businessRowActions"><GsButton aria-label={`编辑${unit.name}`} disabled={unit.unitType === "COMPANY"} onClick={() => setDialog({ type: "editUnit", unit })} htmlType="button"><MaterialIcon name="edit" size={18}/></GsButton>{unit.unitType === "SITE" ? <GsButton aria-label={`${unit.status === "ACTIVE" ? "停用" : "恢复"}${unit.name}`} onClick={() => setDialog({ type: "status", unit })} htmlType="button"><MaterialIcon name={unit.status === "ACTIVE" ? "block" : "check_circle"} size={18}/></GsButton> : null}</span></div>)}</div></section>
    <section className="businessLedger"><div className="businessToolbar"><div><strong>成员组织归属</strong><p className="organizationSectionNote">角色继续由用户管理维护；本表只记录成员主要归属。</p></div></div><div className="workspaceUserTable" role="table" aria-label="成员组织归属"><div className="workspaceUserTableHeader" role="row"><span>成员 / 用户名</span><span>角色</span><span>组织归属</span><span>成员状态 / 版本</span><span>操作</span></div>{page.members.map((member) => <div className="workspaceUserTableRow" role="row" key={member.userId}><span><strong>{member.displayName}{member.userId === page.currentUserId ? "（当前用户）" : ""}</strong><small>{member.username}</small></span><span><strong>{member.roleCode}</strong><small>工作区角色</small></span><span><strong>{member.organizationUnitName}</strong><small>责任归属</small></span><span><em className={`businessStatus businessStatus${member.membershipStatus === "ACTIVE" ? "good" : "info"}`}>{member.membershipStatus === "ACTIVE" ? "已启用" : "已停用"}</em><small>M{member.membershipVersion}</small></span><span className="businessRowActions"><GsButton aria-label={`调整${member.displayName}组织归属`} onClick={() => setDialog({ type: "assign", member })} htmlType="button"><MaterialIcon name="move_down" size={18}/></GsButton></span></div>)}</div></section>
    {dialog && ["create", "editUnit", "workspace"].includes(dialog.type) ? <EditDialog page={page} dialog={dialog} onClose={() => setDialog(null)} onSaved={save}/> : null}
    {dialog?.type === "status" || dialog?.type === "assign" ? <ReasonDialog page={page} dialog={dialog} onClose={() => setDialog(null)} onSaved={save}/> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="info" size={18}/>{toast}</div> : null}
  </div>;
}
