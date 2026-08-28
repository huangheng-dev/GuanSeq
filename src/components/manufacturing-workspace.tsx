"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useOverlayFocus } from "@/hooks/use-overlay-focus";
import type { BusinessPageModel } from "@/lib/business-page-data";
import { brandIdentity } from "@/lib/brand-identity";
import type {
  GlobalSearchItem,
  ManufacturingSnapshot,
  WorkspaceSession,
  WorkspaceSummary,
  WorkOrder,
} from "@/lib/contracts";
import type { SalesOrderPageData } from "@/services/sales-order-server-service";
import type { PlanningDemandPageData } from "@/services/planning-demand-server-service";
import {
  areaPath,
  childPath,
  modulePath,
  productAreas,
  resolveProductRoute,
} from "@/lib/product-navigation";
import {
  capabilityMaturityMeta,
  getCapabilityMaturity,
  type CapabilityMaturity,
} from "@/lib/capability-maturity";
import {
  defaultUserProfile,
  readUserProfile,
} from "@/services/front-end-product-service";
import {
  loadWorkspaceSession,
  selectWorkspace,
} from "@/services/workspace-client-service";
import { ProfileDialog, SessionExitDialog } from "./account-center";
import { GsButton, GsDrawerHost, GsInput } from "./ui";
import { MaterialIcon } from "./material-icon";
import { AiAssistant } from "./ai-assistant";
import { BusinessWorkspace } from "./business-workspace";
import { SalesOrderWorkspace } from "./sales-order-workspace";
import type { SalesShipmentPageData } from "@/services/sales-shipment-server-service";
import { SalesShipmentWorkspace } from "./sales-shipment-workspace";
import type { SalesReturnPageData } from "@/services/sales-return-server-service";
import { SalesReturnWorkspace } from "./sales-return-workspace";
import type { OrderProfitPageData } from "@/services/order-profit-server-service";
import { OrderProfitWorkspace } from "./order-profit-workspace";
import type { ReceivablePageData } from "@/services/receivable-server-service";
import { ReceivableWorkspace } from "./receivable-workspace";
import type { PayablePageData } from "@/services/payable-server-service";
import { PayableWorkspace } from "./payable-workspace";
import type { AccountingPeriodPageData } from "@/services/accounting-period-page-server-service";
import { AccountingPeriodWorkspace } from "./accounting-period-workspace";
import type { GrirAccrualPageData } from "@/services/grir-accrual-page-server-service";
import { GrirAccrualWorkspace } from "./grir-accrual-workspace";
import type { AdvancePageData } from "@/services/advance-page-server-service";
import { AdvanceWorkspace } from "./advance-workspace";
import { PlanningDemandWorkspace } from "./planning-demand-workspace";
import { MrpRunWorkspace } from "./mrp-run-workspace";
import type { MrpRunPageData } from "@/services/mrp-run-server-service";
import type { MrpSuggestionPageData } from "@/services/mrp-suggestion-server-service";
import { MrpSuggestionWorkspace } from "./mrp-suggestion-workspace";
import type { BomPageData } from "@/services/bom-server-service";
import { BomWorkspace } from "./bom-workspace";
import type { RoutingPageData } from "@/services/routing-server-service";
import { RoutingWorkspace } from "./routing-workspace";
import { GuanSeqLogo } from "./guanseq-logo";
import type { InventoryPageData } from "@/services/inventory-server-service";
import { InventoryWorkspace } from "./inventory-workspace";
import type { ProcurementPageData } from "@/services/procurement-server-service";
import { ProcurementOrderWorkspace } from "./procurement-order-workspace";
import type { PurchaseReceiptPageData } from "@/services/purchase-receipt-server-service";
import { PurchaseReceiptWorkspace } from "./purchase-receipt-workspace";
import { MobilePurchaseReceiptWorkspace } from "./mobile-purchase-receipt-workspace";
import type { PurchaseReturnPageData } from "@/services/purchase-return-server-service";
import { PurchaseReturnWorkspace } from "./purchase-return-workspace";
import type { PlanningParameterPageData } from "@/services/planning-parameter-server-service";
import { PlanningParameterWorkspace } from "./planning-parameter-workspace";
import type { ProductionOrderPageData } from "@/services/production-order-server-service";
import { ProductionOrderWorkspace } from "./production-order-workspace";
import type {
  FinalInspectionPageData,
  ProductionExecutionPageData,
} from "@/services/production-execution-server-service";
import { ProductionExecutionWorkspace } from "./production-execution-workspace";
import { FinalInspectionWorkspace } from "./final-inspection-workspace";
import type { IncomingInspectionPageData } from "@/services/incoming-inspection-server-service";
import { IncomingInspectionWorkspace } from "./incoming-inspection-workspace";
import type { MaterialIssuePageData } from "@/services/material-issue-server-service";
import { MaterialIssueWorkspace } from "./material-issue-workspace";
import { MobileMaterialIssueWorkspace } from "./mobile-material-issue-workspace";
import type { MobileProductionReportingPageData } from "@/services/mobile-production-reporting-server-service";
import { MobileProductionReportingWorkspace } from "./mobile-production-reporting-workspace";
import type { LabelingPageData } from "@/services/labeling-server-service";
import { LabelingWorkspace } from "./labeling-workspace";
import type { PutawayPageData } from "@/services/putaway-server-service";
import { PutawayWorkspace } from "./putaway-workspace";
import type { InventoryControlPageData } from "@/services/inventory-control-server-service";
import { InventoryControlWorkspace } from "./inventory-control-workspace";
import type { OperationTaskPageData } from "@/services/operation-task-server-service";
import { OperationTaskWorkspace } from "./operation-task-workspace";
import type { WorkspaceUserPageData } from "@/services/workspace-user-server-service";
import { WorkspaceUserWorkspace } from "./workspace-user-workspace";
import type { OrganizationStructurePageData } from "@/services/organization-structure-server-service";
import { OrganizationStructureWorkspace } from "./organization-structure-workspace";
import type { RolePermissionPageData } from "@/services/role-permission-server-service";
import { RolePermissionWorkspace } from "./role-permission-workspace";
import type { EquipmentAssetPageData } from "@/services/equipment-asset-server-service";
import { EquipmentAssetWorkspace } from "./equipment-asset-workspace";
import type { EquipmentWorkOrderPageData } from "@/services/equipment-work-order-server-service";
import { EquipmentWorkOrderWorkspace } from "./equipment-work-order-workspace";
import type { EquipmentSparePartPageData } from "@/services/equipment-spare-part-server-service";
import { EquipmentSparePartWorkspace } from "./equipment-spare-part-workspace";
import type { EquipmentTelemetryPageData } from "@/services/equipment-telemetry-server-service";
import { EquipmentTelemetryWorkspace } from "./equipment-telemetry-workspace";
import type { EquipmentAlertPageData } from "@/services/equipment-alert-server-service";
import { EquipmentAlertWorkspace } from "./equipment-alert-workspace";
import type { EquipmentOeePageData } from "@/services/equipment-oee-server-service";
import { EquipmentOeeWorkspace } from "./equipment-oee-workspace";
type ManufacturingWorkspaceProps = {
  backendPageUnavailable?: boolean;
  initialSnapshot: ManufacturingSnapshot;
  initialPageModel?: BusinessPageModel;
  initialSearchIndex: GlobalSearchItem[];
  initialSalesOrderPage?: SalesOrderPageData | null;
  initialSalesShipmentPage?: SalesShipmentPageData | null;
  initialSalesReturnPage?: SalesReturnPageData | null;
  initialOrderProfitPage?: OrderProfitPageData | null;
  initialReceivablePage?: ReceivablePageData | null;
  initialPayablePage?: PayablePageData | null;
  initialAccountingPeriodPage?: AccountingPeriodPageData | null;
  initialGrirAccrualPage?: GrirAccrualPageData | null;
  initialAdvancePage?: AdvancePageData | null;
  initialPlanningDemandPage?: PlanningDemandPageData | null;
  initialMrpRunPage?: MrpRunPageData | null;
  initialMrpSuggestionPage?: MrpSuggestionPageData | null;
  initialBomPage?: BomPageData | null;
  initialRoutingPage?: RoutingPageData | null;
  initialInventoryPage?: InventoryPageData | null;
  initialProcurementPage?: ProcurementPageData | null;
  initialPurchaseReceiptPage?: PurchaseReceiptPageData | null;
  initialPurchaseReturnPage?: PurchaseReturnPageData | null;
  initialPlanningParameterPage?: PlanningParameterPageData | null;
  initialProductionOrderPage?: ProductionOrderPageData | null;
  initialProductionExecutionPage?: ProductionExecutionPageData | null;
  initialFinalInspectionPage?: FinalInspectionPageData | null;
  initialIncomingInspectionPage?: IncomingInspectionPageData | null;
  initialMaterialIssuePage?: MaterialIssuePageData | null;
  initialOperationTaskPage?: OperationTaskPageData | null;
  initialMobileProductionReportingPage?: MobileProductionReportingPageData | null;
  initialLabelingPage?: LabelingPageData | null;
  initialPutawayPage?: PutawayPageData | null;
  initialInventoryControlPage?: InventoryControlPageData | null;
  initialWorkspaceUserPage?: WorkspaceUserPageData | null;
  initialOrganizationStructurePage?: OrganizationStructurePageData | null;
  initialRolePermissionPage?: RolePermissionPageData | null;
  initialEquipmentAssetPage?: EquipmentAssetPageData | null;
  initialEquipmentWorkOrderPage?: EquipmentWorkOrderPageData | null;
  initialEquipmentSparePartPage?: EquipmentSparePartPageData | null;
  initialEquipmentTelemetryPage?: EquipmentTelemetryPageData | null;
  initialEquipmentAlertPage?: EquipmentAlertPageData | null;
  initialEquipmentOeePage?: EquipmentOeePageData | null;
};
type SearchResult =
  | {
      type: "生产工单";
      title: string;
      detail: string;
      order: WorkOrder;
      href?: never;
    }
  | {
      type: "功能栏目" | "功能页面";
      title: string;
      detail: string;
      href: string;
      order?: never;
    }
  | (Omit<GlobalSearchItem, "keywords"> & {
      order?: never;
    });
