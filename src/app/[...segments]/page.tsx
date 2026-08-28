import { notFound } from "next/navigation";

import { ManufacturingWorkspace } from "@/components/manufacturing-workspace";
import { getGlobalSearchIndex, getManufacturingSnapshot } from "@/services/manufacturing-service";
import { getBusinessPageWithData } from "@/services/master-data-server-service";
import { getSalesOrderPageData } from "@/services/sales-order-server-service";
import { getSalesShipmentPageData } from "@/services/sales-shipment-server-service";
import { getSalesReturnPageData } from "@/services/sales-return-server-service";
import { getPlanningDemandPageData } from "@/services/planning-demand-server-service";
import { getMrpRunPageData } from "@/services/mrp-run-server-service";
import { getMrpSuggestionPageData } from "@/services/mrp-suggestion-server-service";
import { getBomPageData } from "@/services/bom-server-service";
import { getRoutingPageData } from "@/services/routing-server-service";
import { getInventoryPageData } from "@/services/inventory-server-service";
import { getProcurementPageData } from "@/services/procurement-server-service";
import { getPurchaseReceiptPageData } from "@/services/purchase-receipt-server-service";
import { getPurchaseReturnPageData } from "@/services/purchase-return-server-service";
import { getPlanningParameterPageData } from "@/services/planning-parameter-server-service";
import { getProductionOrderPageData } from "@/services/production-order-server-service";
import { getFinalInspectionPageData, getProductionExecutionPageData } from "@/services/production-execution-server-service";
import { getIncomingInspectionPageData } from "@/services/incoming-inspection-server-service";
import { getMaterialIssuePageData } from "@/services/material-issue-server-service";
import { getOperationTaskPageData } from "@/services/operation-task-server-service";
import { getMobileProductionReportingPageData } from "@/services/mobile-production-reporting-server-service";
import { getLabelingPageData } from "@/services/labeling-server-service";
import { getPutawayPageData } from "@/services/putaway-server-service";
import { getInventoryControlPageData } from "@/services/inventory-control-server-service";
import { getOrderProfitPageData } from "@/services/order-profit-server-service";
import { getReceivablePageData } from "@/services/receivable-server-service";
import { getPayablePageData } from "@/services/payable-server-service";
import { getAccountingPeriodPageData } from "@/services/accounting-period-page-server-service";
import { getGrirAccrualPageData } from "@/services/grir-accrual-page-server-service";
import { getAdvancePageData } from "@/services/advance-page-server-service";
import { getWorkspaceUserPageData } from "@/services/workspace-user-server-service";
import { getOrganizationStructurePageData } from "@/services/organization-structure-server-service";
import { getRolePermissionPageData } from "@/services/role-permission-server-service";
import { getAuditEventPageData } from "@/services/audit-event-server-service";
import { getEquipmentAssetPageData } from "@/services/equipment-asset-server-service";
import { getEquipmentWorkOrderPageData } from "@/services/equipment-work-order-server-service";
import { getEquipmentSparePartPageData } from "@/services/equipment-spare-part-server-service";
import { getEquipmentTelemetryPageData } from "@/services/equipment-telemetry-server-service";
import { getEquipmentAlertPageData } from "@/services/equipment-alert-server-service";
import { getEquipmentOeePageData } from "@/services/equipment-oee-server-service";
import { isBackendPageUnavailable } from "@/lib/backend-page-registry";
import { requireFrontendSession } from "@/services/oidc-session-server";

type RoutePageProps = {
  params: Promise<{ segments: string[] }>;
};

export const dynamic = "force-dynamic";

