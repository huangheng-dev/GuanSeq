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

export const workspaceRoleCodeSchema = z.enum([
  "ADMIN",
  "SALES_MANAGER",
  "PLANNING_MANAGER",
  "PROCUREMENT_MANAGER",
  "PRODUCT_ENGINEER",
  "PRODUCTION_MANAGER",
  "PRODUCTION_OPERATOR",
  "MAINTENANCE_MANAGER",
  "QUALITY_MANAGER",
  "QUALITY_INSPECTOR",
  "WAREHOUSE_MANAGER",
  "INVENTORY_CONTROLLER",
  "FINANCE_MANAGER",
]);

export const workspaceRoleSchema = z.object({
  code: workspaceRoleCodeSchema,
  name: z.string(),
  description: z.string(),
});

export const workspacePermissionRiskSchema = z.enum(["STANDARD", "SENSITIVE", "CRITICAL"]);

export const workspacePermissionSchema = z.object({
  code: z.string().min(1),
  name: z.string().min(1),
  description: z.string().min(1),
  risk: workspacePermissionRiskSchema,
  roleCodes: z.array(workspaceRoleCodeSchema).min(1),
});

export const workspacePermissionGroupSchema = z.object({
  moduleCode: z.string().min(1),
  moduleName: z.string().min(1),
  permissions: z.array(workspacePermissionSchema).min(1),
});

export const workspaceRolePermissionPageSchema = z.object({
  workspaceId: z.string().uuid(),
  workspaceCode: z.string().min(1),
  workspaceName: z.string().min(1),
  companyName: z.string().min(1),
  catalogVersion: z.string().min(1),
  scopeDescription: z.string().min(1),
  roles: z.array(workspaceRoleSchema).min(1),
  groups: z.array(workspacePermissionGroupSchema).min(1),
});

export const workspaceUserSchema = z.object({
  userId: z.string().uuid(),
  username: z.string(),
  displayName: z.string(),
  accountStatus: z.enum(["ACTIVE", "LOCKED", "INACTIVE"]),
  membershipId: z.string().uuid(),
  membershipStatus: z.enum(["ACTIVE", "INACTIVE"]),
  roleCode: workspaceRoleCodeSchema,
  userVersion: z.number().int().nonnegative(),
  membershipVersion: z.number().int().nonnegative(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});

export const workspaceUserPageSchema = z.object({
  currentUserId: z.string().uuid(),
  workspaceId: z.string().uuid(),
  workspaceCode: z.string(),
  workspaceName: z.string(),
  companyName: z.string(),
  availableRoles: z.array(workspaceRoleSchema),
  items: z.array(workspaceUserSchema),
  totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalPages: z.number().int().nonnegative(),
});

export const organizationUnitSchema = z.object({
  id: z.string().uuid(), code: z.string(), name: z.string(), unitType: z.enum(["COMPANY", "PLANT", "SITE"]),
  parentId: z.string().uuid().nullable(), status: z.enum(["ACTIVE", "INACTIVE"]),
  responsibleUserId: z.string().uuid().nullable(), responsibleUserName: z.string().nullable(),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), updatedAt: z.string().datetime(),
});
export const organizationWorkspaceSchema = z.object({
  id: z.string().uuid(), code: z.string(), name: z.string(), status: z.enum(["ACTIVE", "INACTIVE"]),
  operatingOrganizationId: z.string().uuid(), responsibleUserId: z.string().uuid().nullable(),
  responsibleUserName: z.string().nullable(), version: z.number().int().nonnegative(),
  createdAt: z.string().datetime(), updatedAt: z.string().datetime(),
});
export const organizationMemberSchema = z.object({
  userId: z.string().uuid(), username: z.string(), displayName: z.string(), roleCode: workspaceRoleCodeSchema,
  membershipStatus: z.enum(["ACTIVE", "INACTIVE"]), organizationUnitId: z.string().uuid(),
  organizationUnitName: z.string(), membershipVersion: z.number().int().nonnegative(),
});
export const organizationStructurePageSchema = z.object({
  currentUserId: z.string().uuid(), company: organizationUnitSchema, operatingUnit: organizationUnitSchema,
  siteUnits: z.array(organizationUnitSchema), workspace: organizationWorkspaceSchema,
  members: z.array(organizationMemberSchema), scopeDescription: z.string().min(1),
});

