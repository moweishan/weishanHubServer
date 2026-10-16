若依原项目：[RuoYi-Vue-fast](https://github.com/yangzongzhuan/RuoYi-Vue-fast)

# weishanHub Server

weishanHub 的 Java 后端，基于若依单应用版本构建，保留用户、角色权限、菜单、部门、岗位、字典、参数、日志、定时任务、监控和代码生成。当前阶段为基础框架，不包含额外业务模块。

本仓库可独立克隆和运行；配套前端为 [weishanHubWeb](https://github.com/moweishan/weishanHubWeb)。本文所有相对路径和命令均以本仓库根目录为准，无需外层工作区的文档或脚本。

## 技术与环境

| 项目 | 当前配置或已验证版本 |
| --- | --- |
| RuoYi-Vue-fast | 3.9.2 |
| Spring Boot / Spring Framework | 2.5.15 / 5.3.39 |
| Spring Security / JWT | 5.7.14 / jjwt 0.9.1 |
| MyBatis / MyBatis Starter | 3.5.13 / 2.3.1（实际解析依赖） |
| PageHelper Starter / Druid | 1.4.7 / 1.2.28 |
| JDK | POM 编译目标 1.8；已验证 JDK 8u341、17.0.5 |
| Maven | 已验证 3.6.1 |
| MySQL / Redis | 已验证 8.4.11 / 7.2.14；其他版本未做完整运行验证 |

核心版本以 [pom.xml](pom.xml) 为准，未擅自升级上游核心依赖。上游导入提交为 `8c0079b1b20a1d91d54bbba929ae22c80ad22e75`；上游多分支介绍不能代替当前 POM 的 Spring Boot 版本。

## 克隆与目录

```powershell
git clone https://github.com/moweishan/weishanHubServer.git
cd weishanHubServer
java -version
mvn -version
```

确认 `JAVA_HOME`、`PATH` 和 `mvn -version` 使用预期 JDK。本机曾出现命令行 Java 与 Maven 使用不同 JDK 的情况，不要仅检查 `java -version`。

```text
.
├── src/main/java/com/ruoyi/
│   ├── common/                  # 公共工具
│   ├── framework/               # 安全、配置、异常、Web 基础设施
│   └── project/                 # system、monitor、tool 基础模块
├── src/main/resources/
│   ├── application.yml          # 通用配置，默认 8080
│   ├── application-druid.yml    # 数据源及本地配置导入
│   ├── mybatis/                 # Mapper XML 与 MyBatis 配置
│   └── logback.xml
├── config/application-local.example.yml
├── sql/                         # 建库、基础管理、Quartz 初始化 SQL
├── pom.xml
├── LICENSE
├── README.md
└── AGENTS.md
```

## 初始化 MySQL

启动 MySQL，准备可访问 `weishanhub` 的数据库账号。新库默认字符集为 `utf8mb4`，排序规则为 `utf8mb4_unicode_ci`。建库可能需要管理员权限，应用运行账号建议单独授权。

从本仓库根目录连接数据库，将 `your_db_user` 替换为实际账号；`-p` 表示交互输入密码，无密码的本机开发账号可省略该参数：

```powershell
mysql --host=127.0.0.1 --user=your_db_user -p --default-character-set=utf8mb4
```

在 MySQL 客户端中依次执行：

```sql
source sql/00_create_database.sql;
use weishanhub;
source sql/ry_20260417.sql;
source sql/quartz.sql;
show tables;
```

原框架脚本含 `DROP TABLE`，会重建同名表。仅对已确认、已备份的初始化目标执行，不能用作日常增量迁移。初始化后共 31 张表；脚本保留原框架账户和示例数据，管理员用户名为 `admin`，初始密码规则见[若依官方文档](https://doc.ruoyi.vip/ruoyi-vue/)。上线前修改初始密码并处理演示账户。

## 本地配置与 Redis

默认连接 MySQL `localhost:3306/weishanhub` 和 Redis `localhost:6379`（索引 0）。启动 Redis；如启用认证，填写实际 Redis 密码。不要对共享 Redis 执行全库清空。

PowerShell 复制示例（首次克隆后操作；已有本地文件时先检查，避免覆盖）：

```powershell
Copy-Item config/application-local.example.yml config/application-local.yml
```

Linux/macOS 可使用 `cp -n config/application-local.example.yml config/application-local.yml`。编辑新文件，填写数据库用户名、密码、Redis 配置及随机 JWT 密钥；无密码开发连接使用 YAML 空字符串 `""`。示例中的说明文字不是有效凭据，必须替换。

可在 PowerShell 中生成 48 字节随机 JWT 密钥，再填入本地文件的 `token.secret`：

```powershell
$jwtBytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
```

`application-druid.yml` 的 `spring.config.import` 自动导入 `./config/application-local.yml`，路径相对于启动工作目录，因此应始终从本仓库根目录启动。该本地文件不会提交到 Git，也不会随克隆自动存在。可在本地 YAML 的 `spring.datasource.druid.master` 增加 `url` 覆盖 JDBC 地址。

也可不创建本地文件，使用环境变量：

| 变量 | 用途 / 默认 |
| --- | --- |
| DB_URL | 默认 localhost:3306/weishanhub，UTF-8、utf8mb4_unicode_ci；远程地址按部署环境设置 |
| DB_USERNAME、DB_PASSWORD | 数据库用户名及密码，必须由本地文件或环境提供 |
| REDIS_HOST、REDIS_PORT、REDIS_DATABASE | localhost、6379、0 |
| REDIS_PASSWORD | 默认空；启用 Redis 认证时必须提供 |
| TOKEN_SECRET | 随机 JWT 签名密钥，必须提供，不使用公开示例密钥 |
| UPLOAD_PATH、LOG_PATH | 默认 ./data/upload、./logs |
| API_PREFIX | Swagger 请求前缀；开发 /dev-api，生产设置 /prod-api |
| DRUID_CONSOLE_ENABLED | 默认 false；启用时设置 DRUID_USERNAME、DRUID_PASSWORD 并限制访问 |

本地 YAML 可覆盖配置文件中的占位符；不要在本地 YAML 与环境变量两处维护冲突凭据。后端不会自动读取 `.env`。某些 PowerShell 版本不能保留空字符串环境变量，无密码连接优先使用本地 YAML。

## 启动、构建和验证

开发启动：

```powershell
mvn spring-boot:run
```

默认监听 `8080`。访问 `http://localhost:8080/` 应看到 weishanHub 说明；访问 `http://localhost:8080/captchaImage` 应获得 `code`、验证码开关及开启时的 `uuid/img`。验证码和登录需要 MySQL、Redis 均可用。

构建与运行二选一启动方式，构建前先停止已有后端进程，避免 Windows 锁定 jar：

```powershell
mvn clean package
java -jar target/ruoyi.jar
```

构建产物为 `target/ruoyi.jar`。原仓库没有单元测试，构建显示 `No tests to run` 不等于接口已通过测试。独立克隆可直接验证匿名 `/getInfo` 返回业务 `code=401`，再配合前端识别真实验证码并登录，检查用户信息、动态菜单、字典和退出；禁止通过关闭认证完成联调。

初始化阶段已验证两个 JDK 的构建、Maven/可执行 jar 启动，以及验证码登录、用户/权限、路由、基础管理接口、分页、字典、异常、上传下载和退出失效。该记录描述历史验证，不代表本次文档修改重新执行了全部接口回归。完整权限矩阵与生产部署尚未验收。

## 前后端契约

前端开发 `/dev-api/*` 经 Vite 去前缀后转发到本后端 `8080`。生产通常由 Nginx 将 `/prod-api/*` 去前缀后转发到后端；本后端 Controller 路径无需添加这两个前缀。

- `POST /login` 请求含 username/password/code/uuid，成功返回 token。
- 后续使用 `Authorization: Bearer <token>`；`GET /getInfo` 返回 user/roles/permissions，`GET /getRouters` 返回 data 动态路由。
- `POST /logout` 删除 Redis 会话，原 Token 失效。
- 常规 JSON 为 code/msg/data；分页请求 pageNum/pageSize，响应 code/msg/rows/total。
- 文件通过 `/common/upload`、`/common/download`、`/common/download/resource`，沿用 multipart 与二进制约定；返回的 fileName 由前端拼接 API 前缀。
- Spring Security、方法权限、数据权限和统一异常处理均保留；前端按钮隐藏不能替代后端鉴权。

## 开发规范与部署

新增模块放在 `src/main/java/com/ruoyi/project/<模块>`，遵循 controller/domain/mapper/service 分层，Mapper XML 放在 `src/main/resources/mybatis/<模块>`。复用 AjaxResult、TableDataInfo、现有分页、日志、异常及安全机制。Java 类型 UpperCamelCase、成员 lowerCamelCase，表和字段 snake_case；业务表前缀随需求确认。

新增接口同步检查配套前端 API、菜单组件路径及 `模块:资源:动作` 权限标识。数据库变更提供独立 SQL 迁移，保留 sys_/gen_/QRTZ_ 框架表和代码生成功能。中文文档与新增注释；不自行增加业务、不大规模重构、不擅自升级核心依赖。

生产使用独立数据库账号、Redis 凭据、随机 JWT 密钥及持久化上传/日志目录；通过服务管理器守护 `java -jar` 进程，入口配置 HTTPS、代理与防火墙。不要将本地配置、密钥、上传、备份、日志或 target 提交；有意提交配置示例、SQL 和 LICENSE。AI 开发规范见 [AGENTS.md](AGENTS.md)。

## 常见问题

- 缺少 TOKEN_SECRET 或数据库拒绝连接：检查本地文件是否填写完整、工作目录是否为本仓库根目录、druid profile 是否启用。
- Redis 连接或认证错误：检查地址、端口、索引和密码，勿通过删除缓存逻辑解决。
- 旧 MySQL 客户端连接本机 MySQL 8.4 TLS 握手失败：可仅对本机命令增加 `--ssl-mode=DISABLED`；远程生产连接按实际 TLS 要求配置。
- 端口占用：先确认 8080 占用来源；若确需修改 `server.port`，同步调整前端 API_TARGET 和部署代理并记录。
- 前端 401：检查 Bearer Token、Redis 会话、角色与接口权限；不扩大匿名白名单。

## 来源与许可证

Copyright (c) 2018 RuoYi。遵循 [MIT License](LICENSE)，保留原项目地址、许可证和源码作者信息。项目使用 `com.ruoyi` 内部包名；weishanHub 是当前系统显示名称。前端官方来源：[RuoYi-Vue3](https://github.com/yangzongzhuan/RuoYi-Vue3)。
