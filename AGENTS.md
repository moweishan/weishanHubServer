若依原项目：[RuoYi-Vue-fast](https://github.com/yangzongzhuan/RuoYi-Vue-fast)

# weishanHub Server AI 开发规范

## 项目上下文

weishanHub 的独立 Java 后端，基于 RuoYi-Vue-fast，使用 Spring Boot、Spring Security、MyBatis、MySQL、Redis 和 JWT。保留用户、角色、菜单、字典、日志、代码生成等原有基础功能，不在无明确需求时新增业务模块。

技术版本以 `pom.xml` 和实际依赖为准；配置、数据库初始化、启动及构建见 [README.md](README.md)。本仓库独立开发，不依赖外层工作区文件。

## 关键目录

- `src/main/java/com/ruoyi/common/`：公共工具。
- `src/main/java/com/ruoyi/framework/`：配置、安全与 Web 基础设施。
- `src/main/java/com/ruoyi/project/`：模块代码，沿用 controller/domain/mapper/service 分层。
- `src/main/resources/mybatis/`：Mapper XML；同级 `application*.yml` 为应用配置。
- `config/application-local.example.yml`：配置示例；实际本地配置不提交。
- `sql/`：数据库初始化及迁移脚本。

## 架构与开发规范

- 修改前阅读相关实现，复用现有工具、Service、分页、日志和异常处理，控制影响范围。
- 保持 Controller → Service → Mapper/XML 分层；Java 类型使用 UpperCamelCase，方法和变量使用 lowerCamelCase，数据库表和字段使用 snake_case。
- 沿用 JWT、Redis 会话、Spring Security、方法权限及数据权限；不得通过关闭认证、验证码或扩大匿名访问范围解决问题。
- 普通响应使用 AjaxResult，分页使用 TableDataInfo；保持既有请求与响应契约。
- 新增或修改接口时同步检查 [配套前端](https://github.com/moweishan/weishanHubWeb) 的 API、菜单及按钮权限，权限标识沿用“模块:资源:动作”。
- 数据库变更提供独立 SQL 迁移，保留框架表；初始化脚本不能代替增量迁移。
- 不擅自替换或升级核心技术，不进行无关的大规模重构。

## 协作与安全

- 沟通、文档及新增注释使用中文；重要配置、结构和开发约定变化同步相关文档。
- 不覆盖用户文件，不输出或提交真实凭据、本地配置、密钥、运行数据、日志及构建产物。
- 保留配置示例、SQL、[MIT License](LICENSE)、顶部若依地址及原作者信息。
- 完成后执行与改动相称的验证，如实报告结果、未验证范围及阻塞原因；构建成功不能替代功能验收。