type NavigationSearchResult = Extract<
  SearchResult,
  {
    type: "功能栏目" | "功能页面";
  }
>;
const navigationSearchItems: NavigationSearchResult[] = productAreas.flatMap(
  (area) => [
    {
      type: "功能栏目" as const,
      title: area.label,
      detail: area.description,
      href: areaPath(area),
    },
    ...area.modules.flatMap((moduleItem) => [
      {
        type: "功能页面" as const,
        title: moduleItem.label,
        detail: `${area.label} · ${area.description}`,
        href: modulePath(area, moduleItem),
      },
      ...(moduleItem.children?.map((itemChild) => ({
        type: "功能页面" as const,
        title: itemChild.label,
        detail: `${area.label} · ${moduleItem.label}`,
        href: childPath(area, moduleItem, itemChild),
      })) ?? []),
    ]),
  ],
);
const overviewNavigationItem: NavigationSearchResult = {
  type: "功能页面",
  title: "制造经营总览",
  detail: "核心 · 经营总览",
  href: "/",
};
function resolveQuickAccessItem(path: string) {
  return path === "/"
    ? overviewNavigationItem
    : navigationSearchItems.find((item) => item.href === path);
}
const defaultFavoritePaths = [
  "/sales/orders/list",
  "/production/work-orders/operations",
  "/warehouse/inventory/on-hand",
];
const statusLabels = {
  done: "已完成",
  active: "执行中",
  warning: "有风险",
  pending: "待处理",
} as const;
const flowDestinations: Record<string, string> = {
  order: "/sales/orders/list",
  plan: "/planning/mrp/runs",
  supply: "/procurement/orders",
  execute: "/production/work-orders/operations",
  quality: "/quality/final",
  delivery: "/warehouse/sales-shipping/picking",
};
const alertDestinations: Record<string, string> = {
  "A-018": "/planning/mrp/recommendations",
  "A-014": "/planning/capacity",
  "A-009": "/quality/final",
};
function downloadOverviewReport(snapshot: ManufacturingSnapshot) {
  const rows = [
    ["GuanSeq 制造经营日报", snapshot.workspace.date],
    ["工作区", snapshot.workspace.name],
    ["公司", snapshot.workspace.company],
    [],
    ["关键指标", "当前值", "变化"],
    ...snapshot.metrics.map((metric) => [
      metric.label,
      metric.value,
      metric.change,
    ]),
    [],
    ["重点工单", "产品", "客户", "车间", "进度", "状态", "交期"],
    ...snapshot.workOrders.map((order) => [
      order.id,
      order.product,
      order.customer,
      order.workshop,
      `${order.progress}%`,
      order.status,
      `2026-${order.dueDate}`,
    ]),
    [],
    ["风险", "详情", "责任人"],
    ...snapshot.alerts.map((alert) => [alert.title, alert.detail, alert.owner]),
  ];
  const escapeCell = (value: string) => `"${value.replaceAll('"', '""')}"`;
  const csv = rows
    .map((row) => row.map((cell) => escapeCell(String(cell))).join(","))
    .join("\n");
  const href = URL.createObjectURL(
    new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" }),
  );
  const anchor = document.createElement("a");
  anchor.href = href;
  anchor.download = `GuanSeq-制造经营日报-${new Date().toISOString().slice(0, 10)}.csv`;
  anchor.click();
  URL.revokeObjectURL(href);
}
function OrderDetail({
  order,
  onClose,
}: {
  order: WorkOrder;
  onClose: () => void;
}) {
  const drawerRef = useRef<HTMLElement>(null);
  return (
    <GsDrawerHost onClose={onClose}>
      <aside
        ref={drawerRef}
        className="orderDrawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="order-drawer-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header>
          <div>
            <h2 id="order-drawer-title">{order.product}</h2>
          </div>
          <GsButton
            className="iconButton"
            onClick={onClose}
            aria-label="关闭工单详情"
            htmlType="submit"
          >
            <MaterialIcon name="close" />
          </GsButton>
        </header>
        <div className="drawerStatus">
          <span className={`orderStatus orderStatus${order.status}`}>
            {order.status}
          </span>
          <strong>{order.progress}%</strong>
          <small>整体执行进度</small>
          <div className="progressTrack">
            <span style={{ width: `${order.progress}%` }} />
          </div>
        </div>
        <dl className="detailLedger">
          <div>
            <dt>客户</dt>
            <dd>{order.customer}</dd>
          </div>
          <div>
            <dt>生产车间</dt>
            <dd>{order.workshop}</dd>
          </div>
          <div>
            <dt>计划数量</dt>
            <dd>{order.quantity}</dd>
          </div>
          <div>
            <dt>要求完工</dt>
            <dd>2026-{order.dueDate}</dd>
          </div>
          <div>
            <dt>责任人</dt>
            <dd>车间主管 · 王峻</dd>
          </div>
          <div>
            <dt>当前工序</dt>
            <dd>总装 / 电气联调</dd>
          </div>
        </dl>
        <section className="drawerSection">
          <div className="sectionTitleCompact">
            <h3>执行证据</h3>
            <span>最近更新 10:42</span>
          </div>
          <ol className="evidenceTimeline">
            <li>
              <span />
              <div>
                <strong>工序报工完成</strong>
                <small>电气装配 · 合格 8 台 · 操作人 陈磊</small>
              </div>
              <time>10:42</time>
            </li>
            <li>
              <span />
              <div>
                <strong>生产领料完成</strong>
                <small>按批次领用 6 类物料，差异 0</small>
              </div>
              <time>08:16</time>
            </li>
            <li>
              <span />
              <div>
                <strong>工单下达</strong>
                <small>由生产计划 PP-260812-06 生成</small>
              </div>
              <time>08-13</time>
            </li>
          </ol>
        </section>
        <footer>
          <GsButton
            className="secondaryButton"
            onClick={onClose}
            htmlType="submit"
          >
            返回列表
          </GsButton>
          <Link className="primaryButton" href="/production/orders/list">
            <MaterialIcon name="assignment" size={18} />
            查看完整工单
          </Link>
        </footer>
      </aside>
    </GsDrawerHost>
  );
}

function BackendUnavailableState({ title }: { title: string }) {
  return (
    <div
      className="businessPage backendUnavailablePage"
      role="alert"
      aria-live="polite"
    >
      <header className="pageHeading businessPageHeading">
        <div className="pageTitleGroup">
          <span className="pageTitleIcon">
            <MaterialIcon name="cloud_off" size={23} />
          </span>
          <div>
            <h2>{title}</h2>
            <p>正式业务服务当前未连接，页面已停止展示模拟业务记录。</p>
          </div>
        </div>
      </header>
      <section className="businessLedger">
        <div className="businessEmptyState">
          <span>
            <MaterialIcon name="sync_problem" size={25} />
          </span>
          <strong>暂时无法读取正式业务数据</strong>
          <p>
            请确认 GuanSeq Server、当前身份和网络连接后重试。本页不会回退到
            Mock，也不会接受业务提交。
          </p>
          <GsButton
            className="primaryButton"
            onClick={() => window.location.reload()}
            htmlType="button"
          >
            <MaterialIcon name="refresh" size={18} />
            重新加载
          </GsButton>
        </div>
      </section>
    </div>
  );
}

