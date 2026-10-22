# 手机压力测试部署与接口说明

## 1. 数据库迁移

在已初始化的业务数据库中，按以下顺序执行：

```text
server/sql/20261017_add_stress_test.sql
server/sql/20261018_add_stress_request_examples.sql
server/sql/20261019_add_stress_probe.sql
server/sql/20261020_add_stress_task_threads.sql
server/sql/20261021_add_stress_config_import_export.sql
```

脚本可重复执行，不修改历史迁移：

- 新增 `tool_stress_request_config`、`tool_stress_task`、`tool_stress_response` 三张表；
- 确保 `神奇小工具 / 压力测试 / 手机压力测试、请求配置` 菜单存在，已有手机压力测试菜单通过更新 `parent_id` 移入压力测试目录，`menu_id` 不变；
- 新增手机压力测试按钮和请求配置按钮权限；
- 新菜单默认授予启用的 `admin` 角色；为防止移动父目录后已授权角色看不到原页面，会向已拥有压力测试子菜单的角色补授“压力测试”父目录，但不会自动补授新增按钮。已有角色如需提交、停止、查看结果或删除任务，需在“系统管理 / 角色管理”中单独勾选对应按钮权限。
- `20261018_add_stress_request_examples.sql` 必须在 `20261017_add_stress_test.sql` 之后执行，仅向 `tool_stress_request_config` 插入 GET、POST、PUT、DELETE、HEAD、OPTIONS 六条示例配置，名称形如“示例 - GET”，全部 `enabled='1'`（停用）。脚本按示例名称 + `http_method` 判断是否已存在，重复执行不会增加记录，也不会覆盖或更新已有配置；示例 URL 均为 `https://example.invalid/...` 示意地址，启用前需替换为已授权接口并手动启用。
- `20261019_add_stress_probe.sql` 为 `tool_stress_request_config` 增加试活字段 `probe_status`（`untested`/`alive`/`dead`，默认 `untested`）、`probe_time`、`probe_message` 与索引，并新增“批量试活”按钮权限 `magic:requestConfig:probe`（默认授予启用的 `admin` 角色）。字段按 `information_schema` 判断后新增，重复执行不会报错。
- `20261020_add_stress_task_threads.sql` 为 `tool_stress_task` 增加 `threads_per_task`（任务内并发执行线程数，默认 2）；历史任务按默认值处理，重复执行不会报错。
- `20261021_add_stress_config_import_export.sql` 新增“配置导入 / 配置导出”按钮权限 `magic:requestConfig:import`、`magic:requestConfig:export`（默认授予启用的 `admin` 角色），不涉及表结构变更。

## 2. 权限标识

| 功能 | 权限 |
| --- | --- |
| 手机压力测试页面/任务列表 | `magic:mobileStressTest:view` |
| 提交测试 | `magic:mobileStressTest:add` |
| 停止任务 | `magic:mobileStressTest:stop` |
| 查看请求结果 | `magic:mobileStressTest:result` |
| 删除历史任务 | `magic:mobileStressTest:remove` |
| 请求配置页面/列表 | `magic:requestConfig:list` |
| 请求配置详情 | `magic:requestConfig:query` |
| 新增请求配置 | `magic:requestConfig:add` |
| 修改请求配置 | `magic:requestConfig:edit` |
| 删除请求配置 | `magic:requestConfig:remove` |
| 批量试活 | `magic:requestConfig:probe` |
| 配置导入 | `magic:requestConfig:import` |
| 配置导出 | `magic:requestConfig:export` |

普通用户只能操作自己的任务；服务层会按 `owner_id` 再次校验，管理员可操作全部任务。

## 3. 接口契约

统一前缀：`/tool/stress`。分页接口使用若依 `pageNum/pageSize`，返回 `TableDataInfo`。

### 3.1 任务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/task/list?phone=&status=` | 分页查询任务 |
| POST | `/task` | 创建并立即开始任务 |
| PUT | `/task/{taskId}/stop` | 停止运行中任务 |
| GET | `/task/{taskId}/result/list` | 分页查询任务请求结果 |
| DELETE | `/task/{taskId}` | 删除非运行中任务及其结果 |

创建任务请求：

```json
{
  "phone": "13800138000",
  "duration": 5,
  "unit": "MINUTE",
  "threads": 2
}
```

字段约束：

