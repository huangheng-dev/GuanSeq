import { z } from "zod";

export const flowStageSchema = z.object({
  id: z.string(),
  label: z.string(),
  owner: z.string(),
  status: z.enum(["done", "active", "warning", "pending"]),
  count: z.number().int().nonnegative(),
});

export const workOrderSchema = z.object({
  id: z.string(),
  product: z.string(),
  customer: z.string(),
  workshop: z.string(),
  progress: z.number().min(0).max(100),
  status: z.enum(["执行中", "待开工", "有风险", "待检验"]),
  dueDate: z.string(),
  quantity: z.string(),
});

export const manufacturingSnapshotSchema = z.object({
  workspace: z.object({ name: z.string(), company: z.string(), date: z.string() }),
  metrics: z.array(
    z.object({ label: z.string(), value: z.string(), change: z.string(), tone: z.string() }),
  ),
  flow: z.array(flowStageSchema),
  workOrders: z.array(workOrderSchema),
  capacity: z.array(
    z.object({ name: z.string(), load: z.number().min(0).max(100), note: z.string() }),
  ),
  alerts: z.array(
    z.object({ id: z.string(), level: z.enum(["高", "中", "低"]), title: z.string(), detail: z.string(), owner: z.string() }),
  ),
});

export const globalSearchItemSchema = z.object({
  type: z.enum(["销售订单", "物料", "客户", "供应商", "BOM", "采购订单", "生产工单", "库存批次", "质量问题", "设备"]),
  title: z.string(),
  detail: z.string(),
  keywords: z.array(z.string()),
  href: z.string(),
});

export const workspaceSummarySchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  organizationId: z.string().uuid(),
  companyName: z.string(),
  roleCode: z.string(),
  current: z.boolean(),
});

export const workspaceSessionSchema = z.object({
  userId: z.string().uuid(),
  username: z.string(),
  displayName: z.string(),
  currentWorkspaceId: z.string().uuid(),
  selectionVersion: z.number().int().nonnegative(),
  workspaces: z.array(workspaceSummarySchema).min(1),
});

export const workspaceSessionEnvelopeSchema = z.object({
  source: z.enum(["backend", "mock"]),
  session: workspaceSessionSchema,
});

const masterDataBaseSchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  owner: z.string(),
  status: z.enum(["ACTIVE", "INACTIVE"]),
  version: z.number().int().nonnegative(),
  updatedAt: z.string().datetime(),
});

export const customerRecordSchema = masterDataBaseSchema.extend({
  customerType: z.enum(["ENTERPRISE", "DISTRIBUTOR", "INTERNAL"]),
  creditLevel: z.enum(["A", "B", "C"]),
  contactName: z.string().nullable(),
  contactPhone: z.string().nullable(),
});

export const materialRecordSchema = masterDataBaseSchema.extend({
  specification: z.string().nullable(),
  materialType: z.enum(["FINISHED_GOOD", "SEMI_FINISHED", "RAW_MATERIAL", "PACKAGING", "CONSUMABLE"]),
  baseUnit: z.string(),
  procurementType: z.enum(["MAKE", "BUY", "OUTSOURCE"]),
  incomingInspectionRequired: z.boolean(),
});

const pageEnvelope = <T extends z.ZodTypeAny>(item: T) => z.object({
  items: z.array(item),
  totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalPages: z.number().int().nonnegative(),
});

export const customerPageSchema = pageEnvelope(customerRecordSchema);
export const materialPageSchema = pageEnvelope(materialRecordSchema);

export const salesOrderLineSchema = z.object({
  id: z.string().uuid(),
  lineNumber: z.number().int().positive(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  materialSpecification: z.string().nullable(),
  unit: z.string(),
  quantity: z.number().positive(),
  unitPrice: z.number().nonnegative(),
  netAmount: z.number().nonnegative(),
  taxAmount: z.number().nonnegative(),
  grossAmount: z.number().nonnegative(),
  deliveredQuantity: z.number().nonnegative(),
});

export const salesOrderRecordSchema = z.object({
  id: z.string().uuid(),
  orderNumber: z.string(),
  customerId: z.string().uuid(),
  customerCode: z.string(),
  customerName: z.string(),
  currency: z.enum(["CNY", "USD", "EUR"]),
  taxRate: z.number().min(0).max(1),
  requestedDeliveryDate: z.string(),
  promisedDeliveryDate: z.string().nullable(),
  owner: z.string(),
  status: z.enum(["DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED", "RELEASED", "PARTIALLY_SHIPPED", "SHIPPED"]),
  totalNetAmount: z.number().nonnegative(),
  totalTaxAmount: z.number().nonnegative(),
  totalGrossAmount: z.number().nonnegative(),
  rejectionReason: z.string().nullable(),
  version: z.number().int().nonnegative(),
  updatedAt: z.string().datetime(),
  lines: z.array(salesOrderLineSchema).min(1),
});

export const salesOrderPageSchema = pageEnvelope(salesOrderRecordSchema);

export const salesOrderReferenceDataSchema = z.object({
  customers: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string(), creditLevel: z.enum(["A", "B", "C"]) })),
  materials: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string(), specification: z.string().nullable(), baseUnit: z.string() })),
});