export const equipmentAssetCategorySchema = z.enum(["PRODUCTION", "QUALITY", "UTILITY", "LOGISTICS", "OTHER"]);
export const equipmentOperatingStatusSchema = z.enum(["IDLE", "RUNNING", "DOWN", "MAINTENANCE", "INACTIVE"]);
export const equipmentAssetActionSchema = z.enum(["START", "STOP", "REPORT_BREAKDOWN", "START_MAINTENANCE", "COMPLETE_MAINTENANCE", "INACTIVATE"]);
export const equipmentAssetEventSchema = z.object({
  id: z.string().uuid(),
  actorUserId: z.string().uuid(),
  action: z.enum(["CREATED", "UPDATED", "STARTED", "STOPPED", "BREAKDOWN_REPORTED", "MAINTENANCE_STARTED", "MAINTENANCE_COMPLETED", "INACTIVATED"]),
  fromStatus: equipmentOperatingStatusSchema.nullable(),
  toStatus: equipmentOperatingStatusSchema,
  reason: z.string(),
  requestId: z.string(),
  details: z.record(z.string(), z.unknown()),
  occurredAt: z.string().datetime(),
});
export const equipmentAssetSchema = z.object({
  id: z.string().uuid(),
  assetCode: z.string(),
  assetName: z.string(),
  category: equipmentAssetCategorySchema,
  manufacturer: z.string().nullable(),
  model: z.string().nullable(),
  serialNumber: z.string().nullable(),
  workCenterCode: z.string().nullable(),
  workCenterName: z.string().nullable(),
  location: z.string(),
  responsiblePerson: z.string(),
  commissioningDate: z.string().date().nullable(),
  operatingStatus: equipmentOperatingStatusSchema,
  statusChangedAt: z.string().datetime(),
  version: z.number().int().nonnegative(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  events: z.array(equipmentAssetEventSchema),
});
export const equipmentAssetPageSchema = z.object({
  items: z.array(equipmentAssetSchema),
  totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalPages: z.number().int().nonnegative(),
  canMaintain: z.boolean(),
});

export const equipmentTelemetryProtocolSchema = z.enum(["MODBUS_TCP", "MQTT_3_1_1"]);
export const equipmentTelemetryEndpointTypeSchema = z.enum(["SIMULATOR", "PHYSICAL_DEVICE", "EXTERNAL_BROKER"]);
export const equipmentTelemetryConnectionStatusSchema = z.enum(["DRAFT", "ACTIVE", "PAUSED"]);
export const equipmentTelemetryCommunicationStatusSchema = z.enum(["UNKNOWN", "ONLINE", "OFFLINE"]);
export const equipmentTelemetryRegisterTypeSchema = z.enum(["COIL", "HOLDING_REGISTER", "MQTT_JSON"]);
export const equipmentTelemetryValueTypeSchema = z.enum(["BOOLEAN", "UINT16", "INT16", "UINT32", "INT32", "DECIMAL"]);
export const equipmentTelemetryQualitySchema = z.enum(["GOOD", "UNCERTAIN", "BAD"]);
export const equipmentTelemetryPointSchema = z.object({
  id: z.string().uuid(), pointCode: z.string(), name: z.string(), registerType: equipmentTelemetryRegisterTypeSchema,
  address: z.number().int().min(0).max(65535), valueType: equipmentTelemetryValueTypeSchema,
  mqttTopic: z.string().nullable(), mqttValuePointer: z.string().nullable(),
  scale: z.number(), valueOffset: z.number(), engineeringUnit: z.string().nullable(),
  validMin: z.number().nullable(), validMax: z.number().nullable(), sortOrder: z.number().int().positive(),
});
export const equipmentTelemetryCurrentValueSchema = z.object({
  pointId: z.string().uuid(), pointCode: z.string(), rawValue: z.string(), numericValue: z.number().nullable(),
  booleanValue: z.boolean().nullable(), quality: equipmentTelemetryQualitySchema, deviceTime: z.string().datetime().nullable(),
  receivedAt: z.string().datetime(), sequenceNumber: z.number().int().positive(), messageVersion: z.number().int().positive(),
  sourceProtocol: z.string(),
});
export const equipmentTelemetryVerificationCheckSchema = z.object({
  code: z.string(), status: z.enum(["PASSED", "WARNING", "FAILED", "INFO"]), message: z.string(),
});
export const equipmentTelemetryVerificationSchema = z.object({
  verificationVersion: z.number().int().positive(),
  evidenceLevel: z.enum(["SIMULATION_TECHNICAL", "FIELD_CANDIDATE_PRECHECK"]),
  technicalPassed: z.boolean(), fieldAccepted: z.literal(false), protocol: equipmentTelemetryProtocolSchema,
  endpointType: equipmentTelemetryEndpointTypeSchema, pointCount: z.number().int().nonnegative(),
  returnedPointCount: z.number().int().nonnegative(), warningCount: z.number().int().nonnegative(),
  checks: z.array(equipmentTelemetryVerificationCheckSchema), pendingFieldChecks: z.array(z.string()),
  errorCode: z.string().nullable(), errorMessage: z.string().nullable(),
});
export const equipmentTelemetryEventSchema = z.object({
  id: z.string().uuid(), actorUserId: z.string().uuid(),
  action: z.enum(["CREATED", "TEST_SUCCEEDED", "TEST_FAILED", "ACTIVATED", "PAUSED", "POLL_REQUESTED"]),
  fromStatus: equipmentTelemetryConnectionStatusSchema.nullable(), toStatus: equipmentTelemetryConnectionStatusSchema,
  reason: z.string(), requestId: z.string(), details: z.record(z.string(), z.unknown()),
  verification: equipmentTelemetryVerificationSchema.nullable(), occurredAt: z.string().datetime(),
});
export const equipmentTelemetryConnectionSchema = z.object({
  id: z.string().uuid(), connectionCode: z.string(), name: z.string(), assetId: z.string().uuid(),
  assetCode: z.string(), assetName: z.string(), protocol: equipmentTelemetryProtocolSchema,
  endpointType: equipmentTelemetryEndpointTypeSchema, host: z.string().nullable(), port: z.number().int(),
  unitId: z.number().int(), connectTimeoutMs: z.number().int(), readTimeoutMs: z.number().int(),
  mqtt: z.object({ transport: z.enum(["TCP", "TLS"]), clientId: z.string(), qos: z.number().int().min(0).max(1),
    credentialReference: z.string().nullable(), credentialConfigured: z.boolean(), messageIdPointer: z.string(),
    deviceTimePointer: z.string().nullable() }).nullable(),
  pollIntervalSeconds: z.number().int(), status: equipmentTelemetryConnectionStatusSchema,
  communicationStatus: equipmentTelemetryCommunicationStatusSchema,
  lastTestedAt: z.string().datetime().nullable(), lastTestSucceededAt: z.string().datetime().nullable(),
  lastAttemptAt: z.string().datetime().nullable(), lastSuccessAt: z.string().datetime().nullable(),
  lastErrorCode: z.string().nullable(), lastErrorMessage: z.string().nullable(), version: z.number().int().nonnegative(),
  canManage: z.boolean(), points: z.array(equipmentTelemetryPointSchema), currentValues: z.array(equipmentTelemetryCurrentValueSchema),
  events: z.array(equipmentTelemetryEventSchema), createdAt: z.string().datetime(), updatedAt: z.string().datetime(),
});
export const equipmentTelemetryConnectionPageSchema = z.object({
  items: z.array(equipmentTelemetryConnectionSchema), totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(), size: z.number().int().positive(), totalPages: z.number().int().nonnegative(),
  canManage: z.boolean(),
});
export const equipmentTelemetryActionResultSchema = z.object({
  success: z.boolean(), message: z.string(), connection: equipmentTelemetryConnectionSchema,
});
export const equipmentTelemetryFieldAcceptanceStatusSchema = z.enum(["DRAFT", "SUBMITTED", "APPROVED", "REJECTED"]);
export const equipmentTelemetryFieldAcceptanceActionSchema = z.enum(["UPDATE", "SUBMIT", "APPROVE", "REJECT"]);
export const equipmentTelemetryFieldAcceptanceEventSchema = z.object({
  id: z.string().uuid(), actorUserId: z.string().uuid(),
  action: z.enum(["CREATED", "UPDATED", "SUBMITTED", "APPROVED", "REJECTED"]),
  fromStatus: equipmentTelemetryFieldAcceptanceStatusSchema.nullable(),
  toStatus: equipmentTelemetryFieldAcceptanceStatusSchema, reason: z.string(), requestId: z.string(),
  details: z.record(z.string(), z.unknown()), occurredAt: z.string().datetime(),
});
export const equipmentTelemetryFieldAcceptanceSchema = z.object({
  id: z.string().uuid(), acceptanceNumber: z.string(), connectionId: z.string().uuid(),
  status: equipmentTelemetryFieldAcceptanceStatusSchema,
  networkApproved: z.boolean(), securityValidated: z.boolean(), readOnlyConfirmed: z.boolean(),
  disconnectRecoveryVerified: z.boolean(), capacityVerified: z.boolean(), pointMappingApproved: z.boolean(),
  responsibleOwner: z.string().nullable(), testWindowStart: z.string().datetime().nullable(),
  testWindowEnd: z.string().datetime().nullable(), evidenceReference: z.string().nullable(), notes: z.string().nullable(),
  rejectionReason: z.string().nullable(), version: z.number().int().nonnegative(), createdBy: z.string().uuid(),
  createdAt: z.string().datetime(), submittedBy: z.string().uuid().nullable(), submittedAt: z.string().datetime().nullable(),
  approvedBy: z.string().uuid().nullable(), approvedAt: z.string().datetime().nullable(),
  rejectedBy: z.string().uuid().nullable(), rejectedAt: z.string().datetime().nullable(),
  updatedAt: z.string().datetime(), availableActions: z.array(equipmentTelemetryFieldAcceptanceActionSchema),
  events: z.array(equipmentTelemetryFieldAcceptanceEventSchema),
});
export const equipmentTelemetryFieldAcceptanceContextSchema = z.object({
  connectionId: z.string().uuid(), connectionCode: z.string(), connectionName: z.string(),
  protocol: equipmentTelemetryProtocolSchema, endpointType: equipmentTelemetryEndpointTypeSchema,
  fieldEligible: z.boolean(), latestTechnicalPrecheckPassed: z.boolean(), fieldAccepted: z.boolean(),
  canMaintain: z.boolean(), canApprove: z.boolean(), acceptance: equipmentTelemetryFieldAcceptanceSchema.nullable(),
});
export const equipmentAlertRuleTypeSchema = z.enum(["HIGH_LIMIT", "LOW_LIMIT", "COMMUNICATION_FAILURE"]);
export const equipmentAlertSeveritySchema = z.enum(["WARNING", "CRITICAL"]);
export const equipmentAlertRuleStatusSchema = z.enum(["ACTIVE", "PAUSED"]);
export const equipmentAlertStatusSchema = z.enum(["OPEN", "ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED", "CLOSED"]);
export const equipmentAlertRuleEventSchema = z.object({
  id: z.string().uuid(), actorUserId: z.string().uuid(), action: z.enum(["CREATED", "ACTIVATED", "PAUSED"]),
  fromStatus: equipmentAlertRuleStatusSchema.nullable(), toStatus: equipmentAlertRuleStatusSchema,
  reason: z.string(), requestId: z.string(), details: z.record(z.string(), z.unknown()), occurredAt: z.string().datetime(),
});
export const equipmentAlertRuleSchema = z.object({
  id: z.string().uuid(), ruleCode: z.string(), name: z.string(), connectionId: z.string().uuid(),
  connectionCode: z.string(), connectionName: z.string(), assetId: z.string().uuid(), assetCode: z.string(), assetName: z.string(),
  pointId: z.string().uuid().nullable(), pointCode: z.string().nullable(), pointName: z.string().nullable(),
  ruleType: equipmentAlertRuleTypeSchema, thresholdValue: z.number().nullable(), severity: equipmentAlertSeveritySchema,
  defaultAssignee: z.string(), status: equipmentAlertRuleStatusSchema, version: z.number().int().nonnegative(),
  createdAt: z.string().datetime(), updatedAt: z.string().datetime(),
  availableActions: z.array(z.enum(["ACTIVATE", "PAUSE"])), events: z.array(equipmentAlertRuleEventSchema),
});
export const equipmentAlertRulePageSchema = z.object({
  items: z.array(equipmentAlertRuleSchema), totalElements: z.number().int().nonnegative(), page: z.number().int().nonnegative(),
  size: z.number().int().positive(), totalPages: z.number().int().nonnegative(), canManage: z.boolean(),
});
export const equipmentAlertEventSchema = z.object({
  id: z.string().uuid(), actorUserId: z.string().uuid().nullable(),
  action: z.enum(["OCCURRED", "REOPENED", "CONDITION_CLEARED", "ACKNOWLEDGED", "PROCESSING_STARTED", "RESOLVED", "CLOSED", "REPAIR_LINKED"]),
  fromStatus: equipmentAlertStatusSchema.nullable(), toStatus: equipmentAlertStatusSchema, reason: z.string(),
  requestId: z.string(), details: z.record(z.string(), z.unknown()), occurredAt: z.string().datetime(),
});
export const equipmentAlertSchema = z.object({
  id: z.string().uuid(), alertNumber: z.string(), ruleId: z.string().uuid(), ruleCode: z.string(), ruleName: z.string(),
  assetId: z.string().uuid(), assetCode: z.string(), assetName: z.string(), connectionId: z.string().uuid(), connectionCode: z.string(),
  pointId: z.string().uuid().nullable(), pointCode: z.string().nullable(), pointName: z.string().nullable(),
  ruleType: equipmentAlertRuleTypeSchema, severity: equipmentAlertSeveritySchema, status: equipmentAlertStatusSchema,
  conditionActive: z.boolean(), observedValue: z.number().nullable(), observedQuality: equipmentTelemetryQualitySchema.nullable(),
  failureCode: z.string().nullable(), assignee: z.string(), resolutionNotes: z.string().nullable(),
  linkedWorkOrderId: z.string().uuid().nullable(), linkedWorkOrderNumber: z.string().nullable(), version: z.number().int().nonnegative(),
  firstOccurredAt: z.string().datetime(), lastOccurredAt: z.string().datetime(), recoveredAt: z.string().datetime().nullable(),
  acknowledgedAt: z.string().datetime().nullable(), processingStartedAt: z.string().datetime().nullable(),
  resolvedAt: z.string().datetime().nullable(), closedAt: z.string().datetime().nullable(), updatedAt: z.string().datetime(),
  availableActions: z.array(z.enum(["ACKNOWLEDGE", "START_PROCESSING", "RESOLVE", "CLOSE", "LINK_REPAIR"])),
  events: z.array(equipmentAlertEventSchema),
});
export const equipmentAlertPageSchema = z.object({
  items: z.array(equipmentAlertSchema), totalElements: z.number().int().nonnegative(), page: z.number().int().nonnegative(),
  size: z.number().int().positive(), totalPages: z.number().int().nonnegative(),
  activeConditionCount: z.number().int().nonnegative(), unclosedCount: z.number().int().nonnegative(), canManage: z.boolean(),
});
export const equipmentOeeStatusSchema = z.enum(["DRAFT", "SUBMITTED", "APPROVED", "REJECTED"]);
export const equipmentOeeDowntimeCategorySchema = z.enum(["EQUIPMENT_FAILURE", "SETUP_CHANGEOVER", "MATERIAL_WAIT",
  "QUALITY_HOLD", "PERSONNEL_WAIT", "PLANNED_MAINTENANCE", "OTHER"]);
export const equipmentOeeActionSchema = z.enum(["UPDATE", "ADD_DOWNTIME", "UPDATE_DOWNTIME", "REMOVE_DOWNTIME",
  "SUBMIT", "APPROVE", "REJECT"]);
export const equipmentOeeDowntimeSchema = z.object({
  id: z.string().uuid(), startedAt: z.string().datetime(), endedAt: z.string().datetime(),
  durationMinutes: z.number().positive(), reasonCategory: equipmentOeeDowntimeCategorySchema,
  responsibleParty: z.string(), description: z.string(), createdBy: z.string().uuid(), createdAt: z.string().datetime(),
  updatedBy: z.string().uuid(), updatedAt: z.string().datetime(),
});
export const equipmentOeeEventSchema = z.object({
  id: z.string().uuid(), actorUserId: z.string().uuid(),
  action: z.enum(["CREATED", "UPDATED", "DOWNTIME_ADDED", "DOWNTIME_UPDATED", "DOWNTIME_REMOVED",
    "SUBMITTED", "APPROVED", "REJECTED"]),
  fromStatus: equipmentOeeStatusSchema.nullable(), toStatus: equipmentOeeStatusSchema, reason: z.string(),
  requestId: z.string(), details: z.record(z.string(), z.unknown()), occurredAt: z.string().datetime(),
});
export const equipmentOeeRecordSchema = z.object({
  id: z.string().uuid(), recordNumber: z.string(), assetId: z.string().uuid(), assetCode: z.string(), assetName: z.string(),
  workCenterCode: z.string().nullable(), workCenterName: z.string().nullable(), location: z.string(),
  windowStart: z.string().datetime(), windowEnd: z.string().datetime(), plannedProductionMinutes: z.number().positive(),
  downtimeMinutes: z.number().nonnegative(), runMinutes: z.number().nonnegative(), idealCycleSeconds: z.number().positive(),
  totalCount: z.number().int().nonnegative(), goodCount: z.number().int().nonnegative(),
  availabilityRate: z.number().nonnegative(), performanceRate: z.number().nonnegative(), qualityRate: z.number().nonnegative(),
  oeeRate: z.number().nonnegative(), shiftName: z.string(), productionReference: z.string().nullable(),
  sourceType: z.literal("MANUAL_VERIFIED"), sourceReference: z.string().nullable(), status: equipmentOeeStatusSchema,
  rejectionReason: z.string().nullable(), version: z.number().int().nonnegative(), createdBy: z.string().uuid(),
  createdAt: z.string().datetime(), submittedBy: z.string().uuid().nullable(), submittedAt: z.string().datetime().nullable(),
  approvedBy: z.string().uuid().nullable(), approvedAt: z.string().datetime().nullable(),
  rejectedBy: z.string().uuid().nullable(), rejectedAt: z.string().datetime().nullable(), updatedAt: z.string().datetime(),
  availableActions: z.array(equipmentOeeActionSchema), downtimes: z.array(equipmentOeeDowntimeSchema),
  events: z.array(equipmentOeeEventSchema),
});
export const equipmentOeePageSchema = z.object({
  items: z.array(equipmentOeeRecordSchema), totalElements: z.number().int().nonnegative(), page: z.number().int().nonnegative(),
  size: z.number().int().positive(), totalPages: z.number().int().nonnegative(), approvedRecordCount: z.number().int().nonnegative(),
  averageAvailabilityRate: z.number().nonnegative(), averagePerformanceRate: z.number().nonnegative(),
  averageQualityRate: z.number().nonnegative(), averageOeeRate: z.number().nonnegative(),
  canMaintain: z.boolean(), canApprove: z.boolean(),
});
export const equipmentTelemetrySampleSchema = z.object({
  id: z.string().uuid(), pointId: z.string().uuid(), pointCode: z.string(), rawValue: z.string(),
  numericValue: z.number().nullable(), booleanValue: z.boolean().nullable(), quality: equipmentTelemetryQualitySchema,
  deviceTime: z.string().datetime().nullable(), receivedAt: z.string().datetime(),
  sequenceNumber: z.number().int().positive(), messageVersion: z.number().int().positive(), sourceProtocol: z.string(),
});
export const equipmentTelemetrySamplePageSchema = z.object({
  items: z.array(equipmentTelemetrySampleSchema), totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(), size: z.number().int().positive(), totalPages: z.number().int().nonnegative(),
  connectionId: z.string().uuid(), windowFrom: z.string().datetime(), windowTo: z.string().datetime(),
});
export const equipmentTelemetryRetentionEventSchema = z.object({
  id: z.string().uuid(), actorUserId: z.string().uuid(), action: z.enum(["POLICY_UPDATED", "CLEANUP_COMPLETED"]),
  fromRetentionDays: z.number().int().min(7).max(3650), toRetentionDays: z.number().int().min(7).max(3650),
  fromAutomaticCleanupEnabled: z.boolean().nullable(), toAutomaticCleanupEnabled: z.boolean().nullable(),
  fromCleanupIntervalHours: z.number().int().min(1).max(720).nullable(),
  toCleanupIntervalHours: z.number().int().min(1).max(720).nullable(),
  cutoffAt: z.string().datetime().nullable(), deletedSampleCount: z.number().int().nonnegative(),
  reason: z.string(), requestId: z.string(), occurredAt: z.string().datetime(),
});
export const equipmentTelemetryAutomationRunSchema = z.object({
  id: z.string().uuid(), triggerType: z.enum(["SCHEDULED", "USER_RETRY"]),
  status: z.enum(["SUCCEEDED", "PARTIAL", "FAILED"]), initiatedBy: z.string().uuid().nullable(),
  instanceId: z.string(), requestId: z.string(), reason: z.string(), cutoffAt: z.string().datetime(),
  deletedSampleCount: z.number().int().nonnegative(), remainingExpiredCount: z.number().int().nonnegative(),
  failureCode: z.string().nullable(), failureSummary: z.string().nullable(),
  attentionStatus: z.enum(["NONE", "OPEN", "ACKNOWLEDGED"]),
  responsibleRoles: z.array(z.enum(["ADMIN", "MAINTENANCE_MANAGER"])),
  acknowledgedBy: z.string().uuid().nullable(), acknowledgedAt: z.string().datetime().nullable(),
  acknowledgementNote: z.string().nullable(), startedAt: z.string().datetime(), completedAt: z.string().datetime(),
});
export const equipmentTelemetryRetentionPolicySchema = z.object({
  id: z.string().uuid().nullable(), retentionDays: z.number().int().min(7).max(3650),
  expiredSampleCount: z.number().int().nonnegative(), cutoffAt: z.string().datetime(),
  version: z.number().int().nonnegative(), defaultPolicy: z.boolean(), canManage: z.boolean(),
  schedulerAvailable: z.boolean(), automaticCleanupEnabled: z.boolean(),
  cleanupIntervalHours: z.number().int().min(1).max(720), nextCleanupAt: z.string().datetime().nullable(),
  lastAutomationStatus: z.enum(["SUCCEEDED", "PARTIAL", "FAILED"]).nullable(),
  lastAutomationCompletedAt: z.string().datetime().nullable(), consecutiveFailures: z.number().int().nonnegative(),
  updatedBy: z.string().uuid().nullable(), updatedAt: z.string().datetime().nullable(),
  events: z.array(equipmentTelemetryRetentionEventSchema), automationRuns: z.array(equipmentTelemetryAutomationRunSchema),
});
export const equipmentTelemetryCleanupResultSchema = z.object({
  policy: equipmentTelemetryRetentionPolicySchema, deletedSampleCount: z.number().int().nonnegative(),
  cutoffAt: z.string().datetime(), requestId: z.string(), occurredAt: z.string().datetime(), replayed: z.boolean(),
});
export const equipmentTelemetryAutomationActionResultSchema = z.object({
  policy: equipmentTelemetryRetentionPolicySchema, run: equipmentTelemetryAutomationRunSchema, replayed: z.boolean(),
});

export const equipmentWorkTypeSchema = z.enum(["INSPECTION", "PREVENTIVE_MAINTENANCE", "REPAIR"]);
export const equipmentWorkOrderSourceSchema = z.enum(["MANUAL", "BREAKDOWN", "INSPECTION_FAILURE", "MAINTENANCE_FAILURE", "MAINTENANCE_PLAN"]);
export const equipmentWorkOrderPrioritySchema = z.enum(["LOW", "MEDIUM", "HIGH", "URGENT"]);
export const equipmentWorkOrderStatusSchema = z.enum(["PLANNED", "IN_PROGRESS", "WAITING_ACCEPTANCE", "COMPLETED", "CANCELLED"]);
export const equipmentWorkOrderOutcomeSchema = z.enum(["PASS", "FAIL"]);
export const equipmentWorkOrderActionSchema = z.enum(["START", "COMPLETE", "SUBMIT_FOR_ACCEPTANCE", "ACCEPT", "REJECT", "CANCEL"]);
export const equipmentSpareTransactionSchema = z.object({
  id: z.string().uuid(), transactionType: z.enum(["ISSUE", "RETURN"]), returnOfIssueId: z.string().uuid().nullable(),
  sparePartId: z.string().uuid(), materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(),
  unit: z.string(), quantity: z.number().positive(), returnedQuantity: z.number().nonnegative(), returnableQuantity: z.number().nonnegative(),
  unitCost: z.number().positive(), currency: z.string(), amount: z.number().positive(), warehouseId: z.string().uuid(),
  warehouseCode: z.string(), warehouseName: z.string(), warehouseEvidence: z.array(z.record(z.string(), z.unknown())),
  reason: z.string(), requestId: z.string(), actorUserId: z.string().uuid(), occurredAt: z.string().datetime(),
});
export const equipmentLaborTransactionSchema = z.object({
  id: z.string().uuid(), transactionType: z.enum(["ENTRY", "REVERSAL"]), reversalOfEntryId: z.string().uuid().nullable(),
  technicianName: z.string(), hours: z.number().positive(), hourlyRate: z.number().positive(), currency: z.string(),
  amount: z.number().positive(), reversed: z.boolean(), reason: z.string(), requestId: z.string(),
  actorUserId: z.string().uuid(), occurredAt: z.string().datetime(),
});
export const equipmentMaintenanceCostEvidenceSchema = z.object({
  spareCost: z.number(), laborCost: z.number(), totalCost: z.number(), currency: z.string(), basis: z.string(),
  spareTransactions: z.array(equipmentSpareTransactionSchema), laborTransactions: z.array(equipmentLaborTransactionSchema),
  availableActions: z.array(z.enum(["ISSUE_SPARE", "RETURN_SPARE", "RECORD_LABOR", "REVERSE_LABOR"])),
});
export const equipmentWorkOrderEventSchema = z.object({
  id: z.string().uuid(), actorUserId: z.string().uuid(),
  action: z.enum(["CREATED", "STARTED", "EXECUTION_COMPLETED", "SUBMITTED_FOR_ACCEPTANCE", "ACCEPTED", "REJECTED", "CANCELLED", "REPAIR_GENERATED"]),
  fromStatus: equipmentWorkOrderStatusSchema.nullable(), toStatus: equipmentWorkOrderStatusSchema,
  reason: z.string(), outcome: equipmentWorkOrderOutcomeSchema.nullable(), requestId: z.string(),
  details: z.record(z.string(), z.unknown()), occurredAt: z.string().datetime(),
});
export const equipmentWorkOrderSchema = z.object({
  id: z.string().uuid(), workOrderNumber: z.string(), workType: equipmentWorkTypeSchema,
  sourceType: equipmentWorkOrderSourceSchema, sourceWorkOrderId: z.string().uuid().nullable(),
  sourcePlanId: z.string().uuid().nullable(), sourceDueDate: z.string().date().nullable(),
  assetId: z.string().uuid(), assetCode: z.string(), assetName: z.string(), assetLocation: z.string(),
  assetOperatingStatus: equipmentOperatingStatusSchema, assetVersion: z.number().int().nonnegative(),
  title: z.string(), description: z.string(), priority: equipmentWorkOrderPrioritySchema,
  status: equipmentWorkOrderStatusSchema, plannedStartAt: z.string().datetime(), dueAt: z.string().datetime(),
  assignee: z.string(), outcome: equipmentWorkOrderOutcomeSchema.nullable(), completionNotes: z.string().nullable(),
  startedAt: z.string().datetime().nullable(), submittedAt: z.string().datetime().nullable(),
  completedAt: z.string().datetime().nullable(), version: z.number().int().nonnegative(),
  createdAt: z.string().datetime(), updatedAt: z.string().datetime(),
  costEvidence: equipmentMaintenanceCostEvidenceSchema.nullable(),
  availableActions: z.array(equipmentWorkOrderActionSchema), events: z.array(equipmentWorkOrderEventSchema),
});
export const equipmentWorkOrderPageSchema = z.object({
  items: z.array(equipmentWorkOrderSchema), totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(), size: z.number().int().positive(), totalPages: z.number().int().nonnegative(),
  canMaintain: z.boolean(),
});

export const equipmentMaintenancePlanStatusSchema = z.enum(["ACTIVE", "INACTIVE"]);
export const equipmentMaintenancePlanActionSchema = z.enum(["ACTIVATE", "INACTIVATE"]);
export const equipmentMaintenancePlanEventSchema = z.object({
  id: z.string().uuid(), actorUserId: z.string().uuid(), action: z.enum(["CREATED", "ACTIVATED", "INACTIVATED"]),
  fromStatus: equipmentMaintenancePlanStatusSchema.nullable(), toStatus: equipmentMaintenancePlanStatusSchema,
  reason: z.string(), requestId: z.string(), details: z.record(z.string(), z.unknown()), occurredAt: z.string().datetime(),
});
export const equipmentMaintenanceGenerationItemSchema = z.object({
  id: z.string().uuid(), planId: z.string().uuid(), dueDate: z.string().date(),
  outcome: z.enum(["GENERATED", "ALREADY_EXISTS", "SKIPPED_INACTIVE_ASSET"]),
  workOrderId: z.string().uuid().nullable(), message: z.string(),
});
export const equipmentMaintenanceGenerationSchema = z.object({
  id: z.string().uuid(), requestId: z.string(), asOfDate: z.string().date(), reason: z.string(),
  status: z.enum(["RUNNING", "COMPLETED"]), generatedCount: z.number().int().nonnegative(),
  existingCount: z.number().int().nonnegative(), skippedCount: z.number().int().nonnegative(),
  actorUserId: z.string().uuid(), startedAt: z.string().datetime(), completedAt: z.string().datetime().nullable(),
  items: z.array(equipmentMaintenanceGenerationItemSchema),
});
export const equipmentMaintenancePlanSchema = z.object({
  id: z.string().uuid(), planCode: z.string(), name: z.string(),
  workType: z.enum(["INSPECTION", "PREVENTIVE_MAINTENANCE"]), assetId: z.string().uuid(),
  assetCode: z.string(), assetName: z.string(), assetLocation: z.string(), description: z.string(),
  priority: equipmentWorkOrderPrioritySchema, intervalDays: z.number().int().min(1).max(3650),
  leadDays: z.number().int().min(0).max(365), firstDueDate: z.string().date(), nextDueDate: z.string().date(),
  nextGenerationDate: z.string().date(), plannedStartTime: z.string(), dueTime: z.string(), assignee: z.string(),
  status: equipmentMaintenancePlanStatusSchema, generationStatus: z.enum(["DUE", "UPCOMING", "INACTIVE"]),
  overdueWorkOrderCount: z.number().int().nonnegative(), overdueWorkOrderNumbers: z.array(z.string()),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), updatedAt: z.string().datetime(),
  availableActions: z.array(equipmentMaintenancePlanActionSchema), events: z.array(equipmentMaintenancePlanEventSchema),
});
export const equipmentMaintenancePlanPageSchema = z.object({
  items: z.array(equipmentMaintenancePlanSchema), totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(), size: z.number().int().positive(), totalPages: z.number().int().nonnegative(),
  activeCount: z.number().int().nonnegative(), generationDueCount: z.number().int().nonnegative(),
  overdueWorkOrderCount: z.number().int().nonnegative(), canMaintain: z.boolean(),
  recentRuns: z.array(equipmentMaintenanceGenerationSchema),
});

