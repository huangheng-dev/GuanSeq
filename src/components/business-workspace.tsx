"use client";
import { type FormEvent, type KeyboardEvent as ReactKeyboardEvent, useEffect, useId, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { BusinessPageModel, BusinessRow } from "@/lib/business-page-data";
import { readStoredBusinessRows, writeStoredBusinessRows } from "@/services/business-local-store";
import { addRecordAttachment, addRecordComment, deleteBusinessView, readCapabilityFeedbacks, readRecordCollaboration, readSavedBusinessViews, readUserProfile, saveBusinessView, submitCapabilityFeedback, type RecordCollaboration, type SavedBusinessView } from "@/services/front-end-product-service";
import { submitBusinessMutation } from "@/services/manufacturing-service";
import { GsButton, GsCheckbox, GsInput, GsModal, GsPagination, GsTextArea, GsDrawerHost, GsModalHost } from "./ui";
import { MaterialIcon } from "./material-icon";
import { RoundedSelect } from "./rounded-select";
type BusinessWorkspaceProps = {
    model: BusinessPageModel;
};
type FormMode = "create" | "edit";
function SelectionCheckbox({ label, checked, indeterminate = false, onChange }: {
    label: string;
    checked: boolean;
    indeterminate?: boolean;
    onChange: (checked: boolean) => void;
}) {
    return (<span className="selectionCheckbox" onClick={(event) => event.stopPropagation()} onKeyDown={(event) => event.stopPropagation()}>
      <GsCheckbox ariaLabel={label} checked={checked} indeterminate={indeterminate} onCheckedChange={onChange}/>
    </span>);
}
function downloadCsv(model: BusinessPageModel, rows: BusinessRow[]) {
    const escapeCell = (value: string) => `"${value.replaceAll('"', '""')}"`;
    const lines = [
        [...model.columns, "状态", "负责人"].map(escapeCell).join(","),
        ...rows.map((row) => [row.id, ...row.cells, row.status, row.owner].map(escapeCell).join(",")),
    ];
    const blob = new Blob([`\uFEFF${lines.join("\n")}`], { type: "text/csv;charset=utf-8" });
    const href = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = href;
    anchor.download = `${model.title}-${new Date().toISOString().slice(0, 10)}.csv`;
    anchor.click();
    URL.revokeObjectURL(href);
}
function downloadCapabilityPlan(model: BusinessPageModel) {
    const escapeCell = (value: string) => `"${value.replaceAll('"', '""')}"`;
    const rows = [
        [model.title, "能力规划说明"],
        ["当前状态", "尚未接入真实后端或设备"],
        [],
        ["建设阶段", "状态", "说明"],
        ...model.metrics.map((metric) => [metric.label, metric.value, metric.note]),
        [],
        ["实施步骤", "验收说明"],
        ...model.workflow.map((step) => [step.label, step.detail]),
    ];
    const href = URL.createObjectURL(new Blob([`\uFEFF${rows.map((row) => row.map(escapeCell).join(",")).join("\n")}`], { type: "text/csv;charset=utf-8" }));
    const anchor = document.createElement("a");
    anchor.href = href;
    anchor.download = `${model.title}-能力规划.csv`;
    anchor.click();
    URL.revokeObjectURL(href);
}
function DeleteConfirmDialog({ model, count, pending, onClose, onConfirm }: {
    model: BusinessPageModel;
    count: number;
    pending: boolean;
    onClose: () => void;
    onConfirm: () => void;
}) {
    const actionLabel = model.dataSource === "backend" ? "停用" : "删除";
    return (<GsModal open title={<span className="gsModalTitle"><MaterialIcon name={model.dataSource === "backend" ? "block" : "delete"} size={22}/>{actionLabel}选中的 {count} 条{model.recordNoun}？</span>} onCancel={pending ? undefined : onClose} closable={!pending} footer={[
            <GsButton key="cancel" disabled={pending} onClick={onClose}>取消</GsButton>,
            <GsButton key="confirm" intent="danger" loading={pending} onClick={onConfirm}>{pending ? `正在${actionLabel}` : `确认${actionLabel} ${count} 条`}</GsButton>,
        ]}>
      <p id="delete-confirm-description" className="gsModalDescription">{model.dataSource === "backend" ? "停用后记录仍保留，可在页面底部立即恢复；新业务将不能再引用这些记录。" : "这些记录将从当前前端工作区移除。删除完成后可通过页面底部提示立即撤销。"}</p>
    </GsModal>);
}
function toneForStatus(status: string): BusinessRow["tone"] {
    if (["风险", "异常", "逾期", "冻结", "不合格", "失败", "超期"].some((keyword) => status.includes(keyword)))
        return "risk";
    if (["待", "审核", "预警", "处理中", "部分"].some((keyword) => status.includes(keyword)))
        return "warn";
    if (["完成", "合格", "可用", "通过", "关闭", "已发", "已收"].some((keyword) => status.includes(keyword)))
        return "good";
    return "info";
}
function BatchEditDialog({ model, count, statuses, owners, pending, onClose, onConfirm, }: {
    model: BusinessPageModel;
    count: number;
    statuses: string[];
    owners: string[];
    pending: boolean;
    onClose: () => void;
    onConfirm: (changes: {
        status?: string;
        owner?: string;
    }) => void;
}) {
    const [nextStatus, setNextStatus] = useState("保持不变");
    const [nextOwner, setNextOwner] = useState("保持不变");
    const unchanged = nextStatus === "保持不变" && nextOwner === "保持不变";
    return (<GsModal open title={<span className="gsModalTitle"><MaterialIcon name="edit_note" size={22}/>修改选中的 {count} 条{model.recordNoun}</span>} onCancel={pending ? undefined : onClose} closable={!pending} footer={[
            <GsButton key="cancel" disabled={pending} onClick={onClose}>取消</GsButton>,
            <GsButton key="confirm" intent="primary" disabled={unchanged} loading={pending} onClick={() => onConfirm({ status: nextStatus === "保持不变" ? undefined : nextStatus, owner: nextOwner === "保持不变" ? undefined : nextOwner })}><MaterialIcon name="check" size={17}/>应用到 {count} 条</GsButton>,
        ]}>
      <p className="gsModalDescription">只更新选择的字段；设置为“保持不变”的字段不会被覆盖。</p>
      <div className="gsModalForm gsModalFormColumns">
        <label><span>业务状态</span><RoundedSelect ariaLabel="批量设置业务状态" options={["保持不变", ...statuses]} value={nextStatus} onValueChange={setNextStatus} size="field"/></label>
        <label><span>负责人</span><RoundedSelect ariaLabel="批量设置负责人" options={["保持不变", ...owners]} value={nextOwner} onValueChange={setNextOwner} size="field"/></label>
      </div>
      <aside className="gsModalNotice"><MaterialIcon name="info" size={17}/><span>本次修改将应用到已勾选的全部记录，并保留{model.dataSource === "backend" ? "版本与审计记录" : "模拟操作请求"}。</span></aside>
    </GsModal>);
}
function SaveViewDialog({ model, onClose, onSave }: {
    model: BusinessPageModel;
    onClose: () => void;
    onSave: (name: string) => Promise<void>;
}) {
    const [name, setName] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!name.trim()) {
            setError("请输入视图名称。");
            return;
        }
        setPending(true);
        setError("");
        try {
            await onSave(name);
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "视图保存失败，请重试。");
            setPending(false);
        }
    }
    return (<GsModal open title={<span className="gsModalTitle"><MaterialIcon name="bookmark_add" size={22}/>保存{model.title}视图</span>} onCancel={pending ? undefined : onClose} closable={!pending} footer={[
            <GsButton key="cancel" disabled={pending} onClick={onClose}>取消</GsButton>,
            <GsButton key="save" intent="primary" htmlType="submit" form="save-view-form" loading={pending}>保存视图</GsButton>,
        ]}>
      <p className="gsModalDescription">保存当前搜索、筛选、排序和显示列，方便下次直接恢复。</p>
      <form id="save-view-form" className="gsModalForm" onSubmit={submit} noValidate>
        <label><span>视图名称</span><GsInput autoFocus name="viewName" value={name} onChange={(event) => setName(event.target.value)} placeholder="例如：我的待处理订单"/></label>
        {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
      </form>
    </GsModal>);
}
function BusinessFormDialog({ model, mode, row, onClose, onSaved, }: {
    model: BusinessPageModel;
    mode: FormMode;
    row: BusinessRow | null;
    onClose: () => void;
    onSaved: (row: BusinessRow, message: string) => void;
}) {
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const [roleMemberCount, setRoleMemberCount] = useState(2);
    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setPending(true);
        setError("");
        const formData = new FormData(event.currentTarget);
        const values = Object.fromEntries(Array.from(formData.entries()).map(([key, value]) => [key, String(value)]));
        const missingField = model.formFields.find((field) => field.required && !values[field.name]?.trim());
        if (missingField) {
            setError(`请填写或选择${missingField.label}`);
            setPending(false);
            return;
        }
        try {
            if (row?.entityId)
                values._entityId = row.entityId;
            if (row?.version !== undefined)
                values._version = String(row.version);
            const result = await submitBusinessMutation({ pathname: model.pathname, action: mode === "create" ? "create" : "update", values });
            const sampleId = model.rows[0]?.id ?? "NEW-001";
            const idPrefix = sampleId.replace(/-\d+$/, "") || "NEW";
            const savedRow: BusinessRow = result.row ?? {
                id: row?.id ?? `${idPrefix}-${result.requestId.slice(-4)}`,
                cells: model.cellFields.map((fieldName, index) => {
                    const genericFallbacks = index === 0 ? ["name"] : index === 1 ? ["priority", "version", "standard", "dimension"] : index === 2 ? ["amount", "quantity", "workshop", "warehouse", "scope", "sample", "asset", "account"] : ["date", "cycle"];
                    return values[fieldName]?.trim() || genericFallbacks.map((candidate) => values[candidate]?.trim()).find(Boolean) || row?.cells[index] || "待完善";
                }),
                status: row?.status ?? "草稿",
                tone: row?.tone ?? "info",
                owner: values.owner || row?.owner || "当前用户",
                description: values.remark || row?.description || `新建的${model.recordNoun}记录。`,
                ageInDays: row?.ageInDays ?? 0,
            };
            onSaved(savedRow, mode === "create" ? `${model.recordNoun}已创建${model.dataSource === "backend" ? "" : "并保存为草稿"}` : `${model.recordNoun}已更新`);
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "提交失败，请稍后重试。");
            setPending(false);
        }
    }
    return (<GsModal open width={780} title={<span className="gsModalTitle"><MaterialIcon name={model.icon} size={22}/>{mode === "create" ? model.primaryAction : `编辑${model.recordNoun}`}</span>} onCancel={pending ? undefined : onClose} closable={!pending} footer={null}>
        <p id="business-form-description" className="gsModalDescription">{model.dataSource === "backend" ? "保存后立即写入当前工作区，并保留版本与审计记录。" : "信息将通过模拟数据契约异步保存，后续可无缝替换为正式接口。"}</p>
        <form onSubmit={handleSubmit} noValidate>
          <div className="formGrid">
            {model.formFields.map((field) => {
            const cellIndex = model.cellFields.indexOf(field.name);
            const cellValue = cellIndex >= 0 ? row?.cells[cellIndex] ?? "" : "";
            const defaultValue = row?.formValues?.[field.name] ?? (field.name === "owner" ? row?.owner ?? "" : field.name === "remark" ? row?.description ?? "" : cellValue);
            return (<label className={field.span === "full" ? "formField formFieldFull" : "formField"} key={field.name}>
                <span>{field.label}{field.required ? <em>必填</em> : null}</span>
                {field.type === "select" ? (<RoundedSelect ariaLabel={field.label} name={field.name} options={field.options ?? []} defaultValue={defaultValue} placeholder={`请选择${field.label}`} size="field"/>) : field.type === "textarea" ? (<GsTextArea name={field.name} rows={4} defaultValue={defaultValue} placeholder={field.placeholder}/>) : (<GsInput name={field.name} type={field.type} required={field.required} defaultValue={defaultValue || (field.type === "date" ? "2026-08-14" : "")} placeholder={field.placeholder}/>)}
                {field.required ? <small>此项用于后续流程校验</small> : null}
              </label>);
        })}
          </div>
          {model.pathname === "/settings/roles" ? (<section className="roleGovernanceForm" aria-labelledby="role-governance-title">
              <header><div><h3 id="role-governance-title">功能权限与成员范围</h3></div><span><MaterialIcon name="security" size={18}/>敏感操作需要复核</span></header>
              <div className="rolePermissionGrid">
                {["查看业务数据", "创建与编辑", "审核与下达", "导入与导出", "成本与价格", "平台配置"].map((permission, index) => <label key={permission}><GsCheckbox name={`permission-${index}`} defaultChecked={index < 2}/><span><MaterialIcon name={index < 2 ? "check_circle" : "radio_button_unchecked"} size={18}/><strong>{permission}</strong><small>{index < 2 ? "默认授予" : index >= 4 ? "敏感权限" : "按职责选择"}</small></span></label>)}
              </div>
              <div className="roleMemberScope"><div><strong>当前成员</strong><p>{["林浩", "宋可", "周洁"].slice(0, roleMemberCount).map((member) => <span key={member}>{member}</span>)}<GsButton htmlType="button" disabled={roleMemberCount === 3} onClick={() => setRoleMemberCount(3)}><MaterialIcon name="person_add" size={16}/>{roleMemberCount === 3 ? "已添加周洁" : "添加周洁"}</GsButton></p></div><aside><MaterialIcon name="warning" size={19}/><p><strong>职责分离检查</strong><small>平台配置、成本价格与审核下达不建议集中在同一普通业务角色。</small></p></aside></div>
            </section>) : null}
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
          <footer className="gsModalActions">
            <span><MaterialIcon name="shield" size={16}/>提交后保留操作人和时间证据</span>
            <div><GsButton htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton intent="primary" htmlType="submit" loading={pending}><MaterialIcon name="check" size={18}/>保存{model.recordNoun}</GsButton></div>
          </footer>
        </form>
    </GsModal>);
}
function RecordDrawer({ model, row, onClose, onEdit, onAction, }: {
    model: BusinessPageModel;
    row: BusinessRow;
    onClose: () => void;
    onEdit: () => void;
    onAction: (row: BusinessRow, message: string) => void;
}) {
    const [pending, setPending] = useState(false);
    const [activeTab, setActiveTab] = useState<"overview" | "collaboration" | "audit">("overview");
    const [collaboration, setCollaboration] = useState<RecordCollaboration>({ comments: [], attachments: [] });
    const [comment, setComment] = useState("");
    const [collaborationPending, setCollaborationPending] = useState(false);
    const [actionMenuOpen, setActionMenuOpen] = useState(false);
    const [actionError, setActionError] = useState("");
    const drawerRef = useRef<HTMLElement>(null);
    useEffect(() => {
        queueMicrotask(() => setCollaboration(readRecordCollaboration(model.pathname, row.id)));
    }, [model.pathname, row.id]);
    async function advanceWorkflow() {
        setPending(true);
        setActionError("");
        try {
            await submitBusinessMutation({ pathname: model.pathname, action: "workflow", values: { name: row.cells[0], id: row.id } });
            onAction({ ...row, status: row.tone === "good" ? "已复核" : "已提交", tone: row.tone === "good" ? "good" : "info", ageInDays: 0 }, `${row.id} 已提交至下一业务节点`);
        }
        catch (reason) {
            setActionError(reason instanceof Error ? reason.message : "业务节点提交失败，请重试。");
        }
        finally {
            setPending(false);
        }
    }
    async function applyControlledAction(action: "return" | "close" | "reopen" | "follow") {
        setPending(true);
        setActionError("");
        setActionMenuOpen(false);
        try {
            await submitBusinessMutation({ pathname: model.pathname, action: "workflow", values: { id: row.id, command: action } });
            const updates = action === "return"
                ? { status: "退回修改", tone: "warn" as const }
                : action === "close"
                    ? { status: "已关闭", tone: "good" as const }
                    : action === "reopen"
                        ? { status: "处理中", tone: "info" as const }
                        : { status: row.status, tone: row.tone };
            onAction({ ...row, ...updates, ageInDays: 0 }, action === "follow" ? `${row.id} 已加入我的关注` : `${row.id} 已执行${updates.status}`);
        }
        catch (reason) {
            setActionError(reason instanceof Error ? reason.message : "受控操作执行失败，请重试。");
        }
        finally {
            setPending(false);
        }
    }
    async function submitComment(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!comment.trim())
            return;
        setCollaborationPending(true);
        setActionError("");
        try {
            setCollaboration(await addRecordComment(model.pathname, row.id, readUserProfile().name, comment));
            setComment("");
        }
        catch (reason) {
            setActionError(reason instanceof Error ? reason.message : "评论保存失败，请重试。");
        }
        finally {
            setCollaborationPending(false);
        }
    }
    async function attachFile(file: File | undefined) {
        if (!file)
            return;
        setCollaborationPending(true);
        setActionError("");
        try {
            setCollaboration(await addRecordAttachment(model.pathname, row.id, file));
        }
        catch (reason) {
            setActionError(reason instanceof Error ? reason.message : "附件添加失败，请重试。");
        }
        finally {
            setCollaborationPending(false);
        }
    }
    const relatedItems = model.layout === "execution"
        ? [{ label: "来源销售订单", value: "SO-260814-001", href: "/sales/orders/list" }, { label: "关联质量任务", value: "FQC-260814-008", href: "/quality/final" }]
        : model.layout === "inventory"
            ? [{ label: "来源采购订单", value: "PO-260814-026", href: "/procurement/orders" }, { label: "关联生产工单", value: "MO-260814-012", href: "/production/work-orders/operations" }]
            : [{ label: "关联计划建议", value: "MRP-260814-006", href: "/planning/mrp/recommendations" }, { label: "关联生产工单", value: "MO-260814-012", href: "/production/work-orders/operations" }];
    return (<GsDrawerHost onClose={onClose}>
      <aside ref={drawerRef} className="recordDrawer" role="dialog" aria-modal="true" aria-labelledby="record-drawer-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="recordDrawerHeader">
          <div><h2 id="record-drawer-title">{row.cells[0]}</h2><span className={`businessStatus ${row.tone ? `businessStatus${row.tone}` : ""}`}>{row.status}</span></div>
          <GsButton className="iconButton" onClick={onClose} aria-label="关闭详情" htmlType="submit"><MaterialIcon name="close"/></GsButton>
        </header>
        <div className="recordDrawerTabs" role="tablist" aria-label="详情分类">
          <GsButton role="tab" aria-selected={activeTab === "overview"} onClick={() => setActiveTab("overview")} htmlType="submit">业务概览</GsButton>
          <GsButton role="tab" aria-selected={activeTab === "collaboration"} onClick={() => setActiveTab("collaboration")} htmlType="submit">协作与附件<span>{collaboration.comments.length + collaboration.attachments.length}</span></GsButton>
          <GsButton role="tab" aria-selected={activeTab === "audit"} onClick={() => setActiveTab("audit")} htmlType="submit">审计记录</GsButton>
        </div>
        <div className="recordDrawerBody">
          {activeTab === "overview" ? <>
            <section className="detailSummary"><MaterialIcon name={model.icon} size={25}/><div><strong>业务摘要</strong><p>{row.description}</p></div></section>
            <dl className="detailLedger">
              {model.columns.slice(1).map((column, index) => <div key={column}><dt>{column}</dt><dd>{row.cells[index]}</dd></div>)}
              <div><dt>责任人</dt><dd>{row.owner}</dd></div><div><dt>当前状态</dt><dd>{row.status}</dd></div>
            </dl>
            <section className="drawerSection relatedRecords">
              <div className="sectionTitleCompact"><h3>关联业务对象</h3><span>{relatedItems.length} 项</span></div>
              {relatedItems.map((item) => <Link href={item.href} key={item.label} onClick={onClose}><span><small>{item.label}</small><strong>{item.value}</strong></span><MaterialIcon name="arrow_outward" size={18}/></Link>)}
            </section>
            <section className="drawerSection">
              <div className="sectionTitleCompact"><h3>流程与证据</h3><span>最近更新 10:42</span></div>
              <ol className="evidenceTimeline">
                {model.workflow.map((step, index) => <li key={step.label}><span /><div><strong>{step.label}</strong><small>{step.detail}</small></div><time>{index === 0 ? "已完成" : index === 1 ? "当前" : "待处理"}</time></li>)}
              </ol>
            </section>
          </> : null}
          {activeTab === "collaboration" ? <section className="recordCollaboration" role="tabpanel">
            <form onSubmit={submitComment}><label htmlFor={`comment-${row.id}`}>添加协作记录</label><GsTextArea id={`comment-${row.id}`} value={comment} onChange={(event) => setComment(event.target.value)} rows={3} placeholder="记录处理结论、需要协同的事项或 @相关责任人"/><footer><span>内容保存在当前前端工作区</span><GsButton className="primaryButton" disabled={collaborationPending || !comment.trim()} htmlType="submit">发布记录</GsButton></footer></form>
            <div className="recordAttachments"><header><div><strong>附件</strong><small>支持选择文件并保存前端元数据</small></div><label><GsInput type="file" onChange={(event) => { void attachFile(event.target.files?.[0]); event.currentTarget.value = ""; }} disabled={collaborationPending}/><MaterialIcon name="attach_file" size={17}/>添加附件</label></header>{collaboration.attachments.length ? collaboration.attachments.map((attachment) => <article key={attachment.id}><span><MaterialIcon name="description" size={19}/></span><div><strong>{attachment.name}</strong><small>{Math.max(1, Math.round(attachment.size / 1024))} KB · 当前会话附件</small></div></article>) : <p>暂无附件</p>}</div>
            <div className="recordComments"><header><strong>协作记录</strong><span>{collaboration.comments.length}</span></header>{collaboration.comments.length ? collaboration.comments.map((item) => <article key={item.id}><span>{item.author.slice(0, 1)}</span><div><strong>{item.author}</strong><p>{item.content}</p><time>{new Date(item.createdAt).toLocaleString("zh-CN", { hour12: false })}</time></div></article>) : <p>暂无协作记录，发布第一条处理说明。</p>}</div>
          </section> : null}
          {activeTab === "audit" ? <section className="recordAudit" role="tabpanel">
            <header><MaterialIcon name="history" size={20}/><div><strong>完整操作证据</strong><p>记录状态、人员和关键动作；正式接入后由审计服务提供不可篡改证据。</p></div></header>
            <ol>
              <li><span /><div><strong>查看业务详情</strong><p>林浩打开 {row.id} 并读取当前业务状态</p><time>今天 10:42</time></div></li>
              <li><span /><div><strong>更新责任人与业务字段</strong><p>{row.owner} 完成当前节点信息维护</p><time>今天 09:18</time></div></li>
              <li><span /><div><strong>创建业务记录</strong><p>记录由前序业务流程生成并保留来源编号</p><time>昨天 16:30</time></div></li>
            </ol>
          </section> : null}
        </div>
        {actionError ? <div className="drawerActionError" role="alert"><MaterialIcon name="error" size={17}/>{actionError}</div> : null}
        <footer className="recordDrawerFooter">
          {model.dataSource !== "backend" ? <div className="recordActionMenu"><GsButton className="secondaryButton" onClick={() => setActionMenuOpen((open) => !open)} aria-expanded={actionMenuOpen} aria-haspopup="menu" htmlType="submit"><MaterialIcon name="more_horiz" size={18}/>更多操作</GsButton>{actionMenuOpen ? <div role="menu"><GsButton onClick={() => void applyControlledAction("follow")} htmlType="submit"><MaterialIcon name="star" size={17}/>加入关注</GsButton><GsButton onClick={() => void applyControlledAction("return")} htmlType="submit"><MaterialIcon name="undo" size={17}/>退回修改</GsButton>{row.status.includes("关闭") ? <GsButton onClick={() => void applyControlledAction("reopen")} htmlType="submit"><MaterialIcon name="lock_open" size={17}/>重新打开</GsButton> : <GsButton onClick={() => void applyControlledAction("close")} htmlType="submit"><MaterialIcon name="task_alt" size={17}/>关闭记录</GsButton>}</div> : null}</div> : null}
          <GsButton className="secondaryButton" onClick={onEdit} htmlType="submit"><MaterialIcon name="edit" size={17}/>编辑</GsButton>
          {model.dataSource !== "backend" ? <GsButton className="primaryButton" onClick={advanceWorkflow} disabled={pending} htmlType="submit">{pending ? "正在提交" : "提交下一节点"}<MaterialIcon name="arrow_forward" size={17}/></GsButton> : null}
        </footer>
      </aside>
    </GsDrawerHost>);
}
function BusinessContextPanel({ model }: {
    model: BusinessPageModel;
}) {
    const barLayouts = new Set(["planning", "finance", "analytics"]);
    const operationalLayouts = new Set(["execution", "inventory", "quality", "equipment"]);
    return (<section className={`businessContext businessContext${model.layout}`} aria-label={model.context.title}>
      <header>
        <div><p className="eyebrow">{model.context.kicker}</p><h3>{model.context.title}</h3><p>{model.context.summary}</p></div>
        <span className="businessContextMark"><MaterialIcon name={model.icon} size={22}/></span>
      </header>
      {barLayouts.has(model.layout) ? (<div className="businessContextBars">
          {model.context.items.map((item) => <article key={item.label}><div><span>{item.label}</span><strong>{item.value}</strong></div><i><b className={`contextTone${item.tone}`} style={{ width: `${item.progress}%` }}/></i><small>{item.note}</small></article>)}
        </div>) : operationalLayouts.has(model.layout) ? (<div className="businessContextOperations">
          {model.context.items.map((item, index) => <article key={item.label}><span>{String(index + 1).padStart(2, "0")}</span><div><strong>{item.label}</strong><small>{item.note}</small><i><b className={`contextTone${item.tone}`} style={{ width: `${item.progress}%` }}/></i></div><em>{item.value}</em></article>)}
        </div>) : (<div className="businessContextCards">
          {model.context.items.map((item) => <article key={item.label}><span className={`contextDot contextTone${item.tone}`}/><small>{item.label}</small><strong>{item.value}</strong><p>{item.note}</p></article>)}
        </div>)}
    </section>);
}
function matchesView(row: BusinessRow, activeView: string, firstView: string, rows: BusinessRow[], views: string[]) {
    if (activeView === firstView || activeView.startsWith("全部"))
        return true;
    if (includesAny(activeView, ["风险", "异常", "关注", "逾期", "受限", "失败"]))
        return row.tone === "risk" || row.tone === "warn";
    const hasDirectStatus = rows.some((item) => item.status === activeView || item.status.includes(activeView) || activeView.includes(item.status));
    if (hasDirectStatus)
        return row.status === activeView || row.status.includes(activeView) || activeView.includes(row.status);
    if (activeView.startsWith("待"))
        return row.status.startsWith("待") || row.tone === "warn";
    if (activeView.startsWith("已"))
        return row.status.startsWith("已") || row.tone === "good";
    if (includesAny(activeView, ["执行", "进行", "运行", "处理"]))
        return row.tone === "info";
    const normalizedView = activeView.replaceAll("角色", "").replaceAll("视图", "").replaceAll("分析", "").trim();
    if (normalizedView && `${row.cells.join("")}${row.status}`.includes(normalizedView))
        return true;
    const fallbackViewIndex = Math.max(0, views.indexOf(activeView) - 1);
    return rows.indexOf(row) % Math.max(1, views.length - 1) === fallbackViewIndex;
}
function includesAny(value: string, terms: string[]) {
    return terms.some((term) => value.includes(term));
}
function CapabilityReadinessDialog({ model, onClose }: {
    model: BusinessPageModel;
    onClose: () => void;
}) {
    const dialogRef = useRef<HTMLElement>(null);
    return (<GsModalHost onClose={onClose}>
      <section ref={dialogRef} className="capabilityReadinessDialog" role="dialog" aria-modal="true" aria-labelledby="capability-readiness-title" onMouseDown={(event) => event.stopPropagation()}>
        <header><span><MaterialIcon name="fact_check" size={23}/></span><div><h2 id="capability-readiness-title">{model.title}启用条件</h2><p>只有以下业务、技术和治理条件完成后，才能把该能力标记为正式启用。</p></div><GsButton className="iconButton" onClick={onClose} aria-label="关闭启用条件" htmlType="submit"><MaterialIcon name="close"/></GsButton></header>
        <div className="capabilityReadinessBody">
          <section><h3>建设清单</h3>{model.metrics.map((metric, index) => <article key={metric.label}><span>{String(index + 1).padStart(2, "0")}</span><div><strong>{metric.label}</strong><p>{metric.note}</p></div><em>{metric.value}</em></article>)}</section>
          <section><h3>责任与验收</h3>{model.attentionItems.map((item) => <article key={item.title}><span><MaterialIcon name={item.tone === "risk" ? "warning" : "rule"} size={18}/></span><div><strong>{item.title}</strong><p>{item.detail}</p><small>责任人：{item.owner}</small></div></article>)}</section>
          <aside><MaterialIcon name="verified_user" size={18}/><p><strong>启用原则</strong><span>需要具备可验证的数据来源、最小权限、异常恢复、监控告警和审计证据，不以演示数据代替真实验收。</span></p></aside>
        </div>
        <footer><GsButton className="secondaryButton" onClick={() => downloadCapabilityPlan(model)} htmlType="submit"><MaterialIcon name="download" size={17}/>导出清单</GsButton><GsButton className="primaryButton" data-readiness-close="true" onClick={onClose} htmlType="submit">我已了解</GsButton></footer>
      </section>
    </GsModalHost>);
}
function CapabilityFeedbackDialog({ model, onClose, onSaved }: {
    model: BusinessPageModel;
    onClose: () => void;
    onSaved: (id: string) => void;
}) {
    const dialogRef = useRef<HTMLElement>(null);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setPending(true);
        setError("");
        const values = Object.fromEntries(new FormData(event.currentTarget).entries()) as Record<string, string>;
        try {
            const feedback = await submitCapabilityFeedback({ pathname: model.pathname, type: values.type ?? "产品建议", priority: values.priority ?? "普通", scenario: values.scenario ?? "", expectation: values.expectation ?? "" });
            onSaved(feedback.id);
        }
        catch (reason) {
            setError(reason instanceof Error ? reason.message : "规划反馈提交失败，请重试。");
            setPending(false);
        }
    }
    return (<GsModalHost onClose={() => { if (!pending)
        onClose(); }}>
      <section ref={dialogRef} className="capabilityFeedbackDialog" role="dialog" aria-modal="true" aria-labelledby="capability-feedback-title" onMouseDown={(event) => event.stopPropagation()}>
        <header><span><MaterialIcon name="rate_review" size={23}/></span><div><h2 id="capability-feedback-title">反馈{model.title}使用需求</h2><p>反馈将保存在当前浏览器工作区，用于后续业务规则和接口设计。</p></div><GsButton className="iconButton" onClick={onClose} disabled={pending} aria-label="关闭规划反馈" htmlType="submit"><MaterialIcon name="close"/></GsButton></header>
        <form onSubmit={submit} noValidate>
          <div className="capabilityFeedbackFields">
            <label><span>反馈类型</span><RoundedSelect ariaLabel="反馈类型" name="type" options={["产品建议", "业务规则", "接口需求", "权限与审计"]} defaultValue="产品建议" size="field"/></label>
            <label><span>优先级</span><RoundedSelect ariaLabel="反馈优先级" name="priority" options={["普通", "重要", "阻断实施"]} defaultValue="普通" size="field"/></label>
            <label className="capabilityFeedbackFull"><span>使用场景<em>必填</em></span><GsTextArea name="scenario" rows={4} placeholder="说明谁在什么业务场景下使用，以及当前遇到的问题"/></label>
            <label className="capabilityFeedbackFull"><span>期望能力<em>必填</em></span><GsTextArea name="expectation" rows={4} placeholder="描述期望的输入、操作、结果和验收标准"/></label>
          </div>
          {error ? <div className="formError" role="alert"><MaterialIcon name="error" size={18}/>{error}</div> : null}
          <footer><span><MaterialIcon name="info" size={16}/>提交反馈不代表该能力已经启用</span><div><GsButton className="secondaryButton" htmlType="button" onClick={onClose} disabled={pending}>取消</GsButton><GsButton className="primaryButton" htmlType="submit" disabled={pending}>{pending ? "正在提交" : "提交反馈"}</GsButton></div></footer>
        </form>
      </section>
    </GsModalHost>);
}
type PlannedBlueprint = {
    badge: string;
    kicker: string;
    title: string;
    icon: string;
    objectLabel: string;
    evidenceTitle: string;
    aiUse: string;
};
const plannedBlueprints: Record<BusinessPageModel["layout"], PlannedBlueprint> = {
    work: { badge: "CORE", kicker: "工作协同设计", title: "任务、责任与关闭证据", icon: "task_alt", objectLabel: "工作节点", evidenceTitle: "工作规则与验收口径", aiUse: "归纳待办、识别阻塞并生成处理建议，关键操作仍由责任人确认。" },
    relationship: { badge: "SCM", kicker: "协同网络设计", title: "主体、权限与协作链路", icon: "hub", objectLabel: "协同节点", evidenceTitle: "协同范围与责任边界", aiUse: "辅助归纳沟通记录与风险，但不自动变更供应商或客户业务状态。" },
    document: { badge: "ERP", kicker: "受控单据设计", title: "来源、审核与执行闭环", icon: "description", objectLabel: "单据节点", evidenceTitle: "单据规则与业务证据", aiUse: "检查字段完整性、提示异常，不代替审批、过账或业务下达。" },
    catalog: { badge: "PLM", kicker: "工程对象设计", title: "版本、变更与下游引用", icon: "account_tree", objectLabel: "工程节点", evidenceTitle: "受控对象与发布条件", aiUse: "辅助比较版本和解释影响，受控发布仍需工程负责人批准。" },
    planning: { badge: "APS", kicker: "计划约束设计", title: "需求、供需与排程决策链", icon: "calendar_month", objectLabel: "计划阶段", evidenceTitle: "约束、时间围栏与下达口径", aiUse: "生成排程建议和例外解释，不直接下达采购或生产指令。" },
    execution: { badge: "MES", kicker: "现场执行设计", title: "任务、采集与完工证据", icon: "precision_manufacturing", objectLabel: "执行阶段", evidenceTitle: "现场规则与采集边界", aiUse: "辅助诊断现场异常，不替代开工、报工、检验和完工确认。" },
    inventory: { badge: "WMS", kicker: "物流作业设计", title: "库存事实与仓内执行链", icon: "inventory_2", objectLabel: "物流节点", evidenceTitle: "库存口径与交接凭证", aiUse: "辅助推荐库位和拣配顺序，不自动调整库存或释放冻结批次。" },
    quality: { badge: "QMS", kicker: "质量闭环设计", title: "标准、判定与纠正路径", icon: "verified", objectLabel: "质量节点", evidenceTitle: "判定规则与质量证据", aiUse: "辅助聚类缺陷与查找相似问题，最终质量判定必须由授权人员完成。" },
    equipment: { badge: "EAM · IoT", kicker: "工业连接设计", title: "设备、网关与数据质量链路", icon: "sensors", objectLabel: "连接层级", evidenceTitle: "点位、质量与告警契约", aiUse: "辅助识别异常模式和维修线索，不自动控制设备或修改安全参数。" },
    finance: { badge: "ERP", kicker: "财务控制设计", title: "业务事实、核验与记账边界", icon: "account_balance", objectLabel: "控制环节", evidenceTitle: "会计口径与审计证据", aiUse: "辅助解释差异和生成核对清单，不自动记账、结账或提交税务申报。" },
    analytics: { badge: "BI", kicker: "分析产品设计", title: "指标、维度与事实下钻链", icon: "monitoring", objectLabel: "分析层级", evidenceTitle: "指标口径与数据血缘", aiUse: "辅助生成分析摘要和下钻线索，所有结论必须保留指标口径与事实来源。" },
    settings: { badge: "SYS · API", kicker: "平台集成设计", title: "契约、鉴权与运行治理链", icon: "lan", objectLabel: "集成层级", evidenceTitle: "安全边界与运行责任", aiUse: "辅助生成字段映射和排障建议，不读取密钥，也不绕过权限与审计策略。" },
};
function PlannedCapabilityWorkspace({ model, feedbackCount }: {
    model: BusinessPageModel;
    feedbackCount: number;
}) {
    const blueprint = plannedBlueprints[model.layout];
    return (<section className={`plannedBlueprint plannedBlueprint${model.layout}`} aria-labelledby="planned-blueprint-title">
      <header className="plannedBlueprintHeader">
        <span className="plannedBlueprintIcon"><MaterialIcon name={blueprint.icon} size={24}/></span>
        <div><p className="eyebrow">{blueprint.kicker}</p><h3 id="planned-blueprint-title">{blueprint.title}</h3><p>{model.context.summary}</p></div>
        <aside><span>{blueprint.badge}</span><small>{feedbackCount ? `已记录 ${feedbackCount} 条规划反馈` : "待业务与技术联合评审"}</small></aside>
      </header>

      <div className="plannedBlueprintBody">
        <div className="plannedFlowPanel">
          <div className="plannedPanelHeading"><div><p className="eyebrow">{blueprint.objectLabel}</p><h4>{model.title}实施路径</h4></div><span>01—04</span></div>
          <ol className="plannedFlow">
            {model.workflow.map((step, index) => {
            const metric = model.metrics[index % model.metrics.length];
            return <li key={step.label}><span className="plannedFlowIndex">{String(index + 1).padStart(2, "0")}</span><div className="plannedFlowContent"><span><strong>{step.label}</strong><MaterialIcon name={index === model.workflow.length - 1 ? "flag" : "arrow_forward"} size={17}/></span><p>{step.detail}</p><small>{metric.label}<b>{metric.value}</b></small></div></li>;
        })}
          </ol>
        </div>

        <aside className="plannedGatePanel">
          <div className="plannedPanelHeading"><div><p className="eyebrow">进入实施的门槛</p><h4>{model.attentionTitle}</h4></div><span>{model.attentionItems.length}</span></div>
          <div className="plannedGateList">{model.attentionItems.map((item, index) => <article key={item.title}><span><MaterialIcon name={item.tone === "warn" ? "rule" : "shield_lock"} size={19}/></span><div><small>门槛 {String(index + 1).padStart(2, "0")}</small><strong>{item.title}</strong><p>{item.detail}</p><em>责任：{item.owner}</em></div></article>)}</div>
          <div className="plannedAiAssist"><span><MaterialIcon name="auto_awesome" filled size={19}/></span><div><strong>AI 可参与，但不越权</strong><p>{blueprint.aiUse}</p></div></div>
        </aside>
      </div>

      <div className="plannedEvidence">
        <div className="plannedPanelHeading"><div><p className="eyebrow">{blueprint.evidenceTitle}</p><h4>{model.context.title}</h4></div><span>评审材料</span></div>
        <div className="plannedEvidenceGrid">{model.context.items.map((item) => <article key={item.label} className={`plannedEvidence${item.tone}`}><div><span>{item.label}</span><strong>{item.value}</strong></div><p>{item.note}</p><i><b style={{ width: `${item.progress}%` }}/></i></article>)}</div>
      </div>
    </section>);
}
function AnalyticsInsight({ model }: {
    model: BusinessPageModel;
}) {
    const periods = ["第1周", "第2周", "第3周", "第4周", "第5周", "第6周", "第7周", "本周"];
    const values = [72, 78, 75, 82, 80, 86, 84, 91];
    const dimensions = [
        { label: "订单履约贡献", value: "34%", progress: 84, note: "销售承诺与按期交付" },
        { label: "生产执行贡献", value: "28%", progress: 72, note: "工单达成与车间节拍" },
        { label: "质量稳定贡献", value: "23%", progress: 66, note: "一次合格与异常关闭" },
        { label: "库存效率贡献", value: "15%", progress: 48, note: "周转、齐套与呆滞控制" },
    ];
    return (<section className="analyticsInsight" aria-labelledby="analytics-trend-title">
      <div className="sectionHeading"><div><p className="eyebrow">趋势与维度</p><h3 id="analytics-trend-title">{model.title} · 近八周趋势</h3></div><span>当前值 91% · 较八周前 +19%</span></div>
      <div className="analyticsInsightBody">
        <div className="analyticsBars" role="img" aria-label="近八周指标从72%上升至91%">
          {values.map((value, index) => <div key={periods[index]}><i style={{ height: `${value}%` }}><span>{value}%</span></i><small>{periods[index]}</small></div>)}
        </div>
        <div className="analyticsBreakdown">{dimensions.map((item) => <article key={item.label}><div><span>{item.label}</span><strong>{item.value}</strong></div><i><b style={{ width: `${item.progress}%` }}/></i><small>{item.note}</small></article>)}</div>
      </div>
    </section>);
}
function primaryActionIcon(model: BusinessPageModel) {
    if (model.primaryActionMode === "refresh")
        return "refresh";
    if (model.primaryActionMode === "query")
        return "manage_search";
    if (model.primaryActionMode === "export")
        return "file_download";
    if (model.primaryActionMode === "feedback")
        return "rate_review";
    if (includesAny(model.primaryAction, ["发布", "下达"]))
        return "publish";
    if (includesAny(model.primaryAction, ["分析", "测算", "核算", "结转"]))
        return "calculate";
    if (includesAny(model.primaryAction, ["审批", "审核", "评审"]))
        return "approval";
    if (includesAny(model.primaryAction, ["更新", "维护", "确认"]))
        return "edit";
    if (includesAny(model.primaryAction, ["开始", "执行", "查询"]))
        return "play_arrow";
    if (includesAny(model.primaryAction, ["登记", "录入"]))
        return "edit_note";
    if (includesAny(model.primaryAction, ["上传", "导入"]))
        return "upload_file";
    return "add";
}
export function BusinessWorkspace({ model }: BusinessWorkspaceProps) {
    const router = useRouter();
    const usesBackend = model.dataSource === "backend";
    const [rows, setRows] = useState(model.rows);
    const [query, setQuery] = useState("");
    const [activeView, setActiveView] = useState(model.views[0]);
    const [status, setStatus] = useState("全部状态");
    const [owner, setOwner] = useState("全部负责人");
    const [period, setPeriod] = useState(model.filters[2]?.options[0] ?? "本月");
    const [selectedRow, setSelectedRow] = useState<BusinessRow | null>(null);
    const [formMode, setFormMode] = useState<FormMode | null>(null);
    const [editReturnsToDetail, setEditReturnsToDetail] = useState(false);
    const [toast, setToast] = useState("");
    const [refreshing, setRefreshing] = useState(false);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set());
    const [sortColumn, setSortColumn] = useState(0);
    const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [batchEditDialogOpen, setBatchEditDialogOpen] = useState(false);
    const [batchEditing, setBatchEditing] = useState(false);
    const [columnMenuOpen, setColumnMenuOpen] = useState(false);
    const [visibleColumnIndexes, setVisibleColumnIndexes] = useState<Set<number>>(() => new Set(model.columns.map((_, index) => index)));
    const [lastDeletedRows, setLastDeletedRows] = useState<BusinessRow[]>([]);
    const [readinessDialogOpen, setReadinessDialogOpen] = useState(false);
    const [feedbackDialogOpen, setFeedbackDialogOpen] = useState(false);
    const [feedbackCount, setFeedbackCount] = useState(0);
    const [savedViews, setSavedViews] = useState<SavedBusinessView[]>([]);
    const [saveViewDialogOpen, setSaveViewDialogOpen] = useState(false);
    const viewPanelId = useId();
    const columnMenuRef = useRef<HTMLDivElement>(null);
    const filteredRows = useMemo(() => {
        const matches = rows.filter((row) => {
            const matchesQuery = !query.trim() || `${row.id}${row.cells.join("")}${row.owner}${row.status}`.toLowerCase().includes(query.trim().toLowerCase());
            const matchesStatus = status === "全部状态" || row.status === status;
            const matchesOwner = owner === "全部负责人" || row.owner === owner;
            const matchesActiveView = matchesView(row, activeView, model.views[0], rows, model.views);
            const periodLimit = period === "今日" ? 0 : period === "本周" ? 7 : period === "本月" ? 31 : 92;
            const matchesPeriod = (row.ageInDays ?? rows.indexOf(row) * 3) <= periodLimit;
            return matchesQuery && matchesStatus && matchesOwner && matchesActiveView && matchesPeriod;
        });
        return [...matches].sort((left, right) => {
            const leftValue = sortColumn === 0 ? left.id : sortColumn === model.columns.length ? left.status : left.cells[sortColumn - 1] ?? "";
            const rightValue = sortColumn === 0 ? right.id : sortColumn === model.columns.length ? right.status : right.cells[sortColumn - 1] ?? "";
            const result = leftValue.localeCompare(rightValue, "zh-CN", { numeric: true, sensitivity: "base" });
            return sortDirection === "asc" ? result : -result;
        });
    }, [activeView, model.columns.length, model.views, owner, period, query, rows, sortColumn, sortDirection, status]);
    const totalPages = Math.max(1, Math.ceil(filteredRows.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const pageRows = filteredRows.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const pageStart = filteredRows.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
    const pageEnd = Math.min(currentPage * pageSize, filteredRows.length);
    const selectedRows = rows.filter((row) => selectedIds.has(row.id));
    const currentPageIds = pageRows.map((row) => row.id);
    const allCurrentPageSelected = currentPageIds.length > 0 && currentPageIds.every((id) => selectedIds.has(id));
    const someCurrentPageSelected = currentPageIds.some((id) => selectedIds.has(id));
    const allFilteredSelected = filteredRows.length > 0 && filteredRows.every((row) => selectedIds.has(row.id));
    const statusOptions = [...new Set(rows.map((row) => row.status))];
    const statusFilterOptions = [...new Set([...(model.filters[0]?.options ?? ["全部状态"]), ...statusOptions])];
    const statusOptionBadges = useMemo(() => {
        const counts: Record<string, number> = { "全部状态": rows.length };
        rows.forEach((row) => { counts[row.status] = (counts[row.status] ?? 0) + 1; });
        return counts;
    }, [rows]);
    const ownerOptions = [...new Set(rows.map((row) => row.owner))];
    const visibleColumnCount = visibleColumnIndexes.size;
    const filtersActive = Boolean(query.trim()) || status !== "全部状态" || owner !== "全部负责人" || period !== (model.filters[2]?.options[0] ?? "本月");
    const usesRichBusinessContext = ["planning", "execution", "inventory", "quality", "equipment", "finance", "analytics"].includes(model.layout);
    function resetToFirstPage() {
        setPage(1);
    }
    function toggleRowSelection(id: string, checked: boolean) {
        setSelectedIds((current) => {
            const next = new Set(current);
            if (checked)
                next.add(id);
            else
                next.delete(id);
            return next;
        });
    }
    function toggleCurrentPageSelection(checked: boolean) {
        setSelectedIds((current) => {
            const next = new Set(current);
            currentPageIds.forEach((id) => { if (checked)
                next.add(id);
            else
                next.delete(id); });
            return next;
        });
    }
    function selectAllFilteredRows() {
        setSelectedIds((current) => {
            const next = new Set(current);
            filteredRows.forEach((row) => next.add(row.id));
            return next;
        });
    }
    function changeSort(column: number) {
        if (sortColumn === column)
            setSortDirection((current) => current === "asc" ? "desc" : "asc");
        else {
            setSortColumn(column);
            setSortDirection("asc");
        }
        resetToFirstPage();
    }
    function toggleColumn(index: number, checked: boolean) {
        if (index === 0)
            return;
        setVisibleColumnIndexes((current) => {
            const next = new Set(current);
            if (checked)
                next.add(index);
            else
                next.delete(index);
            return next;
        });
    }
    function resetFilters() {
        setQuery("");
        setStatus("全部状态");
        setOwner("全部负责人");
        setPeriod(model.filters[2]?.options[0] ?? "本月");
        resetToFirstPage();
    }
    function applySavedView(view: SavedBusinessView) {
        setQuery(view.query);
        setStatus(view.status);
        setOwner(view.owner);
        setPeriod(view.period);
        setSortColumn(Math.min(model.columns.length, Math.max(0, view.sortColumn)));
        setSortDirection(view.sortDirection);
        setVisibleColumnIndexes(new Set([...view.visibleColumnIndexes.filter((index) => index >= 0 && index < model.columns.length), 0]));
        resetToFirstPage();
        setToast(`已应用视图“${view.name}”`);
    }
    async function saveCurrentView(name: string) {
        const next = await saveBusinessView(model.pathname, { name, query, status, owner, period, sortColumn, sortDirection, visibleColumnIndexes: [...visibleColumnIndexes] });
        setSavedViews(next);
        setSaveViewDialogOpen(false);
        setToast(`视图“${name.trim()}”已保存`);
    }
    useEffect(() => {
        if (usesBackend)
            queueMicrotask(() => setRows(model.rows));
        else {
            const storedRows = readStoredBusinessRows(model.pathname);
            if (storedRows)
                queueMicrotask(() => setRows(storedRows));
        }
        queueMicrotask(() => setSavedViews(readSavedBusinessViews(model.pathname)));
        if (model.planned)
            queueMicrotask(() => setFeedbackCount(readCapabilityFeedbacks(model.pathname).length));
    }, [model.pathname, model.planned, model.rows, usesBackend]);
    useEffect(() => {
        if (!toast)
            return;
        const timer = window.setTimeout(() => setToast(""), 2600);
        return () => window.clearTimeout(timer);
    }, [toast]);
    useEffect(() => {
        if (!columnMenuOpen)
            return;
        function closeColumnMenu(event: PointerEvent) {
            if (!columnMenuRef.current?.contains(event.target as Node))
                setColumnMenuOpen(false);
        }
        window.addEventListener("pointerdown", closeColumnMenu);
        return () => window.removeEventListener("pointerdown", closeColumnMenu);
    }, [columnMenuOpen]);
    function openEdit(row: BusinessRow, returnToDetail = false) {
        setSelectedRow(row);
        setEditReturnsToDetail(returnToDetail);
        setFormMode("edit");
    }
    function closeForm() {
        const shouldReturnToDetail = formMode === "edit" && editReturnsToDetail;
        setFormMode(null);
        setEditReturnsToDetail(false);
        if (!shouldReturnToDetail)
            setSelectedRow(null);
    }
    function saveRow(saved: BusinessRow, message: string) {
        setRows((current) => {
            const nextRows = formMode === "create" ? [{ ...saved, ageInDays: 0 }, ...current] : current.map((item) => item.id === saved.id ? saved : item);
            if (!usesBackend)
                writeStoredBusinessRows(model.pathname, nextRows);
            return nextRows;
        });
        resetToFirstPage();
        setSelectedRow(null);
        setFormMode(null);
        setEditReturnsToDetail(false);
        setToast(message);
    }
    async function refresh(message = "数据已刷新至最新状态") {
        setRefreshing(true);
        if (usesBackend)
            router.refresh();
        else
            await new Promise((resolve) => setTimeout(resolve, 420));
        setRefreshing(false);
        setToast(message);
    }
    function runPrimaryAction() {
        if (model.primaryActionMode === "refresh") {
            void refresh();
            return;
        }
        if (model.primaryActionMode === "query") {
            void refresh(`${model.title}已按当前条件完成查询`);
            return;
        }
        if (model.primaryActionMode === "export") {
            downloadCsv(model, filteredRows);
            setToast(`${model.title}已按当前筛选条件导出`);
            return;
        }
        if (model.primaryActionMode === "feedback") {
            setFeedbackDialogOpen(true);
            return;
        }
        setSelectedRow(null);
        setEditReturnsToDetail(false);
        setFormMode("create");
    }
    function runSecondaryAction() {
        if (model.primaryActionMode === "export") {
            void refresh();
            return;
        }
        downloadCsv(model, filteredRows);
    }
    function openAttentionItem(title: string, tone: BusinessRow["tone"]) {
        const target = rows.find((row) => title.includes(row.id) || title.includes(row.cells[0]))
            ?? rows.find((row) => row.tone === tone)
            ?? rows[0];
        if (target)
            setSelectedRow(target);
    }
    function applyWorkflowUpdate(updatedRow: BusinessRow, message: string) {
        setRows((current) => {
            const nextRows = current.map((item) => item.id === updatedRow.id ? updatedRow : item);
            if (!usesBackend)
                writeStoredBusinessRows(model.pathname, nextRows);
            return nextRows;
        });
        setSelectedRow(null);
        setToast(message);
    }
    async function confirmBulkDelete() {
        if (!selectedRows.length)
            return;
        setDeleting(true);
        try {
            const result = await submitBusinessMutation({
                pathname: model.pathname,
                action: "delete",
                values: { ids: selectedRows.map((row) => row.id).join(",") },
                records: selectedRows.flatMap((row) => row.entityId && row.version !== undefined ? [{ id: row.entityId, version: row.version }] : []),
            });
            const deletedRows = result.rows ?? [...selectedRows];
            setRows((current) => {
                const nextRows = current.filter((row) => !selectedIds.has(row.id));
                if (!usesBackend)
                    writeStoredBusinessRows(model.pathname, nextRows);
                return nextRows;
            });
            setLastDeletedRows(deletedRows);
            setSelectedIds(new Set());
            setDeleteDialogOpen(false);
            setToast(`已${usesBackend ? "停用" : "删除"} ${deletedRows.length} 条${model.recordNoun}`);
        }
        catch (reason) {
            setToast(reason instanceof Error ? reason.message : `${usesBackend ? "停用" : "删除"}失败，请重试`);
        }
        finally {
            setDeleting(false);
        }
    }
    async function confirmBatchEdit(changes: {
        status?: string;
        owner?: string;
    }) {
        if (!selectedRows.length || (!changes.status && !changes.owner))
            return;
        setBatchEditing(true);
        try {
            const result = await submitBusinessMutation({
                pathname: model.pathname,
                action: usesBackend ? "batch" : "update",
                values: { ids: selectedRows.map((row) => row.id).join(","), status: changes.status ?? "", owner: changes.owner ?? "" },
                records: selectedRows.flatMap((row) => row.entityId && row.version !== undefined ? [{ id: row.entityId, version: row.version }] : []),
            });
            setRows((current) => {
                const returned = new Map((result.rows ?? []).map((row) => [row.id, row]));
                const nextRows = current.map((row) => returned.get(row.id) ?? (selectedIds.has(row.id) ? { ...row, status: changes.status ?? row.status, tone: changes.status ? toneForStatus(changes.status) : row.tone, owner: changes.owner ?? row.owner } : row));
                if (!usesBackend)
                    writeStoredBusinessRows(model.pathname, nextRows);
                return nextRows;
            });
            setBatchEditDialogOpen(false);
            setSelectedIds(new Set());
            setToast(`已批量更新 ${selectedRows.length} 条${model.recordNoun}`);
        }
        catch (reason) {
            setToast(reason instanceof Error ? reason.message : "批量更新失败，请重试");
        }
        finally {
            setBatchEditing(false);
        }
    }
    async function undoBulkDelete() {
        if (!lastDeletedRows.length)
            return;
        try {
            const result = usesBackend ? await submitBusinessMutation({
                pathname: model.pathname,
                action: "restore",
                values: {},
                records: lastDeletedRows.flatMap((row) => row.entityId && row.version !== undefined ? [{ id: row.entityId, version: row.version }] : []),
            }) : null;
            const restoredRows = result?.rows ?? lastDeletedRows;
            setRows((current) => {
                const currentIds = new Set(current.map((row) => row.id));
                const nextRows = [...restoredRows.filter((row) => !currentIds.has(row.id)), ...current];
                if (!usesBackend)
                    writeStoredBusinessRows(model.pathname, nextRows);
                return nextRows;
            });
            setToast(`已恢复 ${lastDeletedRows.length} 条${model.recordNoun}`);
            setLastDeletedRows([]);
            resetToFirstPage();
        }
        catch (reason) {
            setToast(reason instanceof Error ? reason.message : "恢复失败，请重试");
        }
    }
    function handleViewKeyDown(event: ReactKeyboardEvent<HTMLElement>, index: number) {
        if (!(["ArrowLeft", "ArrowRight", "Home", "End"] as string[]).includes(event.key))
            return;
        event.preventDefault();
        const nextIndex = event.key === "Home" ? 0 : event.key === "End" ? model.views.length - 1 : (index + (event.key === "ArrowRight" ? 1 : -1) + model.views.length) % model.views.length;
        const nextView = model.views[nextIndex];
        setActiveView(nextView);
        resetToFirstPage();
        document.getElementById(`${viewPanelId}-tab-${nextIndex}`)?.focus();
    }
    if (model.planned) {
        return (<div className={`businessPage businessPage${model.layout}`}>
        <header className="pageHeading businessPageHeading">
          <div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name={model.icon} size={23}/></span><div><h2>{model.title}</h2><p>{model.description}</p></div></div>
          <div className="pageHeadingActions"><GsButton className="secondaryButton" onClick={() => downloadCapabilityPlan(model)} htmlType="submit"><MaterialIcon name="description" size={18}/>导出规划</GsButton><GsButton className="primaryButton" onClick={runPrimaryAction} htmlType="submit"><MaterialIcon name="rate_review" size={18}/>{model.primaryAction}</GsButton></div>
        </header>
        <section className="plannedNotice"><span><MaterialIcon name="science" size={21}/></span><div><strong>此能力尚未连接真实后端或设备</strong><p>当前页面只表达产品边界、依赖条件和验收路径，不代表服务已运行、数据已采集或接口已联通。</p></div><GsButton onClick={() => setReadinessDialogOpen(true)} htmlType="submit">查看启用条件</GsButton></section>
        <PlannedCapabilityWorkspace model={model} feedbackCount={feedbackCount}/>
        {readinessDialogOpen ? <CapabilityReadinessDialog model={model} onClose={() => setReadinessDialogOpen(false)}/> : null}
        {feedbackDialogOpen ? <CapabilityFeedbackDialog model={model} onClose={() => setFeedbackDialogOpen(false)} onSaved={(id) => { setFeedbackDialogOpen(false); setFeedbackCount((count) => count + 1); setToast(`规划反馈 ${id} 已提交`); }}/> : null}
        {toast ? <div className="toastMessage" role="status" aria-live="polite"><span><MaterialIcon name="check_circle" filled size={18}/></span>{toast}</div> : null}
      </div>);
    }
    return (<div className={`businessPage businessPage${model.layout}`} aria-busy={refreshing}>
      <header className="pageHeading businessPageHeading">
        <div className="pageTitleGroup"><span className="pageTitleIcon"><MaterialIcon name={model.icon} size={23}/></span><div><h2>{model.title}</h2><p>{model.description}</p></div></div>
        <div className="pageHeadingActions"><GsButton className="secondaryButton" onClick={runSecondaryAction} htmlType="submit"><MaterialIcon name={model.primaryActionMode === "export" ? "refresh" : "download"} size={18}/>{model.primaryActionMode === "export" ? "刷新" : "导出"}</GsButton><GsButton className={refreshing && ["refresh", "query"].includes(model.primaryActionMode) ? "primaryButton isRefreshing" : "primaryButton"} onClick={runPrimaryAction} disabled={refreshing && ["refresh", "query"].includes(model.primaryActionMode)} htmlType="submit"><MaterialIcon name={primaryActionIcon(model)} size={18}/>{model.primaryAction}</GsButton></div>
      </header>

      {usesRichBusinessContext ? <BusinessContextPanel model={model}/> : null}

      {model.layout === "analytics" ? <AnalyticsInsight model={model}/> : null}

      <section className="businessLedger">
        <div className="businessViews" role="tablist" aria-label={`${model.title}视图`}>
          {model.views.map((view, index) => <GsButton id={`${viewPanelId}-tab-${index}`} key={view} role="tab" aria-selected={activeView === view} aria-controls={viewPanelId} tabIndex={activeView === view ? 0 : -1} className={activeView === view ? "businessViewActive" : ""} onKeyDown={(event) => handleViewKeyDown(event, index)} onClick={() => { setActiveView(view); resetToFirstPage(); }} htmlType="submit">{view}</GsButton>)}
          <span>视图：{activeView}</span>
        </div>
        {savedViews.length ? <div className="savedViewsBar" aria-label="已保存视图"><span><MaterialIcon name="bookmarks" size={16}/>我的视图</span><div>{savedViews.map((view) => <span key={view.id}><GsButton onClick={() => applySavedView(view)} htmlType="submit">{view.name}</GsButton><GsButton onClick={() => setSavedViews(deleteBusinessView(model.pathname, view.id))} aria-label={`删除视图${view.name}`} htmlType="submit"><MaterialIcon name="close" size={14}/></GsButton></span>)}</div></div> : null}
        <div id={viewPanelId} role="tabpanel" aria-labelledby={`${viewPanelId}-tab-${Math.max(0, model.views.indexOf(activeView))}`}>
        <div className="businessToolbar">
          <label className="businessSearch"><MaterialIcon name="search" size={18}/><GsInput aria-label={`搜索${model.title}`} value={query} onChange={(event) => { setQuery(event.target.value); resetToFirstPage(); }} placeholder={`搜索${model.title}编号、名称或负责人`}/>{query ? <GsButton onClick={() => { setQuery(""); resetToFirstPage(); }} aria-label="清空搜索" htmlType="submit"><MaterialIcon name="close" size={15}/></GsButton> : null}</label>
          <div className="businessFilters">
            {model.filters.map((filter, index) => <RoundedSelect key={filter.label} ariaLabel={filter.label} options={index === 0 ? statusFilterOptions : filter.options} optionBadges={index === 0 ? statusOptionBadges : undefined} value={index === 0 ? status : index === 1 ? owner : period} onValueChange={(nextValue) => { if (index === 0)
        setStatus(nextValue); if (index === 1)
        setOwner(nextValue); if (index === 2)
        setPeriod(nextValue); resetToFirstPage(); }}/>)}
          </div>
          <div className="businessTableTools">
            {filtersActive ? <GsButton className="tableToolButton" onClick={resetFilters} htmlType="submit"><MaterialIcon name="filter_alt_off" size={17}/><span>重置</span></GsButton> : null}
            <GsButton className="tableToolButton" onClick={() => setSaveViewDialogOpen(true)} htmlType="submit"><MaterialIcon name="bookmark_add" size={17}/><span>保存视图</span></GsButton>
            <div className="columnSettingsWrap" ref={columnMenuRef}>
              <GsButton className={columnMenuOpen ? "tableToolButton tableToolButtonActive" : "tableToolButton"} onClick={() => setColumnMenuOpen((open) => !open)} aria-label="设置显示列" aria-expanded={columnMenuOpen} aria-haspopup="dialog" htmlType="submit"><MaterialIcon name="view_column" size={18}/><span>列设置</span></GsButton>
              {columnMenuOpen ? <section className="columnSettingsDropdown" role="dialog" aria-label="设置显示列"><header><div><strong>显示列</strong><small>至少保留业务编号列</small></div><GsButton onClick={() => setVisibleColumnIndexes(new Set(model.columns.map((_, index) => index)))} htmlType="submit">恢复默认</GsButton></header><div>{model.columns.map((column, index) => <div key={column}><SelectionCheckbox label={`${visibleColumnIndexes.has(index) ? "隐藏" : "显示"}${column}`} checked={visibleColumnIndexes.has(index)} onChange={(checked) => toggleColumn(index, checked)}/><span>{column}</span>{index === 0 ? <small>固定</small> : null}</div>)}</div></section> : null}
            </div>
            <GsButton className={refreshing ? "iconButton isRefreshing" : "iconButton"} onClick={() => void refresh()} disabled={refreshing} aria-label="刷新" htmlType="submit"><MaterialIcon name="refresh" size={18}/></GsButton>
          </div>
        </div>
        {selectedIds.size ? <div className="businessBulkBar" role="region" aria-label="批量操作"><div><SelectionCheckbox label={allFilteredSelected ? "取消选择全部已选记录" : "选择全部筛选结果"} checked={allFilteredSelected} indeterminate={!allFilteredSelected} onChange={(checked) => { if (!checked)
        setSelectedIds(new Set());
    else
        selectAllFilteredRows(); }}/><strong>已选择 {selectedIds.size} 条</strong>{!allFilteredSelected && filteredRows.length > selectedIds.size ? <GsButton onClick={selectAllFilteredRows} htmlType="submit">选择全部筛选结果（{filteredRows.length} 条）</GsButton> : <span>已选择全部筛选结果</span>}</div><nav aria-label="已选记录操作"><GsButton onClick={() => setBatchEditDialogOpen(true)} htmlType="submit"><MaterialIcon name="edit_note" size={17}/>修改所选</GsButton><GsButton onClick={() => downloadCsv(model, selectedRows)} htmlType="submit"><MaterialIcon name="download" size={17}/>导出所选</GsButton><GsButton className="bulkDangerButton" onClick={() => setDeleteDialogOpen(true)} htmlType="submit"><MaterialIcon name={usesBackend ? "block" : "delete"} size={17}/>{usesBackend ? "停用所选" : "删除所选"}</GsButton><GsButton aria-label="清除选择" onClick={() => setSelectedIds(new Set())} htmlType="submit"><MaterialIcon name="close" size={17}/>清除</GsButton></nav></div> : null}
        <div className="businessTable" role="table" aria-label={`${model.title}列表`}>
          <div role="rowgroup"><div className={`businessTableHeader businessTableColumns${visibleColumnCount}`} role="row"><span className="businessSelectionCell" role="columnheader"><SelectionCheckbox label="选择当前页全部记录" checked={allCurrentPageSelected} indeterminate={someCurrentPageSelected && !allCurrentPageSelected} onChange={toggleCurrentPageSelection}/></span>{model.columns.map((column, index) => visibleColumnIndexes.has(index) ? <span role="columnheader" aria-sort={sortColumn === index ? sortDirection === "asc" ? "ascending" : "descending" : "none"} key={column}><GsButton onClick={() => changeSort(index)} htmlType="submit">{column}<MaterialIcon name={sortColumn === index ? sortDirection === "asc" ? "arrow_upward" : "arrow_downward" : "unfold_more"} size={14}/></GsButton></span> : null)}<span role="columnheader" aria-sort={sortColumn === model.columns.length ? sortDirection === "asc" ? "ascending" : "descending" : "none"}><GsButton onClick={() => changeSort(model.columns.length)} htmlType="submit">状态<MaterialIcon name={sortColumn === model.columns.length ? sortDirection === "asc" ? "arrow_upward" : "arrow_downward" : "unfold_more"} size={14}/></GsButton></span><span role="columnheader">操作</span></div></div>
          <div role="rowgroup">{pageRows.map((row) => <div className={`${selectedIds.has(row.id) ? "businessTableRow businessTableRowSelected" : "businessTableRow"} businessTableColumns${visibleColumnCount}`} role="row" tabIndex={0} aria-label={`查看${row.id}详情`} key={row.id} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        setSelectedRow(row);
    } }} onClick={() => setSelectedRow(row)}><span className="businessSelectionCell" role="cell"><SelectionCheckbox label={`选择${row.id}`} checked={selectedIds.has(row.id)} onChange={(checked) => toggleRowSelection(row.id, checked)}/></span>{visibleColumnIndexes.has(0) ? <strong role="cell">{row.id}</strong> : null}{row.cells.map((cell, index) => visibleColumnIndexes.has(index + 1) ? <span role="cell" data-label={model.columns[index + 1]} key={`${row.id}-${index}`}>{cell}</span> : null)}<em role="cell" className={`businessStatus ${row.tone ? `businessStatus${row.tone}` : ""}`}>{row.status}</em><span className="businessRowActions" role="cell"><GsButton onClick={(event) => { event.stopPropagation(); openEdit(row); }} aria-label={`编辑${row.id}`} title="编辑" htmlType="submit"><MaterialIcon name="edit" size={16}/></GsButton><GsButton onClick={(event) => { event.stopPropagation(); setSelectedRow(row); }} aria-label={`查看${row.id}详情`} title="查看详情" htmlType="submit"><MaterialIcon name="chevron_right" size={18}/></GsButton></span></div>)}</div>
          {filteredRows.length === 0 ? <div className="businessEmpty"><span><MaterialIcon name="search_off" size={25}/></span><strong>没有匹配的{model.recordNoun}</strong><p>调整搜索关键词或筛选条件后再试。</p><GsButton className="secondaryButton" onClick={() => { setQuery(""); setStatus("全部状态"); setOwner("全部负责人"); setPeriod(model.filters[2]?.options[0] ?? "本月"); resetToFirstPage(); }} htmlType="submit">重置筛选</GsButton></div> : null}
        </div>
        <footer className="businessPagination">
          <span>第 {pageStart}–{pageEnd} 条，共 {filteredRows.length} 条{filteredRows.length !== rows.length ? `（总记录 ${rows.length} 条）` : ""}</span>
          <GsPagination aria-label={`${model.title}分页`} current={currentPage} pageSize={pageSize} pageSizeOptions={[10, 20, 50]} responsive showLessItems total={filteredRows.length} onChange={(nextPage, nextPageSize) => {
            if (nextPageSize !== pageSize) {
                setPageSize(nextPageSize);
                setPage(1);
                return;
            }
            setPage(nextPage);
        }}/>
        </footer>
        </div>
      </section>

      <div className="businessBottomGrid">
        <section className="businessAttention"><div className="sectionHeading"><div><p className="eyebrow">待处理业务事项</p><h3>{model.attentionTitle}</h3></div><strong>{model.attentionItems.length}</strong></div>{model.attentionItems.map((item, index) => <article key={item.title}><span className={`attentionTone attentionTone${item.tone}`}>{String(index + 1).padStart(2, "0")}</span><div><strong>{item.title}</strong><p>{item.detail}</p><small>{item.owner}</small></div><GsButton aria-label={`处理${item.title}`} onClick={() => openAttentionItem(item.title, item.tone)} htmlType="submit"><MaterialIcon name="arrow_outward" size={18}/></GsButton></article>)}</section>
        <section className="workflowCard"><div className="sectionHeading"><div><p className="eyebrow">受控业务流程</p><h3>标准业务流程</h3></div><MaterialIcon name="account_tree" size={22}/></div><ol>{model.workflow.map((step, index) => <li key={step.label}><span>{index + 1}</span><div><strong>{step.label}</strong><small>{step.detail}</small></div></li>)}</ol></section>
      </div>

      {selectedRow && !formMode ? <RecordDrawer model={model} row={selectedRow} onClose={() => setSelectedRow(null)} onEdit={() => openEdit(selectedRow, true)} onAction={applyWorkflowUpdate}/> : null}
      {formMode ? <BusinessFormDialog model={model} mode={formMode} row={selectedRow} onClose={closeForm} onSaved={saveRow}/> : null}
      {batchEditDialogOpen ? <BatchEditDialog model={model} count={selectedRows.length} statuses={statusOptions} owners={ownerOptions} pending={batchEditing} onClose={() => setBatchEditDialogOpen(false)} onConfirm={(changes) => void confirmBatchEdit(changes)}/> : null}
      {deleteDialogOpen ? <DeleteConfirmDialog model={model} count={selectedRows.length} pending={deleting} onClose={() => setDeleteDialogOpen(false)} onConfirm={() => void confirmBulkDelete()}/> : null}
      {saveViewDialogOpen ? <SaveViewDialog model={model} onClose={() => setSaveViewDialogOpen(false)} onSave={saveCurrentView}/> : null}
      {toast ? <div className="toastMessage" role="status" aria-live="polite"><span><MaterialIcon name="check_circle" filled size={18}/></span>{toast}{lastDeletedRows.length && (toast.startsWith("已删除") || toast.startsWith("已停用")) ? <GsButton onClick={() => void undoBulkDelete()} htmlType="submit">{usesBackend ? "恢复" : "撤销"}</GsButton> : null}</div> : null}
    </div>);
}