export const salesShipmentLineSchema = z.object({
  id: z.string().uuid(),
  orderLineId: z.string().uuid(),
  lineNumber: z.number().int().positive(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  materialSpecification: z.string().nullable(),
  unit: z.string(),
  shippedQuantity: z.number().positive(),
  stockSummary: z.string(),
});

export const salesShipmentRecordSchema = z.object({
  id: z.string().uuid(),
  shipmentNumber: z.string(),
  salesOrderId: z.string().uuid(),
  orderNumber: z.string(),
  customerId: z.string().uuid(),
  customerCode: z.string(),
  customerName: z.string(),
  warehouseId: z.string().uuid(),
  warehouseCode: z.string(),
  warehouseName: z.string(),
  plannedShippingDate: z.string(),
  actualShippedAt: z.string().datetime(),
  status: z.literal("SHIPPED"),
  note: z.string().nullable(),
  totalShippedQuantity: z.number().positive(),
  version: z.number().int().nonnegative(),
  createdAt: z.string().datetime(),
  lines: z.array(salesShipmentLineSchema).min(1),
});

export const salesShipmentPageSchema = pageEnvelope(salesShipmentRecordSchema);

export const salesShipmentReferenceDataSchema = z.object({
  releasedOrders: z.array(z.object({
    id: z.string().uuid(),
    orderNumber: z.string(),
    customerId: z.string().uuid(),
    customerCode: z.string(),
    customerName: z.string(),
    promisedDeliveryDate: z.string().nullable(),
    lines: z.array(z.object({
      id: z.string().uuid(),
      lineNumber: z.number().int().positive(),
      materialId: z.string().uuid(),
      materialCode: z.string(),
      materialName: z.string(),
      materialSpecification: z.string().nullable(),
      unit: z.string(),
      orderedQuantity: z.number().positive(),
      deliveredQuantity: z.number().nonnegative(),
      outstandingQuantity: z.number().nonnegative(),
    })).min(1),
  })),
  warehouses: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string() })),
});


export const orderProfitLineSchema = z.object({
  id: z.string().uuid(),
  salesOrderLineId: z.string().uuid(),
  lineNumber: z.number().int().positive(),
  productionOrderId: z.string().uuid().nullable(),
  productionOrderNumber: z.string().nullable(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  materialSpecification: z.string().nullable(),
  unit: z.string(),
  orderedQuantity: z.number().positive(),
  shippedQuantity: z.number().positive(),
  acceptedQuantity: z.number().nonnegative().nullable(),
  consumedQuantity: z.number().nonnegative().nullable(),
  unitPrice: z.number().nonnegative(),
  revenue: z.number().nonnegative(),
  materialCost: z.number().nonnegative(),
  processingCost: z.number().nonnegative(),
  totalCost: z.number().nonnegative(),
  grossProfit: z.number(),
  grossMargin: z.number().nullable(),
  costStatus: z.enum(["COMPLETE", "MISSING_COST"]),
  missingItems: z.array(z.string()),
});

export const orderProfitRecordSchema = z.object({
  id: z.string().uuid(),
  settlementNumber: z.string(),
  salesOrderId: z.string().uuid(),
  orderNumber: z.string(),
  customerId: z.string().uuid(),
  customerCode: z.string(),
  customerName: z.string(),
  currency: z.string(),
  orderStatus: z.string().nullable(),
  orderedQuantity: z.number().positive(),
  shippedQuantity: z.number().positive(),
  revenue: z.number().nonnegative(),
  materialCost: z.number().nonnegative(),
  processingCost: z.number().nonnegative(),
  totalCost: z.number().nonnegative(),
  grossProfit: z.number(),
  grossMargin: z.number().nullable(),
  costBasis: z.string(),
  costStatus: z.enum(["COMPLETE", "MISSING_COST"]),
  status: z.literal("SETTLED"),
  missingItems: z.array(z.string()),
  version: z.number().int().nonnegative(),
  settledAt: z.string().datetime(),
  lines: z.array(orderProfitLineSchema).min(1),
});

export const orderProfitPageSchema = pageEnvelope(orderProfitRecordSchema);

export const orderProfitReferenceDataSchema = z.object({
  orders: z.array(z.object({
    salesOrderId: z.string().uuid(),
    orderNumber: z.string(),
    customerName: z.string(),
    orderStatus: z.string(),
    orderedQuantity: z.number().positive(),
    shippedQuantity: z.number().nonnegative(),
    revenueCandidate: z.number().nonnegative(),
    settled: z.boolean(),
    settlementId: z.string().uuid().nullable(),
    settlementNumber: z.string().nullable(),
    costStatus: z.enum(["COMPLETE", "MISSING_COST"]).nullable(),
  })),
});

export const receivableReceiptSchema = z.object({
  id: z.string().uuid(), receiptNumber: z.string(), amount: z.number().positive(), receiptDate: z.string(),
  paymentMethod: z.enum(["BANK_TRANSFER", "CASH", "BILL", "OTHER"]), bankReference: z.string().nullable(),
  note: z.string().nullable(), status: z.literal("POSTED"), createdAt: z.string().datetime(),
});

export const receivableInvoiceLineSchema = z.object({
  id: z.string().uuid(), salesOrderLineId: z.string().uuid(), lineNumber: z.number().int().positive(),
  materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(),
  unit: z.string(), invoiceQuantity: z.number().positive(), unitPrice: z.number().nonnegative(), netAmount: z.number().nonnegative(),
  taxAmount: z.number().nonnegative(), grossAmount: z.number().nonnegative(),
});

export const receivableInvoiceRecordSchema = z.object({
  id: z.string().uuid(), invoiceNumber: z.string(), salesOrderId: z.string().uuid(), orderNumber: z.string(),
  customerId: z.string().uuid(), customerCode: z.string(), customerName: z.string(), currency: z.string(),
  invoiceDate: z.string(), dueDate: z.string(), taxRate: z.number().min(0).max(1), netAmount: z.number().nonnegative(),
  taxAmount: z.number().nonnegative(), grossAmount: z.number().positive(), receivedAmount: z.number().nonnegative(),
  outstandingAmount: z.number().nonnegative(), status: z.enum(["OPEN", "PARTIALLY_PAID", "PAID"]),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), lines: z.array(receivableInvoiceLineSchema).min(1),
  receipts: z.array(receivableReceiptSchema),
});

export const receivableInvoicePageSchema = pageEnvelope(receivableInvoiceRecordSchema);

export const receivableReferenceDataSchema = z.object({
  orders: z.array(z.object({
    salesOrderId: z.string().uuid(), orderNumber: z.string(), customerId: z.string().uuid(), customerCode: z.string(),
    customerName: z.string(), currency: z.string(), taxRate: z.number().min(0).max(1), orderStatus: z.enum(["PARTIALLY_SHIPPED", "SHIPPED"]),
    deliveredAmount: z.number().nonnegative(), invoicedAmount: z.number().nonnegative(), remainingAmount: z.number().nonnegative(),
    lines: z.array(z.object({
      salesOrderLineId: z.string().uuid(), lineNumber: z.number().int().positive(), materialId: z.string().uuid(),
      materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
      deliveredQuantity: z.number().nonnegative(), invoicedQuantity: z.number().nonnegative(), remainingQuantity: z.number().nonnegative(),
      unitPrice: z.number().nonnegative(),
    })),
  })),
});

