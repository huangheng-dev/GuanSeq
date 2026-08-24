"use client";

import { type FormEvent, useState } from "react";

import type { WorkspaceRole, WorkspaceRoleCode, WorkspaceUser, WorkspaceUserPage } from "@/lib/contracts";
import type { WorkspaceUserPageData } from "@/services/workspace-user-server-service";
import {
  changeWorkspaceUserStatus,
  createWorkspaceUser,
  refreshWorkspaceUsers,
  updateWorkspaceUser,
  WorkspaceUserClientError,
} from "@/services/workspace-user-client-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsInput, GsModalHost, GsPagination, GsTextArea } from "./ui";

function errorText(error: unknown) {
  if (error instanceof WorkspaceUserClientError) {
    return error.requestId ? `${error.message}（请求编号：${error.requestId}）` : error.message;
  }
  return error instanceof Error ? error.message : "成员操作失败";
}

function MemberFormDialog({ user, roles, onClose, onSaved }: {
  user: WorkspaceUser | null;
  roles: WorkspaceRole[];
  onClose: () => void;
  onSaved: (saved: WorkspaceUser) => void;
}) {
  const [username, setUsername] = useState(user?.username ?? "");
  const [displayName, setDisplayName] = useState(user?.displayName ?? "");
  const [roleCode, setRoleCode] = useState<WorkspaceRoleCode>(user?.roleCode ?? roles[0]?.code ?? "PRODUCTION_OPERATOR");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const selectedRole = roles.find((role) => role.code === roleCode);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!displayName.trim() || (!user && (!username.trim() || /\s/.test(username)))) {
      setError("请填写显示姓名；外部用户名不能为空或包含空格。");
      return;
    }
    setPending(true);
    setError("");
    try {
      onSaved(user
        ? await updateWorkspaceUser(user, displayName.trim(), roleCode)
        : await createWorkspaceUser(username.trim(), displayName.trim(), roleCode));
    } catch (reason) {
      setError(errorText(reason));
      setPending(false);
    }
  }

  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog" role="dialog" aria-modal="true" aria-labelledby="workspace-user-form-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={user ? "manage_accounts" : "person_add"} size={22}/></span><div><h2 id="workspace-user-form-title">{user ? "编辑工作区成员" : "开通工作区成员"}</h2><p>{user ? `${user.username} · 双版本并发保护` : "用户名必须与身份提供者签发的声明精确一致"}</p></div><GsButton className="iconButton" onClick={onClose} disabled={pending} aria-label="关闭成员表单" htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <form onSubmit={submit}>
      <div className="formGrid">
        <label className="formField"><span>外部用户名<em>必填</em></span><GsInput value={username} disabled={Boolean(user)} maxLength={80} placeholder="例如 zhang.wei" onChange={(event) => setUsername(event.target.value)}/><small>创建后不可由本用例修改，避免身份映射漂移。</small></label>
        <label className="formField"><span>显示姓名<em>必填</em></span><GsInput value={displayName} maxLength={80} onChange={(event) => setDisplayName(event.target.value)}/></label>
        <label className="formField formFieldFull"><span>工作区角色<em>必填</em></span><RoundedSelect ariaLabel="选择工作区角色" options={roles.map((role) => role.name)} value={selectedRole?.name} onValueChange={(name) => { const role = roles.find((candidate) => candidate.name === name); if (role) setRoleCode(role.code); }}/><small>{selectedRole?.description}</small></label>
      </div>
      <div className="mrpRunTruthNotice"><MaterialIcon name="verified_user" size={18}/><span><strong>内部授权边界</strong>外部身份只证明用户是谁；当前角色由贯序后端决定可执行的制造业务动作。</span></div>
      {error ? <div className="formError" role="alert">{error}</div> : null}
      <footer className="dialogFooter"><span><MaterialIcon name="history" size={16}/>保存后记录角色、操作者和请求编号</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent="primary" loading={pending} htmlType="submit">{pending ? "正在保存" : user ? "保存成员" : "开通成员"}</GsButton></div></footer>
    </form>
  </section></GsModalHost>;
}

