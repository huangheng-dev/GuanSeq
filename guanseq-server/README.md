# GuanSeq Server

GuanSeq 制造业务核心后端。组织与工作区、客户与物料主数据、产品 BOM/工艺路线、销售订单到独立需求、库存余额与不可变流水、采购/生产计划接收、MRP 净需求、MRP 建议审核转单，“生产报工 → 完工检验 → 合格品入库”“生产备料/领料单生成 → 按 BOM 组件发料 → 库存扣减 → 已领组件退料 → 库存回补”“生产订单下达 → 工序任务快照 → 开工/完工 → 全部工序完成后报工”，采购收货与来料检验、销售发货与成品出库、销售应收开票与收款核销、采购应付发票与付款核销，订单收入、直接材料、工序人工、制造费用和毛利快照，以及设备台账与人工受控运行状态均已经接入；第一条制造与基础收付款主闭环已经贯通，设备阶段首个纵向切片已经完成。

## 当前已经实现

- Java 25 LTS、Spring Boot 4.1.0。
- Spring Modulith 2.1.0 模块化单体边界。
- PostgreSQL 18.4 与 Flyway 数据库迁移。
- `platform`、`identity`、`masterdata`、`product`、`sales`、`procurement`、`planning`、`production`、`quality`、`warehouse`、`finance`、`equipment` 领域 Schema。
- 版本化 OpenAPI 契约入口。
- 平台状态接口与 `X-Request-Id` 请求追踪。
- 组织、工作区、用户成员关系与当前工作区并发版本模型。
- 受保护的工作区查询、切换接口，以及越权拒绝和服务端审计。
- 客户与物料的分页查询、新建、编辑、批量负责人修改、停用和恢复。
- 主数据按当前工作区所属租户隔离，写入采用乐观并发版本并生成审计事件。
- 销售订单聚合客户和物料快照、明细、数量、价格、税率、金额与交期，支持草稿、待审核、已审核、已驳回和已下达状态。
- 销售订单提交、审核、驳回和下达均执行状态与角色门禁，并保留请求编号和审计证据。
- 销售订单下达后通过模块公开事件，按订单明细在同一事务内生成有效独立需求；任一环节失败时订单下达整体回滚。
- 独立需求支持销售订单与人工两类来源；人工需求支持草稿、有效、已取消状态，销售来源快照禁止手工篡改。
- MRP 输入接口只返回当前租户的有效独立需求，并保留来源单据、来源行、物料、数量、日期、责任和版本证据。
- 仓库、库位、库存余额与不可变流水，支持入库、出库、生产退料、分配、取消分配、冻结和解冻，并验证幂等、乐观锁、质量状态和数量边界。
- MRP 冻结需求、库存、采购/生产计划接收和提前期快照，按日期净算并对自制缺口展开有效 BOM，形成采购、生产或委外建议。
- MRP 建议通过受控审核后，可经采购或生产模块公开命令接口创建带来源追溯的草稿单；审核、转单、并发版本和幂等请求均保留证据。
- 采购订单与生产订单拥有独立状态机，并通过模块公开接口提供计划接收事实。
- 生产模块可按已下达/执行中订单和生效 BOM 生成领料单，支持分批发料、取消、组件退料、库存证据、动作请求号幂等和乐观并发控制。
- 生产订单下达时按有效工艺路线生成不可变工序任务快照；工序支持开工、完工、班次/操作人/数量/请求号审计、乐观锁和动作幂等，首道工序开工自动转入订单执行中。
- 完工报工前校验同单工序任务均已完成；未完成工序返回冲突，避免越过车间执行直接报工。
- 已开工生产订单支持并发安全的生产报工，自动创建质量模块拥有的完工检验任务。
- 完工检验记录合格、不合格、缺陷和检验责任；仅合格数量可生成幂等的成品入库流水并回写生产完成量。
- 生产订单不提供直接完工动作，达到计划数量且不存在在检报工时由结算自动完成。
- 工序实际人工支持登记、完工后审核、冲销、幂等请求、乐观并发和审计事件；只有已审核分钟进入成本。
- 订单利润按已发数量生成收入快照，按生产实际领退料净用量和有效标准成本结转直接材料；人工按已审核实际分钟、制造费用按已完工工序标准分钟和完工日有效工作中心费率归集，缺少证据时明确标记为缺成本。
- 应收发票按销售订单已发数量分批开具，累计开票不得超过实际已发；收款按具体发票核销，支持部分收款、全部收清、请求幂等、乐观并发和审计事件。
- 应付发票按采购订单合格收货数量分批登记，待检和不合格数量不可开票；付款按具体发票核销，并验证供应商发票号唯一、请求幂等、乐观并发和审计事件。
- 设备台账按租户编码唯一并隔离到当前工作区；设备经理、生产经理和管理员可执行受控人工状态流转，每次写入都校验期望版本、原因和请求编号并追加不可变事件。
- 设备采集、网关、点位、自动报警、OEE、远程控制、点检和维修工单尚未接入，不把人工状态宣称为实时遥测。
- 三态身份适配器：`local` Profile 使用开发 Basic 身份，`oidc` Profile 验证正式 Bearer JWT；两种方式互斥。
- OIDC 用户声明必须精确映射到启用的贯序内部账号；外部角色不替代工作区和业务权限事实。
- 空正式库可通过显式开启、令牌保护且数据库只允许成功一次的初始化 API，原子创建首个公司、工厂、工作区和 `ADMIN` 用户；入口默认不存在。
- 仅在 `local` Profile 写入示例组织数据，正式迁移不写入示例数据。
- 模块边界测试、契约测试和真实 PostgreSQL 权限/迁移/API 集成测试。