export const purchaseOrderLineSchema = z.object({
  id: z.string().uuid(), lineNumber: z.number().int().positive(), materialId: z.string().uuid(),
  materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
  orderedQuantity: z.number().positive(), receivedQuantity: z.number().nonnegative(), outstandingQuantity: z.number().nonnegative(),
  unitPrice: z.number().nonnegative(), netAmount: z.number().nonnegative(), taxAmount: z.number().nonnegative(), grossAmount: z.number().nonnegative(),
});
export const purchaseOrderRecordSchema = z.object({
  id: z.string().uuid(), orderNumber: z.string(), supplierId: z.string().uuid(), supplierCode: z.string(), supplierName: z.string(),
  currency: z.enum(["CNY", "USD", "EUR"]), taxRate: z.number().min(0).max(1), requestedReceiptDate: z.string(),
  promisedReceiptDate: z.string().nullable(), buyer: z.string(), status: z.enum(["DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED", "RELEASED"]),
  totalNetAmount: z.number().nonnegative(), totalTaxAmount: z.number().nonnegative(), totalGrossAmount: z.number().nonnegative(),
  rejectionReason: z.string().nullable(), sourceType: z.enum(["MANUAL", "MRP"]), sourceId: z.string().uuid().nullable(), sourceNumber: z.string().nullable(),
  version: z.number().int().nonnegative(), updatedAt: z.string().datetime(),
  lines: z.array(purchaseOrderLineSchema).min(1),
});
export const purchaseOrderPageSchema = pageEnvelope(purchaseOrderRecordSchema);
export const purchaseOrderReferenceDataSchema = z.object({
  suppliers: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string(), contactName: z.string().nullable(), contactPhone: z.string().nullable() })),
  materials: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string(), specification: z.string().nullable(), baseUnit: z.string() })),
});

export const purchaseReceiptLineSchema = z.object({
  id: z.string().uuid(), lineNumber: z.number().int().positive(), purchaseOrderLineId: z.string().uuid(),
  materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(),
  unit: z.string(), receivedQuantity: z.number().positive(), inspectionRequired: z.boolean(), lotNumber: z.string(),
  status: z.enum(["PENDING_INSPECTION", "PARTIALLY_RECEIVED", "RECEIVED", "REJECTED_CLOSED"]),
  inspectionId: z.string().uuid().nullable(), inspectionNumber: z.string().nullable(),
  acceptedQuantity: z.number().nonnegative().nullable(), rejectedQuantity: z.number().nonnegative().nullable(),
  acceptedBalanceId: z.string().uuid().nullable(), rejectedBalanceId: z.string().uuid().nullable(),
  stockSummary: z.string(), version: z.number().int().nonnegative(),
});
export const purchaseReceiptRecordSchema = z.object({
  id: z.string().uuid(), receiptNumber: z.string(), purchaseOrderId: z.string().uuid(), orderNumber: z.string(),
  supplierId: z.string().uuid(), supplierCode: z.string(), supplierName: z.string(),
  warehouseId: z.string().uuid(), warehouseCode: z.string(), warehouseName: z.string(),
  locationId: z.string().uuid(), locationCode: z.string(), locationName: z.string(), note: z.string().nullable(),
  status: z.enum(["PENDING_INSPECTION", "PARTIALLY_RECEIVED", "RECEIVED", "REJECTED_CLOSED"]),
  totalReceivedQuantity: z.number().positive(), acceptedQuantity: z.number().nonnegative(), rejectedQuantity: z.number().nonnegative(),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), lines: z.array(purchaseReceiptLineSchema).min(1),
});
export const purchaseReceiptPageSchema = pageEnvelope(purchaseReceiptRecordSchema);
export const purchaseReceiptReferenceDataSchema = z.object({
  releasedOrders: z.array(z.object({
    id: z.string().uuid(), orderNumber: z.string(), supplierId: z.string().uuid(), supplierCode: z.string(), supplierName: z.string(),
    promisedReceiptDate: z.string().nullable(),
    lines: z.array(z.object({
      id: z.string().uuid(), lineNumber: z.number().int().positive(), materialId: z.string().uuid(), materialCode: z.string(),
      materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
      orderedQuantity: z.number().positive(), receivedQuantity: z.number().nonnegative(), outstandingQuantity: z.number().nonnegative(),
      inspectionRequired: z.boolean(),
    })).min(1),
  })),
  warehouses: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string() })),
  locations: z.array(z.object({ id: z.string().uuid(), warehouseId: z.string().uuid(), code: z.string(), name: z.string(), locationType: z.string() })),
});
export const createPurchaseReceiptSchema = z.object({
  purchaseOrderId: z.string().uuid(), warehouseId: z.string().uuid(), locationId: z.string().uuid(),
  note: z.string().max(500).nullable().optional(),
  lines: z.array(z.object({ orderLineId: z.string().uuid(), receivedQuantity: z.number().positive(), lotNumber: z.string().min(1).max(80) })).min(1).max(100),
});

export const productionOrderRecordSchema = z.object({
  id: z.string().uuid(), orderNumber: z.string(), materialId: z.string().uuid(), materialCode: z.string(),
  materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
  plannedQuantity: z.number().positive(), completedQuantity: z.number().nonnegative(), outstandingQuantity: z.number().nonnegative(),
	 reportedQuantity: z.number().nonnegative(), reportableQuantity: z.number().nonnegative(),
  plannedStartDate: z.string(), plannedReceiptDate: z.string(), workshop: z.string(), owner: z.string(),
  sourceType: z.enum(["MANUAL", "MRP", "SALES_ORDER"]), sourceId: z.string().uuid().nullable(), sourceNumber: z.string().nullable(),
  status: z.enum(["DRAFT", "RELEASED", "IN_PROGRESS", "COMPLETED", "CANCELLED"]),
  cancellationReason: z.string().nullable(), version: z.number().int().nonnegative(), updatedAt: z.string().datetime(),
});
export const productionOrderPageSchema = pageEnvelope(productionOrderRecordSchema);
export const productionOrderReferenceDataSchema = z.object({
  materials: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string(), specification: z.string().nullable(), baseUnit: z.string() })),
});

