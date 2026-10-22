# 工具箱菜单迁移说明（后端）

## 范围

本阶段后端只新增菜单迁移与说明，不修改 Java 代码、认证、应用配置、现有 SQL 或其他表结构。

- 迁移文件：`sql/20261021_add_toolbox.sql`
- 菜单总量：根目录 1 + 分类 5 + 叶子 63 = 69
- 依赖前端：`web/src/data/toolbox/catalog.js`
- 前端工具页面：`web/src/views/toolbox/**/index.vue`

## 菜单结构

| 层级 | path | component | 类型 |
| --- | --- | --- | --- |
| 根 | `toolbox` | `NULL` | M |
| 分类 | `files` / `convert` / `web` / `development` / `security` | `NULL` | M |
| 叶子 | 各工具 slug | `toolbox/{category}/{slug}/index` | C |

- 根目录与 `magicTools` 同级，`parent_id = 0`。
- 分类在根目录下，后端按 `ParentView` 处理。
- 叶子 `route_name`、`perms` 与前端注册表精确一致。
- 叶子 `is_frame = 1`（非外链）、`is_cache = 1`。`SysMenuServiceImpl` 使用
  `StringUtils.equals("1", menu.isCache)` 生成 `meta.noCache=true`，详见
  `server/src/main/java/com/ruoyi/project/system/service/impl/SysMenuServiceImpl.java`。
- 图标全部使用 `web/src/assets/icons/svg` 中已有的小写 SVG 名称，不使用 Element Plus 组件名。

## 可重复执行

脚本基于 `parent_id + path` 判断是否已存在，不硬编码 `menu_id`：

- 根、分类、叶子均使用 `INSERT ... SELECT ... WHERE NOT EXISTS`。
- `sys_role_menu` 使用 `NOT EXISTS` 防重复授权。
- 脚本显式 `START TRANSACTION` / `COMMIT`。
- 不删除、不更新、不覆盖已有菜单或授权。

## 授权

只给满足以下条件的角色授予新增的 69 个菜单：

```sql
role_key = 'admin' AND status = '0' AND del_flag = '0'
```

其他角色需后续在“系统管理 / 角色管理”中按需授权。

## 安装与验证（由 Codex 执行）

本地开发数据库已执行该迁移两次，核对新增 69 条菜单、63 个工具页面，第二次无新增，原有菜单未改变。其他环境部署时：

1. 执行 `server/sql/20261021_add_toolbox.sql`。
2. 再执行一次，确认菜单不重复、授权不重复。
3. 用只读查询核对：
   - `parent_id=0 AND path='toolbox'` 恰好 1 条；
   - 5 个分类各 1 条；
   - 63 个叶子 component 与前端 catalog 匹配；
   - 根+分类+叶子共 69 条；
   - `sys_role_menu` 中 admin 对新增菜单有 69 条，无重复。

示例核对语句（只读）：

```sql
SELECT COUNT(*) AS toolbox_menu_count
FROM sys_menu
WHERE menu_id = (SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'toolbox' LIMIT 1)
   OR parent_id = (SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'toolbox' LIMIT 1)
   OR parent_id IN (
       SELECT menu_id FROM (
           SELECT menu_id FROM sys_menu
           WHERE parent_id = (SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'toolbox' LIMIT 1)
             AND menu_type = 'M'
       ) c
   );
```

预期 `69`。

## 前端菜单字段

前端动态菜单仍由现有 `getRouters` / `loadView` 加载，无需新增静态路由。菜单导入后：

- 一级菜单路径为 `/toolbox`；
- 分类菜单由 `ParentView` 汇总；
- 叶子组件路径与 `catalog.js` 的 `component` 一致；
- 含密码/密钥/文件处理的页面通过 `is_cache=1` 不缓存，页面自身也有停用清理。

## 回退

迁移不修改已有数据，因此回退只需在确认没有依赖后，由具备权限的管理员按备份移除本次新增的 69 条菜单与相应 `sys_role_menu` 记录。不要直接删除整个 `sys_menu` 表或使用 `TRUNCATE`。