function CapabilityMaturityBadge({ pathname }: { pathname: string }) {
  const maturity = getCapabilityMaturity(pathname) ?? "mock";
  return (
    <span className={`capabilityMaturityBadge capabilityMaturityBadge${maturity}`}>
      {capabilityMaturityMeta[maturity].label}
    </span>
  );
}

function CapabilityMaturityNotice({ pathname }: { pathname: string }) {
  const maturity: CapabilityMaturity = getCapabilityMaturity(pathname) ?? "mock";
  const meta = capabilityMaturityMeta[maturity];
  return (
    <section className={`capabilityMaturityNotice capabilityMaturityNotice${maturity}`} aria-label="能力成熟度">
      <span>能力成熟度</span>
      <strong>{meta.pageLabel}</strong>
      <p>{meta.description}</p>
    </section>
  );
}
export function ManufacturingWorkspace({
  backendPageUnavailable,
  initialSnapshot,
  initialPageModel,
  initialSearchIndex,
  initialSalesOrderPage,
  initialPlanningDemandPage,
  initialMrpRunPage,
  initialMrpSuggestionPage,
  initialBomPage,
  initialRoutingPage,
  initialInventoryPage,
  initialProcurementPage,
  initialPlanningParameterPage,
  initialProductionOrderPage,
  initialProductionExecutionPage,
  initialFinalInspectionPage,
  initialMaterialIssuePage,
  initialOperationTaskPage,
  initialMobileProductionReportingPage,
  initialLabelingPage,
  initialPutawayPage,
  initialInventoryControlPage,
  initialPurchaseReceiptPage,
  initialPurchaseReturnPage,
  initialIncomingInspectionPage,
  initialSalesShipmentPage,
  initialSalesReturnPage,
  initialOrderProfitPage,
  initialReceivablePage,
  initialPayablePage,
  initialAccountingPeriodPage,
  initialGrirAccrualPage,
  initialAdvancePage,
  initialWorkspaceUserPage,
  initialOrganizationStructurePage,
  initialRolePermissionPage,
  initialEquipmentAssetPage,
  initialEquipmentWorkOrderPage,
  initialEquipmentSparePartPage,
  initialEquipmentTelemetryPage,
  initialEquipmentAlertPage,
  initialEquipmentOeePage,
}: ManufacturingWorkspaceProps) {
  const pathname = usePathname();
  return (
    <ManufacturingWorkspaceContent
      key={pathname}
      backendPageUnavailable={backendPageUnavailable}
      initialSnapshot={initialSnapshot}
      initialPageModel={initialPageModel}
      initialSearchIndex={initialSearchIndex}
      initialSalesOrderPage={initialSalesOrderPage}
      initialPlanningDemandPage={initialPlanningDemandPage}
      initialMrpRunPage={initialMrpRunPage}
      initialMrpSuggestionPage={initialMrpSuggestionPage}
      initialBomPage={initialBomPage}
      initialRoutingPage={initialRoutingPage}
      initialInventoryPage={initialInventoryPage}
      initialProcurementPage={initialProcurementPage}
      initialPlanningParameterPage={initialPlanningParameterPage}
      initialProductionOrderPage={initialProductionOrderPage}
      initialProductionExecutionPage={initialProductionExecutionPage}
      initialFinalInspectionPage={initialFinalInspectionPage}
      initialMaterialIssuePage={initialMaterialIssuePage}
      initialOperationTaskPage={initialOperationTaskPage}
      initialMobileProductionReportingPage={initialMobileProductionReportingPage}
      initialLabelingPage={initialLabelingPage}
      initialPutawayPage={initialPutawayPage}
      initialInventoryControlPage={initialInventoryControlPage}
      initialPurchaseReceiptPage={initialPurchaseReceiptPage}
      initialPurchaseReturnPage={initialPurchaseReturnPage}
      initialIncomingInspectionPage={initialIncomingInspectionPage}
      initialSalesShipmentPage={initialSalesShipmentPage}
      initialSalesReturnPage={initialSalesReturnPage}
      initialOrderProfitPage={initialOrderProfitPage}
      initialReceivablePage={initialReceivablePage}
      initialPayablePage={initialPayablePage}
      initialAccountingPeriodPage={initialAccountingPeriodPage}
      initialGrirAccrualPage={initialGrirAccrualPage}
      initialAdvancePage={initialAdvancePage}
      initialWorkspaceUserPage={initialWorkspaceUserPage}
      initialOrganizationStructurePage={initialOrganizationStructurePage}
      initialRolePermissionPage={initialRolePermissionPage}
      initialEquipmentAssetPage={initialEquipmentAssetPage}
      initialEquipmentWorkOrderPage={initialEquipmentWorkOrderPage}
      initialEquipmentSparePartPage={initialEquipmentSparePartPage}
      initialEquipmentTelemetryPage={initialEquipmentTelemetryPage}
      initialEquipmentAlertPage={initialEquipmentAlertPage}
      initialEquipmentOeePage={initialEquipmentOeePage}
      pathname={pathname}
    />
  );
}
function ManufacturingWorkspaceContent({
  backendPageUnavailable,
  initialSnapshot,
  initialPageModel,
  initialSearchIndex,
  initialSalesOrderPage,
  initialPlanningDemandPage,
  initialMrpRunPage,
  initialMrpSuggestionPage,
  initialBomPage,
  initialRoutingPage,
  initialInventoryPage,
  initialProcurementPage,
  initialPlanningParameterPage,
  initialProductionOrderPage,
  initialProductionExecutionPage,
  initialFinalInspectionPage,
  initialMaterialIssuePage,
  initialOperationTaskPage,
  initialMobileProductionReportingPage,
  initialLabelingPage,
  initialPutawayPage,
  initialInventoryControlPage,
  initialPurchaseReceiptPage,
  initialPurchaseReturnPage,
  initialIncomingInspectionPage,
  initialSalesShipmentPage,
  initialSalesReturnPage,
  initialOrderProfitPage,
  initialReceivablePage,
  initialPayablePage,
  initialAccountingPeriodPage,
  initialGrirAccrualPage,
  initialAdvancePage,
  initialWorkspaceUserPage,
  initialOrganizationStructurePage,
  initialRolePermissionPage,
  initialEquipmentAssetPage,
  initialEquipmentWorkOrderPage,
  initialEquipmentSparePartPage,
  initialEquipmentTelemetryPage,
  initialEquipmentAlertPage,
  initialEquipmentOeePage,
  pathname,
}: ManufacturingWorkspaceProps & {
  pathname: string;
}) {
  const resolvedRoute = useMemo(
    () => resolveProductRoute(pathname),
    [pathname],
  );
  const activeArea = resolvedRoute?.area ?? productAreas[0];
  const activeModuleItem = resolvedRoute?.module ?? activeArea.modules[0];
  const activeModule = activeModuleItem?.label ?? "模块总览";
  const activeLeaf = resolvedRoute?.child?.label ?? null;
  const [expandedAreaId, setExpandedAreaId] = useState(activeArea.id);
  const [expandedModule, setExpandedModule] = useState<string | null>(
    resolvedRoute?.child ? activeModule : null,
  );
  const [navigationCollapsed, setNavigationCollapsed] = useState(false);
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false);
  const [isMobileViewport, setIsMobileViewport] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState<WorkOrder | null>(null);
  const [query, setQuery] = useState("");
  const [showSearch, setShowSearch] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [aiAssistantOpen, setAiAssistantOpen] = useState(false);
  const [quickAccessOpen, setQuickAccessOpen] = useState(false);
  const [quickAccessTab, setQuickAccessTab] = useState<"favorites" | "recent">(
    "favorites",
  );
  const [workspaceOpen, setWorkspaceOpen] = useState(false);
  const [workspaceName, setWorkspaceName] = useState(
    initialSnapshot.workspace.name,
  );
  const [workspaceSession, setWorkspaceSession] =
    useState<WorkspaceSession | null>(null);
  const [workspaceSwitchPending, setWorkspaceSwitchPending] = useState(false);
  const [favoritePaths, setFavoritePaths] =
    useState<string[]>(defaultFavoritePaths);
  const [recentPaths, setRecentPaths] = useState<string[]>([]);
  const [unreadNotificationCount, setUnreadNotificationCount] = useState(3);
  const [platformToast, setPlatformToast] = useState("");
  const [userProfile, setUserProfile] = useState(defaultUserProfile);
  const [profileDialogOpen, setProfileDialogOpen] = useState(false);
  const [sessionExitOpen, setSessionExitOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);
  const notificationRef = useRef<HTMLDivElement>(null);
  const quickAccessRef = useRef<HTMLDivElement>(null);
  const searchPanelRef = useRef<HTMLElement>(null);
  useOverlayFocus(searchPanelRef, {
    open: showSearch,
    onClose: () => setShowSearch(false),
    preferredSelector: ".searchInput input",
  });
  useEffect(() => {
    const media = window.matchMedia("(max-width: 760px)");
    const updateViewport = () => {
      setIsMobileViewport(media.matches);
      if (!media.matches) setMobileNavigationOpen(false);
    };
    updateViewport();
    media.addEventListener("change", updateViewport);
    return () => media.removeEventListener("change", updateViewport);
  }, []);
  useEffect(() => {
    function handleShortcut(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setShowSearch(true);
      }
      if (event.key === "Escape") {
        setShowSearch(false);
        setSelectedOrder(null);
        setUserMenuOpen(false);
        setNotificationOpen(false);
        setAiAssistantOpen(false);
        setQuickAccessOpen(false);
        setWorkspaceOpen(false);
      }
    }
    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, []);
  useEffect(() => {
    let active = true;
    try {
      const storedFavorites = JSON.parse(
        window.localStorage.getItem("guanseq-favorites") ?? "null",
      ) as unknown;
      const storedRecent = JSON.parse(
        window.localStorage.getItem("guanseq-recent") ?? "null",
      ) as unknown;
      const storedUnreadCount = Number(
        window.localStorage.getItem("guanseq-unread-notifications"),
      );
      const storedProfile = readUserProfile();
      queueMicrotask(() => {
        if (!active) return;
        if (
          Array.isArray(storedFavorites) &&
          storedFavorites.every((item) => typeof item === "string")
        )
          setFavoritePaths(storedFavorites);
        if (
          Array.isArray(storedRecent) &&
          storedRecent.every((item) => typeof item === "string")
        )
          setRecentPaths(storedRecent);
        if (
          Number.isInteger(storedUnreadCount) &&
          storedUnreadCount >= 0 &&
          storedUnreadCount <= 3
        )
          setUnreadNotificationCount(storedUnreadCount);
        setUserProfile(storedProfile);
      });
    } catch {
      // 浏览器禁用本地存储时继续使用当前会话状态。
    }
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    let active = true;
    loadWorkspaceSession()
      .then(({ session }) => {
        if (!active) return;
        const currentWorkspace = session.workspaces.find(
          (workspace) => workspace.id === session.currentWorkspaceId,
        );
        setWorkspaceSession(session);
        if (currentWorkspace) setWorkspaceName(currentWorkspace.name);
        setUserProfile((current) => ({
          ...current,
          name: session.displayName,
        }));
      })
      .catch(() => {
        if (active)
          setPlatformToast("工作区服务暂时不可用，当前页面仍可继续浏览");
      });
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    if (!navigationSearchItems.some((item) => item.href === pathname)) return;
    const updateRecent = window.setTimeout(
      () =>
        setRecentPaths((current) => {
          const next = [
            pathname,
            ...current.filter((path) => path !== pathname),
          ].slice(0, 6);
          try {
            window.localStorage.setItem("guanseq-recent", JSON.stringify(next));
          } catch {
            /* 当前会话仍可使用 */
          }
          return next;
        }),
      0,
    );
    return () => window.clearTimeout(updateRecent);
  }, [pathname]);
  useEffect(() => {
    if (!platformToast) return;
    const timer = window.setTimeout(() => setPlatformToast(""), 2600);
    return () => window.clearTimeout(timer);
  }, [platformToast]);
  useEffect(() => {
    if (!userMenuOpen) return;
    function closeOnOutsidePress(event: PointerEvent) {
      if (!userMenuRef.current?.contains(event.target as Node)) {
        setUserMenuOpen(false);
        setWorkspaceOpen(false);
        setProfileDialogOpen(false);
        setSessionExitOpen(false);
      }
    }
    window.addEventListener("pointerdown", closeOnOutsidePress);
    return () => window.removeEventListener("pointerdown", closeOnOutsidePress);
  }, [userMenuOpen]);
  useEffect(() => {
    if (!notificationOpen) return;
    function closeOnOutsidePress(event: PointerEvent) {
      if (!notificationRef.current?.contains(event.target as Node))
        setNotificationOpen(false);
    }
    window.addEventListener("pointerdown", closeOnOutsidePress);
    return () => window.removeEventListener("pointerdown", closeOnOutsidePress);
  }, [notificationOpen]);
  useEffect(() => {
    if (!quickAccessOpen) return;
    function closeOnOutsidePress(event: PointerEvent) {
      if (!quickAccessRef.current?.contains(event.target as Node))
        setQuickAccessOpen(false);
    }
    window.addEventListener("pointerdown", closeOnOutsidePress);
    return () => window.removeEventListener("pointerdown", closeOnOutsidePress);
  }, [quickAccessOpen]);
  const normalizedQuery = query.trim().toLocaleLowerCase("zh-CN");
  const searchResults: SearchResult[] = normalizedQuery
    ? [
        ...initialSnapshot.workOrders
          .filter((order) =>
            `${order.id}${order.product}${order.customer}`
              .toLocaleLowerCase("zh-CN")
              .includes(normalizedQuery),
          )
          .map((order) => ({
            type: "生产工单" as const,
            title: order.id,
            detail: order.product,
            order,
          })),
        ...initialSearchIndex
          .filter((item) =>
            `${item.title}${item.detail}${item.keywords.join("")}`
              .toLocaleLowerCase("zh-CN")
              .includes(normalizedQuery),
          )
          .map((item) => ({
            type: item.type,
            title: item.title,
            detail: item.detail,
            href: item.href,
          })),
        ...navigationSearchItems.filter((item) =>
          `${item.title}${item.detail}`
            .toLocaleLowerCase("zh-CN")
            .includes(normalizedQuery),
        ),
      ]
        .filter(
          (item, index, items) =>
            items.findIndex(
              (candidate) =>
                candidate.type === item.type && candidate.title === item.title,
            ) === index,
        )
        .slice(0, 24)
    : [];
  const currentIsFavorite = favoritePaths.includes(pathname);
  const currentQuickAccessItem = resolveQuickAccessItem(pathname);
  const favoriteItems = favoritePaths
    .map(resolveQuickAccessItem)
    .filter((item): item is NavigationSearchResult => Boolean(item));
  const recentItems = recentPaths
    .map(resolveQuickAccessItem)
    .filter((item): item is NavigationSearchResult => Boolean(item));
  const aiContext = {
    pathname,
    pageTitle: initialPageModel?.title ?? "制造经营总览",
    pageDescription:
      initialPageModel?.description ??
      "从销售承诺到生产交付，汇总关键指标、责任、风险与待处理任务。",
    workspace: workspaceName,
    metrics:
      initialPageModel?.metrics.map((metric) => ({
        label: metric.label,
        value: metric.value,
        note: metric.note,
      })) ??
      initialSnapshot.metrics.map((metric) => ({
        label: metric.label,
        value: metric.value,
        note: metric.change,
      })),
    alerts:
      initialPageModel?.attentionItems.map((item) => ({
        title: item.title,
        detail: item.detail,
        owner: item.owner,
      })) ??
      initialSnapshot.alerts.map((alert) => ({
        title: alert.title,
        detail: alert.detail,
        owner: alert.owner,
      })),
  };
  function toggleCurrentFavorite() {
    setFavoritePaths((current) => {
      const next = current.includes(pathname)
        ? current.filter((path) => path !== pathname)
        : [pathname, ...current].slice(0, 8);
      try {
        window.localStorage.setItem("guanseq-favorites", JSON.stringify(next));
      } catch {
        /* 当前会话仍可使用 */
      }
      setPlatformToast(
        current.includes(pathname) ? "已取消收藏当前页面" : "已收藏当前页面",
      );
      return next;
    });
  }
  function removeFavorite(path: string) {
    setFavoritePaths((current) => {
      const next = current.filter((item) => item !== path);
      try {
        window.localStorage.setItem("guanseq-favorites", JSON.stringify(next));
      } catch {
        /* 当前会话仍可使用 */
      }
      return next;
    });
    setPlatformToast("已从收藏夹移除");
  }
  function clearRecentPaths() {
    setRecentPaths([]);
    try {
      window.localStorage.setItem("guanseq-recent", "[]");
    } catch {
      /* 当前会话仍可使用 */
    }
    setPlatformToast("最近访问记录已清空");
  }
  async function switchWorkspace(nextWorkspace: WorkspaceSummary) {
    if (!workspaceSession || workspaceSwitchPending || nextWorkspace.current)
      return;
    setWorkspaceSwitchPending(true);
    try {
      const result = await selectWorkspace(
        nextWorkspace.id,
        workspaceSession.selectionVersion,
      );
      const currentWorkspace = result.session.workspaces.find(
        (workspace) => workspace.current,
      );
      setWorkspaceSession(result.session);
      if (currentWorkspace) setWorkspaceName(currentWorkspace.name);
      setWorkspaceOpen(false);
      setUserMenuOpen(false);
      setPlatformToast(
        result.source === "backend"
          ? `已切换到${currentWorkspace?.name ?? nextWorkspace.name}`
          : `已切换到${currentWorkspace?.name ?? nextWorkspace.name}（本地模式）`,
      );
    } catch (error) {
      setPlatformToast(
        error instanceof Error ? error.message : "工作区切换失败，请稍后重试",
      );
    } finally {
      setWorkspaceSwitchPending(false);
    }
  }
  function updateUnreadNotifications(nextCount: number, message?: string) {
    setUnreadNotificationCount(nextCount);
    try {
      window.localStorage.setItem(
        "guanseq-unread-notifications",
        String(nextCount),
      );
    } catch {
      /* 当前会话仍可使用 */
    }
    if (message) setPlatformToast(message);
  }
  function openNotificationDestination() {
    setNotificationOpen(false);
    if (unreadNotificationCount > 0)
      updateUnreadNotifications(unreadNotificationCount - 1);
  }
  return (
    <main
      className={
        navigationCollapsed ? "appShell appShellNavCollapsed" : "appShell"
      }
    >
      {mobileNavigationOpen ? (
        <GsButton
          className="mobileNavBackdrop"
          aria-label="关闭导航"
          onClick={() => setMobileNavigationOpen(false)}
        />
      ) : null}
      <aside
        className={mobileNavigationOpen ? "sidebar sidebarOpen" : "sidebar"}
        data-collapsed={navigationCollapsed ? "true" : "false"}
        inert={isMobileViewport && !mobileNavigationOpen}
        aria-hidden={
          isMobileViewport && !mobileNavigationOpen ? "true" : undefined
        }
      >
        <div
          className="brandPanel"
          aria-label={`${brandIdentity.name} — ${brandIdentity.tagline}`}
        >
          <span className="brandLogo">
            <GuanSeqLogo />
          </span>
          <div className="brandCopy">
            <strong>{brandIdentity.name}</strong>
            <small>{brandIdentity.tagline}</small>
          </div>
          <GsButton
            className="sidebarClose"
            onClick={() => setMobileNavigationOpen(false)}
            aria-label="关闭导航"
            htmlType="submit"
          >
            <MaterialIcon name="close" />
          </GsButton>
        </div>
        <nav className="treeNavigation" aria-label="产品功能导航">
          {productAreas.map((area) => (
            <div className="treeArea" key={area.id}>
              <GsButton
                htmlType="button"
                className={
                  activeArea.id === area.id
                    ? "treeLevelOne treeLevelOneActive"
                    : "treeLevelOne"
                }
                onClick={() => {
                  if (navigationCollapsed) {
                    setNavigationCollapsed(false);
                    setExpandedAreaId(area.id);
                    setExpandedModule(null);
                    return;
                  }
                  setExpandedAreaId((current) =>
                    current === area.id ? "" : area.id,
                  );
                  setExpandedModule(null);
                }}
                title={navigationCollapsed ? area.label : undefined}
                aria-expanded={expandedAreaId === area.id}
              >
                <MaterialIcon
                  name={area.icon}
                  size={20}
                  filled={activeArea.id === area.id}
                />
                <strong>{area.label}</strong>
                <span className="treeLevelOneMeta">
                  <small>{area.capability}</small>
                  <CapabilityMaturityBadge pathname={areaPath(area)} />
                  <MaterialIcon
                    name={
                      expandedAreaId === area.id ? "expand_less" : "expand_more"
                    }
                    size={18}
                  />
                </span>
              </GsButton>
              {expandedAreaId === area.id ? (
                <div className="treeLevelTwoGroup">
                  {area.modules.map((moduleItem) => {
                    const hasChildren = Boolean(moduleItem.children?.length);
                    const moduleActive =
                      activeArea.id === area.id &&
                      activeModule === moduleItem.label;
                    return (
                      <div className="treeModule" key={moduleItem.label}>
                        <Link
                          href={modulePath(area, moduleItem)}
                          className={
                            moduleActive
                              ? "treeLevelTwo treeLevelTwoActive"
                              : "treeLevelTwo"
                          }
                          onClick={(event) => {
                            if (hasChildren) {
                              event.preventDefault();
                              setExpandedModule((current) =>
                                current === moduleItem.label
                                  ? null
                                  : moduleItem.label,
                              );
                            } else {
                              setExpandedModule(null);
                              setMobileNavigationOpen(false);
                            }
                          }}
                          aria-expanded={
                            hasChildren
                              ? expandedModule === moduleItem.label
                              : undefined
                          }
                        >
                          <span />
                          <strong>{moduleItem.label}</strong>
                          <span className="treeModuleMeta">
                            <CapabilityMaturityBadge
                              pathname={modulePath(area, moduleItem)}
                            />
                            {hasChildren ? (
                              <MaterialIcon
                                name={
                                  expandedModule === moduleItem.label
                                    ? "expand_less"
                                    : "expand_more"
                                }
                                size={17}
                              />
                            ) : null}
                          </span>
                        </Link>
                        {hasChildren && expandedModule === moduleItem.label ? (
                          <div className="treeLevelThreeGroup">
                            {moduleItem.children?.map((child) => (
                              <Link
                                key={child.slug}
                                href={childPath(area, moduleItem, child)}
                                className={
                                  activeLeaf === child.label
                                    ? "treeLevelThree treeLevelThreeActive"
                                    : "treeLevelThree"
                                }
                                onClick={() => setMobileNavigationOpen(false)}
                              >
                                <span />
                                <span className="treeLevelThreeLabel">
                                  {child.label}
                                </span>
                                <CapabilityMaturityBadge
                                  pathname={childPath(area, moduleItem, child)}
                                />
                              </Link>
                            ))}
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              ) : null}
            </div>
          ))}
        </nav>
        <div className="sidebarFooter">
          <GsButton
            className="sidebarToggle"
            htmlType="button"
            onClick={() => setNavigationCollapsed((collapsed) => !collapsed)}
            aria-label={navigationCollapsed ? "展开导航" : "收起导航"}
            title={navigationCollapsed ? "展开导航" : "收起导航"}
          >
            <MaterialIcon
              name={
                navigationCollapsed
                  ? "keyboard_double_arrow_right"
                  : "keyboard_double_arrow_left"
              }
              size={18}
            />
            <span className="sidebarToggleLabel">
              {navigationCollapsed ? "展开导航" : "收起导航"}
            </span>
          </GsButton>
        </div>
      </aside>

      <header className="topBar">
        <div className="topSearchArea">
          <GsButton
            className="mobileMenuButton"
            onClick={() => setMobileNavigationOpen(true)}
            aria-label="打开导航"
            htmlType="submit"
          >
            <MaterialIcon name="menu" />
          </GsButton>
          <GsButton
            className="searchTrigger"
            onClick={() => setShowSearch(true)}
            htmlType="submit"
          >
            <MaterialIcon name="search" size={19} />
            <span>搜索订单、物料、工单</span>
            <kbd>Ctrl K</kbd>
          </GsButton>
        </div>
        <div className="topActions">
          <GsButton
            className={
              aiAssistantOpen
                ? "aiAssistantTrigger aiAssistantTriggerOpen"
                : "aiAssistantTrigger"
            }
            htmlType="button"
            onClick={() => {
              setAiAssistantOpen(true);
              setQuickAccessOpen(false);
              setNotificationOpen(false);
              setUserMenuOpen(false);
              setWorkspaceOpen(false);
            }}
            aria-label="打开 AI 制造助手"
            aria-expanded={aiAssistantOpen}
            aria-haspopup="dialog"
          >
            <MaterialIcon name="auto_awesome" filled size={18} />
            <span>AI 助手</span>
          </GsButton>
          <div className="quickAccessWrap" ref={quickAccessRef}>
            <GsButton
              className={
                quickAccessOpen
                  ? "iconButton quickAccessButton quickAccessButtonOpen"
                  : "iconButton quickAccessButton"
              }
              onClick={() => {
                setQuickAccessOpen((open) => !open);
                setNotificationOpen(false);
                setUserMenuOpen(false);
                setWorkspaceOpen(false);
              }}
              aria-label="快捷访问"
              aria-expanded={quickAccessOpen}
              aria-haspopup="dialog"
              htmlType="submit"
            >
              <MaterialIcon
                name="bookmarks"
                filled={currentIsFavorite}
                size={19}
              />
            </GsButton>
            {quickAccessOpen ? (
              <section
                className="quickAccessDropdown"
                role="dialog"
                aria-label="快捷访问"
              >
                <header>
                  <div>
                    <strong>快捷访问</strong>
                    <small>收藏页面与访问记录</small>
                  </div>
                  {currentQuickAccessItem ? (
                    <GsButton
                      className={
                        currentIsFavorite
                          ? "quickCurrentFavorite quickCurrentFavoriteActive"
                          : "quickCurrentFavorite"
                      }
                      onClick={toggleCurrentFavorite}
                      htmlType="submit"
                    >
                      <MaterialIcon
                        name="star"
                        filled={currentIsFavorite}
                        size={17}
                      />
                      {currentIsFavorite ? "已收藏" : "收藏当前页"}
                    </GsButton>
                  ) : null}
                </header>
                <div
                  className="quickAccessTabs"
                  role="tablist"
                  aria-label="快捷访问分类"
                >
                  <GsButton
                    role="tab"
                    aria-selected={quickAccessTab === "favorites"}
                    onClick={() => setQuickAccessTab("favorites")}
                    htmlType="submit"
                  >
                    <MaterialIcon name="star" size={16} />
                    收藏夹<span>{favoriteItems.length}</span>
                  </GsButton>
                  <GsButton
                    role="tab"
                    aria-selected={quickAccessTab === "recent"}
                    onClick={() => setQuickAccessTab("recent")}
                    htmlType="submit"
                  >
                    <MaterialIcon name="history" size={16} />
                    最近访问<span>{recentItems.length}</span>
                  </GsButton>
                </div>
                <div className="quickAccessList" role="tabpanel">
                  {quickAccessTab === "favorites"
                    ? favoriteItems.map((item) => (
                        <div
                          className={
                            item.href === pathname
                              ? "quickAccessItem quickAccessItemActive"
                              : "quickAccessItem"
                          }
                          key={item.href}
                        >
                          <Link
                            href={item.href}
                            onClick={() => setQuickAccessOpen(false)}
                          >
                            <span className="quickAccessItemIcon">
                              <MaterialIcon name="star" filled size={16} />
                            </span>
                            <span>
                              <strong>{item.title}</strong>
                              <small>{item.detail}</small>
                            </span>
                          </Link>
                          <GsButton
                            onClick={() => removeFavorite(item.href)}
                            aria-label={`取消收藏${item.title}`}
                            title="取消收藏"
                            htmlType="submit"
                          >
                            <MaterialIcon name="close" size={16} />
                          </GsButton>
                        </div>
                      ))
                    : recentItems.map((item) => (
                        <div
                          className={
                            item.href === pathname
                              ? "quickAccessItem quickAccessItemActive"
                              : "quickAccessItem"
                          }
                          key={item.href}
                        >
                          <Link
                            href={item.href}
                            onClick={() => setQuickAccessOpen(false)}
                          >
                            <span className="quickAccessItemIcon">
                              <MaterialIcon name="history" size={16} />
                            </span>
                            <span>
                              <strong>{item.title}</strong>
                              <small>{item.detail}</small>
                            </span>
                          </Link>
                        </div>
                      ))}
                  {quickAccessTab === "favorites" &&
                  favoriteItems.length === 0 ? (
                    <div className="quickAccessEmpty">
                      <MaterialIcon name="star" size={22} />
                      <strong>收藏夹还是空的</strong>
                      <small>打开常用页面后，点击“收藏当前页”。</small>
                    </div>
                  ) : null}
                  {quickAccessTab === "recent" && recentItems.length === 0 ? (
                    <div className="quickAccessEmpty">
                      <MaterialIcon name="history" size={22} />
                      <strong>暂无访问记录</strong>
                      <small>访问过的业务页面会显示在这里。</small>
                    </div>
                  ) : null}
                </div>
                {quickAccessTab === "recent" && recentItems.length ? (
                  <footer>
                    <GsButton onClick={clearRecentPaths} htmlType="submit">
                      <MaterialIcon name="delete_sweep" size={16} />
                      清空访问记录
                    </GsButton>
                  </footer>
                ) : null}
              </section>
            ) : null}
          </div>
          <div className="notificationWrap" ref={notificationRef}>
            <GsButton
              className={
                notificationOpen
                  ? "iconButton notificationButton notificationButtonOpen"
                  : "iconButton notificationButton"
              }
              aria-label={
                unreadNotificationCount
                  ? `通知，${unreadNotificationCount} 条未读`
                  : "通知"
              }
              aria-expanded={notificationOpen}
              aria-haspopup="menu"
              onClick={() => {
                setNotificationOpen((open) => !open);
                setQuickAccessOpen(false);
                setUserMenuOpen(false);
                setWorkspaceOpen(false);
              }}
              htmlType="submit"
            >
              <MaterialIcon name="notifications" />
              {unreadNotificationCount ? (
                <i>{unreadNotificationCount}</i>
              ) : null}
            </GsButton>
            {notificationOpen ? (
              <div className="notificationDropdown" role="menu">
                <header>
                  <div>
                    <strong>通知</strong>
                    <small>
                      {unreadNotificationCount
                        ? `${unreadNotificationCount} 条未读`
                        : "已全部阅读"}
                    </small>
                  </div>
                  <GsButton
                    disabled={!unreadNotificationCount}
                    onClick={() =>
                      updateUnreadNotifications(0, "已将全部通知标记为已读")
                    }
                    htmlType="submit"
                  >
                    全部已读
                  </GsButton>
                </header>
                <Link
                  href="/planning/mrp/recommendations"
                  className="notificationItem"
                  role="menuitem"
                  onClick={openNotificationDestination}
                >
                  <span className="notificationTone notificationToneRisk">
                    <MaterialIcon name="priority_high" size={17} />
                  </span>
                  <span>
                    <strong>关键物料预计晚到</strong>
                    <small>影响工单 MO-260814-012</small>
                    <time>10 分钟前</time>
                  </span>
                </Link>
                <Link
                  href="/planning/capacity"
                  className="notificationItem"
                  role="menuitem"
                  onClick={openNotificationDestination}
                >
                  <span className="notificationTone notificationToneWarn">
                    <MaterialIcon name="manufacturing" size={17} />
                  </span>
                  <span>
                    <strong>机加车间负荷超过预警线</strong>
                    <small>未来三日负荷达到 92%</small>
                    <time>35 分钟前</time>
                  </span>
                </Link>
                <Link
                  href="/quality/final"
                  className="notificationItem"
                  role="menuitem"
                  onClick={openNotificationDestination}
                >
                  <span className="notificationTone notificationToneInfo">
                    <MaterialIcon name="verified" size={17} />
                  </span>
                  <span>
                    <strong>完工检验存在待判记录</strong>
                    <small>2 项尺寸记录等待判定</small>
                    <time>1 小时前</time>
                  </span>
                </Link>
                <Link
                  className="notificationViewAll"
                  href="/notifications"
                  onClick={() => setNotificationOpen(false)}
                >
                  查看全部通知
                  <MaterialIcon name="arrow_forward" size={17} />
                </Link>
              </div>
            ) : null}
          </div>
          <div className="userMenuWrap" ref={userMenuRef}>
            <GsButton
              className="userButton"
              onClick={() => {
                setUserMenuOpen((open) => !open);
                setQuickAccessOpen(false);
                setNotificationOpen(false);
                setWorkspaceOpen(false);
              }}
              aria-expanded={userMenuOpen}
              aria-haspopup="menu"
              htmlType="submit"
            >
              <span className="userAvatar">{userProfile.name.slice(0, 1)}</span>
              <div className="userCopy">
                <strong>{userProfile.name}</strong>
                <small>{userProfile.title}</small>
              </div>
              <MaterialIcon name="expand_more" size={17} />
            </GsButton>
            {userMenuOpen ? (
              <div className="userDropdown" role="menu">
                <div className="userDropdownIdentity">
                  <span>{userProfile.name.slice(0, 1)}</span>
                  <div>
                    <strong>{userProfile.name}</strong>
                    <small>
                      {userProfile.title} · {userProfile.department}
                    </small>
                  </div>
                </div>
                <GsButton
                  className="userWorkspaceToggle"
                  role="menuitem"
                  aria-expanded={workspaceOpen}
                  onClick={() => setWorkspaceOpen((open) => !open)}
                  htmlType="submit"
                >
                  <MaterialIcon name="factory" size={18} />
                  <span>
                    <small>当前工作区</small>
                    <strong>{workspaceName}</strong>
                  </span>
                  <MaterialIcon
                    name={workspaceOpen ? "expand_less" : "expand_more"}
                    size={16}
                  />
                </GsButton>
                {workspaceOpen ? (
                  <div
                    className="userWorkspaceOptions"
                    role="group"
                    aria-label="选择工作区"
                  >
                    {workspaceSession ? (
                      workspaceSession.workspaces.map((option) => (
                        <GsButton
                          role="menuitemradio"
                          aria-checked={option.current}
                          disabled={workspaceSwitchPending}
                          key={option.id}
                          onClick={() => switchWorkspace(option)}
                          htmlType="submit"
                        >
                          <span>{option.name}</span>
                          {option.current ? (
                            <MaterialIcon name="check" size={16} />
                          ) : null}
                        </GsButton>
                      ))
                    ) : (
                      <small>正在读取可访问工作区…</small>
                    )}
                  </div>
                ) : null}
                <GsButton
                  role="menuitem"
                  onClick={() => {
                    setUserMenuOpen(false);
                    setWorkspaceOpen(false);
                    setProfileDialogOpen(true);
                  }}
                  htmlType="submit"
                >
                  <MaterialIcon name="person" size={18} />
                  个人资料
                </GsButton>
                <Link
                  role="menuitem"
                  href="/settings/organization/users"
                  onClick={() => {
                    setUserMenuOpen(false);
                    setWorkspaceOpen(false);
                  }}
                >
                  <MaterialIcon name="manage_accounts" size={18} />
                  账号与用户设置
                </Link>
                <GsButton
                  className="userDropdownExit"
                  role="menuitem"
                  onClick={() => {
                    setUserMenuOpen(false);
                    setWorkspaceOpen(false);
                    setSessionExitOpen(true);
                  }}
                  htmlType="submit"
                >
                  <MaterialIcon name="logout" size={18} />
                  退出登录
                </GsButton>
              </div>
            ) : null}
          </div>
        </div>
      </header>

      <section className="mainWorkspace" id="main-content" tabIndex={-1}>
        <CapabilityMaturityNotice pathname={pathname} />
        {backendPageUnavailable ? (
          <BackendUnavailableState
            title={initialPageModel?.title ?? "业务页面"}
          />
        ) : pathname === "/" ? (
          <Overview
            snapshot={initialSnapshot}
            onSelectOrder={setSelectedOrder}
          />
        ) : pathname === "/equipment/oee" && initialEquipmentOeePage ? (
          <EquipmentOeeWorkspace initialData={initialEquipmentOeePage} />
        ) : pathname === "/equipment/alerts" && initialEquipmentAlertPage ? (
          <EquipmentAlertWorkspace initialData={initialEquipmentAlertPage} />
        ) : pathname === "/equipment/telemetry" &&
          initialEquipmentTelemetryPage ? (
          <EquipmentTelemetryWorkspace
            initialData={initialEquipmentTelemetryPage}
          />
        ) : pathname === "/equipment/spare-parts" &&
          initialEquipmentSparePartPage ? (
          <EquipmentSparePartWorkspace
            initialData={initialEquipmentSparePartPage}
          />
        ) : (pathname === "/equipment/inspections" ||
            pathname === "/equipment/maintenance" ||
            pathname === "/equipment/work-orders") &&
          initialEquipmentWorkOrderPage ? (
          <EquipmentWorkOrderWorkspace
            initialData={initialEquipmentWorkOrderPage}
            view={
              pathname === "/equipment/inspections"
                ? "inspections"
                : pathname === "/equipment/maintenance"
                  ? "maintenance"
                  : "work-orders"
            }
          />
        ) : (pathname === "/equipment/assets" ||
            pathname === "/equipment/status") &&
          initialEquipmentAssetPage ? (
          <EquipmentAssetWorkspace
            initialData={initialEquipmentAssetPage}
            view={pathname === "/equipment/status" ? "status" : "assets"}
          />
        ) : pathname === "/settings/organization/structure" && initialOrganizationStructurePage ? (
          <OrganizationStructureWorkspace initialData={initialOrganizationStructurePage} />
        ) : pathname === "/settings/organization/users" &&
          initialWorkspaceUserPage ? (
          <WorkspaceUserWorkspace initialData={initialWorkspaceUserPage} />
        ) : pathname === "/settings/roles" && initialRolePermissionPage ? (
          <RolePermissionWorkspace initialData={initialRolePermissionPage} />
        ) : pathname === "/sales/deliveries/pending" &&
          initialSalesShipmentPage ? (
          <SalesShipmentWorkspace initialData={initialSalesShipmentPage} />
        ) : pathname === "/sales/returns" && initialSalesReturnPage ? (
          <SalesReturnWorkspace initialData={initialSalesReturnPage} />
        ) : pathname === "/finance/order-profit" && initialOrderProfitPage ? (
          <OrderProfitWorkspace initialData={initialOrderProfitPage} />
        ) : pathname === "/finance/accounting-periods" &&
          initialAccountingPeriodPage ? (
          <AccountingPeriodWorkspace
            initialYear={initialAccountingPeriodPage.year}
            initialPeriods={initialAccountingPeriodPage.periods}
          />
        ) : pathname === "/finance/purchase-settlement/grir-accruals" &&
          initialGrirAccrualPage ? (
          <GrirAccrualWorkspace
            initialYear={initialGrirAccrualPage.year}
            initialPage={initialGrirAccrualPage.page}
          />
        ) : pathname === "/finance/advances" && initialAdvancePage ? (
          <AdvanceWorkspace {...initialAdvancePage} />
        ) : (pathname === "/finance/receivables" ||
            pathname === "/finance/sales-settlement/invoicing" ||
            pathname === "/finance/sales-settlement/receipts") &&
          initialReceivablePage ? (
          <ReceivableWorkspace
            initialData={initialReceivablePage}
            pathname={pathname}
          />
        ) : (pathname === "/finance/payables" ||
            pathname === "/finance/purchase-settlement/invoices" ||
            pathname === "/finance/purchase-settlement/payments") &&
          initialPayablePage ? (
          <PayableWorkspace
            initialData={initialPayablePage}
            pathname={pathname}
          />
        ) : pathname === "/sales/orders/list" && initialSalesOrderPage ? (
          <SalesOrderWorkspace initialData={initialSalesOrderPage} />
        ) : pathname === "/planning/demand/independent" &&
          initialPlanningDemandPage ? (
          <PlanningDemandWorkspace initialData={initialPlanningDemandPage} />
        ) : pathname === "/planning/mrp/runs" && initialMrpRunPage ? (
          <MrpRunWorkspace initialData={initialMrpRunPage} />
        ) : pathname === "/planning/mrp/recommendations" &&
          initialMrpSuggestionPage ? (
          <MrpSuggestionWorkspace initialData={initialMrpSuggestionPage} />
        ) : pathname === "/planning/parameters" &&
          initialPlanningParameterPage ? (
          <PlanningParameterWorkspace
            initialData={initialPlanningParameterPage}
          />
        ) : pathname === "/procurement/orders" && initialProcurementPage ? (
          <ProcurementOrderWorkspace initialData={initialProcurementPage} />
        ) : pathname === "/procurement/receipts" &&
          initialPurchaseReceiptPage ? (
          <PurchaseReceiptWorkspace initialData={initialPurchaseReceiptPage} />
        ) : pathname === "/procurement/mobile-receiving" &&
          initialPurchaseReceiptPage ? (
          <MobilePurchaseReceiptWorkspace initialData={initialPurchaseReceiptPage} />
        ) : pathname === "/procurement/returns" &&
          initialPurchaseReturnPage ? (
          <PurchaseReturnWorkspace initialData={initialPurchaseReturnPage} />
        ) : pathname === "/production/orders/list" &&
          initialProductionOrderPage ? (
          <ProductionOrderWorkspace initialData={initialProductionOrderPage} />
        ) : pathname === "/production/reporting/reports" &&
          initialProductionExecutionPage ? (
          <ProductionExecutionWorkspace
            initialData={initialProductionExecutionPage}
          />
        ) : pathname === "/quality/final" && initialFinalInspectionPage ? (
          <FinalInspectionWorkspace initialData={initialFinalInspectionPage} />
        ) : pathname === "/quality/incoming" &&
          initialIncomingInspectionPage ? (
          <IncomingInspectionWorkspace
            initialData={initialIncomingInspectionPage}
          />
        ) : pathname === "/product/boms/list" && initialBomPage ? (
          <BomWorkspace initialData={initialBomPage} />
        ) : pathname === "/product/routings/list" && initialRoutingPage ? (
          <RoutingWorkspace initialData={initialRoutingPage} />
        ) : pathname === "/warehouse/inventory/on-hand" &&
          initialInventoryPage ? (
          <InventoryWorkspace initialData={initialInventoryPage} />
        ) : pathname === "/production/mobile-operations/material-scan" && initialMaterialIssuePage ? (
          <MobileMaterialIssueWorkspace initialData={initialMaterialIssuePage} />
        ) : pathname === "/production/mobile-operations/reporting-scan" && initialMobileProductionReportingPage ? (
          <MobileProductionReportingWorkspace initialData={initialMobileProductionReportingPage} />
        ) : pathname === "/production/mobile-operations/label-reprint" && initialLabelingPage ? (
          <LabelingWorkspace initialData={initialLabelingPage} />
        ) : (pathname === "/warehouse/receiving" || pathname === "/warehouse/barcodes/scanning") && initialPutawayPage ? (
          <PutawayWorkspace initialData={initialPutawayPage} />
        ) : (pathname === "/warehouse/inventory-operations/transfers" || pathname === "/warehouse/counts") && initialInventoryControlPage ? (
          <InventoryControlWorkspace initialData={initialInventoryControlPage} />
        ) : (pathname === "/warehouse/staging" ||
            pathname === "/warehouse/material-issues") &&
          initialMaterialIssuePage ? (
          <MaterialIssueWorkspace initialData={initialMaterialIssuePage} />
        ) : pathname === "/production/work-orders/operations" &&
          initialOperationTaskPage ? (
          <OperationTaskWorkspace initialData={initialOperationTaskPage} />
        ) : initialPageModel ? (
          <BusinessWorkspace model={initialPageModel} />
        ) : null}
      </section>

      <AiAssistant
        open={aiAssistantOpen}
        context={aiContext}
        userName={userProfile.name}
        onClose={() => setAiAssistantOpen(false)}
      />
      {profileDialogOpen ? (
        <ProfileDialog
          profile={userProfile}
          onClose={() => setProfileDialogOpen(false)}
          onSaved={(profile) => {
            setUserProfile(profile);
            setProfileDialogOpen(false);
            setPlatformToast("个人资料与通知偏好已保存");
          }}
        />
      ) : null}
      {sessionExitOpen ? (
        <SessionExitDialog onClose={() => setSessionExitOpen(false)} />
      ) : null}
      {selectedOrder ? (
        <OrderDetail
          order={selectedOrder}
          onClose={() => setSelectedOrder(null)}
        />
      ) : null}
      {showSearch ? (
        <div
          className="searchBackdrop"
          onMouseDown={() => setShowSearch(false)}
        >
          <section
            ref={searchPanelRef}
            className="searchPanel"
            role="dialog"
            aria-modal="true"
            aria-label="全局搜索"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="searchInput">
              <span className="searchInputIcon">
                <MaterialIcon name="search" size={20} />
              </span>
              <GsInput
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="搜索订单、物料、客户、工单或功能"
              />
              <kbd>ESC</kbd>
            </div>
            <div className="searchResults">
              {!query.trim() ? (
                <p>
                  <span>试试搜索</span>
                  <GsButton onClick={() => setQuery("MO-")} htmlType="submit">
                    生产工单
                  </GsButton>
                  <GsButton onClick={() => setQuery("质量")} htmlType="submit">
                    质量管理
                  </GsButton>
                </p>
              ) : null}
              {query.trim() && searchResults.length === 0 ? (
                <p>没有找到匹配结果，请尝试其他关键词。</p>
              ) : null}
              {searchResults.map((result) => (
                <Link
                  href={result.href ?? pathname}
                  key={`${result.type}-${result.title}-${result.href ?? "drawer"}`}
                  onClick={() => {
                    if (result.order) setSelectedOrder(result.order);
                    setShowSearch(false);
                  }}
                >
                  <span>{result.type}</span>
                  <strong>{result.title}</strong>
                  <small>{result.detail}</small>
                  <MaterialIcon name="arrow_forward" size={18} />
                </Link>
              ))}
            </div>
          </section>
        </div>
      ) : null}
      {platformToast ? (
        <div className="toastMessage" role="status" aria-live="polite">
          <span>
            <MaterialIcon name="check_circle" filled size={18} />
          </span>
          {platformToast}
        </div>
      ) : null}
    </main>
  );
}
function Overview({
  snapshot,
  onSelectOrder,
}: {
  snapshot: ManufacturingSnapshot;
  onSelectOrder: (order: WorkOrder) => void;
}) {
  return (
    <div className="overviewPage">
      <header className="pageHeading">
        <div>
          <h2>制造经营总览</h2>
          <p>从销售承诺到生产交付，关注今天必须做出决定的事项。</p>
        </div>
        <div className="pageHeadingActions">
          <GsButton
            className="secondaryButton"
            onClick={() => downloadOverviewReport(snapshot)}
            htmlType="submit"
          >
            <MaterialIcon name="download" size={18} />
            导出日报
          </GsButton>
          <Link className="primaryButton" href="/sales/orders/list">
            <MaterialIcon name="arrow_forward" size={18} />
            进入销售订单
          </Link>
        </div>
      </header>

      <section className="metricStrip" aria-label="关键指标">
        {snapshot.metrics.map((metric, index) => (
          <div key={metric.label}>
            <span>{String(index + 1).padStart(2, "0")}</span>
            <small>{metric.label}</small>
            <strong>{metric.value}</strong>
            <em className={`metric${metric.tone}`}>{metric.change}</em>
          </div>
        ))}
      </section>

      <section className="flowSection">
        <div className="sectionHeading">
          <div>
            <p className="eyebrow">订单履约主链路</p>
            <h3>订单到交付主流程</h3>
          </div>
          <p>共 18 张销售订单进入本周履约窗口</p>
        </div>
        <div className="flowTrack">
          {snapshot.flow.map((stage, index) => (
            <Link
              href={flowDestinations[stage.id]}
              key={stage.id}
              className={`flowStage flowStage${stage.status}`}
            >
              <span className="flowSequence">{index + 1}</span>
              <div>
                <small>{stage.owner}</small>
                <strong>{stage.label}</strong>
                <em>
                  {statusLabels[stage.status]} · {stage.count}
                </em>
              </div>
              {index < snapshot.flow.length - 1 ? (
                <MaterialIcon name="arrow_forward" size={18} />
              ) : null}
            </Link>
          ))}
        </div>
      </section>

      <div className="operationsGrid">
        <section className="workOrderPanel">
          <div className="sectionHeading">
            <div>
              <p className="eyebrow">车间执行动态</p>
              <h3>重点生产工单</h3>
            </div>
            <Link className="textButton" href="/production/orders/list">
              查看全部 <MaterialIcon name="arrow_forward" size={17} />
            </Link>
          </div>
          <div className="orderTable" role="table" aria-label="重点生产工单">
            <div className="orderTableHeader" role="row">
              <span>工单 / 产品</span>
              <span>车间</span>
              <span>进度</span>
              <span>交期</span>
              <span>状态</span>
              <span />
            </div>
            {snapshot.workOrders.map((order) => (
              <GsButton
                className="orderRow"
                role="row"
                key={order.id}
                onClick={() => onSelectOrder(order)}
                htmlType="submit"
              >
                <span className="orderIdentity">
                  <strong>{order.id}</strong>
                  <small>
                    {order.product} · {order.quantity}
                  </small>
                </span>
                <span>{order.workshop}</span>
                <span className="tableProgress">
                  <i>
                    <b style={{ width: `${order.progress}%` }} />
                  </i>
                  <em>{order.progress}%</em>
                </span>
                <span>{order.dueDate}</span>
                <span>
                  <em className={`orderStatus orderStatus${order.status}`}>
                    {order.status}
                  </em>
                </span>
                <MaterialIcon name="chevron_right" size={18} />
              </GsButton>
            ))}
          </div>
        </section>

        <aside className="riskPanel">
          <div className="sectionHeading">
            <div>
              <p className="eyebrow">今日必须处理</p>
              <h3>交付风险与待办</h3>
            </div>
            <strong>{snapshot.alerts.length}</strong>
          </div>
          <div className="alertLedger">
            {snapshot.alerts.map((alert) => (
              <article key={alert.id}>
                <span className={`alertLevel alertLevel${alert.level}`}>
                  {alert.level}
                </span>
                <div>
                  <strong>{alert.title}</strong>
                  <p>{alert.detail}</p>
                  <small>{alert.owner}</small>
                </div>
                <Link
                  href={alertDestinations[alert.id]}
                  aria-label={`处理${alert.title}`}
                >
                  <MaterialIcon name="arrow_outward" size={18} />
                </Link>
              </article>
            ))}
          </div>
        </aside>
      </div>

      <section className="capacityPanel">
        <div className="sectionHeading">
          <div>
            <p className="eyebrow">未来七日产能</p>
            <h3>车间产能负荷</h3>
          </div>
          <p>预警线 85% · 仅供计划决策参考</p>
        </div>
        <div className="capacityGrid">
          {snapshot.capacity.map((item) => (
            <div key={item.name}>
              <header>
                <strong>{item.name}</strong>
                <span className={item.load >= 85 ? "capacityRisk" : ""}>
                  {item.load}%
                </span>
              </header>
              <div className="capacityTrack">
                <i style={{ width: `${item.load}%` }} />
              </div>
              <small>{item.note}</small>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
