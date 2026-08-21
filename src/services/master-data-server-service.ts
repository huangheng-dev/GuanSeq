import "server-only";

import { randomUUID } from "node:crypto";

import type { BusinessFormField, BusinessPageModel, BusinessRow } from "@/lib/business-page-data";
import {
  customerPageSchema,
  customerRecordSchema,
  materialPageSchema,
  materialRecordSchema,
  type CustomerRecord,
  type MaterialRecord,
} from "@/lib/contracts";
import { getBusinessPage } from "@/services/manufacturing-service";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type MasterDataEntity = "customers" | "materials";

const customerTypeLabels = { ENTERPRISE: "企业客户", DISTRIBUTOR: "渠道客户", INTERNAL: "内部客户" } as const;
const customerTypeCodes = { 企业客户: "ENTERPRISE", 渠道客户: "DISTRIBUTOR", 内部客户: "INTERNAL" } as const;
const materialTypeLabels = { FINISHED_GOOD: "成品", SEMI_FINISHED: "半成品", RAW_MATERIAL: "原材料", PACKAGING: "包装物", CONSUMABLE: "耗材" } as const;
const materialTypeCodes = { 成品: "FINISHED_GOOD", 半成品: "SEMI_FINISHED", 原材料: "RAW_MATERIAL", 包装物: "PACKAGING", 耗材: "CONSUMABLE" } as const;
const procurementLabels = { MAKE: "自制", BUY: "采购", OUTSOURCE: "委外" } as const;
const procurementCodes = { 自制: "MAKE", 采购: "BUY", 委外: "OUTSOURCE" } as const;

export function resolveMasterDataEntity(pathname: string): MasterDataEntity | null {
  if (pathname === "/sales/customers/list") return "customers";
  if (pathname === "/product/materials/list") return "materials";
  return null;
}

function statusLabel(status: "ACTIVE" | "INACTIVE") {
  return status === "ACTIVE" ? "已启用" : "已停用";
}

function customerRow(record: CustomerRecord): BusinessRow {
  return {
    id: record.code,
    entityId: record.id,
    version: record.version,
    dataSource: "backend",
    cells: [record.name, customerTypeLabels[record.customerType], `${record.creditLevel}级`, `${record.contactName ?? "未维护"} · ${record.contactPhone ?? "未维护"}`],
    status: statusLabel(record.status),
    tone: record.status === "ACTIVE" ? "good" : "info",
    owner: record.owner,
    description: `${record.name}由${record.owner}负责，最近更新于${new Date(record.updatedAt).toLocaleString("zh-CN", { hour12: false })}。`,
    ageInDays: 0,
    formValues: {
      code: record.code,
      name: record.name,
      customerType: customerTypeLabels[record.customerType],
      creditLevel: `${record.creditLevel}级`,
      contactName: record.contactName ?? "",
      contactPhone: record.contactPhone ?? "",
      owner: record.owner,
    },
  };
}

function materialRow(record: MaterialRecord): BusinessRow {
  return {
    id: record.code,
    entityId: record.id,
    version: record.version,
    dataSource: "backend",
    cells: [record.name, record.specification ?? "未维护", materialTypeLabels[record.materialType], `${procurementLabels[record.procurementType]} · ${record.baseUnit}`],
    status: statusLabel(record.status),
    tone: record.status === "ACTIVE" ? "good" : "info",
    owner: record.owner,
    description: `${record.name}（${record.specification ?? "规格未维护"}），由${record.owner}负责。`,
    ageInDays: 0,
    formValues: {
      code: record.code,
      name: record.name,
      specification: record.specification ?? "",
      materialType: materialTypeLabels[record.materialType],
      baseUnit: record.baseUnit,
      procurementType: procurementLabels[record.procurementType],
      owner: record.owner,
    },
  };
}

function customerFields(): BusinessFormField[] {
  return [
    { name: "code", label: "客户编码", type: "text", required: true, placeholder: "例如 CUS-0005" },
    { name: "name", label: "客户名称", type: "text", required: true },
    { name: "customerType", label: "客户类型", type: "select", required: true, options: ["企业客户", "渠道客户", "内部客户"] },
    { name: "creditLevel", label: "信用等级", type: "select", required: true, options: ["A级", "B级", "C级"] },
    { name: "contactName", label: "联系人", type: "text" },
    { name: "contactPhone", label: "联系电话", type: "text" },
    { name: "owner", label: "负责人", type: "text", required: true },
  ];
}

function materialFields(): BusinessFormField[] {
  return [
    { name: "code", label: "物料编码", type: "text", required: true, placeholder: "例如 MAT-0005" },
    { name: "name", label: "物料名称", type: "text", required: true },
    { name: "specification", label: "规格型号", type: "text", span: "full" },
    { name: "materialType", label: "物料类型", type: "select", required: true, options: ["成品", "半成品", "原材料", "包装物", "耗材"] },
    { name: "baseUnit", label: "基本单位", type: "text", required: true },
    { name: "procurementType", label: "获取方式", type: "select", required: true, options: ["自制", "采购", "委外"] },
    { name: "owner", label: "负责人", type: "text", required: true },
  ];
}