export const equipmentSparePartSchema = z.object({
  id: z.string().uuid(), materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(),
  materialSpecification: z.string().nullable(), unit: z.string(), preferredWarehouseId: z.string().uuid(),
  preferredWarehouseCode: z.string(), preferredWarehouseName: z.string(), reorderPoint: z.number().nonnegative(),
  availableQuantity: z.number().nonnegative(), standardUnitCost: z.number().positive().nullable(), currency: z.string().nullable(),
  costEffectiveDate: z.string().date().nullable(), costStatus: z.enum(["READY", "MISSING_COST"]),
  stockStatus: z.enum(["SUFFICIENT", "BELOW_REORDER_POINT"]), status: z.enum(["ACTIVE", "INACTIVE"]),
  version: z.number().int().nonnegative(), updatedAt: z.string().datetime(),
});
export const equipmentSparePartPageSchema = z.object({
  items: z.array(equipmentSparePartSchema), totalElements: z.number().int().nonnegative(), page: z.number().int().nonnegative(),
  size: z.number().int().positive(), totalPages: z.number().int().nonnegative(), canMaintain: z.boolean(),
});
export const equipmentSparePartReferenceSchema = z.object({
  materials: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string(), specification: z.string().nullable(), unit: z.string() })),
  warehouses: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string() })),
  locations: z.array(z.object({ id: z.string().uuid(), warehouseId: z.string().uuid(), code: z.string(), name: z.string(), locationType: z.string() })),
});
export const equipmentMaintenanceCostMutationResultSchema = z.object({
  workOrderVersion: z.number().int().nonnegative(), costEvidence: equipmentMaintenanceCostEvidenceSchema,
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

export const supplierRecordSchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  contactName: z.string().nullable(),
  contactPhone: z.string().nullable(),
  status: z.enum(["ACTIVE", "INACTIVE"]),
  version: z.number().int().nonnegative(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});
export const supplierPageSchema = pageEnvelope(supplierRecordSchema);

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
  returnedQuantity: z.number().nonnegative(),
  netDeliveredQuantity: z.number().nonnegative(),
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
  status: z.enum(["DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED", "RELEASED", "PARTIALLY_SHIPPED", "SHIPPED", "PARTIALLY_RETURNED", "RETURNED"]),
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

export const salesReturnLineSchema = z.object({
  id: z.string().uuid(),
  orderLineId: z.string().uuid(),
  lineNumber: z.number().int().positive(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  materialSpecification: z.string().nullable(),
  unit: z.string(),
  authorizedQuantity: z.number().positive(),
  receivedQuantity: z.number().nonnegative(),
  acceptedQuantity: z.number().nonnegative(),
  rejectedQuantity: z.number().nonnegative(),
  lotNumber: z.string().nullable(),
  inspectionBalanceId: z.string().uuid().nullable(),
  receiptMovementId: z.string().uuid().nullable(),
  stockSummary: z.string().nullable(),
});

export const salesReturnRecordSchema = z.object({
  id: z.string().uuid(), returnNumber: z.string(), salesOrderId: z.string().uuid(), orderNumber: z.string(),
  customerId: z.string().uuid(), customerCode: z.string(), customerName: z.string(), returnDate: z.string(),
  status: z.enum(["PENDING_RECEIPT", "RECEIVED", "COMPLETED", "CANCELLED", "REVERSED"]),
  reason: z.string(), note: z.string().nullable(), warehouseId: z.string().uuid().nullable(), warehouseCode: z.string().nullable(),
  warehouseName: z.string().nullable(), locationId: z.string().uuid().nullable(), locationCode: z.string().nullable(),
  locationName: z.string().nullable(), totalReturnQuantity: z.number().positive(), receivedAt: z.string().datetime().nullable(),
  inspectedAt: z.string().datetime().nullable(), version: z.number().int().nonnegative(), createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(), availableActions: z.array(z.enum(["CANCEL", "RECEIVE", "INSPECT", "REVERSE_RECEIPT"])),
  lines: z.array(salesReturnLineSchema).min(1),
  events: z.array(z.object({ id: z.string().uuid(), action: z.string(), fromStatus: z.string().nullable(), toStatus: z.string(),
    reason: z.string(), requestId: z.string(), occurredAt: z.string().datetime() })),
});

export const salesReturnPageSchema = pageEnvelope(salesReturnRecordSchema).extend({ canCreate: z.boolean() });

export const salesReturnReferenceDataSchema = z.object({
  orders: z.array(z.object({
    id: z.string().uuid(), orderNumber: z.string(), customerId: z.string().uuid(), customerCode: z.string(),
    customerName: z.string(), status: z.string(), version: z.number().int().nonnegative(),
    lines: z.array(z.object({ id: z.string().uuid(), lineNumber: z.number().int().positive(), materialId: z.string().uuid(),
      materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
      grossDeliveredQuantity: z.number().nonnegative(), returnedQuantity: z.number().nonnegative(),
      pendingReturnQuantity: z.number().nonnegative(), netDeliveredQuantity: z.number().nonnegative(),
      returnableQuantity: z.number().positive() })).min(1),
  })),
  warehouses: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string() })),
  locations: z.array(z.object({ id: z.string().uuid(), warehouseId: z.string().uuid(), code: z.string(), name: z.string(), locationType: z.string() })),
  canCreate: z.boolean(),
});

