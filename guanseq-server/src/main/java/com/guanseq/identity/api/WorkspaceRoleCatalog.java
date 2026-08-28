package com.guanseq.identity.api;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class WorkspaceRoleCatalog {

	public static final String ADMIN = "ADMIN";

	private static final List<WorkspaceRoleRecord> ROLES = List.of(
			new WorkspaceRoleRecord(ADMIN, "系统管理员", "管理当前工作区成员，并可执行全部已接入业务动作"),
			new WorkspaceRoleRecord("SALES_MANAGER", "销售经理", "审核、驳回和下达销售订单"),
			new WorkspaceRoleRecord("PLANNING_MANAGER", "计划经理", "维护需求与参数，执行 MRP 并审核建议"),
			new WorkspaceRoleRecord("PROCUREMENT_MANAGER", "采购经理", "维护、审核和下达采购业务"),
			new WorkspaceRoleRecord("PRODUCT_ENGINEER", "产品工程师", "维护并发布 BOM 与工艺路线"),
			new WorkspaceRoleRecord("PRODUCTION_MANAGER", "生产经理", "维护生产订单、工序、领料、报工和人工审核"),
			new WorkspaceRoleRecord("PRODUCTION_OPERATOR", "生产操作员", "执行工序、登记人工并提交生产报工"),
			new WorkspaceRoleRecord("MAINTENANCE_MANAGER", "设备经理", "维护设备台账并执行人工受控运行状态流转"),
			new WorkspaceRoleRecord("QUALITY_MANAGER", "质量经理", "维护质量结论与质量业务"),
			new WorkspaceRoleRecord("QUALITY_INSPECTOR", "质量检验员", "提交来料与完工检验结论"),
			new WorkspaceRoleRecord("WAREHOUSE_MANAGER", "仓储经理", "执行收发、领退料和库存事务"),
			new WorkspaceRoleRecord("INVENTORY_CONTROLLER", "库存控制员", "执行受控库存事务与库存核对"),
			new WorkspaceRoleRecord("FINANCE_MANAGER", "财务经理", "维护收付款、成本、利润和会计期间"));
	private static final Set<String> CODES = ROLES.stream()
			.map(WorkspaceRoleRecord::code)
			.collect(Collectors.toUnmodifiableSet());

	private WorkspaceRoleCatalog() {
	}

	public static List<WorkspaceRoleRecord> roles() {
		return ROLES;
	}

	public static Set<String> codes() {
		return CODES;
	}
}
