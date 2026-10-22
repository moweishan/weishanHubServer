-- 请求配置新增“导入 / 导出”按钮权限。
-- 在已初始化的业务数据库执行；可重复执行，不重建表、不修改历史迁移。
SET NAMES utf8mb4;
START TRANSACTION;

SET @magic_tools_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = 0 AND path = 'magicTools' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);

SET @stress_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @magic_tools_menu_id AND path = 'stressTest' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);

SET @request_config_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @stress_menu_id AND path = 'requestConfig' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '配置导入', @request_config_menu_id, 6, '', NULL, '',
       1, 0, 'F', '0', '0', 'magic:requestConfig:import', '#',
       'admin', SYSDATE(), '从 JSON 文件导入请求配置'
FROM DUAL
WHERE @request_config_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @request_config_menu_id AND perms = 'magic:requestConfig:import'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '配置导出', @request_config_menu_id, 7, '', NULL, '',
       1, 0, 'F', '0', '0', 'magic:requestConfig:export', '#',
       'admin', SYSDATE(), '按查询条件导出请求配置为 JSON'
FROM DUAL
WHERE @request_config_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @request_config_menu_id AND perms = 'magic:requestConfig:export'
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.parent_id = @request_config_menu_id
    AND m.perms IN ('magic:requestConfig:import', 'magic:requestConfig:export')
WHERE r.role_key = 'admin' AND r.status = '0' AND r.del_flag = '0'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

COMMIT;