export const purchaseReturnLineSchema = z.object({
  id: z.string().uuid(), purchaseReceiptLineId: z.string().uuid(), purchaseOrderLineId: z.string().uuid(),
  lineNumber: z.number().int().positive(), materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(),
  materialSpecification: z.string().nullable(), unit: z.string(), qualityStatus: z.enum(["AVAILABLE", "BLOCKED"]),
  authorizedQuantity: z.number().positive(), shippedQuantity: z.number().nonnegative(), stockBalanceId: z.string().uuid(),
  stockMovementId: z.string().uuid().nullable(), warehouseCode: z.string().nullable(), locationCode: z.string().nullable(),
  lotNumber: z.string().nullable(),
});

export const purchaseReturnRecordSchema = z.object({
  id: z.string().uuid(), returnNumber: z.string(), purchaseOrderId: z.string().uuid(), orderNumber: z.string(),
  supplierId: z.string().uuid(), supplierCode: z.string(), supplierName: z.string(), returnDate: z.string(),
  status: z.enum(["PENDING_SHIPMENT", "SHIPPED", "CANCELLED", "REVERSED"]), reason: z.string(), note: z.string().nullable(),
  totalReturnQuantity: z.number().positive(), acceptedReturnQuantity: z.number().nonnegative(), blockedReturnQuantity: z.number().nonnegative(),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), updatedAt: z.string().datetime(),
  availableActions: z.array(z.enum(["CANCEL", "SHIP", "REVERSE"])), lines: z.array(purchaseReturnLineSchema).min(1),
  events: z.array(z.object({ id: z.string().uuid(), action: z.string(), fromStatus: z.string().nullable(), toStatus: z.string(),
    reason: z.string(), requestId: z.string(), occurredAt: z.string().datetime() })),
});

