# 业务切片：外部 MQTT Broker 只读接入

> 状态：工程闭环完成（真实 Broker / 现场验收待完成）  
> 领域所有者：`equipment`  
> 架构决策：[ADR-0007](./adr/0007-外部MQTTBroker只读接入.md)  
> 前置切片：[可替换 Modbus TCP 只读采集](./业务切片-可替换Modbus只读采集.md)

## 业务目标

设备经理需要把设备台账中的设备连接到部署方自行选择的外部 MQTT Broker，通过受控 Topic 和 JSON Pointer 映射验证消息、启用只读采集，并在现有设备采集工作台查看通讯状态、质量、当前值、有限历史和操作证据。

## 业务对象与状态

- MQTT 连接复用 `DRAFT → ACTIVE ↔ PAUSED`；最近测试收到并解析全部配置 Topic 后才能启用。
- Broker 来源显式区分 `SIMULATOR` 与 `EXTERNAL_BROKER`；仿真只证明标准协议链路。
- 每个点位配置精确 Topic 和 JSON Pointer，首期不接受 `+`、`#` 通配符。
- 每条消息必须通过配置 JSON Pointer 提供稳定消息编号；同一连接、点位和消息编号只能产生一个样本。
- 可选设备时间使用 ISO-8601；平台始终保存独立接收时间。
- 数据质量继续使用 `GOOD / UNCERTAIN / BAD`，越界为 `UNCERTAIN`，连接/解析失败记录为通讯错误且不创建虚假 GOOD 样本。

## 权限与安全

- `ADMIN`、`MAINTENANCE_MANAGER` 可以创建、测试、启用、暂停和立即采集；当前工作区成员只读。
- 管理员可以看到 Broker 主机、Topic、JSON Pointer 和凭据引用名；普通成员不获得敏感连接细节。
- 用户名和密码只通过服务端环境变量解析，不写数据库、不进入 API、日志、事件详情或页面。
- 浏览器不直连 Broker；所有读取继续经过同源 BFF 与后端工作区权限。
- TLS 使用 JVM 信任库验证服务端；私有 CA 由部署方通过 JVM 信任库交付，首期不上传证书或私钥。

## Payload 契约

同一 Topic 可以承载多个点位，例如：

```json
{
  "messageId": "sim-000001",
  "deviceTime": "2026-08-26T14:00:00Z",
  "values": {
    "RUN_STATE": 2,
    "SPINDLE_LOAD": 68.5,
    "DOOR_CLOSED": true
  }
}
```

连接配置消息编号 `/messageId`、设备时间 `/deviceTime`；点位分别映射 `/values/RUN_STATE`、`/values/SPINDLE_LOAD` 和 `/values/DOOR_CLOSED`。已有 Broker 可以按自身 Schema 调整 Pointer，无需采用固定字段名。

## 验收条件

1. ADR-0007 已接受，V44 是 MQTT 参数、点位映射和消息去重的数据库事实来源。
2. OpenAPI 覆盖 MQTT 创建请求、脱敏连接响应和现有测试/启停/采集动作。
3. 标准 MQTT 3.1.1 协议测试覆盖 TCP、订阅、QoS、JSON 映射、消息编号和设备时间。
4. PostgreSQL 集成测试证明成功采集、重复消息无新增、无效 JSON/缺字段失败、权限和工作区隔离。
5. 页面可以在 Modbus TCP 与 MQTT 之间选择，并覆盖加载、校验、提交中、失败、空数据、无权限和成功状态。
6. 桌面和 390px 浏览器完成“创建 MQTT → 测试 → 启用 → 采集 → 查看 → 暂停”，控制台无错误。
7. lint、类型检查、前后端自动化测试和生产构建全部通过。

## 非目标

- 不内置、不启动、不强制推荐 EMQX、Mosquitto、HiveMQ Broker 或云 Broker。
- 不实现 MQTT 5 专属属性、Topic 通配、共享订阅、二进制 Payload、双向 TLS、证书上传或浏览器直连。
- 不改变当前采集调度的单实例限制，不新增独立设备服务、消息总线或边缘网关。
- 不实现远程控制、报警、OEE、趋势聚合或长期归档。
- 仿真通过不代表真实 Broker、现场网络、安全 ACL、容量或设备已经验收。

## 验证结果（2026-08-26）

- V44 已在 PostgreSQL 18.4 空库完整执行；标准 MQTT 3.1.1 协议测试和 PostgreSQL 集成测试覆盖消息映射、设备时间、重复消息编号去重与无效 JSON 失败。
- 后端全量 117/117 项测试通过、0 跳过；前端 lint、类型检查、29 个测试文件/85 项测试和生产构建通过。
- 浏览器经真实 BFF/API/数据库链路完成“创建 → 测试 → 启用 → 采集 → 查看 → 暂停”，得到 `2`、`67%`、`true` 三个 GOOD 当前值和 3 条有限历史。
- 1440px 与 390px 均通过；390px 文档宽度与视口同为 390px，最终控制台 0 错误、0 警告。
- 开发 Broker 只用于标准协议仿真；以上结果不等于用户真实 Broker、TLS/ACL、网络、容量或设备现场已经验收。
