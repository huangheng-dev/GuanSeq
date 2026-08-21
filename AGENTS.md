# GuanSeq 产品与工程约束

GuanSeq（贯序）是独立于 Riveliq 的制造业一体化产品。Riveliq 只能作为产品和视觉参考，不得复制其品牌、代码、进度状态或领域实现。

## 产品边界

- 面向普通制造企业，专业完整但不过度复杂。
- 用户看到一套产品、一个工作台、一套主数据；内部保持 ERP、MES、WMS、QMS、PLM、APS、EAM 的事实边界。
- 第一条主闭环是：销售订单 → 计划/MRP → 采购与备料 → 生产工单 → 工序执行 → 质量检验 → 完工入库 → 发货 → 成本利润。
- 前端产品原型与工程基线已经收口；后续按“业务核心底座 → 第一条业务闭环 → 设备与智能能力”的顺序建设，不创建误导性的伪后端或伪集成。

## 前端原则

- 简体中文是默认产品语言，业务文案集中管理。
- 页面不得直接读取散落的静态 JSON；数据统一通过 `src/services` 访问。
- Mock 契约必须保留异步、错误和状态语义，未来由真实 API 适配器替换。
- 导航以用户工作任务命名，不把 ERP、MES 拆成两套产品入口。
- 视觉强调制造对象、状态、责任、风险、交期和证据，避免通用卡片拼贴。

## 强制开发流程

- 每次开发开始前必须阅读 [开发规范与交付流程](./docs/开发规范与交付流程.md)，并根据任务范围读取相关产品、架构和 ADR 文档。
- 先确认业务对象、状态流转、权限、数据所有权和验收条件，再设计数据库与 API，最后实现前后端。
- 优先交付可验证的纵向业务闭环，不以新增页面、接口数量或技术组件数量衡量完成度。
- 修改代码前检查现有实现、测试和工作区状态；保留用户已有改动，不顺手重构无关代码。
- 所有功能必须覆盖成功、加载、空数据、无权限、校验失败、接口失败和并发冲突等适用状态。
- 每次交付必须运行与风险相称的检查；完成项至少通过 lint、类型检查、自动化测试和生产构建。
- 数据库结构以 Flyway 迁移为事实来源，API 以 OpenAPI 契约为事实来源，重大架构决策以 `docs/adr` 为事实来源。
- 未通过完整完成标准的能力必须明确标记为 Mock、规划或未接入，不得在界面、文档或汇报中宣称已完成。

## 技术引入门禁

- 任何新框架、中间件、数据库、独立服务或部署组件都必须由当前业务需求证明必要性。
- 引入重大技术前必须创建 ADR，记录问题证据、决策、成本、退出条件和验证方式。
- 当前批准的基础架构是 Next.js 前端、Spring Boot 模块化单体、PostgreSQL 与 Flyway；架构清单只记录已经批准采用的组件。
- 不因“未来可能需要”提前增加基础设施，不为了技术先进同时引入功能重叠的组件。
- 前端不得直连数据库、消息中间件或设备 Broker；AI、报表和外部系统不得绕过业务 API 直接修改核心业务事实。

## 后端模块边界

- `guanseq-server` 采用 Spring Boot + Spring Modulith 模块化单体，按业务事实划分模块，不按 Controller、Service、Repository 技术层横切整个系统。
- 每个模块拥有自己的表、迁移脚本、领域模型和公开 API；其他模块不得直接访问其基础设施实现或修改其表。
- 事务边界位于应用用例层，领域规则不得散落在控制器、SQL、前端或消息消费者中。
- 后端权限校验是唯一可信边界；前端隐藏按钮不能替代后端授权。
- 关键业务动作必须具备责任人、时间、来源、请求编号和审计证据。


<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->
