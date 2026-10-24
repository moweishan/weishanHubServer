# 线上通知发送接口文档

核对日期：2026-10-22。线上 JAR 的 SHA-256 与本次部署产物一致；已只读核对渠道和详情页地址配置，未发送真实通知。后续配置调整以后台当前值为准。

## 1. 接口与调用准备

| 项目 | 当前线上值 |
| --- | --- |
| 请求地址 | `https://moweishan.top/prod-api/open/notifications` |
| 请求方法 | `POST` |
| 请求格式 | UTF-8 JSON |
| 鉴权请求头 | `X-Notification-Key: <你的通知调用凭证>` |
| Content-Type | `application/json` |
| 当前已启用的路径编码 | `wxapp` |
| 当前渠道类型 | 企业微信应用 `WECOM_APP` |
| 当前详情页基础地址 | `https://moweishan.top` |

在后台“通知中心 → 调用凭证”创建启用的调用凭证，保存创建时返回的完整 API Key。明文只在创建或重置时返回一次，列表中的前缀不能用于调用。已有 Key 丢失时需要重置，并同步所有使用该 Key 的调用方。

`X-Notification-Key` 与后台登录的 `Authorization: Bearer ...` 相互独立。调用发送接口不需要后台登录 Token，也不需要传企业微信 CorpID、AgentID、Secret 或后端通知主密钥。接收人由所选通知渠道配置决定，当前接口没有临时指定接收人的参数。

## 2. 请求参数

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `requestId` | string | 是 | 同一 API Key 所属凭证下的业务唯一标识，非空，最长 128；重试同一通知必须沿用此值 |
| `routeCodes` | string[] | 是 | 1～50 个已配置且启用的路径编码；当前可用 `["wxapp"]`；每个编码为 1～64 位字母、数字、下划线或短横线 |
| `messageType` | string | 是 | 大写 `TEXT` 或 `CARD` |
| `title` | string | 是 | 非空，最长 200 |
| `summary` | string | CARD 必填 | 最长 1000；卡片中的简略内容；TEXT 时可作为备用正文 |
| `detail` | string | CARD 必填 | 最长 100000；CARD 时为详情页 Markdown；TEXT 时作为纯文本正文 |

TEXT 的 `detail` 与 `summary` 至少一个非空，正文优先取 `detail`。CARD 的 `summary` 和 `detail` 都必须非空。

`routeCodes` 中选中的路径全部发送，重复编码会去重。任一路径不存在、停用或缺少必要 Secret 时，整条新通知拒绝受理，不会只发送其中一部分。渠道配置在受理时保存快照，之后修改渠道不会改变该通知的发送目标。

### 2.1 卡片通知

```json
{
  "requestId": "backup-20261022-001",
  "routeCodes": ["wxapp"],
  "messageType": "CARD",
  "title": "数据库备份完成",
  "summary": "weishanHub 数据库备份成功，点击查看详情。",
  "detail": "# 数据库备份完成\n\n- 数据库：weishanhub\n- 结果：成功\n- 耗时：12 秒\n\n## 说明\n\n备份文件已保存到指定目录。"
}
```

企业微信收到原生 `textcard`，显示标题、简略内容和详情链接。Markdown 详细内容只在详情页展示，不会直接发送到卡片。`summary` 按文本处理并转义 HTML，不支持用 HTML 自定义卡片样式。

接口字段长度和企业微信显示限制不同：实际卡片标题按 128 UTF-8 字节、描述按 512 UTF-8 字节安全截断，中文会占多个字节。需要完整展示的内容应放入 `detail`。

### 2.2 文本通知

```json
{
  "requestId": "health-20261022-001",
  "routeCodes": ["wxapp"],
  "messageType": "TEXT",
  "title": "服务巡检结果",
  "detail": "后端、数据库和 Redis 均运行正常。"
}
```

文本消息包含标题、正文，末尾自动附加“查看详情”链接。企业微信中按纯文本显示，不渲染 Markdown；长文本会截断，代码按 2000 UTF-8 字节预算保留完整详情链接。

## 3. 调用示例

将上面的卡片请求保存为 UTF-8 编码的 `notification.json`。示例不会提供真实 Key，请通过调用环境的私密配置注入。

### 3.1 curl（Bash）

```bash
curl --request POST \
  'https://moweishan.top/prod-api/open/notifications' \
  --header 'Content-Type: application/json' \
  --header "X-Notification-Key: ${NOTIFICATION_API_KEY}" \
  --data-binary '@notification.json'
```

### 3.2 PowerShell

```powershell
# 预先通过安全方式设置环境变量 NOTIFICATION_API_KEY。
$headers = @{ 'X-Notification-Key' = $env:NOTIFICATION_API_KEY }
$body = [System.IO.File]::ReadAllBytes((Resolve-Path './notification.json').Path)
$result = Invoke-RestMethod `
  -Method Post `
  -Uri 'https://moweishan.top/prod-api/open/notifications' `
  -Headers $headers `
  -ContentType 'application/json; charset=utf-8' `
  -Body $body `
  -TimeoutSec 15

if ($result.code -ne 200) {
  throw "通知受理失败：$($result.code) $($result.msg)"
}
$result.data
```

不要把真实 Key 写进前端代码、公开仓库或日志。不要将通知 Key 当作通知加密主密钥使用。

## 4. 响应与发送状态