## 本地运行

需要 Java 25 LTS 和 Docker Desktop。项目自带 Maven Wrapper，不要求全局安装 Maven。

```powershell
docker compose up -d --wait postgres
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

验证状态：

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/platform/status
```

验证本地工作区：

```powershell
$credential = Get-Credential -UserName lin.hao
Invoke-RestMethod http://localhost:8080/api/v1/me/workspaces -Authentication Basic -Credential $credential
```

本地默认密码是 `guanseq_dev`，只用于开发身份适配器。

停止本地数据库：

```powershell
docker compose down
```

数据保存在 `guanseq-postgres-data` 卷中；只有明确需要清空本地数据时才使用 `docker compose down -v`。

## 配置

| 环境变量 | 本地默认值 | 说明 |
|---|---|---|
| `GUANSEQ_DB_URL` | `jdbc:postgresql://localhost:5432/guanseq` | PostgreSQL JDBC 地址 |
| `GUANSEQ_DB_USERNAME` | `guanseq` | 数据库用户 |
| `GUANSEQ_DB_PASSWORD` | `guanseq_dev` | 仅用于本地开发的默认密码 |
| `GUANSEQ_BUILD_VERSION` | `0.1.0-SNAPSHOT` | 状态接口返回的构建版本 |
| `GUANSEQ_DB_PORT` | `5432` | Compose 暴露的本地端口 |
| `GUANSEQ_SECURITY_MODE` | `development` | 安全模式：`disabled`、`development` 或 `oidc`；本地 Profile 默认开发模式 |
| `GUANSEQ_DEV_USERNAME` | `lin.hao` | 本地开发用户名 |
| `GUANSEQ_DEV_PASSWORD` | `guanseq_dev` | 本地开发密码 |
| `GUANSEQ_OIDC_ISSUER_URI` | 无 | 正式 JWT 的 OIDC 签发者，`oidc` Profile 必填 |
| `GUANSEQ_OIDC_JWK_SET_URI` | 无 | 身份提供者 JWK 集地址，`oidc` Profile 必填 |
| `GUANSEQ_OIDC_AUDIENCE` | 无 | 后端要求的访问令牌受众，`oidc` Profile 必填 |
| `GUANSEQ_OIDC_USERNAME_CLAIM` | `preferred_username` | 精确映射内部启用用户名的 JWT 声明 |
| `GUANSEQ_BOOTSTRAP_ENABLED` | `false` | 是否临时注册首次初始化入口；仅空正式库首次开通时设为 `true` |
| `GUANSEQ_BOOTSTRAP_TOKEN` | `disabled` | 首次初始化独立高熵令牌，至少 32 字符；成功后必须删除 |