function MemberStatusDialog({ user, onClose, onSaved }: {
  user: WorkspaceUser;
  onClose: () => void;
  onSaved: (saved: WorkspaceUser) => void;
}) {
  const nextStatus = user.membershipStatus === "ACTIVE" ? "INACTIVE" : "ACTIVE";
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (reason.trim().length < 4) {
      setError("请填写至少 4 个字符的变更原因。");
      return;
    }
    setPending(true);
    setError("");
    try {
      onSaved(await changeWorkspaceUserStatus(user, nextStatus, reason.trim()));
    } catch (reasonError) {
      setError(errorText(reasonError));
      setPending(false);
    }
  }

  return <GsModalHost onClose={() => { if (!pending) onClose(); }}><section className="businessDialog workspaceUserStatusDialog" role="dialog" aria-modal="true" aria-labelledby="workspace-user-status-title" onMouseDown={(event) => event.stopPropagation()}>
    <header className="dialogHeader"><span className="dialogTitleMark"><MaterialIcon name={nextStatus === "ACTIVE" ? "person_check" : "person_off"} size={22}/></span><div><h2 id="workspace-user-status-title">{nextStatus === "ACTIVE" ? "恢复成员访问" : "停用成员访问"}</h2><p>{user.displayName} · {user.username}</p></div><GsButton className="iconButton" onClick={onClose} disabled={pending} aria-label="关闭成员状态确认" htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <form onSubmit={submit}><div className="formGrid"><label className="formField formFieldFull"><span>变更原因<em>必填</em></span><GsTextArea rows={4} maxLength={300} value={reason} placeholder={nextStatus === "ACTIVE" ? "例如：岗位复岗并已完成权限复核" : "例如：岗位调整，暂停当前工作区访问"} onChange={(event) => setReason(event.target.value)}/><small>原因会进入服务端审计，不能只依赖聊天或工单备注。</small></label></div>
      <div className="mrpRunTruthNotice"><MaterialIcon name="info" size={18}/><span><strong>{nextStatus === "ACTIVE" ? "恢复范围" : "停用范围"}</strong>只改变当前工作区成员关系，不修改身份提供者，也不影响其他工作区的合法成员关系。</span></div>
      {error ? <div className="formError" role="alert">{error}</div> : null}<footer className="dialogFooter"><span>版本 {user.membershipVersion}</span><div><GsButton onClick={onClose} disabled={pending} htmlType="button">取消</GsButton><GsButton intent={nextStatus === "ACTIVE" ? "primary" : "danger"} loading={pending} htmlType="submit">{pending ? "正在提交" : nextStatus === "ACTIVE" ? "确认恢复" : "确认停用"}</GsButton></div></footer>
    </form>
  </section></GsModalHost>;
}

function UnavailableState({ data, onRecovered }: { data: Extract<WorkspaceUserPageData, { source: "unavailable" }>; onRecovered: (page: WorkspaceUserPage) => void }) {
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState(data.message);
  async function retry() {
    setPending(true);
    try { onRecovered(await refreshWorkspaceUsers()); }
    catch (error) { setMessage(errorText(error)); setPending(false); }
  }
  return <div className="businessPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="manage_accounts" size={23}/></span><div><h2>用户管理</h2><p>维护当前工作区成员、角色和访问状态。</p></div></div></header><section className="backendUnavailableState" role="alert"><MaterialIcon name={data.status === 403 ? "lock" : "cloud_off"} size={30}/><strong>{data.status === 403 ? "当前角色无权管理成员" : "成员管理服务暂时不可用"}</strong><p>{message}</p><small>请求编号：{data.requestId}</small><GsButton onClick={retry} loading={pending} htmlType="button">重新检查</GsButton></section></div>;
}