- `phone`：中国大陆 11 位手机号（`1[3-9]` 开头）；同一手机号全局最多一个 `RUNNING` 任务。
- `duration`：正整数；`unit` 为 `SECOND`、`MINUTE`、`HOUR`；换算为秒后受 `int` 存储范围限制（不超过 2147483647 秒），不设额外业务时长上限。
- `threads`：任务内并发执行线程数，可选；为空时取 `stress.threads-per-task`（默认 2），允许范围 1～`stress.max-threads-per-task`（默认 8），越界直接拒绝。线程数只决定同时执行多少个不同接口，不改变同一接口 61 秒的最小间隔。
- 任务记录使用提交时所有启用接口的配置副本，后续配置变化仅影响新任务。
- 创建后立即执行；浏览器关闭后由后端线程继续执行；应用重启不恢复，启动时把残留 `RUNNING` 标记为 `INTERRUPTED`。

任务状态：`RUNNING`、`COMPLETED`、`STOPPED`、`INTERRUPTED`、`FAILED`。
成功次数仅统计 HTTP 200-299；失败不额外重试，等待该接口下一次正常周期。

### 3.2 请求配置

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/config/list?configName=&enabled=` | 分页查询配置 |
| GET | `/config/{configId}` | 查询配置详情 |
| POST | `/config` | 新增配置 |
| PUT | `/config` | 修改配置 |
| DELETE | `/config/{configIds}` | 批量删除配置 |

配置字段：

| 字段 | 说明 |
| --- | --- |
| `configName` | 接口名称，最大 100 字符 |
| `requestUrl` | HTTP(S) 地址，最大 2000 字符，支持 `{{phone}}` |
| `httpMethod` | `GET/POST/PUT/DELETE/HEAD/OPTIONS` |
| `headersJson` | JSON 对象字符串；禁止 Host、Content-Length、Connection 等 JDK 受限头 |
| `queryParamsJson` | JSON 对象字符串，值支持字符串/数字/布尔值/数组 |
| `bodyType` | `JSON`、`FORM`、`RAW` |
| `bodyContent` | JSON 必须是合法 JSON；FORM 填写 JSON 对象；RAW 为原始文本；GET/HEAD 不允许请求体 |
| `contentType` | 可选，最多 200 字符；为空时按 `bodyType` 使用默认值 |
| `timeoutMs` | 1000-600000 毫秒，默认 3000（连接与读取超时共用该值） |
| `enabled` | `0` 启用、`1` 停用 |

`{{phone}}` 替换规则：
- URL：手机号做 URL 编码；
- Query/FORM：替换后按 UTF-8 做 `application/x-www-form-urlencoded` 编码；
- JSON 请求体：手机号做 JSON 字符串转义；
- 请求头：原文替换，手机号本身只允许中国大陆 11 位号码。

JSON/form 请求体中占位符必须位于 JSON 字符串内，例如 `{"phone":"{{phone}}"}`；未加引号的占位符会被校验拒绝。

### 3.3 批量试活

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/config/probe` | 启动一次试活，后台执行并立即返回初始进度 |
| GET | `/config/probe/progress` | 查询当前（或最近一次）试活进度 |
| POST | `/config/probe/stop` | 请求停止，已发出的请求自然结束，未开始的接口记入跳过 |
| PUT | `/config/disable-dead` | 一键停用全部“试活失效且仍启用”的配置，返回停用条数 |
| POST | `/config/export` | 按查询条件导出配置为 JSON 文件（`Content-Disposition: attachment`） |
| POST | `/config/import` | 上传 JSON 文件导入配置（`multipart/form-data`，字段名 `file`） |

请求体字段（全部可选）：`configIds`（指定接口，勾选行时使用）、`configName`、`enabled`、`probeStatus`；都不传表示全部接口。

判定规则：

- 用**不可投递号码** `10000000000` 替换 `{{phone}}`（10 开头 11 位，号码规划中不可能分配给个人用户，短信网关无法路由到机主），按配置的方法、请求头、请求体发起一次真实请求；
- 该号码可通过 `stress.probe-phone` 修改；若配置成真实可投递号码（`1[3-9]` 开头的 11 位），试活会被拒绝，除非同时显式设置 `stress.probe-phone-allow-live=true`；
- HTTP 2xx 记为 `alive`（有效），非 2xx 与网络异常记为 `dead`（失效），并回写 `probe_status`、`probe_time`、`probe_message`；
- 列表支持按试活状态筛选，失效接口在页面上以红色标签展示，悬停可看失效原因；
- 同一时刻只允许一个试活任务，重复提交返回“已有试活任务在执行中”；
- 试活不改动 `enabled` 字段，只更新试活状态；试活完成后可点「停用全部失效」一次性停用（权限 `magic:requestConfig:edit`），该操作只处理 `probe_status='dead'` 且仍启用的配置，可重复执行；
- 并发探测线程数由 `stress.probe-threads` 控制（默认 4），单次请求受该配置的 `timeoutMs` 约束。