export async function getBusinessPageWithData(pathname: string): Promise<BusinessPageModel | null> {
  const base = await getBusinessPage(pathname);
  const entity = resolveMasterDataEntity(pathname);
  if (!base || !entity) return base;

  const requestId = `web-list-${randomUUID()}`;
  const response = await requestGuanSeqApi(`/api/v1/masterdata/${entity}?page=0&size=200&status=ALL`, requestId);
  if (!response?.ok) return { ...base, dataSource: "mock" };

  const rows = entity === "customers"
    ? customerPageSchema.parse(await response.json()).items.map(customerRow)
    : materialPageSchema.parse(await response.json()).items.map(materialRow);
  const active = rows.filter((row) => row.status === "已启用").length;
  const inactive = rows.length - active;

  return {
    ...base,
    definitionId: `backend:${pathname}`,
    planned: false,
    dataSource: "backend",
    layout: "catalog",
    recordNoun: entity === "customers" ? "客户" : "物料",
    primaryAction: entity === "customers" ? "新建客户" : "新建物料",
    primaryActionMode: "form",
    metrics: [
      { label: entity === "customers" ? "客户总数" : "物料总数", value: String(rows.length), note: "当前租户可见范围", tone: "info" },
      { label: "已启用", value: String(active), note: "可参与业务流程", tone: "good" },
      { label: "已停用", value: String(inactive), note: inactive ? "需要复核或恢复" : "当前无停用记录", tone: inactive ? "warn" : "good" },
    ],
    views: [entity === "customers" ? "全部客户" : "全部物料", "已启用", "已停用"],
    filters: [
      { label: "全部状态", options: ["全部状态", "已启用", "已停用"] },
      { label: "全部负责人", options: ["全部负责人", ...new Set(rows.map((row) => row.owner))] },
      { label: "本月", options: ["本月", "本周", "今日", "本季度"] },
    ],
    columns: entity === "customers"
      ? ["客户编码", "客户名称", "客户类型", "信用等级", "联系人"]
      : ["物料编码", "物料名称", "规格型号", "物料类型", "获取方式 / 单位"],
    rows,
    formFields: entity === "customers" ? customerFields() : materialFields(),
    cellFields: entity === "customers"
      ? ["name", "customerType", "creditLevel", "contactName"]
      : ["name", "specification", "materialType", "procurementType"],
    attentionTitle: entity === "customers" ? "客户主数据关注" : "物料主数据关注",
    attentionItems: inactive
      ? [{ title: `${inactive} 条记录处于停用状态`, detail: "停用记录不会进入新业务流程，可在确认后恢复。", owner: "主数据负责人", tone: "warn" }]
      : [{ title: "主数据状态正常", detail: "当前可见记录均已启用。", owner: "主数据负责人", tone: "good" }],
  };
}

type MutationInput = {
  pathname: string;
  action: "create" | "update" | "delete" | "restore" | "batch";
  values: Record<string, string>;
  records?: Array<{ id: string; version: number }>;
};

export async function mutateMasterData(input: MutationInput, requestId: string) {
  const entity = resolveMasterDataEntity(input.pathname);
  if (!entity) throw new Error("当前页面不属于主数据后端范围");

  let path = `/api/v1/masterdata/${entity}`;
  let method = "POST";
  let body: unknown;
  if (input.action === "create" || input.action === "update") {
    if (input.action === "update") {
      if (!input.values._entityId) throw new Error("缺少记录标识，请刷新后重试");
      path += `/${input.values._entityId}`;
      method = "PUT";
    }
    body = entity === "customers"
      ? {
          code: input.values.code,
          name: input.values.name,
          customerType: customerTypeCodes[input.values.customerType as keyof typeof customerTypeCodes],
          creditLevel: input.values.creditLevel?.replace("级", ""),
          contactName: input.values.contactName || null,
          contactPhone: input.values.contactPhone || null,
          owner: input.values.owner,
          ...(input.action === "update" ? { expectedVersion: Number(input.values._version) } : {}),
        }
      : {
          code: input.values.code,
          name: input.values.name,
          specification: input.values.specification || null,
          materialType: materialTypeCodes[input.values.materialType as keyof typeof materialTypeCodes],
          baseUnit: input.values.baseUnit,
          procurementType: procurementCodes[input.values.procurementType as keyof typeof procurementCodes],
          owner: input.values.owner,
          ...(input.action === "update" ? { expectedVersion: Number(input.values._version) } : {}),
        };
  } else {
    path += "/batch";
    method = "PATCH";
    body = {
      records: (input.records ?? []).map((record) => ({ id: record.id, expectedVersion: record.version })),
      ...(input.action === "delete" ? { status: "INACTIVE" } : {}),
      ...(input.action === "restore" ? { status: "ACTIVE" } : {}),
      ...(input.action === "batch" && input.values.status ? { status: input.values.status === "已启用" ? "ACTIVE" : "INACTIVE" } : {}),
      ...(input.action === "batch" && input.values.owner ? { owner: input.values.owner } : {}),
    };
  }

  const response = await requestGuanSeqApi(path, requestId, { method, body: JSON.stringify(body) });
  if (!response) throw new GuanSeqApiError("主数据服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "主数据服务暂时无法完成请求");
  if (input.action === "create" || input.action === "update") {
    const row = entity === "customers"
      ? customerRow(customerRecordSchema.parse(await response.json()))
      : materialRow(materialRecordSchema.parse(await response.json()));
    return { source: "backend" as const, requestId: response.headers.get("X-Request-Id") ?? requestId, savedAt: new Date().toISOString(), row };
  }
  const payload = await response.json();
  const rows = entity === "customers"
    ? customerRecordSchema.array().parse(payload).map(customerRow)
    : materialRecordSchema.array().parse(payload).map(materialRow);
  return { source: "backend" as const, requestId: response.headers.get("X-Request-Id") ?? requestId, savedAt: new Date().toISOString(), rows };
}
