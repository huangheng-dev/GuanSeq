package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import org.junit.jupiter.api.Test;

class OpenApiContractTest {

	@Test
	void publishesVersionedPlatformWorkspaceAndMasterDataContracts() throws IOException {
		try (var stream = getClass().getResourceAsStream("/openapi/guanseq-api-v1.yaml")) {
			assertThat(stream).isNotNull();
			String contract = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			Map<String, Object> parsed = new Yaml().loadAs(contract, Map.class);
			assertThat(parsed).containsKey("openapi");
			assertThat(parsed.get("openapi")).isEqualTo("3.1.0");
			Map<String, Object> components = (Map<String, Object>) parsed.get("components");
			Map<String, Object> securitySchemes = (Map<String, Object>) components.get("securitySchemes");
			Map<String, Object> responses = (Map<String, Object>) components.get("responses");
			Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
			assertThat(securitySchemes).containsKey("bearerAuth").doesNotContainKey("basicAuth");
			assertThat((Map<String, Object>) securitySchemes.get("bearerAuth"))
					.containsEntry("type", "http")
					.containsEntry("scheme", "bearer")
					.containsEntry("bearerFormat", "JWT");
			assertThat(responses).containsKeys("ValidationFailed", "AuthenticationRequired", "AccessDenied",
					"ResourceNotFound", "BusinessConflict", "BusinessRuleViolation", "InternalError");
			assertThat(schemas).containsKeys("ApiError", "FieldViolation");
			assertThat(contract)
					.contains("openapi: 3.1.0")
					.contains("/api/v1/platform/status:")
					.contains("/api/v1/bootstrap/initial-workspace:")
					.contains("/api/v1/identity/workspace-users:")
					.contains("/api/v1/identity/workspace-users/{userId}/actions:")
					.contains("/api/v1/equipment/assets:")
					.contains("/api/v1/equipment/assets/{id}:")
					.contains("/api/v1/equipment/assets/{id}/actions:")
					.contains("/api/v1/me/workspaces:")
					.contains("/api/v1/me/current-workspace:")
					.contains("/api/v1/masterdata/customers:")
					.contains("/api/v1/masterdata/customers/batch:")
					.contains("/api/v1/masterdata/materials:")
					.contains("/api/v1/masterdata/materials/batch:")
					.contains("/api/v1/sales/orders:")
					.contains("/api/v1/sales/orders/{id}/actions:")
					.contains("/api/v1/sales/shipments:")
					.contains("/api/v1/sales/shipments/{id}:")
					.contains("/api/v1/sales/shipment-reference-data:")
					.contains("/api/v1/sales/order-reference-data:")
					.contains("/api/v1/finance/receivable-invoices:")
					.contains("/api/v1/finance/receivable-invoices/{id}/receipts:")
					.contains("/api/v1/finance/receivable-reference-data:")
					.contains("/api/v1/finance/payable-invoices:")
					.contains("/api/v1/finance/payable-invoices/{id}/payments:")
					.contains("/api/v1/finance/payable-reference-data:")
					.contains("/api/v1/finance/work-center-cost-rates:")
					.contains("/api/v1/finance/work-center-cost-rates/{id}/status:")
					.contains("/api/v1/planning/independent-demands:")
					.contains("/api/v1/planning/independent-demands/{id}/actions:")
					.contains("/api/v1/planning/mrp-inputs:")
					.contains("/api/v1/planning/mrp-runs:")
					.contains("/api/v1/planning/mrp-runs/{id}:")
					.contains("/api/v1/planning/mrp-suggestions:")
					.contains("/api/v1/planning/mrp-suggestions/{id}/actions:")
					.contains("/api/v1/planning/mrp-suggestions/{id}/convert:")
					.contains("/api/v1/product/boms:")
					.contains("/api/v1/product/boms/{id}:")
					.contains("/api/v1/product/boms/{id}/actions:")
					.contains("/api/v1/product/bom-reference-data:")
					.contains("/api/v1/product/routings:")
					.contains("/api/v1/product/routings/{id}/actions:")
					.contains("/api/v1/product/routing-reference-data:")
					.contains("/api/v1/warehouse/inventory-balances:")
					.contains("/api/v1/warehouse/inventory-balances/{id}/movements:")
					.contains("/api/v1/warehouse/inventory-reference-data:")
					.contains("/api/v1/procurement/orders:")
					.contains("/api/v1/procurement/orders/{id}/actions:")
					.contains("/api/v1/procurement/order-reference-data:")
					.contains("/api/v1/procurement/receipts:")
					.contains("/api/v1/procurement/receipt-reference-data:")
					.contains("/api/v1/production/orders:")
					.contains("/api/v1/production/orders/{id}/actions:")
					.contains("/api/v1/production/operation-labor-entries:")
					.contains("/api/v1/production/operation-labor-entries/{id}/actions:")
					.contains("/api/v1/production/order-reference-data:")
					.contains("/api/v1/production/work-reports:")
					.contains("/api/v1/production/work-reports/{id}/settle:")
				.contains("/api/v1/production/material-issues:")
				.contains("/api/v1/production/material-issues/{id}/actions:")
				.contains("/api/v1/production/material-issues/{id}/returns:")
				.contains("/api/v1/production/material-issue-reference-data:")
					.contains("/api/v1/quality/final-inspections:")
					.contains("/api/v1/quality/final-inspections/{id}/complete:")
					.contains("/api/v1/quality/incoming-inspections:")
					.contains("/api/v1/quality/incoming-inspections/{id}/complete:")
					.contains("/api/v1/planning/material-parameters:")
					.contains("X-Request-Id:")
					.contains("PlatformStatus:")
					.contains("InitialWorkspaceBootstrapRequest:")
					.contains("InitialWorkspaceBootstrapResponse:")
					.contains("WorkspaceUserPage:")
					.contains("WorkspaceUserActionRequest:")
					.contains("EquipmentAssetPage:")
					.contains("EquipmentAsset:")
					.contains("EquipmentAssetEvent:")
					.contains("CreateEquipmentAssetRequest:")
					.contains("UpdateEquipmentAssetRequest:")
					.contains("EquipmentAssetActionRequest:")
					.contains("WorkspaceSession:")
					.contains("SwitchWorkspaceRequest:")
					.contains("CustomerPage:")
					.contains("MaterialPage:")
					.contains("MasterDataBatchRequest:")
					.contains("SalesOrder:")
					.contains("SalesShipment:")
					.contains("CreateSalesShipmentRequest:")
					.contains("SalesShipmentReferenceData:")
					.contains("SalesOrderActionRequest:")
					.contains("SalesOrderReferenceData:")
					.contains("IndependentDemand:")
					.contains("IndependentDemandActionRequest:")
					.contains("PlanningDemandReferenceData:")
					.contains("MrpRun:")
					.contains("MrpDemandSnapshot:")
					.contains("MrpSupplySnapshot:")
					.contains("MrpRunException:")
					.contains("MrpSuggestion:")
					.contains("MrpSuggestionActionRequest:")
					.contains("MrpSuggestionConvertRequest:")
					.contains("CreateMrpRunRequest:");
			assertThat(contract)
					.contains("ReceivableInvoicePage:")
					.contains("ReceivableInvoice:")
					.contains("CreateReceivableInvoiceRequest:")
					.contains("PostReceivableReceiptRequest:")
					.contains("ReceivableReferenceData:")
					.contains("PayableInvoicePage:")
					.contains("PayableInvoice:")
					.contains("CreatePayableInvoiceRequest:")
					.contains("PostPayablePaymentRequest:")
					.contains("PayableReferenceData:")
					.contains("WorkCenterCostRatePage:")
					.contains("WorkCenterCostRate:")
					.contains("CreateWorkCenterCostRateRequest:")
					.contains("ChangeWorkCenterCostRateStatusRequest:")
					.contains("ProductionWorkReport:")
					.contains("CreateProductionWorkReportRequest:")
					.contains("OperationLaborEntryPage:")
					.contains("OperationLaborEntry:")
					.contains("CreateOperationLaborEntryRequest:")
					.contains("OperationLaborEntryActionRequest:")
					.contains("FinalInspection:")
					.contains("CompleteFinalInspectionRequest:");
			assertThat(contract)
					.contains("PurchaseOrderPage:")
					.contains("PurchaseOrderActionRequest:")
					.contains("PurchaseReceiptPage:")
					.contains("CreatePurchaseReceiptRequest:")
					.contains("IncomingInspection:")
					.contains("CompleteIncomingInspectionRequest:")
					.contains("MaterialPlanningParameterPage:")
					.contains("MrpScheduledReceiptSnapshot:");
			assertThat(contract)
					.contains("InventoryPage:")
					.contains("InventoryBalance:")
					.contains("InventoryMovement:")
					.contains("InventoryMovementRequest:")
					.contains("InventoryReferenceData:")
				.contains("MaterialIssuePage:")
				.contains("MaterialIssue:")
				.contains("CreateMaterialIssueRequest:")
				.contains("MaterialIssueActionRequest:")
				.contains("MaterialReturnRequest:")
				.contains("MaterialIssueReferenceData:");
			assertThat(contract)
					.contains("BomPage:")
					.contains("BomLine:")
					.contains("BomEvent:")
					.contains("CreateBomRequest:")
					.contains("UpdateBomRequest:")
					.contains("BomActionRequest:")
					.contains("BomReferenceData:");
		}
	}
}

