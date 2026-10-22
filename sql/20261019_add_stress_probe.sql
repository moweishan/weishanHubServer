-- 压力测试请求配置新增“试活”能力：试活状态字段 + 批量试活按钮权限。
-- 在已初始化的业务数据库执行；可重复执行，不重建表、不修改历史迁移。
SET NAMES utf8mb4;
START TRANSACTION;

-- ----------------------------
-- 1. 试活状态字段（幂等：列不存在时才新增）
-- ----------------------------
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE tool_stress_request_config ADD COLUMN probe_status varchar(20) NOT NULL DEFAULT ''untested'' COMMENT ''试活状态：untested未测 alive有效 dead失效''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tool_stress_request_config'
      AND column_name = 'probe_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE tool_stress_request_config ADD COLUMN probe_time datetime DEFAULT NULL COMMENT ''最后一次试活时间''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tool_stress_request_config'
      AND column_name = 'probe_time');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE tool_stress_request_config ADD COLUMN probe_message varchar(500) DEFAULT NULL COMMENT ''试活结果说明：HTTP状态码或错误信息''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tool_stress_request_config'
      AND column_name = 'probe_message');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'CREATE INDEX idx_stress_config_probe ON tool_stress_request_config (probe_status)',
    'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tool_stress_request_config'
      AND index_name = 'idx_stress_config_probe');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 2. 按钮权限：批量试活
-- ----------------------------
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
SELECT '批量试活', @request_config_menu_id, 5, '', NULL, '',
       1, 0, 'F', '0', '0', 'magic:requestConfig:probe', '#',
       'admin', SYSDATE(), '批量探测接口是否失效'
FROM DUAL
WHERE @request_config_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @request_config_menu_id AND perms = 'magic:requestConfig:probe'
);

-- 默认授予启用的管理员角色；其他角色通过“系统管理 / 角色管理”按需授权。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.parent_id = @request_config_menu_id AND m.perms = 'magic:requestConfig:probe'
WHERE r.role_key = 'admin' AND r.status = '0' AND r.del_flag = '0'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

COMMIT;
