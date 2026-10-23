# 个人工作台（/index）

本文说明 weishanHub 后台首页个人工作台的接口、数据库迁移、天气 Key 配置和验证方式。公开首页 `/`（太阳系）不受影响。

## 1. 功能范围

- 天气：按当前请求访问者 IP 使用高德 Web 服务 IP 定位；实况与今日预报分别请求高德 `extensions=base` 与 `extensions=all`，实况成功但预报失败时仍展示实况、高低温留空；定位失败可手动搜索中国大陆城市/区县；手动选城后可恢复 IP 自动定位。不接入 GPS。
- 快捷入口：按登录账号保存 `menuId` 与排序，读取和保存时按当前账号菜单权限实时校验；支持三级及多层菜单、ParentView、顶级 C 菜单和菜单 query；目录、按钮、外链、隐藏菜单不作为入口。
- 今日通知：统计北京时间 `[今日 00:00, 明日 00:00)` 内首个渠道成功 `sent_at` 的父通知记录数。仅拥有 `notification:record:query` 的账号可读取，管理员沿用既有 `*:*:*` 规则。
- 页面只保留以上模块；天气与通知卡片在上方并列，快捷入口在下方整行；没有通知权限时天气卡片占满；深色/浅色与移动端自适应。

首次启用快捷入口前，必须执行 sql/20261023_add_home_workbench.sql；单纯重启不会自动创建数据库表。若 /home/shortcuts 提示 sys_user_shortcut 不存在，应补执行该增量迁移，不能重新运行框架初始化脚本。

2026-10-23 已在本机 localhost:3306/weishanhub 补执行迁移，表结构、索引、实际 Mapper 查询、事务回滚读写、排序、用户隔离及重复执行均验证通过。该结果不代表线上数据库已迁移。

## 2. 后端接口

所有接口都要求登录，未新增匿名路径或认证白名单。

### 2.1 天气

`GET /home/weather?adcode=330100`

- 不传 `adcode`：按当前 HTTP 请求的访问者 IP 自动定位。接口不接受客户端 `ip` 参数，不会误查服务器城市。
- 传 `adcode`：手动选城。只接受 6 位中国大陆行政区划编码，城市名称以高德返回为准，不采信前端提交的名称。
- 实况请求 `extensions=base`（`lives`），今日预报请求 `extensions=all`（`forecasts[0].casts`），两次请求分别解析后合成；实况失败才返回 `WEATHER_FAILED`。
- 今日高低温以 `homeClock` 的北京时间日期匹配 `casts[].date`；不以 `reporttime` 日期代替今日，也不在无匹配时回退第一条预报。预报失败/无匹配时实况仍为 `OK`，高低温为 `null`。
- 响应始终为业务成功（`code=200`），天气失败通过 `data.status` 表达，避免影响首页其他模块。

响应 `data`：

```json
{
  "status": "OK",
  "configured": true,
  "locationMode": "IP",
  "message": null,
  "city": { "name": "杭州市", "adcode": "330100", "province": "浙江省", "city": "杭州市", "level": "city", "label": "杭州市" },
  "weather": {
    "cityName": "杭州市",
    "adcode": "330100",
    "weather": "多云",
    "temperature": "23",
    "minTemperature": "17",
    "maxTemperature": "26",
    "humidity": "61",
    "windDirection": "东南风",
    "windPower": "3",
    "reportTime": "2026-01-01 10:00:00",
    "fetchedAt": "2026-01-01 10:01:02"
  },
  "source": "高德地图",
  "updatedAt": "2026-01-01 10:01:02"
}
```

`status`：

| 值 | 含义 | 页面行为 |
| --- | --- | --- |
| `OK` | 天气成功 | 展示城市、图标/文字、温度、高低温、湿度、风向风力、更新时间、定位方式 |
| `NO_KEY` | 后端未配置高德 Key | 展示明确配置提示；不影响快捷入口和通知 |
| `UNSUPPORTED_IP` | 私网、本地、IPv6 或未知地址 | 提示手动选择城市 |
| `LOCATE_FAILED` | 高德 IP 定位失败、海外/无法识别、超时 | 提示手动选择城市 |
| `WEATHER_FAILED` | 已定位但实况查询失败/超时 | 保留已定位城市并提示稍后重试；预报失败不进入此状态 |