export const productionWorkReportRecordSchema = z.object({
  id: z.string().uuid(), reportNumber: z.string(), orderId: z.string().uuid(), orderNumber: z.string(),
  materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(),
  unit: z.string(), workshop: z.string(), shiftName: z.string(), operatorName: z.string(), reportedQuantity: z.number().positive(),
  note: z.string().nullable(), inspectionId: z.string().uuid(), inspectionNumber: z.string(), inspectionStatus: z.enum(["PENDING", "COMPLETED"]),
  qualityResult: z.enum(["PASSED", "PARTIALLY_PASSED", "FAILED"]).nullable(), acceptedQuantity: z.number().nonnegative().nullable(),
  rejectedQuantity: z.number().nonnegative().nullable(), receiptBalanceId: z.string().uuid().nullable(), receiptMovementId: z.string().uuid().nullable(),
  receiptWarehouse: z.string().nullable(), receiptLocation: z.string().nullable(), lotNumber: z.string().nullable(),
  status: z.enum(["PENDING_INSPECTION", "READY_FOR_RECEIPT", "READY_TO_CLOSE", "RECEIVED", "REJECTED_CLOSED"]),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), settledAt: z.string().datetime().nullable(),
});
export const productionWorkReportPageSchema = pageEnvelope(productionWorkReportRecordSchema);

export const finalInspectionRecordSchema = z.object({
  id: z.string().uuid(), inspectionNumber: z.string(), sourceType: z.literal("PRODUCTION_REPORT"), sourceId: z.string().uuid(), sourceNumber: z.string(),
  orderId: z.string().uuid(), orderNumber: z.string(), materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(),
  materialSpecification: z.string().nullable(), unit: z.string(), inspectionQuantity: z.number().positive(), status: z.enum(["PENDING", "COMPLETED"]),
  result: z.enum(["PASSED", "PARTIALLY_PASSED", "FAILED"]).nullable(), acceptedQuantity: z.number().nonnegative().nullable(),
  rejectedQuantity: z.number().nonnegative().nullable(), inspector: z.string().nullable(), defectDescription: z.string().nullable(), conclusion: z.string().nullable(),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), completedAt: z.string().datetime().nullable(),
});
export const finalInspectionPageSchema = pageEnvelope(finalInspectionRecordSchema);

export const incomingInspectionRecordSchema = z.object({
  id: z.string().uuid(), inspectionNumber: z.string(), sourceId: z.string().uuid(), sourceNumber: z.string(),
  purchaseOrderId: z.string().uuid(), purchaseOrderNumber: z.string(), supplierId: z.string().uuid(), supplierCode: z.string(), supplierName: z.string(),
  materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
  inspectionQuantity: z.number().positive(), status: z.enum(["PENDING", "COMPLETED"]),
  result: z.enum(["PASSED", "PARTIALLY_PASSED", "FAILED"]).nullable(),
  acceptedQuantity: z.number().nonnegative().nullable(), rejectedQuantity: z.number().nonnegative().nullable(),
  inspector: z.string().nullable(), defectDescription: z.string().nullable(), conclusion: z.string().nullable(),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), completedAt: z.string().datetime().nullable(),
});
export const incomingInspectionPageSchema = pageEnvelope(incomingInspectionRecordSchema);

export const materialPlanningParameterSchema = z.object({
  materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(),
  procurementType: z.enum(["MAKE", "BUY", "OUTSOURCE"]), unit: z.string(), leadTimeDays: z.number().int().positive().nullable(),
  configured: z.boolean(), version: z.number().int().nonnegative(), updatedAt: z.string().datetime().nullable(),
});
export const materialPlanningParameterPageSchema = pageEnvelope(materialPlanningParameterSchema);

