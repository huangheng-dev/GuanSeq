# GuanSeq Server

GuanSeq 制造业务核心后端。组织与工作区、客户与物料主数据、产品 BOM/工艺路线、销售订单到独立需求、库存余额与不可变流水、采购/生产计划接收、MRP 净需求、MRP 建议审核转单，“生产报工 → 完工检验 → 合格品入库”“生产备料/领料单生成 → 按 BOM 组件发料 → 库存扣减 → 已领组件退料 → 库存回补”“生产订单下达 → 工序任务快照 → 开工/完工 → 全部工序完成后报工”，采购收货与来料检验、不合格评审与 CAPA、采购/领料/报工移动扫码、工序/本人/库存受控标签生成补打、仓储收货上架与库内扫码、库内调拨与单余额盘点、销售发货与成品出库、销售退货与质量处置、采购退货与供应商处置、销售应收开票与收款核销、采购应付发票与付款核销，订单收入、直接材料、工序人工、制造费用和毛利快照，以及设备台账、维护、运维成本、周期任务、Modbus/MQTT 只读采集、有限历史、受控样本保留、技术预检、现场接入验收、基础报警责任和人工核实 OEE/停机审批均已经接入；第一条制造与基础收付款主闭环、销售与采购反向实物流、质量不合格最小闭环、三个现场移动扫码切片、受控标签、仓储上架、调拨盘点及阶段 D 设备工程闭环已经贯通。

## 当前已经实现

- Java 25 LTS、Spring Boot 4.1.0。
- Spring Modulith 2.1.0 模块化单体边界。
- PostgreSQL 18.4 与 Flyway 数据库迁移。
- `platform`、`identity`、`masterdata`、`product`、`sales`、`procurement`、`planning`、`production`、`quality`、`warehouse`、`finance`、`equipment`、`labeling` 领域 Schema。
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
- 库内调拨支持同仓正式库位、开放额度、固定顺序余额锁、成对出入库流水、取消和补偿冲回；单余额盘点支持账面快照、实盘录入、差异审批、零差异、版本冲突与补偿冲回。
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
- 销售退货按已发订单建立授权，实物先进入 `INSPECTION`，质量判定后拆分为 `AVAILABLE` 与 `BLOCKED`；取消和收货冲回均保留责任事件，订单同时维护毛发货、累计退货和净发货，退货后可受控补发替换品。
- 采购收货支持桌面表单与移动扫码来源审计，移动端复用同一正式用例、权限和幂等边界；采购退货后允许按净收货缺口补收替换品，数据库同时保留毛收货、累计退货与净收货约束。
- 标签模块通过 production、identity、warehouse 公开接口读取工序、本人和库存事实，保存 Code 128B 载荷、模板版本、对象快照、首次/补打模式、份数、原因、责任人和稳定请求编号；首次唯一、补打前置记录、对象版本、角色、租户和本人身份均由后端校验，`PREPARED` 不表示物理出纸成功。
- 设备台账按租户编码唯一并隔离到当前工作区；设备经理、生产经理和管理员可执行受控人工状态流转，每次写入都校验期望版本、原因和请求编号并追加不可变事件。
- 一次性点检、预防性保养和维修工单按后端状态机推进；点检/保养失败原子生成维修工单，维修必须经过送验和验收，不能从台账动作绕过证据链。
- 周期维护模板与人工到期生成、备件领退与维修成本、Modbus TCP 只读连接/点位/采集、有限历史、人工和受控自动保留已经接入；真实设备、现场网络与容量仍未验收，不把协议仿真宣称为真实现场上线。
- MQTT 3.1.1 外部 Broker 只读适配已接入统一样本模型；连接测试记录结构化技术预检并固定 `fieldAccepted=false`。V45 已接入基础报警责任，V46 已接入人工核实 OEE 与审核冻结，V47 已接入独立现场验收单：只有物理设备或用户外部 Broker 在最新现场候选预检成功且网络、安全、只读、恢复、容量、映射六项证据完整后，才可提交并由独立角色批准。模拟器由后端拒绝验收。GuanSeq 不内置或强制 Broker；OPC UA、可选边缘网关、自动 OEE 来源/维护联动、厂商质量码/迟滞/风暴等高级报警、外部通知、长期归档和远程控制属于按需增强。
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
| `GUANSEQ_BUILD_VERSION` | `0.1.0-alpha.1` | 状态接口返回的构建版本 |
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
| `GUANSEQ_TELEMETRY_POLLING_ENABLED` | `false` | 是否启用已显式开启连接的后台周期采集 |
| `GUANSEQ_TELEMETRY_RETENTION_SCHEDULER_ENABLED` | `false` | 是否提供样本保留自动运行器；启用后仍需工作区管理员在产品内显式开启策略 |

MQTT Broker 凭据不写数据库。连接只保存别名，服务端按下列环境变量解析：

```text
GUANSEQ_MQTT_CREDENTIAL_<别名>_USERNAME
GUANSEQ_MQTT_CREDENTIAL_<别名>_PASSWORD
```

例如别名 `SHOPFLOOR_A` 对应 `GUANSEQ_MQTT_CREDENTIAL_SHOPFLOOR_A_USERNAME` 和 `GUANSEQ_MQTT_CREDENTIAL_SHOPFLOOR_A_PASSWORD`。不需要认证的开发 Broker 可以不填别名；不要把用户名或密码写进连接请求、数据库或日志。

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
├─ quality       来料/完工检验、质量判定、不合格评审与 CAPA
├─ warehouse     仓库、库位、库存余额与不可变流水
├─ finance       销售应收、采购应付、收付款核销、订单利润、物料标准成本、工作中心费率与结算快照
└─ equipment     设备台账、维护与报警状态机、只读采集、现场验收、人工核实 OEE、停机与不可变事件
```

每个业务模块后续按 `api / application / domain / infrastructure` 分层。模块只能通过公开 API 协作，不直接访问其他模块的数据表或基础设施实现。

## 下一里程碑

第一条制造主闭环和阶段 D 设备工程闭环已经形成可执行证据。正式安装时使用现有 V47 验收状态机接收真实现场资料并完成实例级上线门禁：

```text
厂商寄存器表或 MQTT Topic / Schema
→ 网络、安全、责任人与测试端点准入
→ 现场候选技术预检
→ 六项证据提交与独立审批
```

新增协议、自动 OEE、边缘网关、时序数据库和独立接入服务仍必须由具体现场与容量证据证明必要性；不因阶段 D 已完成而预先引入 EMQX、TimescaleDB 或独立服务。



