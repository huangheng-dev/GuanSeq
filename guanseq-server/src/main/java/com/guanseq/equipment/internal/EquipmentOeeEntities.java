package com.guanseq.equipment.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "equipment", name = "oee_records")
class EquipmentOeeRecordEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "record_number") private String recordNumber;
	@Column(name = "asset_id") private UUID assetId;
	@Column(name = "asset_code_snapshot") private String assetCodeSnapshot;
	@Column(name = "asset_name_snapshot") private String assetNameSnapshot;
	@Column(name = "work_center_code_snapshot") private String workCenterCodeSnapshot;
	@Column(name = "work_center_name_snapshot") private String workCenterNameSnapshot;
	@Column(name = "location_snapshot") private String locationSnapshot;
	@Column(name = "window_start") private Instant windowStart;
	@Column(name = "window_end") private Instant windowEnd;
	@Column(name = "planned_production_minutes") private BigDecimal plannedProductionMinutes;
	@Column(name = "downtime_minutes") private BigDecimal downtimeMinutes;
	@Column(name = "run_minutes") private BigDecimal runMinutes;
	@Column(name = "ideal_cycle_seconds") private BigDecimal idealCycleSeconds;
	@Column(name = "total_count") private long totalCount;
	@Column(name = "good_count") private long goodCount;
	@Column(name = "availability_rate") private BigDecimal availabilityRate;
	@Column(name = "performance_rate") private BigDecimal performanceRate;
	@Column(name = "quality_rate") private BigDecimal qualityRate;
	@Column(name = "oee_rate") private BigDecimal oeeRate;
	@Column(name = "shift_name") private String shiftName;
	@Column(name = "production_reference") private String productionReference;
	@Column(name = "source_type") private String sourceType;
	@Column(name = "source_reference") private String sourceReference;
	private String status;
	@Column(name = "rejection_reason") private String rejectionReason;
	@Version private long version;
	@Column(name = "creation_request_id") private String creationRequestId;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "submitted_by") private UUID submittedBy;
	@Column(name = "submitted_at") private Instant submittedAt;
	@Column(name = "approved_by") private UUID approvedBy;
	@Column(name = "approved_at") private Instant approvedAt;
	@Column(name = "rejected_by") private UUID rejectedBy;
	@Column(name = "rejected_at") private Instant rejectedAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentOeeRecordEntity() { }

	EquipmentOeeRecordEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String number,
			EquipmentAssetEntity asset, Instant start, Instant end, BigDecimal plannedMinutes,
			BigDecimal idealSeconds, long total, long good, String shift, String productionReference,
			String sourceReference, String requestId, UUID actorId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId;
		this.recordNumber = number;
		this.assetId = asset.getId();
		this.assetCodeSnapshot = asset.getAssetCode();
		this.assetNameSnapshot = asset.getAssetName();
		this.workCenterCodeSnapshot = asset.getWorkCenterCode();
		this.workCenterNameSnapshot = asset.getWorkCenterName();
		this.locationSnapshot = asset.getLocation();
		this.sourceType = "MANUAL_VERIFIED";
		this.status = "DRAFT";
		this.creationRequestId = requestId;
		this.createdBy = actorId;
		this.createdAt = Instant.now();
		updateFacts(start, end, plannedMinutes, idealSeconds, total, good, shift, productionReference,
				sourceReference, BigDecimal.ZERO, actorId);
	}

	void updateFacts(Instant start, Instant end, BigDecimal plannedMinutes, BigDecimal idealSeconds,
			long total, long good, String shift, String productionReference, String sourceReference,
			BigDecimal downtime, UUID actorId) {
		this.windowStart = start;
		this.windowEnd = end;
		this.plannedProductionMinutes = plannedMinutes.setScale(2, RoundingMode.HALF_UP);
		this.idealCycleSeconds = idealSeconds.setScale(4, RoundingMode.HALF_UP);
		this.totalCount = total;
		this.goodCount = good;
		this.shiftName = shift.trim();
		this.productionReference = nullable(productionReference);
		this.sourceReference = nullable(sourceReference);
		recalculate(downtime);
		this.rejectionReason = null;
		touch(actorId);
	}

	void recalculate(BigDecimal downtime) {
		this.downtimeMinutes = downtime.setScale(2, RoundingMode.HALF_UP);
		this.runMinutes = this.plannedProductionMinutes.subtract(this.downtimeMinutes).setScale(2, RoundingMode.HALF_UP);
		this.availabilityRate = percent(this.runMinutes, this.plannedProductionMinutes);
		this.performanceRate = this.runMinutes.signum() == 0 ? BigDecimal.ZERO.setScale(4)
				: percent(this.idealCycleSeconds.multiply(BigDecimal.valueOf(this.totalCount)),
						this.runMinutes.multiply(BigDecimal.valueOf(60)));
		this.qualityRate = this.totalCount == 0 ? BigDecimal.ZERO.setScale(4)
				: percent(BigDecimal.valueOf(this.goodCount), BigDecimal.valueOf(this.totalCount));
		this.oeeRate = this.availabilityRate.multiply(this.performanceRate).multiply(this.qualityRate)
				.divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);
	}

	void refreshDowntime(BigDecimal downtime, UUID actorId) { recalculate(downtime); touch(actorId); }

	void submit(UUID actorId) {
		this.status = "SUBMITTED";
		this.rejectionReason = null;
		this.submittedBy = actorId;
		this.submittedAt = Instant.now();
		touch(actorId);
	}

	void approve(UUID actorId) {
		this.status = "APPROVED";
		this.approvedBy = actorId;
		this.approvedAt = Instant.now();
		touch(actorId);
	}

	void reject(String reason, UUID actorId) {
		this.status = "REJECTED";
		this.rejectionReason = reason.trim();
		this.rejectedBy = actorId;
		this.rejectedAt = Instant.now();
		touch(actorId);
	}

	private void touch(UUID actorId) { this.updatedBy = actorId; this.updatedAt = Instant.now(); }
	private static String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
		return denominator.signum() == 0 ? BigDecimal.ZERO.setScale(4)
				: numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 4, RoundingMode.HALF_UP);
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getRecordNumber() { return recordNumber; }
	UUID getAssetId() { return assetId; }
	String getAssetCodeSnapshot() { return assetCodeSnapshot; }
	String getAssetNameSnapshot() { return assetNameSnapshot; }
	String getWorkCenterCodeSnapshot() { return workCenterCodeSnapshot; }
	String getWorkCenterNameSnapshot() { return workCenterNameSnapshot; }
	String getLocationSnapshot() { return locationSnapshot; }
	Instant getWindowStart() { return windowStart; }
	Instant getWindowEnd() { return windowEnd; }
	BigDecimal getPlannedProductionMinutes() { return plannedProductionMinutes; }
	BigDecimal getDowntimeMinutes() { return downtimeMinutes; }
	BigDecimal getRunMinutes() { return runMinutes; }
	BigDecimal getIdealCycleSeconds() { return idealCycleSeconds; }
	long getTotalCount() { return totalCount; }
	long getGoodCount() { return goodCount; }
	BigDecimal getAvailabilityRate() { return availabilityRate; }
	BigDecimal getPerformanceRate() { return performanceRate; }
	BigDecimal getQualityRate() { return qualityRate; }
	BigDecimal getOeeRate() { return oeeRate; }
	String getShiftName() { return shiftName; }
	String getProductionReference() { return productionReference; }
	String getSourceType() { return sourceType; }
	String getSourceReference() { return sourceReference; }
	String getStatus() { return status; }
	String getRejectionReason() { return rejectionReason; }
	long getVersion() { return version; }
	UUID getCreatedBy() { return createdBy; }
	Instant getCreatedAt() { return createdAt; }
	UUID getSubmittedBy() { return submittedBy; }
	Instant getSubmittedAt() { return submittedAt; }
	UUID getApprovedBy() { return approvedBy; }
	Instant getApprovedAt() { return approvedAt; }
	UUID getRejectedBy() { return rejectedBy; }
	Instant getRejectedAt() { return rejectedAt; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "equipment", name = "oee_downtimes")
class EquipmentOeeDowntimeEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "oee_record_id") private UUID oeeRecordId;
	@Column(name = "started_at") private Instant startedAt;
	@Column(name = "ended_at") private Instant endedAt;
	@Column(name = "duration_minutes") private BigDecimal durationMinutes;
	@Column(name = "reason_category") private String reasonCategory;
	@Column(name = "responsible_party") private String responsibleParty;
	private String description;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentOeeDowntimeEntity() { }

	EquipmentOeeDowntimeEntity(EquipmentOeeRecordEntity record, Instant start, Instant end, String category,
			String responsibleParty, String description, UUID actorId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = record.getTenantOrganizationId();
		this.workspaceId = record.getWorkspaceId();
		this.oeeRecordId = record.getId();
		this.createdBy = actorId;
		this.createdAt = Instant.now();
		update(start, end, category, responsibleParty, description, actorId);
	}

	void update(Instant start, Instant end, String category, String responsibleParty, String description, UUID actorId) {
		this.startedAt = start;
		this.endedAt = end;
		this.durationMinutes = BigDecimal.valueOf(end.toEpochMilli() - start.toEpochMilli())
				.divide(BigDecimal.valueOf(60000), 2, RoundingMode.HALF_UP);
		this.reasonCategory = category;
		this.responsibleParty = responsibleParty.trim();
		this.description = description.trim();
		this.updatedBy = actorId;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getOeeRecordId() { return oeeRecordId; }
	Instant getStartedAt() { return startedAt; }
	Instant getEndedAt() { return endedAt; }
	BigDecimal getDurationMinutes() { return durationMinutes; }
	String getReasonCategory() { return reasonCategory; }
	String getResponsibleParty() { return responsibleParty; }
	String getDescription() { return description; }
	UUID getCreatedBy() { return createdBy; }
	Instant getCreatedAt() { return createdAt; }
	UUID getUpdatedBy() { return updatedBy; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "equipment", name = "oee_events")
class EquipmentOeeEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "oee_record_id") private UUID oeeRecordId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected EquipmentOeeEventEntity() { }

	EquipmentOeeEventEntity(EquipmentOeeRecordEntity record, UUID actorId, String action, String fromStatus,
			String toStatus, String reason, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = record.getTenantOrganizationId();
		this.workspaceId = record.getWorkspaceId();
		this.actorUserId = actorId;
		this.oeeRecordId = record.getId();
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.reason = reason.trim();
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getActorUserId() { return actorUserId; }
	String getAction() { return action; }
	String getFromStatus() { return fromStatus; }
	String getToStatus() { return toStatus; }
	String getReason() { return reason; }
	String getRequestId() { return requestId; }
	Map<String, Object> getDetails() { return details; }
	Instant getOccurredAt() { return occurredAt; }
}
