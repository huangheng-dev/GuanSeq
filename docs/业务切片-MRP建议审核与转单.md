# 业务切片：MRP 建议审核与转单

## 目标

把 MRP 净需求结果推进为可审计、可并发控制的执行入口。计划人员先审核采购、委外或生产建议，再由采购或生产模块创建草稿单；审核本身不建单，转单也不自动下达。

## 事实所有权与边界

| 事实 | 所属模块 | 约束 |
| --- | --- | --- |
| MRP 建议、审核状态与转单证据 | 计划 | 只管理建议生命周期，不直接写下游表 |
| 采购/委外草稿单 | 采购 | 通过 `PurchaseOrderCommandService` 创建并继续使用采购状态机 |
| 生产草稿单 | 生产 | 通过 `ProductionOrderCommandService` 创建并继续使用生产状态机 |

模块化单体使用同一事务完成“创建下游草稿 + 回写建议转单证据”。采购与生产以 `source_type=MRP`、建议 ID 和运算编号保存来源；同一建议只能形成一张对应草稿单。

## 状态与规则

可执行建议状态：`PROPOSED → APPROVED → CONVERTED`，或 `PROPOSED → REJECTED`。

- `NONE`、`BLOCKED` 和净需求为零的结果不可进入审核列表。
- 只有计划负责人或管理员可以审核、驳回和转单。
- 驳回必须填写原因；审核意见可选。
- 只有 `APPROVED` 建议可以转单，审核不会创建任何订单。
- 采购/委外转单必须选择供应商、币种、税率、价格、采购员和要求到货日期。
- 生产转单必须填写计划开工、完工、车间和责任人。
- 每次动作校验建议版本；并发更新返回 `409`，相同请求号重试返回既有结果。

## API 与数据

- `GET /api/v1/planning/mrp-suggestions`
- `GET /api/v1/planning/mrp-suggestions/{id}`
- `POST /api/v1/planning/mrp-suggestions/{id}/actions`
- `POST /api/v1/planning/mrp-suggestions/{id}/convert`
- Flyway `V18__create_mrp_suggestion_decision_and_conversion.sql`

OpenAPI 是接口事实来源；Flyway 是结构事实来源。建议事件保存动作、前后状态、操作者、工作区、请求号、时间和上下文证据。

## 前端验收

`/planning/mrp/recommendations` 使用真实 API，提供搜索、类型/状态筛选、圆润自定义下拉、选择、CSV 导出、完整分页、详情抽屉、审核/驳回弹窗和按建议类型变化的转单表单。已转单记录可跳转采购订单或生产订单页面。

## 已验证与限制

自动化测试覆盖生产和采购建议的审核、转单、草稿来源追溯与请求幂等。当前仍不提供建议合并、拆分、批量转单、供应商自动寻源、自动下达或跨工厂调拨建议；这些能力需要独立规则和验收，不在本切片中伪装完成。