export default async function RoutePage({ params }: RoutePageProps) {
  const { segments } = await params;
  const pathname = `/${segments.join("/")}`;
  await requireFrontendSession(pathname);
  const [snapshot, pageModel, searchIndex, salesOrderPage, planningDemandPage, mrpRunPage, mrpSuggestionPage, bomPage, routingPage, inventoryPage, procurementPage, planningParameterPage, productionOrderPage, productionExecutionPage, finalInspectionPage, materialIssuePage, operationTaskPage, mobileProductionReportingPage, labelingPage, putawayPage, inventoryControlPage, purchaseReceiptPage, purchaseReturnPage, incomingInspectionPage, salesShipmentPage, salesReturnPage, orderProfitPage, receivablePage, payablePage, accountingPeriodPage, grirAccrualPage, advancePage, workspaceUserPage, organizationStructurePage, rolePermissionPage, auditEventPage, equipmentAssetPage, equipmentWorkOrderPage, equipmentSparePartPage, equipmentTelemetryPage, equipmentAlertPage, equipmentOeePage] = await Promise.all([
    getManufacturingSnapshot(),
    getBusinessPageWithData(pathname),
    getGlobalSearchIndex(),
    getSalesOrderPageData(pathname),
    getPlanningDemandPageData(pathname),
    getMrpRunPageData(pathname),
    getMrpSuggestionPageData(pathname),
    getBomPageData(pathname),
    getRoutingPageData(pathname),
    getInventoryPageData(pathname),
    getProcurementPageData(pathname),
    getPlanningParameterPageData(pathname),
    getProductionOrderPageData(pathname),
    getProductionExecutionPageData(pathname),
    getFinalInspectionPageData(pathname),
    getMaterialIssuePageData(pathname),
    getOperationTaskPageData(pathname),
    getMobileProductionReportingPageData(pathname),
    getLabelingPageData(pathname),
    getPutawayPageData(pathname),
    getInventoryControlPageData(pathname),
    getPurchaseReceiptPageData(pathname),
    getPurchaseReturnPageData(pathname),
    getIncomingInspectionPageData(pathname),
    getSalesShipmentPageData(pathname),
    getSalesReturnPageData(pathname),
    getOrderProfitPageData(pathname),
    getReceivablePageData(pathname),
    getPayablePageData(pathname),
    getAccountingPeriodPageData(pathname),
    getGrirAccrualPageData(pathname),
    getAdvancePageData(pathname),
    getWorkspaceUserPageData(pathname),
    getOrganizationStructurePageData(pathname),
    getRolePermissionPageData(pathname),
    getAuditEventPageData(pathname),
    getEquipmentAssetPageData(pathname),
    getEquipmentWorkOrderPageData(pathname),
    getEquipmentSparePartPageData(pathname),
    getEquipmentTelemetryPageData(pathname),
    getEquipmentAlertPageData(pathname),
    getEquipmentOeePageData(pathname),
  ]);
  if (!pageModel) notFound();
  const backendPageUnavailable = isBackendPageUnavailable(pathname, {
    masterData: pageModel.dataSource === "backend" ? pageModel : null,
    salesOrder: salesOrderPage,
    salesShipment: salesShipmentPage,
    salesReturn: salesReturnPage,
    planningDemand: planningDemandPage,
    mrpRun: mrpRunPage,
    mrpSuggestion: mrpSuggestionPage,
    planningParameter: planningParameterPage,
    bom: bomPage,
    routing: routingPage,
    inventory: inventoryPage,
    materialIssue: materialIssuePage,
    procurementOrder: procurementPage,
    purchaseReceipt: purchaseReceiptPage,
    purchaseReturn: purchaseReturnPage,
    productionOrder: productionOrderPage,
    operationTask: operationTaskPage,
    mobileReporting: mobileProductionReportingPage,
    labeling: labelingPage,
    putaway: putawayPage,
    inventoryControl: inventoryControlPage,
    productionExecution: productionExecutionPage,
    finalInspection: finalInspectionPage,
    incomingInspection: incomingInspectionPage,
    orderProfit: orderProfitPage,
    receivable: receivablePage,
    payable: payablePage,
    accountingPeriod: accountingPeriodPage,
    grirAccrual: grirAccrualPage,
    advance: advancePage,
    workspaceUser: workspaceUserPage,
    organizationStructure: organizationStructurePage,
    rolePermission: rolePermissionPage,
    auditEvent: auditEventPage,
    equipmentAsset: equipmentAssetPage,
    equipmentWorkOrder: equipmentWorkOrderPage,
    equipmentSparePart: equipmentSparePartPage,
    equipmentTelemetry: equipmentTelemetryPage,
    equipmentAlert: equipmentAlertPage,
    equipmentOee: equipmentOeePage,
  });
  return <ManufacturingWorkspace backendPageUnavailable={backendPageUnavailable} initialSnapshot={snapshot} initialPageModel={pageModel} initialSearchIndex={searchIndex} initialSalesOrderPage={salesOrderPage} initialPlanningDemandPage={planningDemandPage} initialMrpRunPage={mrpRunPage} initialMrpSuggestionPage={mrpSuggestionPage} initialBomPage={bomPage} initialRoutingPage={routingPage} initialInventoryPage={inventoryPage} initialProcurementPage={procurementPage} initialPlanningParameterPage={planningParameterPage} initialProductionOrderPage={productionOrderPage} initialProductionExecutionPage={productionExecutionPage} initialFinalInspectionPage={finalInspectionPage} initialMaterialIssuePage={materialIssuePage} initialOperationTaskPage={operationTaskPage} initialMobileProductionReportingPage={mobileProductionReportingPage} initialLabelingPage={labelingPage} initialPutawayPage={putawayPage} initialInventoryControlPage={inventoryControlPage} initialPurchaseReceiptPage={purchaseReceiptPage} initialPurchaseReturnPage={purchaseReturnPage} initialIncomingInspectionPage={incomingInspectionPage} initialSalesShipmentPage={salesShipmentPage} initialSalesReturnPage={salesReturnPage} initialOrderProfitPage={orderProfitPage} initialReceivablePage={receivablePage} initialPayablePage={payablePage} initialAccountingPeriodPage={accountingPeriodPage} initialGrirAccrualPage={grirAccrualPage} initialAdvancePage={advancePage} initialWorkspaceUserPage={workspaceUserPage} initialOrganizationStructurePage={organizationStructurePage} initialRolePermissionPage={rolePermissionPage} initialAuditEventPage={auditEventPage} initialEquipmentAssetPage={equipmentAssetPage} initialEquipmentWorkOrderPage={equipmentWorkOrderPage} initialEquipmentSparePartPage={equipmentSparePartPage} initialEquipmentTelemetryPage={equipmentTelemetryPage} initialEquipmentAlertPage={equipmentAlertPage} initialEquipmentOeePage={equipmentOeePage} />;
}

