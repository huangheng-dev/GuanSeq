"use client";

import { useMemo, useState } from "react";

import type { WorkspaceRoleCode, WorkspaceRolePermissionPage } from "@/lib/contracts";
import type { RolePermissionPageData } from "@/services/role-permission-server-service";
import { refreshRolePermissions, RolePermissionClientError } from "@/services/role-permission-client-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsInput } from "./ui";

const riskLabels = { STANDARD: "标准", SENSITIVE: "敏感", CRITICAL: "关键" } as const;

function errorText(error: unknown) {
  if (error instanceof RolePermissionClientError) {
    return error.requestId ? `${error.message}（请求编号：${error.requestId}）` : error.message;
  }
  return error instanceof Error ? error.message : "角色权限目录加载失败";
}

function UnavailableState({ data, onRecovered }: {
  data: Extract<RolePermissionPageData, { source: "unavailable" }>;
  onRecovered: (page: WorkspaceRolePermissionPage) => void;
}) {
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState(data.message);

  async function retry() {
    setPending(true);
    try {
      onRecovered(await refreshRolePermissions());
    } catch (error) {
      setMessage(errorText(error));
      setPending(false);
    }
  }

  return <div className="businessPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="admin_panel_settings" size={23}/></span><div><h2>角色权限</h2><p>查看后端实际执行的受控角色与业务动作。</p></div></div></header><section className="backendUnavailableState" role="alert"><MaterialIcon name={data.status === 403 ? "lock" : "cloud_off"} size={30}/><strong>{data.status === 403 ? "当前角色无权查看权限矩阵" : "角色权限目录暂时不可用"}</strong><p>{message}</p><small>请求编号：{data.requestId}</small><GsButton onClick={retry} loading={pending} htmlType="button">重新检查</GsButton></section></div>;
}