首次受理成功示例，ID 和详情票据仅为示意：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "recordId": 1001,
    "requestId": "backup-20261022-001",
    "duplicate": false,
    "detailUrl": "https://moweishan.top/notification/detail/<随机票据>",
    "status": "PENDING"
  }
}
```

| 响应字段 | 含义 |
| --- | --- |
| `code` | 业务返回码，200 表示受理成功 |
| `msg` | 结果说明 |
| `data.recordId` | 后台通知记录 ID |
| `data.requestId` | 调用方提交的幂等标识 |
| `data.duplicate` | true 表示同一通知的幂等重复调用，没有重新发送 |
| `data.detailUrl` | 完整详情页地址，直接使用返回值，不自行拼接票据 |
| `data.status` | 当前记录状态，首次受理通常为 PENDING |

**这是异步接口：`code=200` 表示已受理，不表示企业微信已经收到，也不表示用户已读。** 后台扫描任务再执行发送。发送状态如下：

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 等待发送 |
| `SENDING` | 发送处理中 |
| `SUCCESS` | 所有目标渠道均发送成功 |
| `PARTIAL_SUCCESS` | 部分渠道成功、部分失败 |
| `FAILED` | 所有目标渠道失败 |

最终结果在“通知中心 → 通知记录”查看。成功表示企业微信平台接受消息，不代表已读。失败渠道不会自动重试，需要在后台明确发起重试；只重试失败渠道，成功渠道不重复发送。

如需程序查询，可调用管理接口：

```http
GET https://moweishan.top/prod-api/notification/record/{recordId}
Authorization: Bearer <后台登录 Token>
```

此接口要求 `notification:record:query` 权限，通知 API Key 不能用于查询。成功后 `data.record` 为父记录，`data.channels` 为各渠道发送结果，`data.detailUrl` 为详情链接。当前没有仅凭通知 API Key 查询最终结果的开放接口，也没有调用方回调参数。

## 5. 幂等与重试

- 幂等范围为“同一调用凭证 + 同一 `requestId`”。同一通知请求超时或断线时，使用原 `requestId` 和原内容重试，避免重复发送。
- 同一标识、相同内容返回原记录，`duplicate=true`；即使原记录发送失败也不会重新入队。需要后台重试失败渠道。
- 同一标识、不同内容返回业务码 409。判断内容包括消息类型、标题、摘要、详情及去重后的目标路径集合；路径顺序变化不产生新通知。
- 已删除记录的 `requestId` 不允许复用。新通知使用新的唯一标识；不要为网络重试重新生成标识。
- 修改后台详情页基础地址只影响之后新受理的通知；历史通知和幂等响应沿用接单时的地址快照。

## 6. 错误与限流

业务异常通常仍返回 HTTP 200，必须检查 JSON 中的 `code`；Nginx、网络、TLS 等故障还可能表现为非 200 HTTP 状态或无 JSON 响应。

| 业务码 | 典型原因 | 建议 |
| --- | --- | --- |
| 400 | 路径不存在/停用、路径编码非法、消息内容不满足要求 | 修正请求或后台渠道配置 |
| 401 | 缺少 Key、Key 无效或调用凭证被禁用 | 检查通知 Key 和凭证状态 |
| 409 | requestId 内容冲突或原记录已删除 | 检查幂等标识；不要盲目更换标识重发 |
| 429 | IP 或调用凭证请求过于频繁 | 降低频率，等待窗口结束后用原标识重试 |
| 503 | 后端通知主密钥或详情页地址配置不可用 | 由管理员处理后端配置 |
| 500 | 系统异常；当前实现中的部分 Bean Validation 参数校验也使用此业务码 | 同时检查 msg，不要只按 400 判断参数错误 |

例如缺少调用凭证：

```json
{
  "code": 401,
  "msg": "缺少调用凭证"
}
```

目前线上私密配置未覆盖限流项，使用代码默认固定窗口：窗口 60 秒，每个调用凭证最多 60 次、每个来源 IP 最多 120 次。重复调用也会占用限流额度；这是进程内限流，不是企业微信平台配额。

## 7. 详情页访问规则

使用发送接口返回的 `detailUrl` 打开页面，地址形如：

```text
https://moweishan.top/notification/detail/<随机票据>
```

- 从首个渠道发送成功起计算 1 小时；后续渠道成功或手动重试不延长该窗口。
- 窗口内，微信或企业微信内置浏览器（手机端或桌面端）的 User-Agent 含 `MicroMessenger` 或 `wxwork` 时免登录，不识别具体用户；持有链接的访问者均可查看。
- 普通浏览器、超过 1 小时、尚无渠道发送成功时，必须登录后台并拥有 `notification:record:query` 权限。
- 链接不存在、记录被删除或链接被撤销时返回不可用，不再允许访问。
- 这里依据客户端 UA 判断微信环境，不是微信 OAuth 身份验证；UA 可以被伪造，详情链接本身也应妥善保管。

详情页面由前端加载数据；如需区分网络请求，JSON 数据接口为 `/prod-api/notification/detail/{token}`，它与上述不含 `/prod-api` 的网页地址不同。不要将通知 API Key 放在详情链接中。

## 8. 核对依据与验证范围

- 请求契约：`NotificationOpenController`、`NotificationAcceptRequest`、`NotificationReceiveService`。
- 异步发送及企业微信内容：`NotificationDispatchService`、`WeComNotificationSender`。
- 详情访问：`NotificationDetailController`、`NotificationAccessPolicy`。
- 线上只读核对：JAR 校验值、启用路径 `wxapp`、详情页基础地址、限流配置覆盖项。
- 本次未创建凭证、修改渠道、提交真实通知或验证企业微信实际送达。
