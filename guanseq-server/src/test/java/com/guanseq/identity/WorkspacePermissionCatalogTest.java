package com.guanseq.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.guanseq.identity.api.WorkspacePermission;
import com.guanseq.identity.api.WorkspaceRoleCatalog;

class WorkspacePermissionCatalogTest {

	@Test
	void containsOnlyControlledRolesAndKeepsAdministratorOnEveryPermission() {
		assertThat(WorkspaceRoleCatalog.roles()).hasSize(13);
		assertThat(WorkspacePermission.catalog()).hasSize(58);
		assertThat(WorkspacePermission.catalog())
				.allSatisfy(permission -> {
					assertThat(permission.roleCodes()).contains(WorkspaceRoleCatalog.ADMIN);
					assertThat(WorkspaceRoleCatalog.codes()).containsAll(permission.roleCodes());
				});
		assertThat(WorkspacePermission.catalog().stream().map(WorkspacePermission::name)).doesNotHaveDuplicates();
	}

	@Test
	void mirrorsRoleSetsEnforcedByBusinessApplicationServices() throws Exception {
		Map<WorkspacePermission, RoleSetField> enforced = new LinkedHashMap<>();
		enforced.put(WorkspacePermission.SALES_ORDER_APPROVE_RELEASE, field("sales.internal.SalesOrderApplicationService", "APPROVAL_ROLES"));
		enforced.put(WorkspacePermission.SALES_SHIPMENT_REGISTER, field("sales.internal.SalesShipmentApplicationService", "SHIPMENT_ROLES"));
		enforced.put(WorkspacePermission.SALES_RETURN_AUTHORIZE, field("sales.internal.SalesReturnApplicationService", "CREATE_ROLES"));
		enforced.put(WorkspacePermission.SALES_RETURN_RECEIVE, field("sales.internal.SalesReturnApplicationService", "RECEIPT_ROLES"));
		enforced.put(WorkspacePermission.SALES_RETURN_INSPECT, field("sales.internal.SalesReturnApplicationService", "QUALITY_ROLES"));
		enforced.put(WorkspacePermission.PLANNING_DEMAND_MAINTAIN, field("planning.internal.IndependentDemandApplicationService", "PLANNING_ROLES"));
		enforced.put(WorkspacePermission.PLANNING_PARAMETER_MAINTAIN, field("planning.internal.MaterialPlanningParameterApplicationService", "PLANNING_ROLES"));
		enforced.put(WorkspacePermission.PLANNING_MRP_RUN, field("planning.internal.MrpRunApplicationService", "PLANNING_ROLES"));
		enforced.put(WorkspacePermission.PLANNING_MRP_SUGGESTION_REVIEW, field("planning.internal.MrpSuggestionApplicationService", "PLANNING_ROLES"));
		enforced.put(WorkspacePermission.PROCUREMENT_SUPPLIER_MAINTAIN, field("procurement.internal.SupplierApplicationService", "WRITE_ROLES"));
		enforced.put(WorkspacePermission.PROCUREMENT_ORDER_APPROVE_RELEASE, field("procurement.internal.PurchaseOrderApplicationService", "APPROVAL_ROLES"));
		enforced.put(WorkspacePermission.PROCUREMENT_RECEIPT_REGISTER, field("procurement.internal.PurchaseReceiptApplicationService", "RECEIPT_ROLES"));
		enforced.put(WorkspacePermission.PROCUREMENT_RETURN_AUTHORIZE, field("procurement.internal.PurchaseReturnApplicationService", "CREATE_ROLES"));
		enforced.put(WorkspacePermission.PROCUREMENT_RETURN_SHIP, field("procurement.internal.PurchaseReturnApplicationService", "SHIP_ROLES"));
		enforced.put(WorkspacePermission.PRODUCT_BOM_MAINTAIN, field("product.internal.BomApplicationService", "PRODUCT_ROLES"));
		enforced.put(WorkspacePermission.PRODUCT_ROUTING_MAINTAIN, field("product.internal.RoutingApplicationService", "PRODUCT_ROLES"));
		enforced.put(WorkspacePermission.PRODUCTION_ORDER_CONTROL, field("production.internal.ProductionOrderApplicationService", "CONTROL_ROLES"));
		enforced.put(WorkspacePermission.PRODUCTION_MATERIAL_ISSUE, field("production.internal.MaterialIssueApplicationService", "CONTROL_ROLES"));
		enforced.put(WorkspacePermission.PRODUCTION_OPERATION_EXECUTE, field("production.internal.OperationTaskApplicationService", "TASK_ROLES"));
		enforced.put(WorkspacePermission.PRODUCTION_LABOR_READ, field("production.internal.OperationLaborEntryApplicationService", "READ_ROLES"));
		enforced.put(WorkspacePermission.PRODUCTION_LABOR_RECORD, field("production.internal.OperationLaborEntryApplicationService", "RECORD_ROLES"));
		enforced.put(WorkspacePermission.PRODUCTION_LABOR_APPROVE, field("production.internal.OperationLaborEntryApplicationService", "APPROVAL_ROLES"));
		enforced.put(WorkspacePermission.PRODUCTION_REPORT, field("production.internal.ProductionWorkReportApplicationService", "REPORT_ROLES"));
		enforced.put(WorkspacePermission.QUALITY_INSPECTION_COMPLETE, field("quality.internal.FinalInspectionApplicationService", "QUALITY_ROLES"));
		enforced.put(WorkspacePermission.QUALITY_NONCONFORMANCE_READ, field("quality.internal.NonconformanceApplicationService", "READ_ROLES"));
		enforced.put(WorkspacePermission.QUALITY_NONCONFORMANCE_REVIEW, field("quality.internal.NonconformanceApplicationService", "REVIEW_ROLES"));
		enforced.put(WorkspacePermission.QUALITY_CORRECTIVE_ACTION_EXECUTE, field("quality.internal.NonconformanceApplicationService", "EXECUTE_ROLES"));
		enforced.put(WorkspacePermission.QUALITY_CORRECTIVE_ACTION_VERIFY, field("quality.internal.NonconformanceApplicationService", "VERIFY_ROLES"));
		enforced.put(WorkspacePermission.WAREHOUSE_INVENTORY_MOVE, field("warehouse.internal.InventoryApplicationService", "MOVEMENT_ROLES"));
		enforced.put(WorkspacePermission.WAREHOUSE_PUTAWAY, field("warehouse.internal.PutawayApplicationService", "PUTAWAY_ROLES"));
		enforced.put(WorkspacePermission.WAREHOUSE_TRANSFER, field("warehouse.internal.InventoryTransferApplicationService", "ROLES"));
		enforced.put(WorkspacePermission.WAREHOUSE_STOCK_COUNT, field("warehouse.internal.StockCountApplicationService", "ROLES"));
		enforced.put(WorkspacePermission.LABEL_OPERATION, field("labeling.internal.LabelingApplicationService", "OPERATION_ROLES"));
		enforced.put(WorkspacePermission.LABEL_SELF_EMPLOYEE, field("labeling.internal.LabelingApplicationService", "LABEL_ROLES"));
		enforced.put(WorkspacePermission.LABEL_STOCK, field("labeling.internal.LabelingApplicationService", "STOCK_ROLES"));
		enforced.put(WorkspacePermission.FINANCE_ADVANCE, field("finance.internal.AdvanceApplicationService", "WRITE_ROLES"));
		enforced.put(WorkspacePermission.FINANCE_GRIR, field("finance.internal.GrirAccrualApplicationService", "WRITE_ROLES"));
		enforced.put(WorkspacePermission.FINANCE_ORDER_PROFIT, field("finance.internal.OrderProfitApplicationService", "SETTLE_ROLES"));
		enforced.put(WorkspacePermission.FINANCE_PAYABLE, field("finance.internal.PayableApplicationService", "WRITE_ROLES"));
		enforced.put(WorkspacePermission.FINANCE_RECEIVABLE, field("finance.internal.ReceivableApplicationService", "WRITE_ROLES"));
		enforced.put(WorkspacePermission.FINANCE_COST_RATE, field("finance.internal.WorkCenterCostRateApplicationService", "WRITE_ROLES"));
		enforced.put(WorkspacePermission.EQUIPMENT_ASSET, field("equipment.internal.EquipmentAssetApplicationService", "MAINTENANCE_ROLES"));
		enforced.put(WorkspacePermission.EQUIPMENT_WORK_ORDER, field("equipment.internal.EquipmentWorkOrderApplicationService", "MAINTENANCE_ROLES"));
		enforced.put(WorkspacePermission.EQUIPMENT_MAINTENANCE_PLAN, field("equipment.internal.EquipmentMaintenancePlanApplicationService", "MAINTENANCE_ROLES"));
		enforced.put(WorkspacePermission.EQUIPMENT_SPARE_PART, field("equipment.internal.EquipmentSparePartApplicationService", "MAINTENANCE_ROLES"));
		enforced.put(WorkspacePermission.EQUIPMENT_ALERT, field("equipment.internal.EquipmentAlertApplicationService", "MANAGER_ROLES"));
		enforced.put(WorkspacePermission.EQUIPMENT_OEE_MAINTAIN, field("equipment.internal.EquipmentOeeApplicationService", "MAINTAIN_ROLES"));
		enforced.put(WorkspacePermission.EQUIPMENT_OEE_APPROVE, field("equipment.internal.EquipmentOeeApplicationService", "APPROVE_ROLES"));
		enforced.put(WorkspacePermission.EQUIPMENT_TELEMETRY, field("equipment.internal.EquipmentTelemetryApplicationService", "MANAGER_ROLES"));
		enforced.put(WorkspacePermission.EQUIPMENT_TELEMETRY_LIFECYCLE, field("equipment.internal.EquipmentTelemetryLifecycleApplicationService", "MANAGER_ROLES"));
		enforced.put(WorkspacePermission.EQUIPMENT_FIELD_ACCEPTANCE_MAINTAIN, field("equipment.internal.EquipmentTelemetryFieldAcceptanceApplicationService", "MAINTAIN_ROLES"));
		enforced.put(WorkspacePermission.EQUIPMENT_FIELD_ACCEPTANCE_APPROVE, field("equipment.internal.EquipmentTelemetryFieldAcceptanceApplicationService", "APPROVE_ROLES"));

		assertThat(enforced).hasSize(52);
		for (Map.Entry<WorkspacePermission, RoleSetField> entry : enforced.entrySet()) {
			assertThat(entry.getValue().roles()).as(entry.getKey().name()).containsExactlyInAnyOrderElementsOf(entry.getKey().roleCodes());
		}
	}

	private static RoleSetField field(String classSuffix, String fieldName) throws Exception {
		Class<?> type = Class.forName("com.guanseq." + classSuffix);
		Field field = type.getDeclaredField(fieldName);
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		Set<String> roles = (Set<String>) field.get(null);
		return new RoleSetField(roles);
	}

	private record RoleSetField(Set<String> roles) {
	}
}
