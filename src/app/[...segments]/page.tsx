import { notFound } from "next/navigation";

import { ManufacturingWorkspace } from "@/components/manufacturing-workspace";
import { getGlobalSearchIndex, getManufacturingSnapshot } from "@/services/manufacturing-service";
import { getBusinessPageWithData } from "@/services/master-data-server-service";
import { getSalesOrderPageData } from "@/services/sales-order-server-service";
import { getSalesShipmentPageData } from "@/services/sales-shipment-server-service";
import { getPlanningDemandPageData } from "@/services/planning-demand-server-service";
import { getMrpRunPageData } from "@/services/mrp-run-server-service";
import { getMrpSuggestionPageData } from "@/services/mrp-suggestion-server-service";
import { getBomPageData } from "@/services/bom-server-service";
import { getRoutingPageData } from "@/services/routing-server-service";
import { getInventoryPageData } from "@/services/inventory-server-service";
import { getProcurementPageData } from "@/services/procurement-server-service";
import { getPurchaseReceiptPageData } from "@/services/purchase-receipt-server-service";
import { getPlanningParameterPageData } from "@/services/planning-parameter-server-service";
import { getProductionOrderPageData } from "@/services/production-order-server-service";
import { getFinalInspectionPageData, getProductionExecutionPageData } from "@/services/production-execution-server-service";
import { getIncomingInspectionPageData } from "@/services/incoming-inspection-server-service";
import { getMaterialIssuePageData } from "@/services/material-issue-server-service";
import { getOperationTaskPageData } from "@/services/operation-task-server-service";
import { getOrderProfitPageData } from "@/services/order-profit-server-service";
import { getReceivablePageData } from "@/services/receivable-server-service";
import { getPayablePageData } from "@/services/payable-server-service";
import { getAccountingPeriodPageData } from "@/services/accounting-period-page-server-service";
import { getGrirAccrualPageData } from "@/services/grir-accrual-page-server-service";
import { getAdvancePageData } from "@/services/advance-page-server-service";
import { getWorkspaceUserPageData } from "@/services/workspace-user-server-service";
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
  const [snapshot, pageModel, searchIndex, salesOrderPage, planningDemandPage, mrpRunPage, mrpSuggestionPage, bomPage, routingPage, inventoryPage, procurementPage, planningParameterPage, productionOrderPage, productionExecutionPage, finalInspectionPage, materialIssuePage, operationTaskPage, purchaseReceiptPage, incomingInspectionPage, salesShipmentPage, orderProfitPage, receivablePage, payablePage, accountingPeriodPage, grirAccrualPage, advancePage, workspaceUserPage] = await Promise.all([
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
    getPurchaseReceiptPageData(pathname),
    getIncomingInspectionPageData(pathname),
    getSalesShipmentPageData(pathname),
    getOrderProfitPageData(pathname),
    getReceivablePageData(pathname),
    getPayablePageData(pathname),
    getAccountingPeriodPageData(pathname),
    getGrirAccrualPageData(pathname),
    getAdvancePageData(pathname),
    getWorkspaceUserPageData(pathname),
  ]);
  if (!pageModel) notFound();
  const backendPageUnavailable = isBackendPageUnavailable(pathname, {
    masterData: pageModel.dataSource === "backend" ? pageModel : null,
    salesOrder: salesOrderPage,
    salesShipment: salesShipmentPage,
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
    productionOrder: productionOrderPage,
    operationTask: operationTaskPage,
    productionExecution: productionExecutionPage,
    finalInspection: finalInspectionPage,
    incomingInspection: incomingInspectionPage,
    orderProfit: orderProfitPage,
    receivable: receivablePage,
    payable: payablePage,
    accountingPeriod: accountingPeriodPage,
    grirAccrual: grirAccrualPage,
    advance: advancePage,
  });
  return <ManufacturingWorkspace backendPageUnavailable={backendPageUnavailable} initialSnapshot={snapshot} initialPageModel={pageModel} initialSearchIndex={searchIndex} initialSalesOrderPage={salesOrderPage} initialPlanningDemandPage={planningDemandPage} initialMrpRunPage={mrpRunPage} initialMrpSuggestionPage={mrpSuggestionPage} initialBomPage={bomPage} initialRoutingPage={routingPage} initialInventoryPage={inventoryPage} initialProcurementPage={procurementPage} initialPlanningParameterPage={planningParameterPage} initialProductionOrderPage={productionOrderPage} initialProductionExecutionPage={productionExecutionPage} initialFinalInspectionPage={finalInspectionPage} initialMaterialIssuePage={materialIssuePage} initialOperationTaskPage={operationTaskPage} initialPurchaseReceiptPage={purchaseReceiptPage} initialIncomingInspectionPage={incomingInspectionPage} initialSalesShipmentPage={salesShipmentPage} initialOrderProfitPage={orderProfitPage} initialReceivablePage={receivablePage} initialPayablePage={payablePage} initialAccountingPeriodPage={accountingPeriodPage} initialGrirAccrualPage={grirAccrualPage} initialAdvancePage={advancePage} initialWorkspaceUserPage={workspaceUserPage} />;
}