export const independentDemandRecordSchema = z.object({
  id: z.string().uuid(),
  demandNumber: z.string(),
  sourceType: z.enum(["SALES_ORDER", "MANUAL"]),
  sourceId: z.string().uuid().nullable(),
  sourceNumber: z.string().nullable(),
  sourceLineId: z.string().uuid().nullable(),
  sourceLineNumber: z.number().int().positive().nullable(),
  sourceCustomer: z.string().nullable(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  materialSpecification: z.string().nullable(),
  unit: z.string(),
  quantity: z.number().positive(),
  requiredDate: z.string(),
  priority: z.enum(["LOW", "NORMAL", "HIGH", "URGENT"]),
  owner: z.string(),
  status: z.enum(["DRAFT", "ACTIVE", "CANCELLED"]),
  note: z.string().nullable(),
  cancellationReason: z.string().nullable(),
  version: z.number().int().nonnegative(),
  updatedAt: z.string().datetime(),
});

export const independentDemandPageSchema = pageEnvelope(independentDemandRecordSchema);

export const planningDemandReferenceDataSchema = z.object({
  materials: z.array(z.object({
    id: z.string().uuid(),
    code: z.string(),
    name: z.string(),
    specification: z.string().nullable(),
    baseUnit: z.string(),
  })),
});

export const mrpDemandSnapshotSchema = z.object({
  id: z.string().uuid(),
  demandId: z.string().uuid(),
  demandNumber: z.string(),
  sourceType: z.enum(["SALES_ORDER", "MANUAL"]),
  sourceNumber: z.string().nullable(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  materialSpecification: z.string().nullable(),
  procurementType: z.enum(["MAKE", "BUY", "OUTSOURCE"]),
  unit: z.string(),
  quantity: z.number().positive(),
  requiredDate: z.string(),
  priority: z.enum(["LOW", "NORMAL", "HIGH", "URGENT"]),
  owner: z.string(),
  snapshottedAt: z.string().datetime(),
});

export const mrpRunExceptionSchema = z.object({
  id: z.string().uuid(),
  code: z.enum(["SCHEDULED_RECEIPTS_UNAVAILABLE", "STOCK_POSITION_UNAVAILABLE", "LEAD_TIME_UNAVAILABLE", "BOM_UNAVAILABLE", "ROUTING_UNAVAILABLE"]),
  severity: z.enum(["BLOCKER", "WARNING"]),
  materialId: z.string().uuid().nullable(),
  materialCode: z.string().nullable(),
  materialName: z.string().nullable(),
  message: z.string(),
  resolutionPath: z.string(),
  createdAt: z.string().datetime(),
});

export const mrpSupplySnapshotSchema = z.object({
  id: z.string().uuid(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  unit: z.string(),
  onHandQuantity: z.number().nonnegative(),
  allocatedQuantity: z.number().nonnegative(),
  frozenQuantity: z.number().nonnegative(),
  availableQuantity: z.number().nonnegative(),
  balanceCount: z.number().int().nonnegative(),
  snapshottedAt: z.string().datetime(),
});

export const mrpScheduledReceiptSnapshotSchema = z.object({
  id: z.string().uuid(), sourceType: z.enum(["PURCHASE_ORDER", "PRODUCTION_ORDER"]), sourceOrderId: z.string().uuid(),
  sourceOrderNumber: z.string(), sourceLineId: z.string().uuid(), sourceName: z.string().nullable(), materialId: z.string().uuid(),
  materialCode: z.string(), materialName: z.string(), unit: z.string(), outstandingQuantity: z.number().positive(),
  expectedReceiptDate: z.string(), snapshottedAt: z.string().datetime(),
});

export const mrpNetRequirementSchema = z.object({
  id: z.string().uuid(), requirementLevel: z.number().int().min(0).max(50), sourceType: z.enum(["INDEPENDENT_DEMAND", "BOM_COMPONENT"]),
  parentMaterialId: z.string().uuid().nullable(), parentMaterialCode: z.string().nullable(), materialId: z.string().uuid(),
  materialCode: z.string(), materialName: z.string(), procurementType: z.enum(["MAKE", "BUY", "OUTSOURCE"]), unit: z.string(),
  grossQuantity: z.number().positive(), availableConsumed: z.number().nonnegative(), scheduledReceiptConsumed: z.number().nonnegative(),
  netQuantity: z.number().nonnegative(), requiredDate: z.string(), recommendedReleaseDate: z.string().nullable(),
  recommendationType: z.enum(["NONE", "PRODUCTION", "PURCHASE", "OUTSOURCE", "BLOCKED"]),
  decisionStatus: z.enum(["NOT_APPLICABLE", "PROPOSED", "APPROVED", "REJECTED", "CONVERTED"]),
  convertedOrderType: z.enum(["PURCHASE_ORDER", "PRODUCTION_ORDER"]).nullable(), convertedOrderId: z.string().uuid().nullable(),
  convertedOrderNumber: z.string().nullable(), version: z.number().int().nonnegative(), createdAt: z.string().datetime(),
});

export const mrpRunRecordSchema = z.object({
  id: z.string().uuid(),
  runNumber: z.string(),
  name: z.string(),
  horizonStart: z.string(),
  horizonEnd: z.string(),
  status: z.enum(["PREPARING", "BLOCKED", "COMPLETED"]),
  demandCount: z.number().int().positive(),
  totalQuantity: z.number().positive(),
  exceptionCount: z.number().int().nonnegative(),
  startedAt: z.string().datetime(),
  finishedAt: z.string().datetime().nullable(),
  requestId: z.string().nullable(),
  version: z.number().int().nonnegative(),
  demands: z.array(mrpDemandSnapshotSchema),
  supplies: z.array(mrpSupplySnapshotSchema),
  scheduledReceipts: z.array(mrpScheduledReceiptSnapshotSchema),
  netRequirements: z.array(mrpNetRequirementSchema),
  exceptions: z.array(mrpRunExceptionSchema),
});

export const mrpRunPageSchema = pageEnvelope(mrpRunRecordSchema);

export const mrpSuggestionSchema = z.object({
  id: z.string().uuid(), runId: z.string().uuid(), runNumber: z.string(), runName: z.string(),
  requirementLevel: z.number().int().min(0).max(50), sourceType: z.enum(["INDEPENDENT_DEMAND", "BOM_COMPONENT"]),
  parentMaterialCode: z.string().nullable(), materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(),
  procurementType: z.enum(["MAKE", "BUY", "OUTSOURCE"]), unit: z.string(), grossQuantity: z.number().positive(),
  netQuantity: z.number().positive(), requiredDate: z.string(), recommendedReleaseDate: z.string().nullable(),
  recommendationType: z.enum(["PRODUCTION", "PURCHASE", "OUTSOURCE"]),
  decisionStatus: z.enum(["PROPOSED", "APPROVED", "REJECTED", "CONVERTED"]), decisionComment: z.string().nullable(),
  decidedAt: z.string().datetime().nullable(), convertedOrderType: z.enum(["PURCHASE_ORDER", "PRODUCTION_ORDER"]).nullable(),
  convertedOrderId: z.string().uuid().nullable(), convertedOrderNumber: z.string().nullable(), convertedAt: z.string().datetime().nullable(),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(),
});
export const mrpSuggestionPageSchema = pageEnvelope(mrpSuggestionSchema);

export const bomMaterialReferenceSchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  specification: z.string().nullable(),
  baseUnit: z.string(),
  procurementType: z.enum(["MAKE", "BUY", "OUTSOURCE"]),
});

export const bomLineSchema = z.object({
  id: z.string().uuid(),
  lineNumber: z.number().int().positive(),
  componentMaterialId: z.string().uuid(),
  componentMaterialCode: z.string(),
  componentMaterialName: z.string(),
  componentMaterialSpecification: z.string().nullable(),
  unit: z.string(),
  quantity: z.number().positive(),
  scrapRate: z.number().min(0).lt(1),
  note: z.string().nullable(),
});

export const bomEventSchema = z.object({
  id: z.string().uuid(),
  action: z.enum(["CREATED", "UPDATED", "PUBLISHED", "INACTIVATED"]),
  fromStatus: z.enum(["DRAFT", "PUBLISHED", "INACTIVE"]).nullable(),
  toStatus: z.enum(["DRAFT", "PUBLISHED", "INACTIVE"]),
  requestId: z.string().nullable(),
  details: z.record(z.string(), z.unknown()),
  occurredAt: z.string().datetime(),
});

export const bomRecordSchema = z.object({
  id: z.string().uuid(),
  bomNumber: z.string(),
  parentMaterialId: z.string().uuid(),
  parentMaterialCode: z.string(),
  parentMaterialName: z.string(),
  parentMaterialSpecification: z.string().nullable(),
  parentUnit: z.string(),
  usageType: z.enum(["PRODUCTION"]),
  versionCode: z.string(),
  baseQuantity: z.number().positive(),
  effectiveFrom: z.string(),
  effectiveTo: z.string().nullable(),
  owner: z.string(),
  changeReason: z.string(),
  status: z.enum(["DRAFT", "PUBLISHED", "INACTIVE"]),
  version: z.number().int().nonnegative(),
  updatedAt: z.string().datetime(),
  publishedAt: z.string().datetime().nullable(),
  lines: z.array(bomLineSchema),
  events: z.array(bomEventSchema),
});

export const bomPageSchema = pageEnvelope(bomRecordSchema);

export const bomReferenceDataSchema = z.object({
  parentMaterials: z.array(bomMaterialReferenceSchema),
  componentMaterials: z.array(bomMaterialReferenceSchema),
});

export const routingOperationSchema = z.object({
  id: z.string().uuid(),
  sequenceNumber: z.number().int().positive(),
  operationCode: z.string(),
  operationName: z.string(),
  workCenterCode: z.string(),
  workCenterName: z.string(),
  setupMinutes: z.number().nonnegative(),
  runMinutesPerUnit: z.number().nonnegative(),
  queueMinutes: z.number().nonnegative(),
  inspectionRequired: z.boolean(),
  instructionSummary: z.string().nullable(),
});

export const routingEventSchema = bomEventSchema;

export const routingRecordSchema = z.object({
  id: z.string().uuid(),
  routingNumber: z.string(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  materialSpecification: z.string().nullable(),
  materialUnit: z.string(),
  usageType: z.enum(["PRODUCTION"]),
  versionCode: z.string(),
  baseQuantity: z.number().positive(),
  effectiveFrom: z.string(),
  effectiveTo: z.string().nullable(),
  owner: z.string(),
  changeReason: z.string(),
  status: z.enum(["DRAFT", "PUBLISHED", "INACTIVE"]),
  version: z.number().int().nonnegative(),
  updatedAt: z.string().datetime(),
  publishedAt: z.string().datetime().nullable(),
  operations: z.array(routingOperationSchema),
  events: z.array(routingEventSchema),
});

export const routingPageSchema = pageEnvelope(routingRecordSchema);

export const routingReferenceDataSchema = z.object({
  materials: z.array(bomMaterialReferenceSchema),
});

export const inventoryMovementTypeSchema = z.enum(["RECEIPT", "ISSUE", "RETURN", "ALLOCATE", "DEALLOCATE", "FREEZE", "UNFREEZE"]);
export const inventoryQualityStatusSchema = z.enum(["AVAILABLE", "INSPECTION", "BLOCKED"]);
export const inventoryMovementSchema = z.object({
  id: z.string().uuid(), movementNumber: z.string(), movementType: inventoryMovementTypeSchema,
  quantity: z.number().positive(), reason: z.string(), requestId: z.string(),
  beforeOnHand: z.number().nonnegative(), afterOnHand: z.number().nonnegative(),
  beforeAllocated: z.number().nonnegative(), afterAllocated: z.number().nonnegative(),
  beforeFrozen: z.number().nonnegative(), afterFrozen: z.number().nonnegative(), occurredAt: z.string().datetime(),
});
export const inventoryRecordSchema = z.object({
  id: z.string().uuid(), warehouseId: z.string().uuid(), warehouseCode: z.string(), warehouseName: z.string(),
  locationId: z.string().uuid(), locationCode: z.string(), locationName: z.string(), materialId: z.string().uuid(),
  materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
  lotNumber: z.string(), qualityStatus: inventoryQualityStatusSchema, onHandQuantity: z.number().nonnegative(),
  allocatedQuantity: z.number().nonnegative(), frozenQuantity: z.number().nonnegative(), availableQuantity: z.number().nonnegative(),
  version: z.number().int().nonnegative(), updatedAt: z.string().datetime(), movements: z.array(inventoryMovementSchema),
});
export const inventoryPageSchema = pageEnvelope(inventoryRecordSchema);
export const inventoryReferenceDataSchema = z.object({
  warehouses: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string() })),
  locations: z.array(z.object({ id: z.string().uuid(), warehouseId: z.string().uuid(), code: z.string(), name: z.string(), locationType: z.string() })),
});


