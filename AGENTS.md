若依原项目：[RuoYi-Vue-fast](https://github.com/yangzongzhuan/RuoYi-Vue-fast)

# weishanHub Server AI 开发规范

## 1. 项目概述

本仓库是 weishanHub 的独立 Java 后端，基于 RuoYi-Vue-fast 3.9.2。当前为基础框架阶段，保留全部基础管理与代码生成；不在无需求时添加业务模块。沟通、文档及新增注释使用中文。以本仓库 README、POM 和实际源码为依据，不依赖外层工作区文件。

## 2. 技术栈

Spring Boot 2.5.15、Spring Framework 5.3.39、Spring Security 5.7.14、MyBatis 3.5.13 / Starter 2.3.1、PageHelper Starter 1.4.7、Druid 1.2.28、jjwt 0.9.1、Fastjson2 2.0.64、Springfox 3.0.0。POM 编译目标 Java 8，已验证 JDK 8u341、17.0.5 和 Maven 3.6.1；本机初始化验证使用 MySQL 8.4.11、Redis 7.2.14。版本以 pom.xml 及实际解析依赖为准。

上游导入提交：8c0079b1b20a1d91d54bbba929ae22c80ad22e75；不得将上游其他分支的 Spring Boot 版本当作本仓库版本。

## 3. 目录与仓库

仓库：https://github.com/moweishan/weishanHubServer.git，当前 master 跟踪 origin/master。仓库根目录即 pom.xml 所在目录，与配套前端独立管理，不创建嵌套 Git 仓库。

- src/main/java/com/ruoyi/common：公共工具。
- framework：配置、安全、Web 基础设施、异常处理。
- project/system、monitor、tool：原有基础管理、监控、生成器。
- src/main/resources/mybatis：Mapper XML。
- application.yml、application-druid.yml（位于 src/main/resources）：通用配置与数据源。
- config/application-local.example.yml：无真实凭据示例；application-local.yml 为被忽略的本地配置。
- sql/：00_create_database.sql、ry_20260417.sql、quartz.sql。
- README.md：独立部署指南；LICENSE：原 MIT 许可证。

## 4. 架构与接口

Spring Security JWT Filter → Controller → Service → Mapper/XML → MySQL。Redis 保存 LoginUser 会话、验证码、字典/参数缓存和限流等信息。POST /login 获取 JWT；前端附加 Authorization: Bearer token；/getInfo 返回用户、角色和权限；/getRouters 返回动态菜单；/logout 删除会话。

使用现有 AjaxResult(code/msg/data)、TableDataInfo(code/msg/rows/total) 和 pageNum/pageSize。上传 multipart，下载二进制。保留 @PreAuthorize、数据权限、审计日志、统一异常和验证码，不为测试绕过认证。前端按钮权限不能替代后端检查。

## 5. 配置与环境

后端默认 8080，MySQL localhost:3306/weishanhub（utf8mb4 / utf8mb4_unicode_ci），Redis localhost:6379/0。默认 druid profile 从启动工作目录导入 ./config/application-local.yml，所以从本仓库根目录运行。

独立克隆后先按 README 初始化数据库，再复制 config/application-local.example.yml 为 application-local.yml，填写实际数据库、Redis 配置和随机 JWT 密钥。已有文件不得直接覆盖。也可完全使用环境变量 DB_URL、DB_USERNAME、DB_PASSWORD、TOKEN_SECRET 和 REDIS_HOST/PORT/DATABASE/PASSWORD；后端不自动读取 .env。不得输出、提交或在文档记录真实本地凭据。

UPLOAD_PATH 默认 ./data/upload，LOG_PATH 默认 ./logs。生产 API_PREFIX=/prod-api；Druid 控制台默认关闭，启用需设置独立凭据与访问限制。完整变量和配置优先级见 README。

## 6. 启动与构建

在本仓库根目录执行：

```powershell
mvn -version
mvn spring-boot:run
```

另一种运行方式（先停止已有后端，Windows 会锁 jar）：

```powershell
mvn clean package
java -jar target/ruoyi.jar
```

原框架没有单元测试，不能把 No tests to run 写成单测通过。验证首页、/captchaImage、匿名 /getInfo，再配合前端真实登录检查相关接口；不要依赖仓库外的检查脚本。MySQL 初始化顺序为 sql/00_create_database.sql → use weishanhub → sql/ry_20260417.sql → sql/quartz.sql；原脚本含 DROP TABLE，执行前确认目标并备份。

## 7. 开发规范

- 新业务位于 project/<明确模块>/controller、domain、mapper、service，XML 位于 resources/mybatis/<模块>，遵循当前模块现有实现。
- 优先复用公共工具、Service、分页、响应与权限机制；不随意修改 com.ruoyi 包名。
- Java 类型 UpperCamelCase、方法和变量 lowerCamelCase；数据库表/字段 snake_case，业务前缀随需求确认。
- 新增接口同时核对 [配套前端](https://github.com/moweishan/weishanHubWeb) 的 src/api、菜单路径和按钮权限；权限标识沿用 模块:资源:动作。
- 保留用户、角色、菜单、字典、日志、生成器及原有其他功能；保留 sys_、gen_、QRTZ_ 框架表。
- 数据库变更提供独立 SQL 迁移，不能重跑初始化脚本代替迁移。
- 不擅自升级 Spring Boot、Security、MyBatis 等核心依赖；不大规模重构或新增无需求模块。
- 本地配置、密钥、运行数据、备份、日志与 target 禁止提交；示例、SQL、LICENSE 必须保留。

## 8. AI 协作规范

每次开发先读本文件和相关实现；控制修改范围；接口变更检查前端适配；执行可行的构建或针对性验证，说明未执行部分与阻塞原因。涉及长期运行方式、结构、版本或约定变化时同步 README 和 AGENTS；明确区分已完成、进行中、计划和已知问题。不得将真实密码或密钥写入任何文档。

## 9. 当前状态

已完成：完整后端源码、中文品牌、独立仓库、本地敏感配置导入、原始 SQL 和独立开发文档。初始化阶段已验证 JDK8/17 构建、Maven/jar 启动，以及真实验证码认证、权限/菜单、分页、字典、异常和文件传输等接口。当前文档变更不表示重新执行全部运行验证。

未完成：生产服务守护、HTTPS、完整权限矩阵和逐页面验收。具体业务尚未规划或实施。独立克隆没有开发者的本地配置、数据库或 Redis，需要按 README 自行准备。

## 10. 后续注意事项

遵守 MIT 许可证，保留 Copyright (c) 2018 RuoYi、文件顶部上游地址与作者注释。新增业务先确认需求，扩展沿用认证、权限和数据访问边界。生产凭据及初始账户处理由实际部署负责，不沿用公开示例密钥或本机开发配置。