`locationMode` 为 `IP` 或 `MANUAL`。`source` 固定为“高德地图”，`reportTime` 是高德观测/发布时间，`updatedAt` 是本次响应时间（缓存命中时天气数据保留首次获取时间）。

### 2.2 城市搜索

`GET /home/weather/cities?keyword=西湖`

- 使用高德行政区划查询，支持中国大陆城市/区县，不写死城市列表。
- 返回 `level=city/district`，并额外放行高德返回 `level=province` 的四个直辖市 adcode：`110000`、`120000`、`310000`、`500000`；其他省份和港澳台仍过滤。
- 返回规范名称、adcode、省市和展示用 `label`。
- 未配置 Key 时返回 `configured=false` 和明确提示，不抛 5xx。

```json
{
  "configured": true,
  "message": null,
  "cities": [
    { "name": "西湖区", "adcode": "330106", "province": "浙江省", "city": "杭州市", "level": "district", "label": "杭州市 · 西湖区" }
  ]
}
```

### 2.3 快捷入口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/home/shortcuts` | 当前账号已保存且仍有权限/可见性的快捷入口，按保存顺序 |
| GET | `/home/shortcuts/options` | 当前账号有权限且可导航的站内页面，用于编辑抽屉搜索 |
| PUT | `/home/shortcuts` | 覆盖保存，body：`{ "menuIds": [3, 5, 8] }` |

规则：

- `userId` 只从登录身份读取，接口不接收客户端 `userId`。
- 保存时每个 `menuId` 必须存在于当前账号可用页面集合，否则整体返回 400；最多 20 个，不允许重复。
- 读取时重新计算权限，菜单失权、删除、隐藏、改为外链或组件不可导航后自动不显示。
- 数据库只保存 `user_id`、`menu_id`、`order_num` 和时间；标题、图标、路径、query 全部由当前菜单实时生成。
- 路由计算沿用若依菜单规则：顶级目录 `/<path>`，多层目录逐级拼接；顶级 C 菜单为 `/<path>`；ParentView 目录参与路径；仅 `menu_type='C'`、`is_frame='1'`、`visible='0'` 且组件为普通页面时进入候选；菜单 `query` 必须是 JSON 对象。

### 2.4 今日通知

`GET /home/notification/today`

- 服务端 `@PreAuthorize("@ss.hasPermi('notification:record:query')")`；无权限返回业务码 403，前端隐藏不能替代鉴权。测试通过 Spring 方法安全代理实际调用接口，验证无权限不会进入 service。
- 只返回父记录数、北京时间日期和时区，不返回通知内容、渠道配置或成功/失败明细。

```json
{ "count": 3, "date": "2026-01-01", "timeZone": "Asia/Shanghai" }
```

统计 SQL 语义：

```sql
select count(1)
from notification_record
where create_by = 'open-api'
  and sent_at >= ?   -- 北京时间今日 00:00
  and sent_at <  ?;  -- 北京时间明日 00:00
```

父记录只有一个 `sent_at`，因此多渠道、部分成功、已成功重试都只计 1；不按 `create_time`、`status` 或渠道子表计数；软删除记录不排除，不扣减历史统计。

## 3. 数据库迁移

**首次启用个人工作台前必须先执行本迁移。** 漏执行时 `sys_user_shortcut` 不存在，`/home/shortcuts` 会返回 500；Mapper 与前端无需改动。

新增独立增量脚本：[`sql/20261023_add_home_workbench.sql`](../sql/20261023_add_home_workbench.sql)。

- 创建 `sys_user_shortcut`（唯一键 `user_id + menu_id`，索引 `user_id + order_num`）。
- 为 `notification_record.sent_at` 按需补充索引；通过 `information_schema` 判断，MySQL 5.7/8 可重复执行。
- 只新增对象，不删除/修改原表或已有数据，不代替初始化脚本。

执行示例（在目标业务库执行，执行前自行备份）：

```sql
source sql/20261023_add_home_workbench.sql;
```

本实现未在真实数据库执行该脚本，需在部署前由验收环境执行并核对。

## 4. 高德 Key 配置

1. 在高德开放平台创建应用，添加 **Web 服务** 类型的 Key（不是 Web 端 JS API Key，也不是 Android/iOS Key）。
2. 确认该 Key 已开通“IP 定位”和“天气查询”对应的 Web 服务权限及配额。
3. 只配置在后端，任选一种：
   - 环境变量：`AMAP_WEB_SERVICE_KEY=你的Key`；
   - 私密本地配置 `server/config/application-local.yml`：