export function WorkspaceUserWorkspace({ initialData }: { initialData: WorkspaceUserPageData }) {
  const [pageData, setPageData] = useState<WorkspaceUserPage | null>(initialData.page);
  const [unavailable, setUnavailable] = useState(initialData.source === "unavailable" ? initialData : null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("全部状态");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [dialog, setDialog] = useState<{ type: "create" } | { type: "edit" | "status"; user: WorkspaceUser } | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [toast, setToast] = useState("");

  if (!pageData && unavailable) return <UnavailableState data={unavailable} onRecovered={(next) => { setPageData(next); setUnavailable(null); }}/>;
  if (!pageData) return null;
  const roleMap = new Map(pageData.availableRoles.map((role) => [role.code, role]));
  const filtered = pageData.items.filter((item) => {
    const queryMatches = !query.trim() || `${item.username}${item.displayName}${roleMap.get(item.roleCode)?.name ?? ""}`.toLowerCase().includes(query.trim().toLowerCase());
    const statusMatches = status === "全部状态" || (status === "已启用" ? item.membershipStatus === "ACTIVE" : item.membershipStatus === "INACTIVE");
    return queryMatches && statusMatches;
  });
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const rows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const active = pageData.items.filter((item) => item.membershipStatus === "ACTIVE").length;
  const admins = pageData.items.filter((item) => item.membershipStatus === "ACTIVE" && item.roleCode === "ADMIN").length;

  function replace(saved: WorkspaceUser) {
    setPageData((current) => current ? { ...current, items: current.items.some((item) => item.userId === saved.userId) ? current.items.map((item) => item.userId === saved.userId ? saved : item) : [...current.items, saved], totalElements: current.items.some((item) => item.userId === saved.userId) ? current.totalElements : current.totalElements + 1 } : current);
    setDialog(null);
    setToast(`${saved.displayName}的成员事实已更新`);
    window.setTimeout(() => setToast(""), 2800);
  }

  async function refresh() {
    setRefreshing(true);
    try { setPageData(await refreshWorkspaceUsers()); setToast("成员与角色事实已刷新"); window.setTimeout(() => setToast(""), 2200); }
    catch (error) { setToast(errorText(error)); }
    finally { setRefreshing(false); }
  }

  return <div className="businessPage workspaceUserPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="manage_accounts" size={23}/></span><div><h2>用户管理</h2><p>{pageData.companyName} · {pageData.workspaceName}（{pageData.workspaceCode}）成员、角色与访问治理</p></div></div><div className="pageHeadingActions"><GsButton onClick={refresh} loading={refreshing} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新</GsButton><GsButton intent="primary" onClick={() => setDialog({ type: "create" })} htmlType="button"><MaterialIcon name="person_add" size={17}/>开通成员</GsButton></div></header>
    <section className="businessMetrics"><div><small>工作区成员</small><strong className="businessMetricinfo">{pageData.items.length}</strong><em>只统计当前工作区</em></div><div><small>已启用</small><strong className="businessMetricgood">{active}</strong><em>可进入当前工作区</em></div><div><small>已停用</small><strong className={active === pageData.items.length ? "businessMetricinfo" : "businessMetricwarn"}>{pageData.items.length - active}</strong><em>保留账号与审计</em></div><div><small>启用管理员</small><strong className="businessMetricinfo">{admins}</strong><em>管理员不能在此修改自己</em></div></section>
    <section className="businessLedger"><div className="businessToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索工作区成员" value={query} placeholder="搜索用户名、姓名或角色" onChange={(event) => { setQuery(event.target.value); setPage(1); }}/></div><RoundedSelect ariaLabel="成员状态" options={["全部状态", "已启用", "已停用"]} value={status} onValueChange={(value) => { setStatus(value); setPage(1); }}/></div>
      <div className="workspaceUserTable" role="table" aria-label="工作区成员列表"><div className="workspaceUserTableHeader" role="row"><span>成员 / 外部用户名</span><span>工作区角色</span><span>账号与成员状态</span><span>版本 / 更新时间</span><span>操作</span></div>{rows.length ? rows.map((user) => { const self = user.userId === pageData.currentUserId; const role = roleMap.get(user.roleCode); return <div className="workspaceUserTableRow" role="row" key={user.userId}><span><strong>{user.displayName}{self ? "（当前用户）" : ""}</strong><small>{user.username}</small></span><span><strong>{role?.name ?? user.roleCode}</strong><small>{user.roleCode}</small></span><span><em className={`businessStatus businessStatus${user.membershipStatus === "ACTIVE" ? "good" : "info"}`}>{user.membershipStatus === "ACTIVE" ? "成员已启用" : "成员已停用"}</em><small>内部账号：{user.accountStatus === "ACTIVE" ? "已启用" : user.accountStatus}</small></span><span><strong>U{user.userVersion} · M{user.membershipVersion}</strong><small>{new Date(user.updatedAt).toLocaleString("zh-CN", { hour12: false })}</small></span><span className="businessRowActions"><GsButton aria-label={`编辑${user.displayName}`} disabled={self} onClick={() => setDialog({ type: "edit", user })} htmlType="button"><MaterialIcon name="edit" size={18}/></GsButton><GsButton aria-label={`${user.membershipStatus === "ACTIVE" ? "停用" : "恢复"}${user.displayName}`} disabled={self} onClick={() => setDialog({ type: "status", user })} htmlType="button"><MaterialIcon name={user.membershipStatus === "ACTIVE" ? "person_off" : "person_check"} size={18}/></GsButton></span></div>; }) : <div className="businessEmptyState"><MaterialIcon name="group_off" size={28}/><strong>没有符合条件的成员</strong><p>调整搜索或状态筛选，或开通第一位业务岗位成员。</p></div>}</div>
      <footer className="businessLedgerFooter"><span>第 {filtered.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, filtered.length)} 条，共 {filtered.length} 条</span><GsPagination current={currentPage} pageSize={pageSize} total={filtered.length} pageSizeOptions={[10, 20, 50]} onChange={(nextPage, nextSize) => { setPage(nextPage); setPageSize(nextSize); }}/></footer>
    </section>
    <section className="workspaceRoleBoundary"><MaterialIcon name="shield_lock" size={20}/><div><strong>角色目录是当前已接入后端权限的事实</strong><p>本页不创建自定义角色，不自动读取 IdP 组。组织架构、岗位和组合权限仍属于后续受控能力。</p></div></section>
    {dialog?.type === "create" ? <MemberFormDialog user={null} roles={pageData.availableRoles} onClose={() => setDialog(null)} onSaved={replace}/> : null}
    {dialog?.type === "edit" ? <MemberFormDialog user={dialog.user} roles={pageData.availableRoles} onClose={() => setDialog(null)} onSaved={replace}/> : null}
    {dialog?.type === "status" ? <MemberStatusDialog user={dialog.user} onClose={() => setDialog(null)} onSaved={replace}/> : null}
    {toast ? <div className="toastMessage" role="status"><MaterialIcon name="info" size={18}/>{toast}</div> : null}
  </div>;
}
