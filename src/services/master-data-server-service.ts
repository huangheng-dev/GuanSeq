import "server-only";

import { randomUUID } from "node:crypto";

import type { BusinessFormField, BusinessPageModel, BusinessRow } from "@/lib/business-page-data";
import {
  customerPageSchema,
  customerRecordSchema,
  materialPageSchema,
  materialRecordSchema,
  supplierPageSchema,
  supplierRecordSchema,
  type CustomerRecord,
  type MaterialRecord,
  type SupplierRecord,
} from "@/lib/contracts";
import { getBusinessPage } from "@/services/manufacturing-service";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type MasterDataEntity = "customers" | "materials" | "suppliers";

const customerTypeLabels = { ENTERPRISE: "企业客户", DISTRIBUTOR: "渠道客户", INTERNAL: "内部客户" } as const;
const customerTypeCodes = { 企业客户: "ENTERPRISE", 渠道客户: "DISTRIBUTOR", 内部客户: "INTERNAL" } as const;
const materialTypeLabels = { FINISHED_GOOD: "成品", SEMI_FINISHED: "半成品", RAW_MATERIAL: "原材料", PACKAGING: "包装物", CONSUMABLE: "耗材" } as const;
const materialTypeCodes = { 成品: "FINISHED_GOOD", 半成品: "SEMI_FINISHED", 原材料: "RAW_MATERIAL", 包装物: "PACKAGING", 耗材: "CONSUMABLE" } as const;
const procurementLabels = { MAKE: "自制", BUY: "采购", OUTSOURCE: "委外" } as const;
const procurementCodes = { 自制: "MAKE", 采购: "BUY", 委外: "OUTSOURCE" } as const;

