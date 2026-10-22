# 通知中心（后端）

通知中心把「外部系统发通知」与「企业微信渠道配置」拆开：调用方只拿独立 API Key 调一个开放接口，后台维护路径、渠道、调用凭证并查看每条通知的各渠道结果。没有引入 MQ、没有替换若依认证体系，也没有修改基线 `application.yml`。

## 1. 数据库迁移

执行：

```sql
source sql/20261021_add_notification_center.sql;
source sql/20261022_notification_detail_url.sql;
```

脚本特性：

- 只新增 4 张业务表：`notification_channel`、`notification_credential`、`notification_record`、`notification_record_channel`。
- `CREATE TABLE IF NOT EXISTS`，不 DROP / TRUNCATE / DELETE 原表。
- 新增「通知中心」一级菜单及「通知渠道 / 调用凭证 / 通知记录」三个页面、按钮权限，并只给启用且未删除的 `admin` 角色补授权。
- 不写入任何真实 CorpID / AgentID / Secret 或 API Key。
- `20261022_notification_detail_url.sql` 用 `information_schema` + `PREPARE` 为 `notification_record` 幂等增加 nullable `detail_base_url varchar(500)`，并给 `sys_config` 插入固定 key `notification.publicBaseUrl` 的空值种子；不写死域名，不修改菜单、渠道或凭证数据，可重复执行。

关键约束：

- `notification_channel.route_code` 唯一。
- `notification_credential.key_hash` 唯一，库中只有 SHA-256。
- `notification_record.token_hash` 唯一；`(credential_id, request_id)` 唯一，用于同凭证幂等。
- `sent_at` / `expires_at` 只由首个成功渠道的 CAS 更新写入。

## 2. 配置

示例见 [`config/application-notification.example.yml`](../config/application-notification.example.yml)。将配置合并到本地私密配置或使用环境变量，**不要提交真实值**。

```yaml
notification:
  master-key: "${NOTIFICATION_MASTER_KEY:}"
  public-base-url: "${NOTIFICATION_PUBLIC_BASE_URL:https://hub.example.com}"
  dispatch:
    enabled: true
    scan-interval-ms: 2000
    batch-size: 20
    core-pool-size: 2
    max-pool-size: 4
    queue-capacity: 200
  rate-limit:
    window-seconds: 60
    per-credential: 60
    per-ip: 120
```

| 配置 | 说明 | 缺失时行为 |
| --- | --- | --- |
| `notification.master-key` / `NOTIFICATION_MASTER_KEY` | 渠道 Secret 与详情票据 AES-GCM 加密主密钥，建议至少 32 字节随机值 | 只禁用保存 Secret、接单与发送；应用照常启动，后台其他功能不受影响 |
| `notification.public-base-url` / `NOTIFICATION_PUBLIC_BASE_URL` | 前端站点公网地址，用于生成详情链接；通知渠道页保存的 `notification.publicBaseUrl` 优先，页面未配置时回退本项 | 两者都未配置时接单返回配置错误；绝不使用请求 Host 拼链接 |
| `notification.dispatch.*` | 有界发送线程池与扫描参数 | 使用代码默认值 |
| `notification.rate-limit.*` | 进程内凭证 / IP 固定窗口限流 | 使用代码默认值 |

`public-base-url` 生产必须是 `https`；仅 `localhost`、`127.0.0.1`、`::1` 允许开发期 `http`。地址不得带 userinfo、query / fragment，可带部署子路径与端口，保存时去掉末尾 `/`。

### 2.1 详情页地址保存优先级与生效方式