export const purchaseReturnPageSchema = pageEnvelope(purchaseReturnRecordSchema).extend({ canCreate: z.boolean() });

export const purchaseReturnReferenceDataSchema = z.object({
  orders: z.array(z.object({
    id: z.string().uuid(), orderNumber: z.string(), supplierId: z.string().uuid(), supplierCode: z.string(), supplierName: z.string(),
    version: z.number().int().nonnegative(), lines: z.array(z.object({
      purchaseReceiptLineId: z.string().uuid(), receiptNumber: z.string(), purchaseOrderLineId: z.string().uuid(),
      materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(),
      unit: z.string(), qualityStatus: z.enum(["AVAILABLE", "BLOCKED"]), stockBalanceId: z.string().uuid(),
      warehouseCode: z.string(), locationCode: z.string(), lotNumber: z.string().nullable(), sourceQuantity: z.number().positive(),
      pendingQuantity: z.number().nonnegative(), stockAvailableQuantity: z.number().nonnegative(), returnableQuantity: z.number().positive(),
    })).min(1),
  })),
  canCreate: z.boolean(),
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
  revenue: z.number(),
  materialCost: z.number().nonnegative(),
  processingCost: z.number().nonnegative(),
  totalCost: z.number().nonnegative(),
  grossProfit: z.number(),
  grossMargin: z.number().nullable(),
  costBasis: z.string(),
  costStatus: z.enum(["COMPLETE", "MISSING_COST"]),
  status: z.enum(["SETTLED", "IMPACTED", "SUPERSEDED"]),
  settlementVersion: z.number().int().min(1),
  supersedesId: z.string().uuid().nullable(),
  impactReason: z.string().nullable(),
  missingItems: z.array(z.string()),
  version: z.number().int().nonnegative(),
  settledAt: z.string().datetime(),
  lines: z.array(orderProfitLineSchema).min(1),
});

