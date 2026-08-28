package com.guanseq.identity.api;

import java.util.List;
import java.util.Set;

public enum WorkspacePermission {
	IDENTITY_MEMBER_MANAGE("IDENTITY", "身份与工作区", "维护工作区成员", "创建成员、调整单一受控角色、停用或恢复当前工作区访问", Risk.CRITICAL, "ADMIN"),
	IDENTITY_ROLE_MATRIX_READ("IDENTITY", "身份与工作区", "查看角色权限矩阵", "读取当前受控角色及后端角色门禁目录", Risk.SENSITIVE, "ADMIN"),

	SALES_ORDER_APPROVE_RELEASE("SALES", "销售", "审核与下达销售订单", "审核、驳回和下达销售订单", Risk.SENSITIVE, "SALES_MANAGER", "PLANNING_MANAGER", "ADMIN"),
	SALES_SHIPMENT_REGISTER("SALES", "销售", "登记销售发货", "按已下达订单登记成品出库与发货证据", Risk.CRITICAL, "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "SALES_MANAGER", "ADMIN"),
	SALES_RETURN_AUTHORIZE("SALES", "销售", "建立销售退货授权", "建立或取消客户退货授权", Risk.SENSITIVE, "SALES_MANAGER", "ADMIN"),
	SALES_RETURN_RECEIVE("SALES", "销售", "登记销售退货收货", "登记客户退货到待检库存或执行收货冲回", Risk.CRITICAL, "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN"),
	SALES_RETURN_INSPECT("SALES", "销售", "判定销售退货质量", "提交客户退货质量判定与库存处置", Risk.CRITICAL, "QUALITY_INSPECTOR", "QUALITY_MANAGER", "ADMIN"),

	PLANNING_DEMAND_MAINTAIN("PLANNING", "计划", "维护独立需求", "创建、更新与关闭独立计划需求", Risk.STANDARD, "PLANNING_MANAGER", "ADMIN"),
	PLANNING_PARAMETER_MAINTAIN("PLANNING", "计划", "维护计划参数", "维护物料计划参数与补货策略", Risk.SENSITIVE, "PLANNING_MANAGER", "ADMIN"),
	PLANNING_MRP_RUN("PLANNING", "计划", "执行 MRP 运算", "发起净需求运算并保留计算证据", Risk.SENSITIVE, "PLANNING_MANAGER", "ADMIN"),
	PLANNING_MRP_SUGGESTION_REVIEW("PLANNING", "计划", "审核与转换 MRP 建议", "审核、驳回并转换采购或生产建议", Risk.CRITICAL, "PLANNING_MANAGER", "ADMIN"),

	PROCUREMENT_SUPPLIER_MAINTAIN("PROCUREMENT", "采购", "维护供应商", "创建、更新、启用或停用供应商主数据", Risk.SENSITIVE, "PROCUREMENT_MANAGER", "ADMIN"),
	PROCUREMENT_ORDER_APPROVE_RELEASE("PROCUREMENT", "采购", "审核与下达采购订单", "审核、驳回和下达采购订单", Risk.SENSITIVE, "PROCUREMENT_MANAGER", "PLANNING_MANAGER", "ADMIN"),
	PROCUREMENT_RECEIPT_REGISTER("PROCUREMENT", "采购", "登记采购到货", "登记采购收货并形成待检或合格库存事实", Risk.CRITICAL, "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "PROCUREMENT_MANAGER", "ADMIN"),
	PROCUREMENT_RETURN_AUTHORIZE("PROCUREMENT", "采购", "建立采购退货授权", "建立或取消供应商退货授权", Risk.SENSITIVE, "PROCUREMENT_MANAGER", "ADMIN"),
	PROCUREMENT_RETURN_SHIP("PROCUREMENT", "采购", "执行采购退回出库", "按原批次退回供应商或执行出库冲回", Risk.CRITICAL, "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN"),

	PRODUCT_BOM_MAINTAIN("PRODUCT", "产品与工艺", "维护 BOM 版本", "创建、更新并发布产品 BOM 版本", Risk.SENSITIVE, "PRODUCT_ENGINEER", "PLANNING_MANAGER", "ADMIN"),
	PRODUCT_ROUTING_MAINTAIN("PRODUCT", "产品与工艺", "维护工艺路线版本", "创建、更新并发布工艺路线版本", Risk.SENSITIVE, "PRODUCT_ENGINEER", "PLANNING_MANAGER", "ADMIN"),

	PRODUCTION_ORDER_CONTROL("PRODUCTION", "生产", "维护生产订单", "创建、下达、开工与控制生产订单", Risk.SENSITIVE, "PRODUCTION_MANAGER", "PLANNING_MANAGER", "ADMIN"),
	PRODUCTION_MATERIAL_ISSUE("PRODUCTION", "生产", "执行生产领退料", "维护备料并执行领料、退料和冲回", Risk.CRITICAL, "PRODUCTION_MANAGER", "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN"),
	PRODUCTION_OPERATION_EXECUTE("PRODUCTION", "生产", "执行车间工序", "开始、完成或恢复本人可执行的工序任务", Risk.SENSITIVE, "PRODUCTION_OPERATOR", "PRODUCTION_MANAGER", "ADMIN"),
	PRODUCTION_LABOR_READ("PRODUCTION", "生产", "查看工序人工", "查看受控工序人工与成本输入", Risk.SENSITIVE, "PRODUCTION_OPERATOR", "PRODUCTION_MANAGER", "FINANCE_MANAGER", "ADMIN"),
	PRODUCTION_LABOR_RECORD("PRODUCTION", "生产", "登记工序人工", "登记本人或受控范围内的实际人工工时", Risk.SENSITIVE, "PRODUCTION_OPERATOR", "PRODUCTION_MANAGER", "ADMIN"),
	PRODUCTION_LABOR_APPROVE("PRODUCTION", "生产", "审核工序人工", "审核、驳回或冲销工序人工记录", Risk.CRITICAL, "PRODUCTION_MANAGER", "ADMIN"),
	PRODUCTION_REPORT("PRODUCTION", "生产", "提交与结算生产报工", "提交产量、送检并完成合格品入库结算", Risk.CRITICAL, "PRODUCTION_OPERATOR", "PRODUCTION_MANAGER", "ADMIN"),

	QUALITY_INSPECTION_COMPLETE("QUALITY", "质量", "提交来料与完工检验", "提交 IQC 或完工检验数量、结论与缺陷证据", Risk.CRITICAL, "QUALITY_INSPECTOR", "QUALITY_MANAGER", "ADMIN"),

	WAREHOUSE_INVENTORY_MOVE("WAREHOUSE", "仓储与库存", "过账库存事务", "执行受控库存移动、调整、冻结或解冻", Risk.CRITICAL, "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN"),
	WAREHOUSE_PUTAWAY("WAREHOUSE", "仓储与库存", "执行收货上架", "完成、取消或冲回收货上架任务", Risk.CRITICAL, "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN"),
	WAREHOUSE_TRANSFER("WAREHOUSE", "仓储与库存", "执行库内调拨", "创建、完成、取消或冲回同仓库位调拨", Risk.CRITICAL, "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN"),
	WAREHOUSE_STOCK_COUNT("WAREHOUSE", "仓储与库存", "执行库存盘点", "创建盘点、录入实盘、审批差异或冲回", Risk.CRITICAL, "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN"),

	LABEL_OPERATION("LABELING", "现场标签", "生成工序标签", "生成或受控补打工序任务标签", Risk.SENSITIVE, "PRODUCTION_OPERATOR", "PRODUCTION_MANAGER", "ADMIN"),
	LABEL_SELF_EMPLOYEE("LABELING", "现场标签", "生成本人标签", "生成或受控补打当前认证人员的本人标签", Risk.SENSITIVE, "PRODUCTION_OPERATOR", "PRODUCTION_MANAGER", "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN"),
	LABEL_STOCK("LABELING", "现场标签", "生成库存标签", "生成或受控补打精确库存余额标签", Risk.SENSITIVE, "WAREHOUSE_MANAGER", "INVENTORY_CONTROLLER", "ADMIN"),

	FINANCE_ADVANCE("FINANCE", "成本与结算", "维护预收预付", "登记、抵扣或退还预收预付", Risk.CRITICAL, "FINANCE_MANAGER", "ADMIN"),
	FINANCE_GRIR("FINANCE", "成本与结算", "运行与冲回暂估", "生成或冲回采购收货暂估", Risk.CRITICAL, "FINANCE_MANAGER", "ADMIN"),
	FINANCE_ORDER_PROFIT("FINANCE", "成本与结算", "结算订单利润", "结算或重算订单收入与直接成本利润", Risk.CRITICAL, "FINANCE_MANAGER", "ADMIN"),
	FINANCE_PAYABLE("FINANCE", "成本与结算", "维护应付与付款", "登记供应商发票、付款、红字、退款与反核销", Risk.CRITICAL, "FINANCE_MANAGER", "ADMIN"),
	FINANCE_RECEIVABLE("FINANCE", "成本与结算", "维护应收与收款", "登记客户发票、收款、红字、退款与反核销", Risk.CRITICAL, "FINANCE_MANAGER", "ADMIN"),
	FINANCE_COST_RATE("FINANCE", "成本与结算", "维护工作中心费率", "发布或变更制造费用与人工费率", Risk.CRITICAL, "FINANCE_MANAGER", "ADMIN"),
	FINANCE_ACCOUNTING_PERIOD_MANAGE("FINANCE", "成本与结算", "维护与关闭会计期间", "创建并关闭会计期间", Risk.CRITICAL, "FINANCE_MANAGER", "ADMIN"),
	FINANCE_ACCOUNTING_PERIOD_REOPEN("FINANCE", "成本与结算", "重开会计期间", "填写原因后重开已关闭会计期间", Risk.CRITICAL, "ADMIN"),

	EQUIPMENT_ASSET("EQUIPMENT", "设备与资产", "维护设备台账与状态", "维护设备台账并执行人工受控状态流转", Risk.SENSITIVE, "MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN"),
	EQUIPMENT_WORK_ORDER("EQUIPMENT", "设备与资产", "执行设备运维工单", "执行点检、保养、维修送验与验收", Risk.SENSITIVE, "MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN"),
	EQUIPMENT_MAINTENANCE_PLAN("EQUIPMENT", "设备与资产", "维护周期保养计划", "维护周期模板并人工生成到期工单", Risk.SENSITIVE, "MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN"),
	EQUIPMENT_SPARE_PART("EQUIPMENT", "设备与资产", "维护备件与运维成本", "维护备件台账并登记或冲销运维成本", Risk.CRITICAL, "MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN"),
	EQUIPMENT_ALERT("EQUIPMENT", "设备与资产", "管理设备报警", "维护报警规则并确认、处理、解决或关闭报警", Risk.SENSITIVE, "MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN"),
	EQUIPMENT_OEE_MAINTAIN("EQUIPMENT", "设备与资产", "维护 OEE 与停机证据", "创建、编辑、提交或重提人工核实 OEE 记录", Risk.SENSITIVE, "MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN"),
	EQUIPMENT_OEE_APPROVE("EQUIPMENT", "设备与资产", "审核 OEE 记录", "审核通过或驳回人工核实 OEE 记录", Risk.CRITICAL, "PRODUCTION_MANAGER", "ADMIN"),
	EQUIPMENT_TELEMETRY("EQUIPMENT", "设备与资产", "管理设备采集连接", "维护、预检并运行 Modbus TCP 或外部 MQTT 只读连接", Risk.CRITICAL, "MAINTENANCE_MANAGER", "ADMIN"),
	EQUIPMENT_TELEMETRY_LIFECYCLE("EQUIPMENT", "设备与资产", "管理设备样本生命周期", "维护保留策略、清理样本和受控自动运行", Risk.CRITICAL, "MAINTENANCE_MANAGER", "ADMIN"),
	EQUIPMENT_FIELD_ACCEPTANCE_MAINTAIN("EQUIPMENT", "设备与资产", "维护设备现场验收单", "创建、编辑、提交或重提真实端点验收证据", Risk.CRITICAL, "MAINTENANCE_MANAGER", "ADMIN"),
	EQUIPMENT_FIELD_ACCEPTANCE_APPROVE("EQUIPMENT", "设备与资产", "审核设备现场验收", "独立批准或驳回真实端点现场验收", Risk.CRITICAL, "PRODUCTION_MANAGER", "ADMIN");

	private final String moduleCode;
	private final String moduleName;
	private final String displayName;
	private final String description;
	private final Risk risk;
	private final List<String> roleCodes;

	WorkspacePermission(String moduleCode, String moduleName, String displayName, String description, Risk risk, String... roleCodes) {
		this.moduleCode = moduleCode;
		this.moduleName = moduleName;
		this.displayName = displayName;
		this.description = description;
		this.risk = risk;
		this.roleCodes = List.of(roleCodes);
		if (!WorkspaceRoleCatalog.codes().containsAll(this.roleCodes)) {
			throw new IllegalArgumentException("权限目录包含未知角色编码: " + name());
		}
	}

	public String moduleCode() { return moduleCode; }
	public String moduleName() { return moduleName; }
	public String displayName() { return displayName; }
	public String description() { return description; }
	public Risk risk() { return risk; }
	public List<String> roleCodes() { return roleCodes; }
	public boolean allows(String roleCode) { return roleCodes.contains(roleCode); }

	public static List<WorkspacePermission> catalog() { return List.of(values()); }

	public enum Risk { STANDARD, SENSITIVE, CRITICAL }
}