### 3.4 配置导入 / 导出（JSON）

导出文件为**请求配置数组**，字段与导入一一对应，可直接回导：

```json
[
  {
    "configName": "首选0001 login.nipic.com/account/check-mobile",
    "requestUrl": "https://login.nipic.com/account/check-mobile",
    "httpMethod": "POST",
    "headersJson": "{\"accept\":\"application/json\"}",
    "queryParamsJson": "{}",
    "bodyType": "FORM",
    "bodyContent": "{\"mobile\":\"{{phone}}\"}",
    "contentType": "application/x-www-form-urlencoded;charset=UTF-8",
    "timeoutMs": 3000,
    "enabled": "0",
    "remark": "来源=xxx"
  }
]
```

导入规则：

- 支持两种形态：上述数组，或 `{"configs": [...]}`；
- 单文件 ≤10MB、单次 ≤2000 条，超出直接拒绝；
- 每条都按 `StressRequestValidator` 的现有规则校验（URL/方法/请求头/请求体/超时），校验失败的条目记入 `errors` 并继续处理后续条目；
- 去重键为 `requestUrl`：与库中已有配置重复、或同一文件内重复的条目计入 `skipped`；
- 导入不写入 `configId`、试活状态与审计字段（`probe_status` 重置为 `untested`，`create_by` 取当前登录用户）；
- 返回 `{total, inserted, skipped, failed, errors[]}`，页面会展示汇总与最多 50 条失败明细。

## 4. 执行与资源限制

- 一个任务的执行线程数在创建时指定（`threads`，默认 `stress.threads-per-task`=2，上限 `stress.max-threads-per-task`=8），多条线程并发执行不同接口；同一接口不会被两条线程同时开始。
- 同一接口本次开始到下一次开始至少 61 秒；任务各自独立计时，并发不改变该间隔。
- 持续时长从提交时间开始按实际经过时间计算，并注册独立截止定时器；到期或手动停止时停止调度，并异步尝试 `disconnect` 当前 HTTP 连接（已发送请求无法撤回）。取消动作使用有界异步执行器，慢 `disconnect` 不阻塞截止定时器或停止接口。
- 任务线程池（任务槽）有最大并发上限，使用无缓冲队列，满额时拒绝新任务而不是无限排队；默认 20，可通过 `stress.max-concurrent-tasks` 调整。停止后最多再等 10 秒让任务内工作线程收尾，避免慢连接长期占住任务槽。
- 单条响应最多读取/保存 64KB 字符/字节，超出标记 `truncated=1`；错误信息最多 1000 字符。
- 请求配置接口路径 `/tool/stress/config` 与 `/tool/stress/config/**` 加入 XSS 过滤排除；其他 `/tool/*` 路径和认证流程不变，配置内容按原始字符保存并由表单/接口校验保证合法性。
- 使用 JDK 默认 TLS 校验，不关闭证书或主机名校验。
- 应用关闭时停止线程池、取消定时器并尽力把运行中任务标记为 `INTERRUPTED`。
- 任务执行状态保存在当前应用实例内存中；数据库唯一索引可保证多实例下同一手机号也不会重复创建，但停止操作和重启中断恢复按实例生效。当前按单实例部署设计。

## 5. 测试说明

后端测试使用本机临时端口、真实慢 HTTP 服务和模拟传输层，不访问真实外部接口，覆盖：

- 61 秒接口间隔与短测试周期；
- 两条工作线程并发执行不同接口；
- 同一接口在两条线程下不会被同时开始，且间隔仍满足调度周期；
- 慢接口在前时后续接口仍能开始，不被慢接口饿死；
- 截止定时器取消真实慢响应体时不阻塞其他任务截止；
- 手动停止立即返回，迟到响应不记为成功；
- 并发提交同一手机号只有一个成功；
- 提交时配置副本不随后续修改变化；
- 启动中断恢复；
- 所有者授权与运行中任务不可删除；
- URL/JSON/FORM 模板编码、200-299 成功分类、响应截断；
- 批量试活：2xx 记有效、非 2xx 记失效（说明含状态码）、网络异常记失效（说明含异常信息）、多接口全部回写；
- XSS 过滤器下请求配置 URL/body 原样保留，其他 /tool 路径仍过滤。

运行后端测试：在 `server/` 目录执行 `mvn test`。