export const materialIssueStatusSchema = z.enum(["DRAFT", "PARTIAL", "ISSUED", "CANCELLED"]);

export const materialIssueLineSchema = z.object({
  id: z.string().uuid(),
  lineNumber: z.number().int().positive(),
  componentMaterialId: z.string().uuid(),
  componentMaterialCode: z.string(),
  componentMaterialName: z.string(),
  componentMaterialSpecification: z.string().nullable(),
  unit: z.string(),
  requiredQuantity: z.number().nonnegative(),
  issuedQuantity: z.number().nonnegative(),
  returnedQuantity: z.number().nonnegative(),
  issuableQuantity: z.number().nonnegative(),
  bomNote: z.string().nullable(),
  version: z.number().int().nonnegative(),
});

export const materialReturnLineSchema = z.object({
  id: z.string().uuid(),
  issueLineId: z.string().uuid(),
  lineNumber: z.number().int().positive(),
  componentMaterialId: z.string().uuid(),
  componentMaterialCode: z.string(),
  componentMaterialName: z.string(),
  componentMaterialSpecification: z.string().nullable(),
  unit: z.string(),
  quantity: z.number().positive(),
  reason: z.string().nullable(),
});

export const materialReturnSchema = z.object({
  id: z.string().uuid(),
  returnNumber: z.string(),
  issueId: z.string().uuid(),
  issueNumber: z.string(),
  productionOrderId: z.string().uuid(),
  orderNumber: z.string(),
  warehouseId: z.string().uuid(),
  warehouseCode: z.string(),
  warehouseName: z.string(),
  locationId: z.string().uuid(),
  locationCode: z.string(),
  locationName: z.string(),
  reason: z.string(),
  createdAt: z.string().datetime(),
  lines: z.array(materialReturnLineSchema),
});

