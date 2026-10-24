若依原项目：[RuoYi-Vue-fast](https://github.com/yangzongzhuan/RuoYi-Vue-fast)

# weishanHub Server

## 平台简介

weishanHub 的 Java 后端，基于 RuoYi-Vue-fast 3.9.2，采用 Spring Boot 2.5.15、Spring Security、MyBatis、MySQL、Redis 和 JWT，保留若依原有认证、权限及基础管理功能。

配套前端：[weishanHubWeb](https://github.com/moweishan/weishanHubWeb)。开发规范见 [AGENTS.md](AGENTS.md)。

后台个人工作台（天气、快捷入口、今日通知）的接口、迁移及高德 Key 配置见 [docs/home-workbench.md](docs/home-workbench.md)。首次启用工作台前需先执行 `sql/20261023_add_home_workbench.sql`，否则快捷入口会因 `sys_user_shortcut` 缺失返回 500。

## 后端运行

通知详情页的原生问题远程回答接口及增量迁移见 [docs/notification-questions.md](docs/notification-questions.md)。

准备 JDK 8 或 17、Maven、MySQL 和 Redis，以下操作均在本仓库根目录执行。

```powershell
# 克隆项目
git clone https://github.com/moweishan/weishanHubServer.git
cd weishanHubServer

# 首次创建本地配置，已有文件时不要覆盖
Copy-Item config/application-local.example.yml config/application-local.yml
```

在 MySQL 客户端依次执行以下 SQL。初始化脚本会重建同名表，执行前确认目标并备份。

```sql
source sql/00_create_database.sql;
use weishanhub;
source sql/ry_20260417.sql;
source sql/quartz.sql;
```

编辑 `config/application-local.yml`，填写数据库账号、密码、Redis 连接信息及随机 JWT 密钥（至少 32 字节）。数据库默认为 `localhost:3306/weishanhub`，字符集 `utf8mb4`；Redis 默认为 `localhost:6379`。

本地文件由 `src/main/resources/application-druid.yml` 导入，不提交到 Git。也可不创建本地文件，使用 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_DATABASE`、`REDIS_PASSWORD`、`TOKEN_SECRET` 环境变量；本地文件中的显式配置可覆盖配置文件占位符。

```powershell
# 启动服务
mvn spring-boot:run
```

后端地址：[http://localhost:8080/](http://localhost:8080/)。

## 打包部署

先停止已运行的后端，再执行：

```powershell
mvn clean package
java -jar target/ruoyi.jar
```

生产环境配置独立凭据、持久化上传目录和日志目录；通过服务管理器运行后端，使用反向代理及 HTTPS 对外提供服务。配套前端默认使用 `/prod-api`，代理转发时去除该前缀，并设置后端 `API_PREFIX=/prod-api`。

## 开源许可

遵循 [MIT License](LICENSE)，保留 Copyright (c) 2018 RuoYi 及源码作者信息。