正式环境必须显式注入数据库凭据，不使用本地默认密码。应用不再默认激活 `local` Profile；未接入正式身份提供方时，受保护接口保持关闭。正式启动示例：

```powershell
$env:SPRING_PROFILES_ACTIVE = "oidc"
$env:GUANSEQ_OIDC_ISSUER_URI = "https://identity.example.com/realms/guanseq"
$env:GUANSEQ_OIDC_JWK_SET_URI = "https://identity.example.com/realms/guanseq/protocol/openid-connect/certs"
$env:GUANSEQ_OIDC_AUDIENCE = "guanseq-api"
.\mvnw.cmd spring-boot:run
```

后端会校验签名、签发者、受众和令牌时间，并在认证阶段拒绝未知或停用的贯序用户。OpenAPI 只把 Bearer JWT 作为正式安全契约；Basic 仅是本地实现细节。

空正式库不能预置客户身份数据。首次上线应按 [试点上线运行手册](../docs/试点上线运行手册.md) 临时开启 `POST /api/v1/bootstrap/initial-workspace`，以已核实的 OIDC 用户名创建首个内部管理员。数据库悲观锁保证并发请求只有一个成功；成功后即使保留原令牌也不能再次初始化，但仍须立即关闭入口并删除令牌。不要用人工 SQL 绕过初始化事务与审计。该入口不承担后续用户管理；初始化完成后，当前工作区 `ADMIN` 通过 `/api/v1/identity/workspace-users` 开通租户内部账号、分配受控角色并停用或恢复当前工作区成员关系。用户和成员关系分别使用乐观锁，动作写入审计；动态角色、组织层级维护和 IdP 自动同步仍未建设。

## 测试

```powershell
.\mvnw.cmd verify
```

Docker 可用时，测试会启动一次性 PostgreSQL 18.4 容器，执行真实 Flyway 迁移并验证 Schema 和 API。Docker 不可用时数据库集成测试会明确跳过，模块边界和契约测试仍会运行；正式交付不接受跳过真实数据库测试。

## 模块结构

```text
com.guanseq
├─ platform      请求追踪、安全边界与平台治理
├─ identity      用户、组织、工作区与授权
├─ masterdata    客户与物料主数据
├─ product       BOM 与工艺路线受控版本
├─ sales         销售订单、审核、变更与交付承诺
├─ procurement   供应商、采购订单与采购计划接收
├─ planning      独立需求、物料参数与 MRP 净需求
├─ production    生产订单、生产计划接收、工序执行、生产备料领退料与生产报工
├─ quality       完工检验与质量判定
├─ warehouse     仓库、库位、库存余额与不可变流水
├─ finance       销售应收、采购应付、收付款核销、订单利润、物料标准成本、工作中心费率与结算快照
└─ equipment     设备台账、人工状态机与不可变状态事件
```

每个业务模块后续按 `api / application / domain / infrastructure` 分层。模块只能通过公开 API 协作，不直接访问其他模块的数据表或基础设施实现。

## 下一里程碑

第一条制造主闭环已经形成真实证据链，设备台账与人工状态事实也已落地。下一阶段优先形成不依赖物联网基础设施的设备运维闭环：

```text
点检/保养计划与执行记录
→ 故障报修、维修工单、停机原因与验收
→ 备件领用和设备运维成本证据
```

点位模型、边缘网关和遥测接入必须由具体设备与协议需求证明必要性后再建设；不因进入设备阶段而预先引入 EMQX、TimescaleDB 或独立接入服务。