export const orderProfitResettleRequestSchema = z.object({
  reason: z.string().min(4).max(500),
  settlementDate: z.string().nullable().optional(),
  expectedVersion: z.number().int().nonnegative().nullable().optional(),
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
  id: z.string().uuid(), receiptNumber: z.string(), direction: z.enum(["RECEIPT", "REFUND"]),
  amount: z.number().positive(), receiptDate: z.string(),
  paymentMethod: z.enum(["BANK_TRANSFER", "CASH", "BILL", "OTHER"]), bankReference: z.string().nullable(),
  note: z.string().nullable(), status: z.enum(["POSTED", "REVERSED"]), createdAt: z.string().datetime(),
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
  outstandingAmount: z.number().nonnegative(), creditBalance: z.number().nonnegative(),
  status: z.enum(["OPEN", "PARTIALLY_PAID", "PAID", "CREDIT_PENDING", "SETTLED"]),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), lines: z.array(receivableInvoiceLineSchema).min(1),
  receipts: z.array(receivableReceiptSchema),
});

export const receivableInvoicePageSchema = pageEnvelope(receivableInvoiceRecordSchema);

export const receivableReferenceDataSchema = z.object({
  orders: z.array(z.object({
    salesOrderId: z.string().uuid(), orderNumber: z.string(), customerId: z.string().uuid(), customerCode: z.string(),
    customerName: z.string(), currency: z.string(), taxRate: z.number().min(0).max(1), orderStatus: z.enum(["PARTIALLY_SHIPPED", "SHIPPED", "PARTIALLY_RETURNED", "RETURNED"]),
    deliveredAmount: z.number().nonnegative(), invoicedAmount: z.number().nonnegative(), remainingAmount: z.number().nonnegative(),
    lines: z.array(z.object({
      salesOrderLineId: z.string().uuid(), lineNumber: z.number().int().positive(), materialId: z.string().uuid(),
      materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
      deliveredQuantity: z.number().nonnegative(), invoicedQuantity: z.number().nonnegative(), remainingQuantity: z.number().nonnegative(),
      unitPrice: z.number().nonnegative(),
    })),
  })),
});

export const receivableCreditNoteLineSchema = z.object({
  id: z.string().uuid(), originalInvoiceLineId: z.string().uuid(), salesOrderLineId: z.string().uuid(),
  lineNumber: z.number().int().positive(), materialId: z.string().uuid(), materialCode: z.string(),
  materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
  creditQuantity: z.number().positive(), unitPrice: z.number().nonnegative(),
  netAmount: z.number().nonpositive(), taxAmount: z.number().nonpositive(), grossAmount: z.number().negative(),
});

export const receivableCreditNoteSchema = z.object({
  id: z.string().uuid(), creditNoteNumber: z.string(), originalInvoiceId: z.string().uuid(),
  originalInvoiceNumber: z.string(), salesOrderId: z.string().uuid(), orderNumber: z.string(),
  customerId: z.string().uuid(), customerCode: z.string(), customerName: z.string(), currency: z.string(),
  taxNoticeNumber: z.string().nullable(), creditNoteDate: z.string(), dueDate: z.string(),
  taxRate: z.number().min(0).max(1), netAmount: z.number().nonpositive(), taxAmount: z.number().nonpositive(),
  grossAmount: z.number().negative(), reason: z.string().min(4), status: z.literal("POSTED"),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(),
  lines: z.array(receivableCreditNoteLineSchema).min(1),
});

export const receivableCreditNotePageSchema = pageEnvelope(receivableCreditNoteSchema);

// ---- Payable (AP) ----

export const payablePaymentSchema = z.object({
  id: z.string().uuid(), paymentNumber: z.string(), direction: z.enum(["PAYMENT", "REFUND"]),
  amount: z.number().positive(), paymentDate: z.string(),
  paymentMethod: z.enum(["BANK_TRANSFER", "CASH", "BILL", "OTHER"]), bankReference: z.string().nullable(),
  note: z.string().nullable(), status: z.enum(["POSTED", "REVERSED"]), createdAt: z.string().datetime(),
});

export const payableInvoiceLineSchema = z.object({
  id: z.string().uuid(), purchaseOrderLineId: z.string().uuid(), lineNumber: z.number().int().positive(),
  materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(),
  unit: z.string(), invoiceQuantity: z.number().positive(), unitPrice: z.number().nonnegative(), netAmount: z.number().nonnegative(),
  taxAmount: z.number().nonnegative(), grossAmount: z.number().nonnegative(),
});

export const payableInvoiceRecordSchema = z.object({
  id: z.string().uuid(), invoiceNumber: z.string(), supplierInvoiceNumber: z.string(),
  purchaseOrderId: z.string().uuid(), orderNumber: z.string(),
  supplierId: z.string().uuid(), supplierCode: z.string(), supplierName: z.string(), currency: z.string(),
  invoiceDate: z.string(), dueDate: z.string(), taxRate: z.number().min(0).max(1), netAmount: z.number().nonnegative(),
  taxAmount: z.number().nonnegative(), grossAmount: z.number().positive(), paidAmount: z.number().nonnegative(),
  outstandingAmount: z.number().nonnegative(), creditBalance: z.number().nonnegative(),
  status: z.enum(["OPEN", "PARTIALLY_PAID", "PAID", "CREDIT_PENDING", "SETTLED"]),
  purchaseReturnImpactStatus: z.enum(["NONE", "REVIEW_REQUIRED"]),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), lines: z.array(payableInvoiceLineSchema).min(1),
  payments: z.array(payablePaymentSchema),
});

export const payableInvoicePageSchema = pageEnvelope(payableInvoiceRecordSchema);

export const payableReferenceDataSchema = z.object({
  orders: z.array(z.object({
    purchaseOrderId: z.string().uuid(), orderNumber: z.string(), supplierId: z.string().uuid(), supplierCode: z.string(),
    supplierName: z.string(), currency: z.string(), taxRate: z.number().min(0).max(1), orderStatus: z.string(),
    acceptedAmount: z.number().nonnegative(), invoicedAmount: z.number().nonnegative(), remainingAmount: z.number().nonnegative(),
    lines: z.array(z.object({
      purchaseOrderLineId: z.string().uuid(), lineNumber: z.number().int().positive(), materialId: z.string().uuid(),
      materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
      acceptedQuantity: z.number().nonnegative(), invoicedQuantity: z.number().nonnegative(), remainingQuantity: z.number().nonnegative(),
      unitPrice: z.number().nonnegative(),
    })),
  })),
});

export const payableCreditNoteLineSchema = z.object({
  id: z.string().uuid(), originalInvoiceLineId: z.string().uuid(), purchaseOrderLineId: z.string().uuid(),
  lineNumber: z.number().int().positive(), materialId: z.string().uuid(), materialCode: z.string(),
  materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
  creditQuantity: z.number().positive(), unitPrice: z.number().nonnegative(),
  netAmount: z.number().nonpositive(), taxAmount: z.number().nonpositive(), grossAmount: z.number().negative(),
});

export const payableCreditNoteSchema = z.object({
  id: z.string().uuid(), creditNoteNumber: z.string(), originalInvoiceId: z.string().uuid(),
  originalInvoiceNumber: z.string(), supplierCreditNoteNumber: z.string().nullable(),
  purchaseOrderId: z.string().uuid(), orderNumber: z.string(),
  supplierId: z.string().uuid(), supplierCode: z.string(), supplierName: z.string(), currency: z.string(),
  taxNoticeNumber: z.string().nullable(), creditNoteDate: z.string(), dueDate: z.string(),
  taxRate: z.number().min(0).max(1), netAmount: z.number().nonpositive(), taxAmount: z.number().nonpositive(),
  grossAmount: z.number().negative(), reason: z.string().min(4), status: z.literal("POSTED"),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(),
  lines: z.array(payableCreditNoteLineSchema).min(1),
});