export function RolePermissionWorkspace({ initialData }: { initialData: RolePermissionPageData }) {
  const [pageData, setPageData] = useState<WorkspaceRolePermissionPage | null>(initialData.page);
  const [unavailable, setUnavailable] = useState(initialData.source === "unavailable" ? initialData : null);
  const [roleName, setRoleName] = useState("全部角色");
  const [moduleName, setModuleName] = useState("全部模块");
  const [query, setQuery] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [notice, setNotice] = useState("");

  const selectedRole = pageData?.roles.find((role) => role.name === roleName) ?? null;
  const filteredGroups = useMemo(() => {
    if (!pageData) return [];
    const normalizedQuery = query.trim().toLowerCase();
    return pageData.groups.map((group) => ({
      ...group,
      permissions: group.permissions.filter((permission) => {
        const moduleMatches = moduleName === "全部模块" || group.moduleName === moduleName;
        const roleMatches = !selectedRole || permission.roleCodes.includes(selectedRole.code);
        const queryMatches = !normalizedQuery || `${permission.name}${permission.description}${permission.code}${group.moduleName}`.toLowerCase().includes(normalizedQuery);
        return moduleMatches && roleMatches && queryMatches;
      }),
    })).filter((group) => group.permissions.length > 0);
  }, [moduleName, pageData, query, selectedRole]);

  if (!pageData && unavailable) return <UnavailableState data={unavailable} onRecovered={(next) => { setPageData(next); setUnavailable(null); }}/>;
  if (!pageData) return null;

  const roleMap = new Map(pageData.roles.map((role) => [role.code, role]));
  const permissionCount = pageData.groups.reduce((sum, group) => sum + group.permissions.length, 0);
  const visibleCount = filteredGroups.reduce((sum, group) => sum + group.permissions.length, 0);
  const criticalCount = pageData.groups.reduce((sum, group) => sum + group.permissions.filter((permission) => permission.risk === "CRITICAL").length, 0);

  async function refresh() {
    setRefreshing(true);
    setNotice("");
    try {
      setPageData(await refreshRolePermissions());
      setNotice("后端角色权限目录已刷新");
      window.setTimeout(() => setNotice(""), 2400);
    } catch (error) {
      setNotice(errorText(error));
    } finally {
      setRefreshing(false);
    }
  }

  return <div className="businessPage rolePermissionPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="admin_panel_settings" size={23}/></span><div><h2>角色权限</h2><p>{pageData.companyName} · {pageData.workspaceName}（{pageData.workspaceCode}）后端授权事实</p></div></div><div className="pageHeadingActions"><GsButton onClick={refresh} loading={refreshing} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新目录</GsButton></div></header>
    <section className="businessMetrics rolePermissionMetrics"><div><small>受控内置角色</small><strong className="businessMetricinfo">{pageData.roles.length}</strong><em>每位成员当前一个角色</em></div><div><small>角色受控动作</small><strong className="businessMetricinfo">{permissionCount}</strong><em>后端显式角色门禁</em></div><div><small>业务分组</small><strong className="businessMetricgood">{pageData.groups.length}</strong><em>按事实所有者归类</em></div><div><small>关键动作</small><strong className="businessMetricwarn">{criticalCount}</strong><em>仍需对象状态和范围校验</em></div></section>
    <section className="workspaceRoleBoundary rolePermissionBoundary"><MaterialIcon name="verified_user" size={20}/><div><strong>只读后端事实 · 目录版本 {pageData.catalogVersion}</strong><p>{pageData.scopeDescription}</p></div></section>
    <section className="roleCatalog" aria-labelledby="role-catalog-title"><header><div><h3 id="role-catalog-title">受控角色目录</h3><p>角色来自贯序内部工作区，不读取或信任外部身份提供者的角色声明。</p></div></header><div className="roleCatalogGrid">{pageData.roles.map((role) => <article key={role.code} className={selectedRole?.code === role.code ? "roleCatalogCard roleCatalogCardActive" : "roleCatalogCard"}><span><MaterialIcon name={role.code === "ADMIN" ? "shield_person" : "badge"} size={19}/></span><div><strong>{role.name}</strong><code>{role.code}</code><p>{role.description}</p></div></article>)}</div></section>
    <section className="businessLedger rolePermissionLedger" aria-label="后端角色权限矩阵"><div className="businessToolbar rolePermissionToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索受控动作" value={query} placeholder="搜索动作、说明或权限编码" onChange={(event) => setQuery(event.target.value)}/></div><RoundedSelect ariaLabel="筛选业务分组" options={["全部模块", ...pageData.groups.map((group) => group.moduleName)]} value={moduleName} onValueChange={setModuleName}/><RoundedSelect ariaLabel="筛选角色" options={["全部角色", ...pageData.roles.map((role) => role.name)]} value={roleName} onValueChange={setRoleName}/></div>
      <div className="rolePermissionResults"><header><span>受控业务动作</span><span>风险</span><span>允许角色</span></header>{filteredGroups.length ? filteredGroups.map((group) => <section key={group.moduleCode} className="rolePermissionGroup" aria-labelledby={`permission-group-${group.moduleCode}`}><h3 id={`permission-group-${group.moduleCode}`}><span>{group.moduleName}</span><small>{group.moduleCode} · {group.permissions.length} 项</small></h3>{group.permissions.map((permission) => <article key={permission.code}><span><strong>{permission.name}</strong><small>{permission.description}</small><code>{permission.code}</code></span><span><em className={`roleRisk roleRisk${permission.risk.toLowerCase()}`}>{riskLabels[permission.risk]}</em></span><span className="rolePermissionChips">{permission.roleCodes.map((roleCode) => <span key={roleCode} title={roleCode}>{roleMap.get(roleCode as WorkspaceRoleCode)?.name ?? roleCode}</span>)}</span></article>)}</section>) : <div className="businessEmptyState"><MaterialIcon name="policy" size={28}/><strong>没有符合条件的受控动作</strong><p>调整角色、业务分组或搜索条件。读取权限和对象范围不会因为这里没有结果而自动授予。</p></div>}</div><footer className="businessLedgerFooter"><span>当前显示 {visibleCount} / {permissionCount} 项受控动作</span><span>本页没有保存、授权或删除入口</span></footer>
    </section>
    <section className="rolePermissionLimit"><MaterialIcon name="info" size={19}/><div><strong>当前能力边界</strong><p>矩阵不代表动态 RBAC、字段权限或组织数据范围已经完成。所有业务动作仍必须经过后端的租户、工作区、对象状态、本人范围、版本和幂等校验。</p></div></section>
    {notice ? <div className="toastMessage" role="status"><MaterialIcon name="info" size={18}/>{notice}</div> : null}
  </div>;
}