- 优先级：`sys_config.notification.publicBaseUrl` 非空且合法时优先；该 key 为空时回退私密配置 `notification.public-base-url` / `NOTIFICATION_PUBLIC_BASE_URL`。
- 通知渠道页的「详情页地址」卡片通过 `PUT /notification/channel/detail-url` 只修改这一个固定 key，使用 `ISysConfigService.updateConfig/insertConfig`，不读写主密钥或渠道 Secret。
- 保存成功后参数缓存同步刷新，立即影响后续新接单通知，无需重启；保存失败会返回明确错误，页面保留上一次生效值，不会假成功。
- 所有路径共用同一详情地址，不是每渠道一个地址；多路由同一通知仍共用同一条详情链接。
- 数据库值非法时不会静默回退，接单会明确报配置错误；管理员可在页面看到错误并修正。
- 历史记录不读取页面当前值：接单时保存地址快照，发送、重试、幂等返回和后台详情都使用该快照；旧记录缺少快照时只回退旧私密配置。

### 2.2 保存渠道提示「通知主密钥未配置」的排查

保存渠道时提示「后端通知加密主密钥未配置或长度不足」，指后端通知模块的加密主密钥缺失，**不是页面里的企业微信应用 Secret 填错**。

- **区分两种 Secret**：
  - 页面渠道表单的「Secret」：企业微信自建应用的 Secret（CorpSecret），用于调用企业微信接口，由渠道表单填写并加密落库。
  - `notification.master-key` / `NOTIFICATION_MASTER_KEY`：后端通知模块的加密主密钥，只用于 AES-GCM 加密渠道 Secret 与详情票据；不在渠道表单填写，也不能用企业微信应用 Secret 代替。
- **配置位置**：写入实际加载的 `config/application-local.yml`（本地私密配置，不提交），或设置环境变量 `NOTIFICATION_MASTER_KEY`。`config/application-notification.example.yml`、`config/application-local.example.yml` 只是示例，不会自动加载，必须把需要的配置合并到真实 `application-local.yml`。
- **长度要求**：至少 16 字符，推荐 32 字节随机值；不要使用默认固定值、可猜测字符串，或把企业微信应用 Secret 当主密钥。
- **生效方式**：修改配置文件或环境变量后必须重启后端；仅保存渠道表单不会生效。
- **已有主密钥不可随意更换**：已落库的渠道 Secret 与票据密文依赖该主密钥解密，直接更换会导致旧密文无法解密；如需轮换，应先评估并保留旧数据。
- 文档、日志、前端和提交中都不要放置任何真实主密钥。

## 3. 对外 API

### 3.1 受理通知

```http
POST /open/notifications
X-Notification-Key: ntf_xxx
Content-Type: application/json
```

请求：

```json
{
  "requestId": "order-20261021-0001",
  "routeCodes": ["order-alert", "ops-alert"],
  "messageType": "CARD",
  "title": "订单告警",
  "summary": "订单 123 支付超时",
  "detail": "## 处理建议\n\n- 检查支付回调\n- 联系客户"
}
```

字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `requestId` | 是 | 同一调用凭证下唯一，最长 128；重复调用必须内容一致 |
| `routeCodes` | 是 | 1-50 个，去重后全部发送；任一路径不存在/停用则整体拒绝 |
| `messageType` | 是 | `TEXT` 或 `CARD` |
| `title` | 是 | 最长 200 |
| `summary` | `CARD` 必填 | 最长 1000；`CARD` 的卡片描述只取该字段；`TEXT` 可在 `detail` 为空时作为正文 |
| `detail` | `CARD` 必填 | 最长 100000；`CARD` 为详情页 Markdown，绝不下发到消息；`TEXT` 作为纯文本正文。`TEXT` 时 `detail` 与 `summary` 至少一个非空 |