export const payableCreditNotePageSchema = pageEnvelope(payableCreditNoteSchema);

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
  source: z.enum(["DESKTOP_FORM", "MOBILE_SCAN"]),
  status: z.enum(["PENDING_INSPECTION", "PARTIALLY_RECEIVED", "RECEIVED", "REJECTED_CLOSED"]),
  totalReceivedQuantity: z.number().positive(), acceptedQuantity: z.number().nonnegative(), rejectedQuantity: z.number().nonnegative(),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), lines: z.array(purchaseReceiptLineSchema).min(1),
});
export const purchaseReceiptPageSchema = pageEnvelope(purchaseReceiptRecordSchema);
export const purchaseReceiptReferenceDataSchema = z.object({
  canCreate: z.boolean(),
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
  source: z.enum(["DESKTOP_FORM", "MOBILE_SCAN"]).optional(),
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
  operationTaskId: z.string().uuid().nullable(), operationTaskNumber: z.string().nullable(),
  source: z.enum(["DESKTOP_FORM", "MOBILE_SCAN"]),
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
  source: z.enum(["DESKTOP_FORM", "MOBILE_SCAN"]),
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
  balanceId: z.string().uuid(),
  lotNumber: z.string(),
  movementId: z.string().uuid(),
  movementNumber: z.string(),
  source: z.enum(["DESKTOP_FORM", "MOBILE_SCAN"]),
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
  canControl: z.boolean(),
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
  availableStocks: z.array(z.object({
    id: z.string().uuid(),
    warehouseId: z.string().uuid(),
    warehouseCode: z.string(),
    locationId: z.string().uuid(),
    locationCode: z.string(),
    locationName: z.string(),
    materialId: z.string().uuid(),
    materialCode: z.string(),
    lotNumber: z.string(),
    availableQuantity: z.number().positive(),
    version: z.number().int().nonnegative(),
  })),
});