```yaml
home:
  weather:
    amap-key: "你的高德 Web 服务 Key"
```

`config/application-local.example.yml` 中 `home.weather.amap-key` 默认为空字符串，复制示例后需要显式填写真实 Key；空字符串会按未配置处理。`application.yml` 未写入任何高德 Key 或占位配置；`HomeWeatherProperties` 优先读取本地私密配置，未配置时再读取进程环境变量 `AMAP_WEB_SERVICE_KEY`。不要把 Key 写入任何 `VITE_` 变量、前端代码、SQL、日志或 Git。

Key 缺失时后端返回 `NO_KEY`，天气卡片明确提示，快捷入口和通知统计仍独立加载；不会伪造天气，也不会启动失败。

可选调优项（均有默认值，通常无需配置）：

```yaml
home:
  weather:
    connect-timeout-ms: 1500
    read-timeout-ms: 2500
    location-cache-minutes: 5
    weather-cache-minutes: 10
    city-cache-minutes: 10
    cache-max-entries: 500
```

## 5. 缓存、超时与安全

- 高德 IP 定位与天气均使用独立客户端，显式设置建连/读取超时；没有复用会打印完整 URL/响应且无超时的 `HttpUtils`。
- 实况与预报分别请求，各自短超时；预报失败不拖垮实况展示。IP 定位缓存 5 分钟、城市天气缓存 10 分钟、城市搜索缓存 10 分钟；进程内 LRU + TTL，最大条目数有界。失败结果不缓存。
- 高德请求失败只记录安全类别；不向前端返回 Key、含 Key 的 URL、原始异常或原始响应。
- 仅公网 IPv4 交给高德 IP 定位；私网、回环、链路本地、保留地址、IPv6、未知地址直接提示手动选城。
- 天气接口不接收客户端 IP；后端从当前请求解析访问者 IP。

## 6. 前端

- `web/src/api/home.js`：统一 request 封装。
- `web/src/views/index.vue`：工作台布局与权限条件渲染。
- `web/src/views/home/components/HomeWeatherCard.vue`：天气、手动选城、恢复自动定位、独立加载/错误状态。
- `web/src/views/home/components/HomeNotificationCard.vue`：今日统计；同时拥有 `notification:record:list` 时才显示跳转。
- `web/src/views/home/components/HomeShortcuts.vue`：快捷入口网格、编辑抽屉、搜索、拖动排序、保存刷新。
- `web/src/views/home/workbench.js`：图标映射、权限判断、搜索过滤和路由目标构造。

三个卡片分别请求、分别展示加载/错误，天气慢不会阻塞快捷入口和通知统计。

## 7. 验证命令

后端（在 `server/`，JDK 17 + Maven，不执行 `mvn clean`）：

```powershell
$env:JAVA_HOME='D:/AAAAA/develop/Java/jdk-17.0.5'
D:/AAAAA/develop/apache-maven-3.6.1/bin/mvn.cmd test
```

前端（在 `web/`）：

```powershell
C:/Program Files/nodejs/node.exe --test tests/home/*.test.js
npm run build:prod
```

当前自动化测试覆盖：高德 base/all 真实契约（含本地 JDK HttpServer 走真实 `AmapClient`，不访问外网）、缺 Key/私网/IPv6/定位失败、预报失败保留实况、北京时间今日 cast、缓存/超时、城市搜索直辖市与非大陆过滤、快捷入口权限/深层路由/排序、通知北京时间边界/软删除/多渠道父记录/Mapper SQL、通知方法安全代理 403。2026-10-23 独立验收：后端 Maven 全量 192 项测试通过（含上述本地 HTTP 契约与真实方法安全代理）；前端首页 4 项、通知页面 11 项测试通过，生产构建通过。浏览器使用模拟接口挂载真实页面，检查了无 Key 提示、权限隐藏且不请求统计、快捷入口添加/拖动排序/保存回读、手动选城/恢复定位、独立加载、桌面/移动端和深色布局；另提取实际 Mapper SQL 验证了统计日界、软删除及部分成功口径。模拟接口检查不等于跨设备真实数据库联调。本机数据库迁移已补执行并验证，线上迁移未执行，亦未使用真实高德 Key 联调；本次未提交、推送或部署。
