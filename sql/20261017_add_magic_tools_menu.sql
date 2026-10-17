-- 新增“神奇小工具 / 手机压力测试”菜单。
-- 在已初始化的业务数据库执行；可重复执行，不重建表、不修改已有菜单。
-- 默认仅授予启用的管理员角色；其他角色通过“系统管理 / 角色管理”按需授权。
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '神奇小工具', 0, 5, 'magicTools', NULL, 'MagicTools',
       1, 0, 'M', '0', '0', '', 'tool',
       'admin', SYSDATE(), '神奇小工具目录'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'magicTools'
);

SET @magic_tools_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = 0 AND path = 'magicTools' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '手机压力测试', @magic_tools_menu_id, 1, 'mobileStressTest',
       'magic-tools/mobile-stress-test/index', 'MobileStressTest',
       1, 0, 'C', '0', '0', 'magic:mobileStressTest:view', 'phone',
       'admin', SYSDATE(), '手机压力测试空白页面'
FROM DUAL
WHERE @magic_tools_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @magic_tools_menu_id AND path = 'mobileStressTest'
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id = @magic_tools_menu_id
    OR (m.parent_id = @magic_tools_menu_id AND m.path = 'mobileStressTest')
WHERE r.role_key = 'admin' AND r.status = '0' AND r.del_flag = '0'
    AND NOT EXISTS (
        SELECT 1 FROM sys_role_menu rm
        WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
    );

COMMIT;