export function resolveMasterDataEntity(pathname: string): MasterDataEntity | null {
  if (pathname === "/sales/customers/list") return "customers";
  if (pathname === "/product/materials/list") return "materials";
  if (pathname === "/procurement/suppliers/list") return "suppliers";
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

function supplierRow(record: SupplierRecord): BusinessRow {
  return {
    id: record.code,
    entityId: record.id,
    version: record.version,
    dataSource: "backend",
    cells: [record.name, record.contactName ?? "未维护", record.contactPhone ?? "未维护"],
    status: statusLabel(record.status),
    tone: record.status === "ACTIVE" ? "good" : "info",
    owner: "采购部",
    description: `${record.name}，最近更新于${new Date(record.updatedAt).toLocaleString("zh-CN", { hour12: false })}。`,
    ageInDays: 0,
    formValues: {
      code: record.code,
      name: record.name,
      contactName: record.contactName ?? "",
      contactPhone: record.contactPhone ?? "",
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

function supplierFields(): BusinessFormField[] {
  return [
    { name: "code", label: "供应商编码", type: "text", required: true, placeholder: "例如 SUP-0005" },
    { name: "name", label: "供应商名称", type: "text", required: true },
    { name: "contactName", label: "联系人", type: "text" },
    { name: "contactPhone", label: "联系电话", type: "text" },
  ];
}

const ENTITY_LABELS: Record<MasterDataEntity, { noun: string; columns: string[]; primaryAction: string }> = {
  customers: { noun: "客户", columns: ["客户编码", "客户名称", "客户类型", "信用等级", "联系人"], primaryAction: "新建客户" },
  materials: { noun: "物料", columns: ["物料编码", "物料名称", "规格型号", "物料类型", "获取方式 / 单位"], primaryAction: "新建物料" },
  suppliers: { noun: "供应商", columns: ["供应商编码", "供应商名称", "联系人", "联系电话"], primaryAction: "新建供应商" },
};

export async function getBusinessPageWithData(pathname: string): Promise<BusinessPageModel | null> {
  const base = await getBusinessPage(pathname);
  const entity = resolveMasterDataEntity(pathname);
  if (!base || !entity) return base;

  const requestId = `web-list-${randomUUID()}`;
  const endpoint = entity === "suppliers" ? "/api/v1/procurement/suppliers" : `/api/v1/masterdata/${entity}`;
  const response = await requestGuanSeqApi(`${endpoint}?page=0&size=200&status=ALL`, requestId);
  if (!response) return { ...base, dataSource: "mock" };
  if (!response.ok) await readApiError(response, "主数据服务加载失败");

  const json = await response.json();
  const rows =
    entity === "customers"
      ? customerPageSchema.parse(json).items.map(customerRow)
      : entity === "materials"
        ? materialPageSchema.parse(json).items.map(materialRow)
        : supplierPageSchema.parse(json).items.map(supplierRow);

  const active = rows.filter((row) => row.status === "已启用").length;
  const inactive = rows.length - active;
  const meta = ENTITY_LABELS[entity];

  return {
    ...base,
    definitionId: `backend:${pathname}`,
    planned: false,
    dataSource: "backend",
    layout: "catalog",
    recordNoun: meta.noun,
    primaryAction: meta.primaryAction,
    primaryActionMode: "form",
    metrics: [
      { label: `${meta.noun}总数`, value: String(rows.length), note: "当前租户可见范围", tone: "info" },
      { label: "已启用", value: String(active), note: "可参与业务流程", tone: "good" },
      { label: "已停用", value: String(inactive), note: inactive ? "需要复核或恢复" : "当前无停用记录", tone: inactive ? "warn" : "good" },
    ],
    views: [`全部${meta.noun}`, "已启用", "已停用"],
    filters: [
      { label: "全部状态", options: ["全部状态", "已启用", "已停用"] },
      ...(entity !== "suppliers"
        ? [{ label: "全部负责人", options: ["全部负责人", ...new Set(rows.map((row) => row.owner).filter(Boolean))] }]
        : []),
      { label: "本月", options: ["本月", "本周", "今日", "本季度"] },
    ],
    columns: meta.columns,
    rows,
    formFields: entity === "customers" ? customerFields() : entity === "materials" ? materialFields() : supplierFields(),
    cellFields:
      entity === "customers"
        ? ["name", "customerType", "creditLevel", "contactName"]
        : entity === "materials"
          ? ["name", "specification", "materialType", "procurementType"]
          : ["name", "contactName", "contactPhone"],
    attentionTitle: `${meta.noun}主数据关注`,
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

  const isSupplier = entity === "suppliers";
  const basePath = isSupplier ? "/api/v1/procurement/suppliers" : `/api/v1/masterdata/${entity}`;
  let path = basePath;
  let method = "POST";
  let body: unknown;

  if (input.action === "create" || input.action === "update") {
    if (input.action === "update") {
      if (!input.values._entityId) throw new Error("缺少记录标识，请刷新后重试");
      path += `/${input.values._entityId}`;
      method = "PUT";
    }
    if (isSupplier) {
      body = {
        code: input.values.code,
        name: input.values.name,
        contactName: input.values.contactName || null,
        contactPhone: input.values.contactPhone || null,
        ...(input.action === "update" ? { expectedVersion: Number(input.values._version) } : {}),
      };
    } else {
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
    }
  } else {
    // Supplier status change via /actions endpoint; customer/material via /batch
    if (isSupplier) {
      const status = input.action === "restore" ? "ACTIVE" : "INACTIVE";
      const results: BusinessRow[] = [];
      for (const record of input.records ?? []) {
        const resp = await requestGuanSeqApi(`${basePath}/${record.id}/actions`, requestId, {
          method: "POST",
          body: JSON.stringify({ status, expectedVersion: record.version ?? 0 }),
        });
        if (!resp) throw new GuanSeqApiError("供应商服务暂时不可用", 503);
        if (!resp.ok) await readApiError(resp, "供应商状态更新失败");
        results.push(supplierRow(supplierRecordSchema.parse(await resp.json())));
      }
      return { source: "backend" as const, requestId, savedAt: new Date().toISOString(), rows: results };
    }
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
    const row =
      entity === "customers"
        ? customerRow(customerRecordSchema.parse(await response.json()))
        : entity === "materials"
          ? materialRow(materialRecordSchema.parse(await response.json()))
          : supplierRow(supplierRecordSchema.parse(await response.json()));
    return { source: "backend" as const, requestId: response.headers.get("X-Request-Id") ?? requestId, savedAt: new Date().toISOString(), row };
  }
  const payload = await response.json();
  const rows = entity === "customers"
    ? customerRecordSchema.array().parse(payload).map(customerRow)
    : materialRecordSchema.array().parse(payload).map(materialRow);
  return { source: "backend" as const, requestId: response.headers.get("X-Request-Id") ?? requestId, savedAt: new Date().toISOString(), rows };
}