export const materialIssueEventSchema = z.object({
  id: z.string().uuid(),
  action: z.string(),
  fromStatus: materialIssueStatusSchema.nullable(),
  toStatus: materialIssueStatusSchema,
  requestId: z.string().nullable(),
  occurredAt: z.string().datetime(),
});

export const materialIssueStockTransactionSchema = z.object({
  id: z.string().uuid(),
  issueLineId: z.string().uuid().nullable(),
  returnLineId: z.string().uuid().nullable(),
  movementType: z.enum(["ISSUE", "RETURN"]),
  componentMaterialCode: z.string(),
  quantity: z.number().positive(),
  warehouseId: z.string().uuid(),
  warehouseCode: z.string(),
  warehouseName: z.string(),
  locationId: z.string().uuid(),
  locationCode: z.string(),
  locationName: z.string(),
  movementId: z.string().uuid(),
  movementNumber: z.string(),
  requestId: z.string(),
  occurredAt: z.string().datetime(),
});

export const materialIssueRecordSchema = z.object({
  id: z.string().uuid(),
  issueNumber: z.string(),
  productionOrderId: z.string().uuid(),
  orderNumber: z.string(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  materialSpecification: z.string().nullable(),
  unit: z.string(),
  plannedQuantity: z.number().positive(),
  warehouseId: z.string().uuid(),
  warehouseCode: z.string(),
  warehouseName: z.string(),
  status: materialIssueStatusSchema,
  cancellationReason: z.string().nullable(),
  version: z.number().int().nonnegative(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  lines: z.array(materialIssueLineSchema),
  returns: z.array(materialReturnSchema),
  events: z.array(materialIssueEventSchema),
  stockTransactions: z.array(materialIssueStockTransactionSchema),
});

export const materialIssuePageSchema = pageEnvelope(materialIssueRecordSchema);
export const materialIssueReferenceDataSchema = z.object({
  productionOrders: z.array(z.object({
    id: z.string().uuid(),
    orderNumber: z.string(),
    materialId: z.string().uuid(),
    materialCode: z.string(),
    materialName: z.string(),
    materialSpecification: z.string().nullable(),
    unit: z.string(),
    plannedQuantity: z.number().positive(),
    plannedStartDate: z.string(),
    workshop: z.string().nullable(),
    owner: z.string().nullable(),
  })),
  warehouses: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string() })),
  locations: z.array(z.object({ id: z.string().uuid(), warehouseId: z.string().uuid(), code: z.string(), name: z.string(), locationType: z.string() })),
});

export type ManufacturingSnapshot = z.infer<typeof manufacturingSnapshotSchema>;
export type FlowStage = z.infer<typeof flowStageSchema>;
export type WorkOrder = z.infer<typeof workOrderSchema>;
export type GlobalSearchItem = z.infer<typeof globalSearchItemSchema>;
export type WorkspaceSummary = z.infer<typeof workspaceSummarySchema>;
export type WorkspaceSession = z.infer<typeof workspaceSessionSchema>;
export type WorkspaceSessionEnvelope = z.infer<typeof workspaceSessionEnvelopeSchema>;
export type CustomerRecord = z.infer<typeof customerRecordSchema>;
export type MaterialRecord = z.infer<typeof materialRecordSchema>;
export type SalesOrderLine = z.infer<typeof salesOrderLineSchema>;
export type SalesOrderRecord = z.infer<typeof salesOrderRecordSchema>;
export type SalesOrderReferenceData = z.infer<typeof salesOrderReferenceDataSchema>;
export type SalesShipmentRecord = z.infer<typeof salesShipmentRecordSchema>;
export type OrderProfitRecord = z.infer<typeof orderProfitRecordSchema>;
export type OrderProfitReferenceData = z.infer<typeof orderProfitReferenceDataSchema>;
export type ReceivableInvoiceRecord = z.infer<typeof receivableInvoiceRecordSchema>;
export type ReceivableReferenceData = z.infer<typeof receivableReferenceDataSchema>;
export type SalesShipmentReferenceData = z.infer<typeof salesShipmentReferenceDataSchema>;
export type PurchaseOrderLine = z.infer<typeof purchaseOrderLineSchema>;
export type PurchaseOrderRecord = z.infer<typeof purchaseOrderRecordSchema>;
export type PurchaseReceiptRecord = z.infer<typeof purchaseReceiptRecordSchema>;
export type PurchaseOrderReferenceData = z.infer<typeof purchaseOrderReferenceDataSchema>;
export type ProductionOrderRecord = z.infer<typeof productionOrderRecordSchema>;
export type ProductionOrderReferenceData = z.infer<typeof productionOrderReferenceDataSchema>;
export type ProductionWorkReportRecord = z.infer<typeof productionWorkReportRecordSchema>;
export type FinalInspectionRecord = z.infer<typeof finalInspectionRecordSchema>;
export type IncomingInspectionRecord = z.infer<typeof incomingInspectionRecordSchema>;
export type MaterialPlanningParameter = z.infer<typeof materialPlanningParameterSchema>;
export type IndependentDemandRecord = z.infer<typeof independentDemandRecordSchema>;
export type PlanningDemandReferenceData = z.infer<typeof planningDemandReferenceDataSchema>;
export type MrpDemandSnapshot = z.infer<typeof mrpDemandSnapshotSchema>;
export type MrpSupplySnapshot = z.infer<typeof mrpSupplySnapshotSchema>;
export type MrpScheduledReceiptSnapshot = z.infer<typeof mrpScheduledReceiptSnapshotSchema>;
export type MrpNetRequirement = z.infer<typeof mrpNetRequirementSchema>;
export type MrpRunException = z.infer<typeof mrpRunExceptionSchema>;
export type MrpRunRecord = z.infer<typeof mrpRunRecordSchema>;
export type MrpSuggestion = z.infer<typeof mrpSuggestionSchema>;
export type BomMaterialReference = z.infer<typeof bomMaterialReferenceSchema>;
export type BomLine = z.infer<typeof bomLineSchema>;
export type BomEvent = z.infer<typeof bomEventSchema>;
export type BomRecord = z.infer<typeof bomRecordSchema>;
export type BomReferenceData = z.infer<typeof bomReferenceDataSchema>;
export type RoutingOperation = z.infer<typeof routingOperationSchema>;
export type RoutingEvent = z.infer<typeof routingEventSchema>;
export type RoutingRecord = z.infer<typeof routingRecordSchema>;
export type RoutingReferenceData = z.infer<typeof routingReferenceDataSchema>;
export type InventoryMovementType = z.infer<typeof inventoryMovementTypeSchema>;
export type InventoryMovement = z.infer<typeof inventoryMovementSchema>;
export type InventoryRecord = z.infer<typeof inventoryRecordSchema>;
export type InventoryReferenceData = z.infer<typeof inventoryReferenceDataSchema>;
export type MaterialIssueStatus = z.infer<typeof materialIssueStatusSchema>;
export type MaterialIssueLine = z.infer<typeof materialIssueLineSchema>;
export type MaterialReturnLine = z.infer<typeof materialReturnLineSchema>;
export type MaterialReturnRecord = z.infer<typeof materialReturnSchema>;
export type MaterialIssueEvent = z.infer<typeof materialIssueEventSchema>;
export type MaterialIssueStockTransaction = z.infer<typeof materialIssueStockTransactionSchema>;
export type MaterialIssueRecord = z.infer<typeof materialIssueRecordSchema>;
export type MaterialIssueReferenceData = z.infer<typeof materialIssueReferenceDataSchema>;