export const accountingPeriodSchema = z.object({
  id: z.string().uuid(),
  fiscalYear: z.number().int().min(2020).max(2099),
  fiscalPeriod: z.number().int().min(1).max(12),
  periodLabel: z.string(),
  status: z.enum(["OPEN", "CLOSED"]),
  closedAt: z.string().datetime().nullable(),
  closedByName: z.string().nullable(),
  reopenedAt: z.string().datetime().nullable(),
  reopenedByName: z.string().nullable(),
  reopenReason: z.string().nullable(),
  version: z.number().int().nonnegative(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});

export const createAccountingPeriodSchema = z.object({
  fiscalYear: z.number().int().min(2020).max(2099),
  fiscalPeriod: z.number().int().min(1).max(12),
});

export const reopenAccountingPeriodSchema = z.object({
  reason: z.string().min(4).max(500),
  expectedVersion: z.number().int().nonnegative().optional(),
});

export const grirAccrualLineSchema = z.object({
  id: z.string().uuid(),
  purchaseOrderId: z.string().uuid(),
  orderNumber: z.string(),
  supplierId: z.string().uuid(),
  supplierCode: z.string(),
  supplierName: z.string(),
  purchaseOrderLineId: z.string().uuid(),
  lineNumber: z.number().int().positive(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  materialSpecification: z.string().nullable(),
  unit: z.string(),
  receivedQuantity: z.number(),
  invoicedQuantity: z.number(),
  accruedQuantity: z.number(),
  unitPrice: z.number(),
  netAmount: z.number(),
});

export const grirAccrualSchema = z.object({
  id: z.string().uuid(),
  accrualNumber: z.string(),
  fiscalYear: z.number().int().min(2000).max(2100),
  fiscalPeriod: z.number().int().min(1).max(12),
  accrualDate: z.string().date(),
  status: z.enum(["POSTED", "REVERSED"]),
  totalNetAmount: z.number(),
  reversedByAccrualId: z.string().uuid().nullable(),
  reversalDate: z.string().date().nullable(),
  reversalReason: z.string().nullable(),
  note: z.string().nullable(),
  version: z.number().int().nonnegative(),
  createdAt: z.string().datetime(),
  lines: z.array(grirAccrualLineSchema),
});

export const grirAccrualPageSchema = pageEnvelope(grirAccrualSchema);

export const grirAccrualPreviewLineSchema = z.object({
  purchaseOrderId: z.string().uuid(),
  orderNumber: z.string(),
  supplierId: z.string().uuid(),
  supplierCode: z.string(),
  supplierName: z.string(),
  purchaseOrderLineId: z.string().uuid(),
  lineNumber: z.number().int().positive(),
  materialId: z.string().uuid(),
  materialCode: z.string(),
  materialName: z.string(),
  materialSpecification: z.string().nullable(),
  unit: z.string(),
  receivedQuantity: z.number(),
  invoicedQuantity: z.number(),
  accruedQuantity: z.number(),
  unitPrice: z.number(),
  netAmount: z.number(),
});

export const grirAccrualPreviewSchema = z.object({
  fiscalYear: z.number().int(),
  fiscalPeriod: z.number().int(),
  priorAccrualId: z.string().uuid().nullable(),
  priorAccrualNumber: z.string().nullable(),
  priorAccrualAmount: z.number(),
  totalNetAmount: z.number(),
  lines: z.array(grirAccrualPreviewLineSchema),
});

export const runGrirAccrualSchema = z.object({
  fiscalYear: z.number().int().min(2000).max(2100),
  fiscalPeriod: z.number().int().min(1).max(12),
  accrualDate: z.string().date().optional(),
  note: z.string().max(500).optional(),
});

export const reverseGrirAccrualSchema = z.object({
  reversalDate: z.string().date(),
  reason: z.string().min(4).max(500),
});

export type ManufacturingSnapshot = z.infer<typeof manufacturingSnapshotSchema>;
export type FlowStage = z.infer<typeof flowStageSchema>;
export type WorkOrder = z.infer<typeof workOrderSchema>;
export type GlobalSearchItem = z.infer<typeof globalSearchItemSchema>;
export type WorkspaceSummary = z.infer<typeof workspaceSummarySchema>;
export type WorkspaceSession = z.infer<typeof workspaceSessionSchema>;
export type WorkspaceSessionEnvelope = z.infer<typeof workspaceSessionEnvelopeSchema>;
export type WorkspaceRoleCode = z.infer<typeof workspaceRoleCodeSchema>;
export type WorkspaceRole = z.infer<typeof workspaceRoleSchema>;
export type WorkspacePermissionRisk = z.infer<typeof workspacePermissionRiskSchema>;
export type WorkspacePermission = z.infer<typeof workspacePermissionSchema>;
export type WorkspacePermissionGroup = z.infer<typeof workspacePermissionGroupSchema>;
export type WorkspaceRolePermissionPage = z.infer<typeof workspaceRolePermissionPageSchema>;
export type WorkspaceUser = z.infer<typeof workspaceUserSchema>;
export type WorkspaceUserPage = z.infer<typeof workspaceUserPageSchema>;
export type OrganizationUnit = z.infer<typeof organizationUnitSchema>;
export type OrganizationWorkspace = z.infer<typeof organizationWorkspaceSchema>;
export type OrganizationMember = z.infer<typeof organizationMemberSchema>;
export type OrganizationStructurePage = z.infer<typeof organizationStructurePageSchema>;
export type EquipmentAssetCategory = z.infer<typeof equipmentAssetCategorySchema>;
export type EquipmentOperatingStatus = z.infer<typeof equipmentOperatingStatusSchema>;
export type EquipmentAssetAction = z.infer<typeof equipmentAssetActionSchema>;
export type EquipmentAssetEvent = z.infer<typeof equipmentAssetEventSchema>;
export type EquipmentAsset = z.infer<typeof equipmentAssetSchema>;
export type EquipmentAssetPage = z.infer<typeof equipmentAssetPageSchema>;
export type EquipmentTelemetryEndpointType = z.infer<typeof equipmentTelemetryEndpointTypeSchema>;
export type EquipmentTelemetryProtocol = z.infer<typeof equipmentTelemetryProtocolSchema>;
export type EquipmentTelemetryRegisterType = z.infer<typeof equipmentTelemetryRegisterTypeSchema>;
export type EquipmentTelemetryValueType = z.infer<typeof equipmentTelemetryValueTypeSchema>;
export type EquipmentTelemetryPoint = z.infer<typeof equipmentTelemetryPointSchema>;
export type EquipmentTelemetryConnection = z.infer<typeof equipmentTelemetryConnectionSchema>;
export type EquipmentTelemetryConnectionPage = z.infer<typeof equipmentTelemetryConnectionPageSchema>;
export type EquipmentTelemetryActionResult = z.infer<typeof equipmentTelemetryActionResultSchema>;
export type EquipmentTelemetryFieldAcceptanceStatus = z.infer<typeof equipmentTelemetryFieldAcceptanceStatusSchema>;
export type EquipmentTelemetryFieldAcceptanceAction = z.infer<typeof equipmentTelemetryFieldAcceptanceActionSchema>;
export type EquipmentTelemetryFieldAcceptance = z.infer<typeof equipmentTelemetryFieldAcceptanceSchema>;
export type EquipmentTelemetryFieldAcceptanceContext = z.infer<typeof equipmentTelemetryFieldAcceptanceContextSchema>;
export type EquipmentTelemetryQuality = z.infer<typeof equipmentTelemetryQualitySchema>;
export type EquipmentTelemetrySamplePage = z.infer<typeof equipmentTelemetrySamplePageSchema>;
export type EquipmentTelemetryRetentionPolicy = z.infer<typeof equipmentTelemetryRetentionPolicySchema>;
export type EquipmentTelemetryCleanupResult = z.infer<typeof equipmentTelemetryCleanupResultSchema>;
export type EquipmentTelemetryAutomationRun = z.infer<typeof equipmentTelemetryAutomationRunSchema>;
export type EquipmentTelemetryAutomationActionResult = z.infer<typeof equipmentTelemetryAutomationActionResultSchema>;
export type EquipmentAlertRuleType = z.infer<typeof equipmentAlertRuleTypeSchema>;
export type EquipmentAlertSeverity = z.infer<typeof equipmentAlertSeveritySchema>;
export type EquipmentAlertRule = z.infer<typeof equipmentAlertRuleSchema>;
export type EquipmentAlertRulePage = z.infer<typeof equipmentAlertRulePageSchema>;
export type EquipmentAlert = z.infer<typeof equipmentAlertSchema>;
export type EquipmentAlertPage = z.infer<typeof equipmentAlertPageSchema>;
export type EquipmentOeeStatus = z.infer<typeof equipmentOeeStatusSchema>;
export type EquipmentOeeDowntimeCategory = z.infer<typeof equipmentOeeDowntimeCategorySchema>;
export type EquipmentOeeAction = z.infer<typeof equipmentOeeActionSchema>;
export type EquipmentOeeDowntime = z.infer<typeof equipmentOeeDowntimeSchema>;
export type EquipmentOeeRecord = z.infer<typeof equipmentOeeRecordSchema>;
export type EquipmentOeePage = z.infer<typeof equipmentOeePageSchema>;
export type EquipmentWorkType = z.infer<typeof equipmentWorkTypeSchema>;
export type EquipmentWorkOrderSource = z.infer<typeof equipmentWorkOrderSourceSchema>;
export type EquipmentWorkOrderPriority = z.infer<typeof equipmentWorkOrderPrioritySchema>;
export type EquipmentWorkOrderStatus = z.infer<typeof equipmentWorkOrderStatusSchema>;
export type EquipmentWorkOrderOutcome = z.infer<typeof equipmentWorkOrderOutcomeSchema>;
export type EquipmentWorkOrderAction = z.infer<typeof equipmentWorkOrderActionSchema>;
export type EquipmentWorkOrderEvent = z.infer<typeof equipmentWorkOrderEventSchema>;
export type EquipmentWorkOrder = z.infer<typeof equipmentWorkOrderSchema>;
export type EquipmentWorkOrderPage = z.infer<typeof equipmentWorkOrderPageSchema>;
export type EquipmentMaintenancePlan = z.infer<typeof equipmentMaintenancePlanSchema>;
export type EquipmentMaintenancePlanPage = z.infer<typeof equipmentMaintenancePlanPageSchema>;
export type EquipmentMaintenancePlanAction = z.infer<typeof equipmentMaintenancePlanActionSchema>;
export type EquipmentMaintenanceGeneration = z.infer<typeof equipmentMaintenanceGenerationSchema>;
export type EquipmentSpareTransaction = z.infer<typeof equipmentSpareTransactionSchema>;
export type EquipmentLaborTransaction = z.infer<typeof equipmentLaborTransactionSchema>;
export type EquipmentMaintenanceCostEvidence = z.infer<typeof equipmentMaintenanceCostEvidenceSchema>;
export type EquipmentSparePart = z.infer<typeof equipmentSparePartSchema>;
export type EquipmentSparePartPage = z.infer<typeof equipmentSparePartPageSchema>;
export type EquipmentSparePartReference = z.infer<typeof equipmentSparePartReferenceSchema>;
export type EquipmentMaintenanceCostMutationResult = z.infer<typeof equipmentMaintenanceCostMutationResultSchema>;
export type CustomerRecord = z.infer<typeof customerRecordSchema>;
export type MaterialRecord = z.infer<typeof materialRecordSchema>;
export type SupplierRecord = z.infer<typeof supplierRecordSchema>;
export type SalesOrderLine = z.infer<typeof salesOrderLineSchema>;
export type SalesOrderRecord = z.infer<typeof salesOrderRecordSchema>;
export type SalesOrderReferenceData = z.infer<typeof salesOrderReferenceDataSchema>;
export type SalesShipmentRecord = z.infer<typeof salesShipmentRecordSchema>;
export type SalesReturnRecord = z.infer<typeof salesReturnRecordSchema>;
export type SalesReturnReferenceData = z.infer<typeof salesReturnReferenceDataSchema>;
export type PurchaseReturnRecord = z.infer<typeof purchaseReturnRecordSchema>;
export type PurchaseReturnReferenceData = z.infer<typeof purchaseReturnReferenceDataSchema>;
export type OrderProfitRecord = z.infer<typeof orderProfitRecordSchema>;
export type OrderProfitReferenceData = z.infer<typeof orderProfitReferenceDataSchema>;
export type ReceivableInvoiceRecord = z.infer<typeof receivableInvoiceRecordSchema>;
export type ReceivableReferenceData = z.infer<typeof receivableReferenceDataSchema>;
export type ReceivableCreditNoteRecord = z.infer<typeof receivableCreditNoteSchema>;
export type PayableInvoiceRecord = z.infer<typeof payableInvoiceRecordSchema>;
export type PayableReferenceData = z.infer<typeof payableReferenceDataSchema>;
export type PayableCreditNoteRecord = z.infer<typeof payableCreditNoteSchema>;
export type SalesShipmentReferenceData = z.infer<typeof salesShipmentReferenceDataSchema>;
export type PurchaseOrderLine = z.infer<typeof purchaseOrderLineSchema>;
export type PurchaseOrderRecord = z.infer<typeof purchaseOrderRecordSchema>;
export type PurchaseReceiptRecord = z.infer<typeof purchaseReceiptRecordSchema>;
export type PurchaseReceiptReferenceData = z.infer<typeof purchaseReceiptReferenceDataSchema>;
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
  source: z.enum(["SYSTEM", "DESKTOP_FORM", "MOBILE_SCAN"]),
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

export type AccountingPeriod = z.infer<typeof accountingPeriodSchema>;
export type GrirAccrual = z.infer<typeof grirAccrualSchema>;
export type GrirAccrualLine = z.infer<typeof grirAccrualLineSchema>;
export type GrirAccrualPreview = z.infer<typeof grirAccrualPreviewSchema>;
export type RunGrirAccrualPayload = z.infer<typeof runGrirAccrualSchema>;
export type ReverseGrirAccrualPayload = z.infer<typeof reverseGrirAccrualSchema>;
export type CreateAccountingPeriodPayload = z.infer<typeof createAccountingPeriodSchema>;

// ── 预收预付（Advance Receipts & Payments） ──

export const advanceApplicationSchema = z.object({
  id: z.string().uuid(),
  invoiceId: z.string().uuid(),
  invoiceNumber: z.string(),
  appliedAmount: z.number(),
  applicationDate: z.string(),
  createdAt: z.string(),
});

export const advanceRefundSchema = z.object({
  id: z.string().uuid(),
  refundAmount: z.number(),
  refundDate: z.string(),
  reason: z.string(),
  createdAt: z.string(),
});

export const advanceSchema = z.object({
  id: z.string().uuid(),
  advanceNumber: z.string(),
  type: z.enum(["RECEIVABLE", "PAYABLE"]),
  partyType: z.enum(["CUSTOMER", "SUPPLIER"]),
  partyId: z.string().uuid(),
  partyCode: z.string(),
  partyName: z.string(),
  currency: z.string(),
  advanceDate: z.string(),
  totalAmount: z.number(),
  appliedAmount: z.number(),
  refundedAmount: z.number(),
  availableBalance: z.number(),
  status: z.enum(["OPEN", "PARTIALLY_USED", "CLOSED"]),
  note: z.string().nullable(),
  version: z.number().int(),
  createdAt: z.string(),
  applications: z.array(advanceApplicationSchema),
  refunds: z.array(advanceRefundSchema),
});

export const advancePageSchema = z.object({
  items: z.array(advanceSchema),
  totalElements: z.number().int(),
  totalPages: z.number().int(),
  page: z.number().int(),
  size: z.number().int(),
});

export const createAdvanceSchema = z.object({
  type: z.enum(["RECEIVABLE", "PAYABLE"]),
  partyId: z.string().uuid(),
  advanceDate: z.string(),
  totalAmount: z.number().positive(),
  note: z.string().max(500).optional(),
});

export const refundAdvanceSchema = z.object({
  refundAmount: z.number().positive(),
  refundDate: z.string(),
  reason: z.string().min(4).max(500),
});

export type Advance = z.infer<typeof advanceSchema>;
export type AdvanceApplication = z.infer<typeof advanceApplicationSchema>;
export type AdvanceRefund = z.infer<typeof advanceRefundSchema>;
export type AdvancePage = z.infer<typeof advancePageSchema>;
export type CreateAdvancePayload = z.infer<typeof createAdvanceSchema>;
export type RefundAdvancePayload = z.infer<typeof refundAdvanceSchema>;