成功：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "recordId": 1001,
    "requestId": "order-20261021-0001",
    "duplicate": false,
    "detailUrl": "https://hub.example.com/notification/detail/xxxxxxxx",
    "status": "PENDING"
  }
}
```

说明：

- `X-Notification-Key` 与系统 `Authorization` 独立；不要复用后台登录 token。
- 同一凭证、同一 `requestId` 重复调用返回原记录（`duplicate=true`），不重复发送；内容指纹不一致返回业务码 `409`。
- 接单在单事务内锁定全部目标渠道，校验存在且启用，复制 CorpID / AgentID / UserID / 加密 Secret 快照后保存父记录与子记录。
- 接单时只解析一次当前详情页基地址并写入父记录 `detail_base_url` 快照；返回的 `detailUrl` 由该快照拼接，后续修改页面地址不影响该记录。
- 只要有一个路径不满足，整体回滚，不会出现部分渠道入队。
- `detailUrl` 基于接单快照或旧 `notification.public-base-url`，不是请求 Host。

错误码（HTTP 状态为 200，业务码在 body `code` 中）：

| code | 场景 |
| --- | --- |
| 400 | 参数/路径不合法、路径不存在或停用 |
| 401 | 缺少、错误或已禁用的调用凭证 |
| 409 | requestId 内容冲突，或原记录已删除 |
| 429 | 触发凭证 / IP 限流 |
| 503 | 主密钥或 public-base-url 未配置 |

### 3.2 公共详情

```http
GET /notification/detail/{token}
```

响应仅包含：

```json
{
  "title": "订单告警",
  "summary": "订单 123 支付超时",
  "detail": "## 处理建议...",
  "messageType": "CARD",
  "sentAt": "2026-10-21 09:00:00",
  "expiresAt": "2026-10-21 10:00:00",
  "serverTime": "2026-10-21 09:30:00",
  "expired": false,
  "accessMode": "WECHAT"
}
```

不包含渠道、CorpID、UserID、Secret、内部错误、请求方、票据哈希等。

访问矩阵（服务端每次请求重新判断）：

| 条件 | 结果 |
| --- | --- |
| 票据不存在 / 记录软删除 / 已撤销 | `404` |
| 微信个人或企业微信 UA（含 `MicroMessenger` 或 `wxwork`），且 `sentAt` 非空、`server now < expiresAt` | `200`，`accessMode=WECHAT` |
| 已登录且拥有 `notification:record:query` 权限 | `200`，`accessMode=LOGIN`；过期后仍可查看 |
| 未登录且不满足微信窗口 | `401`，前端展示登录链接 |
| 已登录但无查询权限且不满足微信窗口 | `403` |
| `sentAt` 为空（尚无成功渠道） | 不能免登录 |

安全边界：

- `expiresAt` 在首个渠道成功时 CAS 写入，固定为 `sentAt + 1 小时`。后续渠道成功和手动重试都不会延长。
- 票据为 256 bit `SecureRandom` 生成，URL-safe Base64；库中保存 SHA-256 哈希和加密票密文，后者仅用于幂等重复调用返回同一链接。
- 详情响应设置 `Cache-Control: no-store`、`Pragma: no-cache`、`Referrer-Policy: no-referrer`、`X-Robots-Tag: noindex`。
- **UA 可伪造边界已明确接受**：`MicroMessenger` / `wxwork` 只是免登录入口条件，不做 OAuth、不识别身份。真正的约束是随机票据、服务端时间、至少一次成功、撤销/删除和后台权限；不额外增加 UA 白名单或其他限制。
- 前端页面同时设置 `no-referrer`，Markdown 链接 `rel="noopener noreferrer"`。

### 3.3 后台接口

所有管理接口走现有 JWT 与方法权限，按钮权限：

| 接口 | 方法/路径 | 权限 |
| --- | --- | --- |
| 渠道列表 | `GET /notification/channel/list` | `notification:channel:list` |
| 渠道选项 | `GET /notification/channel/options` | `notification:channel:list` 或 `notification:record:list` |
| 渠道详情 | `GET /notification/channel/{channelId}` | `notification:channel:list` |
| 详情页地址查看 | `GET /notification/channel/detail-url` | `notification:channel:list` |
| 详情页地址保存 | `PUT /notification/channel/detail-url` | `notification:channel:edit`，并写操作审计 |
| 新增/修改/删除渠道 | `POST /notification/channel`、`PUT /notification/channel/{id}`、`DELETE /notification/channel/{ids}` | `notification:channel:add/edit/remove` |
| 凭证列表/详情 | `GET /notification/credential/list`、`GET /notification/credential/{id}` | `notification:credential:list` |
| 新增/修改/删除凭证 | `POST /notification/credential`、`PUT /notification/credential/{id}`、`DELETE /notification/credential/{ids}` | `notification:credential:add/edit/remove` |
| 重置凭证 | `PUT /notification/credential/{id}/reset` | `notification:credential:reset` |
| 记录列表 | `GET /notification/record/list` | `notification:record:list` |
| 记录详情 | `GET /notification/record/{recordId}` | `notification:record:query` |
| 仅重试失败渠道 | `POST /notification/record/{recordId}/retry` | `notification:record:retry` |
| 撤销链接 | `PUT /notification/record/{recordId}/revoke` | `notification:record:revoke` |
| 删除记录 | `DELETE /notification/record/{recordIds}` | `notification:record:remove` |

渠道接口永不返回 Secret 明文或密文，只返回 `secretConfigured`。凭证创建/重置响应中的 `apiKey` 只返回一次；操作日志关闭该响应记录并排除 `apiKey`。渠道 Secret 通过 `@Log(excludeParamNames = {"secret"})` 排除。

`GET /notification/channel/detail-url` 返回 `effectiveUrl`、`configuredValue`、`fallbackUrl`、`source`（`DATABASE` / `CONFIG` / `NONE`）与 `error`；`PUT` 只接受 `{ "url": "https://..." }`，复用 `NotificationUrlService.normalize` 校验，并把规范化结果保存到固定 key。GET 只需列表权限，无编辑权限的账号可查看但不能保存。

## 4. 发送、重试与恢复

- 使用 WxJava **weixin-java-cp 4.6.0** 真实 SDK，JDK8 / Spring Boot 2.5.15 兼容，不升级核心框架。
- 实际类：`me.chanjar.weixin.cp.bean.message.WxCpMessage`、`me.chanjar.weixin.cp.api.impl.WxCpServiceImpl`、`me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl`。4.6.0 的 Service 实现位于 `api.impl`（不是旧版的 `service.impl`），发送调用为 `WxCpService.getMessageService().send(message)`。
- `TEXT` 使用企业微信文本消息，正文取 `detail`（为空回退 `summary`），末尾追加完整详情链接，按 UTF-8 截断到 2048 字节以内。
- `CARD` 使用企业微信原生 `textcard`：只发送 `title`、`summary`（HTML 转义并按 128/512 UTF-8 字节截断）和跳转 `url`，绝不把 `detail` Markdown 放进卡片消息；详细 Markdown 只在独立详情页展示。
- 通知模块 factory 自己拥有每个实例独立的 Apache `PoolingHttpClientConnectionManager` / `CloseableHttpClient` 并在实例内复用（20 总连接、每 host 10），显式设置 connect/socket/connectionRequest 超时 5 秒，关闭 Apache 自动重试，并保留 SDK `maxRetryTimes=0`。不使用 SDK 全局单例池；`@PreDestroy` 只关闭本实例的 client/pool，DevTools 应用重载后新 factory 仍可正常使用。
- 成功定义：企业微信返回 `errcode=0` 即平台接受，不要求已读。若平台返回全部接收人无效，则按真实失败处理；部分接收人无效仍按平台接受成功。
- 发送读取接单时的渠道快照与详情页地址快照，后续修改渠道配置或页面地址都不会影响已接单记录。
- 重试复用原记录的 `detail_base_url`；旧记录缺失快照时兼容旧 `notification.public-base-url`，不套用后来写入数据库的新地址，避免历史链接被改写。
- 数据库待发送子记录就是队列：扫描线程只取 `PENDING`，用条件 `UPDATE ... WHERE status='PENDING'` 抢占，避免并发重复发送。
- SDK 调用在事务外；成功后由独立 `@Transactional` 完成器按“父记录 `FOR UPDATE` → 子渠道成功/失败 → 首次 `sentAt` CAS → 汇总”原子提交，汇总前父行锁串行化最新计数，避免并发覆盖。失败、手工重试与启动恢复遵守同一父行→子行锁顺序。
- 通知专用线程池有界（默认 2/4 线程、200 队列）；队列满时记录保持 `PENDING`，下一轮扫描再处理。
- 失败只落为 `FAILED`，不自动循环重试；后台只重置失败渠道为 `PENDING`，成功渠道不重复发送。
- 单次 SDK 发送关闭内部重试，HTTP 连接/读取使用 SDK 默认 5 秒超时，结果为有界等待。
- 启动时把上次进程遗留的 `SENDING` 渠道标记为 `FAILED`，错误说明「服务重启，发送结果未知」，不自动重发，需人工确认后重试。
- 当前启动恢复按共享数据库执行，适用于单实例或整组重启；多实例滚动发布时如需避免误伤其他实例在途任务，应后续增加实例心跳/租约，而不是盲目改回自动重发。

## 5. 敏感数据与日志

- Secret 使用 AES-256-GCM（随机 12 字节 IV）加密落库，主密钥经 SHA-256 派生；没有主密钥时拒绝相关操作，不阻断启动。
- API Key 为 32 字节安全随机值，库中只存 SHA-256；详情票据同样至少 256 bit。
- API Key 不放 URL、不在列表/详情回显、不写日志；Secret 不回显，操作日志排除。
- `@Log` 对渠道 Secret、凭证 apiKey 做了排除，凭证创建/重置不保存响应体。
- 新增接口路径不落在现有 XSS 过滤的 `/system/*`、`/monitor/*`、`/tool/*` 匹配内，因此 JSON Markdown / Secret 不会被 `EscapeUtil.clean` 改写；Markdown 的安全过滤在前端 DOMPurify 完成。
- WxJava 的 `BaseWxCpServiceImpl` 在 WARN/DEBUG 级别会输出完整请求 URL（可能含 `access_token`）和消息 JSON。`logback.xml` 对精确包 `me.chanjar.weixin` 设置 `level="OFF"`，不记录 SDK 原始日志；通知模块仍通过发送结果和记录表保存脱敏后的错误码与通用原因，应用仍可审计安全错误信息。

## 6. 测试

不启动真实数据库、Redis，也不发真实企业微信。已新增：

- `NotificationReceiveServiceTest`：鉴权、禁用凭证、重复 requestId、内容冲突、并发唯一键兜底。
- `NotificationRecordWriterTest`：全路径校验回滚、配置快照复制。
- `NotificationDispatchServiceTest`：claim 冲突、首个成功 1 小时、后续成功不延长、失败不置成功、部分失败、撤销、启动恢复。
- `NotificationDetailServiceTest`：四类微信/企微 UA、精确过期边界、无成功、撤销/删除、登录权限。
- `NotificationSecurityTest`：AES-GCM、摘要、主密钥缺失、公网 URL、JSON 不泄漏、限流、凭证哈希。
- `NotificationDetailUrlServiceTest`：数据库值优先、空值回退、非法值拒绝且不静默回退、URL 规范化、只更新固定 key、GET/PUT 权限与审计注解。
- 接单/发送/后台详情测试补充地址快照：接单只解析一次并落父记录；修改设置后重试、幂等返回和后台详情仍使用快照；旧记录回退旧私密配置。
- `WeComNotificationSenderTest`：mock 企业微信 SDK，验证 textcard 仅标题/摘要/链接、TEXT 正文、错误码、全部接收人无效。
- `WeComSdkHttpIntegrationTest`：JDK HttpServer 本地回环 + 真实 WxJava，核对实际序列化 textcard/TEXT JSON，不连接企业微信。
- `NotificationSendCompletionServiceTest`：父行锁 → 子行 → sentAt CAS → 汇总的锁顺序与事务注解。
- `NotificationRecordServiceTest`：只重试失败渠道且不改写过期时间。
- `NotificationMapperXmlTest`：只解析 Mapper XML，不连数据库，校验命名空间/语句 ID/XML 语法。

建议 Codex 执行：

```bash
mvn -Dtest='Notification*Test,WeCom*Test' test
```

## 7. 需要独立验证的点

1. Maven 依赖实际下载与 `WxCpServiceImpl` / `getMessageService().send()` API 编译核对。
2. MySQL 8 执行迁移脚本两遍，确认幂等、菜单父子关系、admin 授权不重复。
3. 真实企业微信测试环境发送 TEXT/CARD，核对内容截断、接收人无效、超时与错误码落库。
4. 并发重复 `requestId`、并发多渠道首个成功、重启恢复的数据库行为。
5. 浏览器验证手机/桌面微信、企微 UA、普通浏览器登录后过期查看、撤销/删除、Markdown XSS 与 Referer。

## 本地验收记录（2026-10-21）

- 后端通知模块 60 项测试全部通过，无跳过；包含真实 WxJava 向本地 HttpServer 发送 textcard/TEXT、无效接收人、实例关闭后重建、SDK 日志禁用验证。未向企业微信平台发送真实通知。
- 本地 MySQL 菜单迁移执行两遍：首次新增 15 个菜单及权限项，第二次新增 0 个，原有菜单内容未改变。
- 真实 MyBatis/MySQL 验证了渠道及凭证写入、渠道快照隔离、领取 CAS、首次成功时间 CAS、精确过期、登录权限、撤销和软删除；验收工具现使用显式 JDBC 事务，并在回滚后核对无测试记录残留。
- 前端通知及工具箱回归共 83 项通过；8 组真实应用浏览器流程通过，包括 Markdown 安全、移动布局、到期清空、旧凭证登录回跳、403/404 与三个后台页面。前端生产构建通过。
- 微信客户端仅使用代表性 UA 做规则测试；企业微信真实账号投递及各客户端实际打开效果，需配置应用凭据和公网 HTTPS 地址后联调。

### 2026-10-22 主密钥配置修复验收

本地主密钥缺失导致渠道保存失败，现已在被 Git 忽略的私密配置中补齐独立随机主密钥并重启后端。使用实际私密配置、假企业微信 Secret 验证渠道保存与加解密通过，不写入业务数据、不发送通知；12 项后端安全测试、8 项前端测试及页面模板编译通过。验收工具改用显式 JDBC 事务，并清理、复核了上次遗留的精确测试记录。

### 2026-10-22 详情页地址与渠道页通用化

- 新增迁移 `sql/20261022_notification_detail_url.sql`：可重复执行地增加 `notification_record.detail_base_url` 与 `sys_config` 空值种子。
- 新增 `GET/PUT /notification/channel/detail-url`，分别使用 `notification:channel:list` / `notification:channel:edit` 权限，保存写审计；只改固定 key，不触碰主密钥与渠道密钥。
- 接单时写入地址快照，发送、重试、幂等返回、后台详情统一读取快照；旧记录仅回退旧私密配置。
- 前端渠道页改为通用表头、provider 标签/摘要集中扩展、只读详情抽屉、分组编辑弹窗与统一地址卡片；移动端自适应。
- 验收：70 项通知后端测试通过，86 项通知与工具箱前端测试通过，生产构建通过；9 组 Chromium 模拟接口流程通过，包含渠道详情、地址保存成功/失败及仅查看权限。
- 本地增量迁移执行两遍通过，原有菜单、渠道和通知数据保持不变；真实 MyBatis/JDBC 验证地址快照落库及设置修改后仍使用快照，测试数据显式回滚并经独立连接复核无残留。后端已重启成功，本次验收未发送真实通知。