export const operationTaskStatusSchema = z.enum(["PENDING", "IN_PROGRESS", "COMPLETED"]);

export const operationTaskEventSchema = z.object({
  id: z.string().uuid(),
  action: z.enum(["CREATED", "START", "COMPLETE"]),
  fromStatus: operationTaskStatusSchema.nullable(),
  toStatus: operationTaskStatusSchema,
  requestId: z.string().nullable(),
  comment: z.string().nullable(),
  occurredAt: z.string().datetime(),
});

export const operationTaskRecordSchema = z.object({
  id: z.string().uuid(),
  taskNumber: z.string(),
  orderId: z.string().uuid(),
  orderNumber: z.string(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  materialSpecification: z.string().nullable(),
  unit: z.string(),
  plannedQuantity: z.number().positive(),
  workshop: z.string().nullable(),
  routingId: z.string().uuid(),
  routingNumber: z.string(),
  routingVersionCode: z.string(),
  sourceOperationId: z.string().uuid(),
  sequenceNumber: z.number().int().positive(),
  operationCode: z.string(),
  operationName: z.string(),
  workCenterCode: z.string(),
  workCenterName: z.string(),
  setupMinutes: z.number().nonnegative(),
  runMinutesPerUnit: z.number().nonnegative(),
  queueMinutes: z.number().nonnegative(),
  inspectionRequired: z.boolean(),
  instructionSummary: z.string().nullable(),
  status: operationTaskStatusSchema,
  startedAt: z.string().datetime().nullable(),
  completedAt: z.string().datetime().nullable(),
  completedQuantity: z.number().positive().nullable(),
  shiftName: z.string().nullable(),
  operatorName: z.string().nullable(),
  note: z.string().nullable(),
  version: z.number().int().nonnegative(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  events: z.array(operationTaskEventSchema),
});

export const operationTaskPageSchema = pageEnvelope(operationTaskRecordSchema);

export const operationLaborStatusSchema = z.enum(["RECORDED", "APPROVED", "VOIDED"]);
export const operationLaborEventSchema = z.object({
  id: z.string().uuid(),
  action: z.enum(["RECORDED", "APPROVED", "VOIDED"]),
  fromStatus: operationLaborStatusSchema.nullable(),
  toStatus: operationLaborStatusSchema,
  requestId: z.string(),
  comment: z.string().nullable(),
  occurredAt: z.string().datetime(),
});
export const operationLaborEntrySchema = z.object({
  id: z.string().uuid(),
  entryNumber: z.string(),
  taskId: z.string().uuid(),
  taskNumber: z.string(),
  orderId: z.string().uuid(),
  orderNumber: z.string(),
  operationCode: z.string(),
  operationName: z.string(),
  workCenterCode: z.string(),
  workCenterName: z.string(),
  workDate: z.string().date(),
  shiftName: z.string(),
  operatorName: z.string(),
  actualMinutes: z.number().positive().max(1440),
  status: operationLaborStatusSchema,
  note: z.string().nullable(),
  approvedBy: z.string().uuid().nullable(),
  approvedAt: z.string().datetime().nullable(),
  voidedBy: z.string().uuid().nullable(),
  voidedAt: z.string().datetime().nullable(),
  voidReason: z.string().nullable(),
  version: z.number().int().nonnegative(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  events: z.array(operationLaborEventSchema),
});
export const operationLaborEntryPageSchema = pageEnvelope(operationLaborEntrySchema);

export type OperationTaskStatus = z.infer<typeof operationTaskStatusSchema>;
export type OperationTaskEvent = z.infer<typeof operationTaskEventSchema>;
export type OperationTaskRecord = z.infer<typeof operationTaskRecordSchema>;
export type OperationLaborStatus = z.infer<typeof operationLaborStatusSchema>;
export type OperationLaborEvent = z.infer<typeof operationLaborEventSchema>;
export type OperationLaborEntry = z.infer<typeof operationLaborEntrySchema>;

