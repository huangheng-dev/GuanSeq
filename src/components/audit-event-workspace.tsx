"use client";

import { useState } from "react";

import type { WorkspaceAuditEvent, WorkspaceAuditPage } from "@/lib/contracts";
import type { AuditEventFilters, AuditEventPageData } from "@/services/audit-event-server-service";
import { AuditEventClientError, refreshAuditEvents } from "@/services/audit-event-client-service";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
import { GsButton, GsDrawerHost, GsInput, GsPagination } from "./ui";

const eventLabels: Record<string, string> = {
  SYSTEM_BOOTSTRAPPED: "系统初始化", WORKSPACE_SWITCHED: "切换工作区", USER_PROVISIONED: "创建用户",
  WORKSPACE_MEMBER_UPDATED: "更新成员", WORKSPACE_MEMBER_ACTIVATED: "恢复成员", WORKSPACE_MEMBER_DEACTIVATED: "停用成员",
  ORGANIZATION_SITE_CREATED: "创建现场单元", ORGANIZATION_UNIT_UPDATED: "更新组织单元",
  ORGANIZATION_SITE_ACTIVATED: "恢复现场单元", ORGANIZATION_SITE_DEACTIVATED: "停用现场单元",
  WORKSPACE_ORGANIZATION_UPDATED: "更新工作区组织", WORKSPACE_MEMBER_ORGANIZATION_ASSIGNED: "调整成员归属",
};
const objectLabels: Record<string, string> = {
  SYSTEM: "系统", WORKSPACE: "工作区", USER: "用户", WORKSPACE_MEMBER: "工作区成员", ORGANIZATION_UNIT: "组织单元",
};

function dateTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "medium", hour12: false }).format(new Date(value));
}
function day(value: string) { return value.slice(0, 10); }
function localDay(value: Date) {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const date = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${date}`;
}
function localDayBoundary(value: string, endOfDay = false) {
  return new Date(`${value}T${endOfDay ? "23:59:59.999" : "00:00:00.000"}`).toISOString();
}
function errorText(error: unknown) {
  if (error instanceof AuditEventClientError) return error.requestId ? `${error.message}（请求编号：${error.requestId}）` : error.message;
  return error instanceof Error ? error.message : "系统操作审计加载失败";
}
function detailText(value: unknown) {
  if (value == null) return "—";
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return String(value);
  return JSON.stringify(value);
}

function AuditDetail({ event, onClose }: { event: WorkspaceAuditEvent; onClose: () => void }) {
  return <GsDrawerHost onClose={onClose}><aside className="recordDrawer auditEventDrawer" role="dialog" aria-modal="true" aria-labelledby="audit-detail-title" onMouseDown={(e) => e.stopPropagation()}>
    <header className="recordDrawerHeader"><div><h2 id="audit-detail-title">{eventLabels[event.eventType] ?? event.eventType}</h2><p>{objectLabels[event.objectType] ?? event.objectType} · {event.objectId ?? "无对象编号"}</p></div><GsButton className="iconButton" aria-label="关闭审计详情" onClick={onClose} htmlType="button"><MaterialIcon name="close"/></GsButton></header>
    <section className="auditEventDetailSummary"><div><small>发生时间</small><strong>{dateTime(event.occurredAt)}</strong></div><div><small>操作人</small><strong>{event.actorDisplayName ?? "系统"}</strong><span>{event.actorUsername ?? "无登录主体"}</span></div><div><small>请求编号</small><strong>{event.requestId ?? "未提供"}</strong></div><div><small>审计编号</small><strong>{event.id}</strong></div></section>
    <section className="mrpRunDetailSection auditEventDetails"><header><div><h3>服务端证据字段</h3><p>字段来自不可由前端覆盖的 identity.audit_events 事实。</p></div><strong>{Object.keys(event.details).length}</strong></header>{Object.keys(event.details).length ? <dl>{Object.entries(event.details).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{detailText(value)}</dd></div>)}</dl> : <div className="businessEmptyState"><MaterialIcon name="data_object" size={26}/><strong>本事件没有附加字段</strong><p>事件类型、对象、责任人、请求编号和时间仍构成基础审计证据。</p></div>}</section>
  </aside></GsDrawerHost>;
}

function Unavailable({ data, onRecovered }: { data: Extract<AuditEventPageData, { source: "unavailable" }>; onRecovered: (page: WorkspaceAuditPage) => void }) {
  const [pending, setPending] = useState(false); const [message, setMessage] = useState(data.message);
  async function retry() { setPending(true); try { onRecovered(await refreshAuditEvents()); } catch (error) { setMessage(errorText(error)); setPending(false); } }
  return <div className="businessPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="manage_search" size={23}/></span><div><h2>操作审计</h2><p>查询当前工作区的身份与系统治理证据。</p></div></div></header><section className="backendUnavailableState" role="alert"><MaterialIcon name={data.status === 403 ? "lock" : "cloud_off"} size={30}/><strong>{data.status === 403 ? "当前角色无权查看操作审计" : "系统操作审计暂时不可用"}</strong><p>{message}</p><small>请求编号：{data.requestId}</small><GsButton onClick={retry} loading={pending} htmlType="button">重新检查</GsButton></section></div>;
}

export function AuditEventWorkspace({ initialData }: { initialData: AuditEventPageData }) {
  const [pageData, setPageData] = useState(initialData.page); const [unavailable, setUnavailable] = useState(initialData.source === "unavailable" ? initialData : null);
  const [eventType, setEventType] = useState(""); const [objectType, setObjectType] = useState(""); const [actorId, setActorId] = useState(""); const [query, setQuery] = useState("");
  const [fromDate, setFromDate] = useState(initialData.page ? day(initialData.page.occurredFrom) : ""); const [toDate, setToDate] = useState(initialData.page ? day(initialData.page.occurredTo) : "");
  const [pending, setPending] = useState(false); const [notice, setNotice] = useState(""); const [detail, setDetail] = useState<WorkspaceAuditEvent | null>(null);
  if (!pageData && unavailable) return <Unavailable data={unavailable} onRecovered={(next) => { setPageData(next); setUnavailable(null); setFromDate(day(next.occurredFrom)); setToDate(day(next.occurredTo)); }}/>;
  if (!pageData) return null;

  const currentPageSize = pageData.size;
  const activeFilters = [eventType, objectType, actorId, query].filter(Boolean).length + (fromDate || toDate ? 1 : 0);
  const actorName = (id: string) => pageData.actors.find((actor) => actor.id === id)?.displayName ?? id;
  const filters = (page = 0, size = pageData.size): AuditEventFilters => ({ page, size, eventType, objectType, actorId, query: query.trim(),
    occurredFrom: fromDate ? localDayBoundary(fromDate) : undefined, occurredTo: toDate ? localDayBoundary(toDate, true) : undefined });
  async function load(nextFilters: AuditEventFilters, success: string) { setPending(true); setNotice(""); try { const next = await refreshAuditEvents(nextFilters); setPageData(next); setNotice(success); window.setTimeout(() => setNotice(""), 2400); } catch (error) { setNotice(errorText(error)); } finally { setPending(false); } }
  function reset() { setEventType(""); setObjectType(""); setActorId(""); setQuery(""); const to = new Date(); const from = new Date(to.getTime() - 30 * 86400000); setFromDate(localDay(from)); setToDate(localDay(to)); void load({ page: 0, size: currentPageSize }, "已恢复最近 30 天的默认范围"); }

  return <div className="businessPage auditEventPage"><header className="pageHeading businessPageHeading"><div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name="manage_search" size={23}/></span><div><h2>操作审计</h2><p>{pageData.companyName} · {pageData.workspaceName}（{pageData.workspaceCode}）治理证据</p></div></div><div className="pageHeadingActions"><GsButton onClick={() => load(filters(pageData.page), "审计事实已刷新")} loading={pending} htmlType="button"><MaterialIcon name="refresh" size={17}/>刷新</GsButton></div></header>
    <section className="businessMetrics"><div><small>筛选结果</small><strong className="businessMetricinfo">{pageData.totalElements}</strong><em>服务端分页结果</em></div><div><small>事件类型</small><strong>{pageData.eventTypes.length}</strong><em>当前工作区已有事实</em></div><div><small>操作主体</small><strong className="businessMetricgood">{pageData.actors.length}</strong><em>系统事件可能没有登录主体</em></div><div><small>查询范围</small><strong className="businessMetricwarn">≤ 90 天</strong><em>当前使用 {activeFilters} 组条件</em></div></section>
    <section className="workspaceRoleBoundary auditEventBoundary"><MaterialIcon name="verified_user" size={20}/><div><strong>只读后端事实 · 当前工作区隔离</strong><p>{pageData.scopeDescription}</p></div></section>
    <section className="businessLedger auditEventLedger" aria-label="系统操作审计"><div className="businessToolbar auditEventToolbar"><div className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label="搜索请求或对象编号" maxLength={120} value={query} onChange={(e) => setQuery(e.target.value)} placeholder="请求编号或对象编号"/></div><RoundedSelect ariaLabel="筛选事件类型" options={["全部事件", ...pageData.eventTypes.map((item) => eventLabels[item] ?? item)]} value={eventType ? eventLabels[eventType] ?? eventType : "全部事件"} onValueChange={(value) => setEventType(value === "全部事件" ? "" : pageData.eventTypes.find((item) => (eventLabels[item] ?? item) === value) ?? "")}/><RoundedSelect ariaLabel="筛选对象类型" options={["全部对象", ...pageData.objectTypes.map((item) => objectLabels[item] ?? item)]} value={objectType ? objectLabels[objectType] ?? objectType : "全部对象"} onValueChange={(value) => setObjectType(value === "全部对象" ? "" : pageData.objectTypes.find((item) => (objectLabels[item] ?? item) === value) ?? "")}/><RoundedSelect ariaLabel="筛选操作人" options={["全部操作人", ...pageData.actors.map((actor) => actor.displayName)]} value={actorId ? actorName(actorId) : "全部操作人"} onValueChange={(value) => setActorId(value === "全部操作人" ? "" : pageData.actors.find((actor) => actor.displayName === value)?.id ?? "")}/><div className="auditDateRange"><GsInput aria-label="审计开始日期" type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)}/><span>至</span><GsInput aria-label="审计结束日期" type="date" value={toDate} onChange={(e) => setToDate(e.target.value)}/></div><div className="businessTableTools"><GsButton onClick={reset} disabled={pending} htmlType="button">重置</GsButton><GsButton intent="primary" onClick={() => load(filters(0), "筛选条件已应用")} loading={pending} htmlType="button"><MaterialIcon name="filter_alt" size={17}/>查询</GsButton></div></div>
      <div className="auditEventTable" role="table"><div className="auditEventTableHeader" role="row"><span>动作 / 对象</span><span>操作人</span><span>请求编号</span><span>发生时间</span><span>证据</span></div>{pageData.items.length ? pageData.items.map((event) => <GsButton className="auditEventTableRow" role="row" key={event.id} onClick={() => setDetail(event)} htmlType="button"><span><strong>{eventLabels[event.eventType] ?? event.eventType}</strong><small>{objectLabels[event.objectType] ?? event.objectType} · {event.objectId ?? "无对象编号"}</small></span><span><strong>{event.actorDisplayName ?? "系统"}</strong><small>{event.actorUsername ?? "无登录主体"}</small></span><span><code>{event.requestId ?? "未提供"}</code></span><span>{dateTime(event.occurredAt)}</span><span><MaterialIcon name="open_in_new" size={17}/>查看</span></GsButton>) : <div className="businessEmptyState"><MaterialIcon name="manage_search" size={30}/><strong>当前条件下没有审计事件</strong><p>调整时间范围或筛选条件；本页不会用 Mock 数据填充空结果。</p></div>}</div>
      <footer className="businessLedgerFooter"><span>共 {pageData.totalElements} 条 · 第 {pageData.totalPages ? pageData.page + 1 : 0} / {pageData.totalPages} 页</span><GsPagination current={pageData.page + 1} pageSize={pageData.size} total={pageData.totalElements} pageSizeOptions={[10, 20, 50, 100]} onChange={(nextPage, nextSize) => void load(filters(nextPage - 1, nextSize), "审计分页已刷新")}/></footer>
    </section>
    <section className="rolePermissionLimit auditEventLimit"><MaterialIcon name="account_tree" size={19}/><div><strong>业务事件仍由业务模块持有</strong><p>销售订单、生产工单、检验、库存流水和结算记录的完整责任链在对应单据详情中查看；这里不会直接扫描或拼接其他模块的事件表。</p></div></section>
    {detail ? <AuditDetail event={detail} onClose={() => setDetail(null)}/> : null}{notice ? <div className="toastMessage" role="status"><MaterialIcon name="info" size={18}/>{notice}</div> : null}
  </div>;
}
