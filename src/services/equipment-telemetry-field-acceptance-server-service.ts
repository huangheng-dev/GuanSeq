import "server-only";

import { equipmentTelemetryFieldAcceptanceContextSchema,
  type EquipmentTelemetryFieldAcceptanceAction, type EquipmentTelemetryFieldAcceptanceContext } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type EquipmentTelemetryFieldAcceptanceMutation =
  | { operation: "save"; connectionId: string; networkApproved: boolean; securityValidated: boolean;
      readOnlyConfirmed: boolean; disconnectRecoveryVerified: boolean; capacityVerified: boolean;
      pointMappingApproved: boolean; responsibleOwner?: string | null; testWindowStart?: string | null;
      testWindowEnd?: string | null; evidenceReference?: string | null; notes?: string | null;
      expectedVersion?: number | null; reason: string }
  | { operation: "act"; connectionId: string; action: Exclude<EquipmentTelemetryFieldAcceptanceAction, "UPDATE">;
      expectedVersion: number; reason: string };

export async function loadEquipmentTelemetryFieldAcceptance(connectionId: string, requestId: string): Promise<{
  context: EquipmentTelemetryFieldAcceptanceContext; requestId: string;
}> {
  const response = await requestGuanSeqApi(
    `/api/v1/equipment/telemetry-field-acceptances/${connectionId}`, requestId);
  if (!response) throw new GuanSeqApiError("现场接入验收服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "现场接入验收加载失败");
  return { context: equipmentTelemetryFieldAcceptanceContextSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function mutateEquipmentTelemetryFieldAcceptance(input: EquipmentTelemetryFieldAcceptanceMutation,
  requestId: string): Promise<{ context: EquipmentTelemetryFieldAcceptanceContext; requestId: string }> {
  const save = input.operation === "save";
  const path = save ? `/api/v1/equipment/telemetry-field-acceptances/${input.connectionId}`
    : `/api/v1/equipment/telemetry-field-acceptances/${input.connectionId}/actions`;
  const body = save ? {
    networkApproved: input.networkApproved, securityValidated: input.securityValidated,
    readOnlyConfirmed: input.readOnlyConfirmed, disconnectRecoveryVerified: input.disconnectRecoveryVerified,
    capacityVerified: input.capacityVerified, pointMappingApproved: input.pointMappingApproved,
    responsibleOwner: input.responsibleOwner ?? null, testWindowStart: input.testWindowStart ?? null,
    testWindowEnd: input.testWindowEnd ?? null, evidenceReference: input.evidenceReference ?? null,
    notes: input.notes ?? null, expectedVersion: input.expectedVersion ?? null, reason: input.reason,
  } : { action: input.action, expectedVersion: input.expectedVersion, reason: input.reason };
  const response = await requestGuanSeqApi(path, requestId, {
    method: save ? "PUT" : "POST", body: JSON.stringify(body),
  });
  if (!response) throw new GuanSeqApiError("现场接入验收服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "现场接入验收操作失败");
  return { context: equipmentTelemetryFieldAcceptanceContextSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId };
}
