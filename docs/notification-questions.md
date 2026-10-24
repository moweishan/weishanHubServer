# 原生选择与通知交互接口

在现有通知接口上增加问题批次。先执行 `sql/20261024_add_notification_question.sql`。不影响普通 TEXT/CARD 通知，也不新增后台菜单。前端通知详情页会自动读取交互问题。

## 插件调用

三个接口均使用既有通知调用凭证 `X-Notification-Key`，不是后台 JWT 或企业微信 Secret。只能查询、结束同一凭证创建的问题。生产使用 HTTPS；前缀按部署加 `/prod-api`，本地直连 8080 不加前缀。返回仍是若依 `{code,msg,data}`。

`POST /open/notification-questions`：

```json
{
  "requestId": "每次原生问题生成的UUID",
  "routeCodes": ["已启用的通知路径"],
  "messageType": "CARD",
  "title": "DeepSeek 等待你的选择",
  "summary": "请确认下一步",
  "detail": "# 任务背景\n请在下方提交选择。",
  "instanceId": "插件启动实例UUID",
  "agentId": "真实原生Agent编号，可省略",
  "callId": "原生调用有提供时填写，可省略",
  "questions": [{
    "id": "mode", "question": "选择执行方案", "header": "方案", "multiSelect": false,
    "options": [{"label": "方案A", "description": "简要说明"}, {"label": "方案B"}]
  }]
}
```

通知与问题快照同事务写入，使用原有通知发送器。`routeCodes` 全部发送。相同凭证与 requestId 幂等；内容冲突返回 409；不能把普通通知改成交互问题。受理响应沿用通知接口的 recordId、requestId、detailUrl、status、duplicate。

`GET /open/notification-questions/{requestId}`：插件轮询（默认两秒），返回 `state`、`source`、`questions`、`answer`。不缓存。通知撤销或删除时返回 CANCELLED。没有问题返回 404，不自动选答案。

`POST /open/notification-questions/{requestId}/settle`：原生调用已采用答案后发送回执，支持幂等重试：

```json
{"state":"APPLIED","source":"REMOTE","answer":{"answers":[{"id":"mode","selected":["方案B"]}]}}
```

本地回答使用 source=LOCAL；取消使用 `{"state":"CANCELLED"}`。REMOTE 回执必须匹配已提交的答案。本地先完成时以原生调用实际采用的答案覆盖尚未应用的网页提交；已 APPLIED/CANCELLED 后不能再次改写。不得仅因答案已保存就发送 APPLIED。

## 详情页与提交

- `GET /notification/detail/{token}/question`：普通通知 data 为空；交互通知返回问题与状态，不包含内部归属编号或调用凭证。
- `POST /notification/detail/{token}/answer`：提交完整 `{"answers":[{"id":"mode","selected":["方案B"]}]}`。

逐次复用详情鉴权：首次成功发送后的一个小时内，微信/企业微信 UA 免登录，不识别回答者身份；其余情况需登录且有 `notification:record:query` 权限。过期不自动关闭问题，仍可登录提交。撤销或删除后禁止读写。所有响应禁止缓存与 Referer 泄漏票据。

每个问题 ID 必须出现一次，只能选择原有标签；单选不能同时有多个标签或混用非空 custom。多选可以附加 custom。`selected:[]` 且无 custom 表示显式跳过，不等于未收到答案。首份网页提交保存后不能改写，重复或并发提交返回 409。

状态依次为 WAITING → ANSWERED → APPLIED，也可由本地直接 WAITING → APPLIED，或取消进入 CANCELLED。网页在 ANSWERED 显示“等待应用”，不会提前宣称 AI 已继续。

APPLIED 表示答案已返回原生问答等待，不代表 AI 后续业务操作已成功完成。写入事务使用 READ COMMITTED，并锁定通知父行后重新授权及读取问题，避免 MySQL 默认快照读导致并发覆盖旧状态。

## 验证与运行边界

使用 JDK17 运行 `mvn -Dtest=Notification*Test test`。显式设置 `-Dweishanhub.integration=true -Dtest=NotificationQuestionDatabaseTest` 可使用本地私密数据库配置验证真实 Mapper 闭环，只提交增量建表、回滚合成业务记录、不启动真实发送器。旧 JDK8 的受限 AES 策略可能导致已有通知加密失败，应使用配置支持的 JDK。

DSH 关闭后原工具等待不自动恢复；答案保存在数据库不代表调用仍存活。插件网络回执失败会重试并告警，页面继续显示“等待应用”。实际桌面与企业微信闭环在确认安装配置后单独验收。

需要串联本机原生工具、正式插件入口、Controller/Service/Mapper/MySQL 和真实 Vue 表单时，在上述数据库测试命令增加 `-Dweishanhub.browserIntegration=true` 并保持本地 Vite 运行。该测试只启动回环 MockMvc HTTP 适配器，所有数据库请求回到父测试事务线程以便最终回滚，不启动调度器、正式后端或真实桌面。首次发送成功和原生 UI 应答器由测试模拟，页面请求与答案读写均使用真实实现。检查单选、多选、自由输入、跳过、普通浏览器拒绝免登录、无效答案拒绝和原生回执；完整断言完成后才接受子进程成功标记。它不验证真实桌面卡片、企业微信发送、JWT 过滤链或跨连接并发事务。
