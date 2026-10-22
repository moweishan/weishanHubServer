-- 新增“压力测试”目录、请求配置页面及手机压力测试按钮权限，并执行可重复的增量表结构变更。
-- 在已初始化的业务数据库执行；可重复执行，不修改历史迁移，不删除已有菜单和角色授权。
-- 新表/新菜单默认授予启用的管理员角色；已拥有手机压力测试页面的角色会补授新的父目录，避免迁移后菜单不可见。
SET NAMES utf8mb4;
START TRANSACTION;

-- ----------------------------
-- 1. 请求配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS tool_stress_request_config (
    config_id          bigint(20)    not null auto_increment    comment '配置ID',
    config_name        varchar(100)  not null                    comment '接口名称',
    request_url        varchar(2000) not null                    comment '请求URL，支持{{phone}}占位符',
    http_method        varchar(10)   not null                    comment 'HTTP方法',
    headers_json       text                                       comment '请求头JSON对象',
    query_params_json  text                                       comment 'Query参数JSON对象',
    body_type          varchar(10)   not null default 'RAW'      comment '请求体类型：JSON/FORM/RAW',
    body_content       longtext                                   comment '请求体内容',
    content_type       varchar(200)  default null                 comment 'Content-Type，空则按bodyType默认',
    timeout_ms         int(11)       not null default 10000       comment '超时毫秒数',
    enabled            char(1)       not null default '0'         comment '启用状态：0启用 1停用',
    create_by          varchar(64)   default ''                   comment '创建者',
    create_time        datetime                                   comment '创建时间',
    update_by          varchar(64)   default ''                   comment '更新者',
    update_time        datetime                                   comment '更新时间',
    remark             varchar(500)  default null                 comment '备注',
    primary key (config_id),
    key idx_stress_config_enabled (enabled)
) engine=innodb default charset=utf8mb4 comment='压力测试请求配置';

-- ----------------------------
-- 2. 任务表
-- running_phone 仅在 RUNNING 时等于手机号，利用唯一索引从数据库层阻止同一手机号并发重复提交；
-- 任务结束后置空，MySQL 唯一索引允许多个 NULL。
-- ----------------------------
CREATE TABLE IF NOT EXISTS tool_stress_task (
    task_id           bigint(20)   not null auto_increment    comment '任务ID',
    phone             varchar(32)  not null                    comment '测试手机号',
    duration_seconds  int(11)      not null                    comment '持续时长秒数',
    status            varchar(20)  not null                    comment '任务状态：RUNNING/COMPLETED/STOPPED/INTERRUPTED/FAILED',
    running_phone     varchar(32)  default null                comment '运行中手机号，用于唯一约束',
    start_time        datetime     not null                    comment '开始时间（提交时间）',
    plan_end_time     datetime     not null                    comment '计划结束时间',
    actual_end_time   datetime     default null                comment '实际结束时间',
    success_count     int(11)      not null default 0          comment '成功次数（HTTP 200-299）',
    fail_count        int(11)      not null default 0          comment '失败次数',
    snapshot_json     longtext     not null                    comment '提交时启用的接口配置快照',
    owner_id          bigint(20)   not null                    comment '任务所有者用户ID',
    create_by         varchar(64)  default ''                  comment '创建者',
    create_time       datetime                                 comment '创建时间',
    update_by         varchar(64)  default ''                  comment '更新者',
    update_time       datetime                                 comment '更新时间',
    remark            varchar(500) default null                comment '备注',
    primary key (task_id),
    unique key uk_stress_task_running_phone (running_phone),
    key idx_stress_task_owner (owner_id, task_id),
    key idx_stress_task_phone (phone),
    key idx_stress_task_status (status)
) engine=innodb default charset=utf8mb4 comment='手机压力测试任务';

-- ----------------------------
-- 3. 请求结果表
-- ----------------------------
CREATE TABLE IF NOT EXISTS tool_stress_response (
    response_id    bigint(20)    not null auto_increment    comment '结果ID',
    task_id        bigint(20)    not null                    comment '任务ID',
    config_id      bigint(20)    default null                comment '快照中的接口配置ID',
    config_name    varchar(100)  default null                comment '接口名称',
    request_url    varchar(2000) default null                comment '请求URL',
    http_method    varchar(10)   default null                comment 'HTTP方法',
    status_code    int(11)       default null                comment 'HTTP状态码，网络异常时为空',
    duration_ms    bigint(20)    not null default 0          comment '耗时毫秒数',
    success        char(1)       not null default '0'        comment '是否成功：1成功 0失败',
    response_text  mediumtext                                comment '响应文本（有上限）',
    error_message  varchar(1000) default null                comment '错误信息',
    truncated      char(1)       not null default '0'        comment '响应是否截断：1是 0否',
    create_time    datetime                                 comment '记录时间',
    primary key (response_id),
    key idx_stress_response_task (task_id, response_id)
) engine=innodb default charset=utf8mb4 comment='手机压力测试请求结果';

-- ----------------------------
-- 4. 菜单：兼容旧库补建“神奇小工具”一级目录
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '神奇小工具', 0, 5, 'magicTools', NULL, 'MagicTools',
       1, 0, 'M', '0', '0', '', 'tool',
       'admin', SYSDATE(), '神奇小工具目录'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'magicTools' AND menu_type = 'M'
);

SET @magic_tools_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = 0 AND path = 'magicTools' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);

-- ----------------------------
-- 5. 新增“压力测试”二级目录
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '压力测试', @magic_tools_menu_id, 1, 'stressTest', NULL, 'StressTest',
       1, 0, 'M', '0', '0', '', 'monitor',
       'admin', SYSDATE(), '手机压力测试目录'
FROM DUAL
WHERE @magic_tools_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @magic_tools_menu_id AND path = 'stressTest' AND menu_type = 'M'
);

SET @stress_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @magic_tools_menu_id AND path = 'stressTest' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);

-- ----------------------------
-- 6. 将已有“手机压力测试”页面挪入压力测试目录，保留 menu_id 从而保留原角色授权
-- ----------------------------
UPDATE sys_menu
SET parent_id = @stress_menu_id,
    order_num = 1,
    update_time = SYSDATE()
WHERE @stress_menu_id IS NOT NULL
  AND menu_type = 'C'
  AND path = 'mobileStressTest'
  AND component = 'magic-tools/mobile-stress-test/index'
  AND (parent_id IS NULL OR parent_id != @stress_menu_id);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '手机压力测试', @stress_menu_id, 1, 'mobileStressTest',
       'magic-tools/mobile-stress-test/index', 'MobileStressTest',
       1, 0, 'C', '0', '0', 'magic:mobileStressTest:view', 'phone',
       'admin', SYSDATE(), '手机压力测试页面'
FROM DUAL
WHERE @stress_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @stress_menu_id AND path = 'mobileStressTest' AND menu_type = 'C'
);

SET @mobile_stress_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @stress_menu_id AND path = 'mobileStressTest' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);

-- ----------------------------
-- 7. 新增“请求配置”页面
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '请求配置', @stress_menu_id, 2, 'requestConfig',
       'magic-tools/request-config/index', 'RequestConfig',
       1, 0, 'C', '0', '0', 'magic:requestConfig:list', 'edit',
       'admin', SYSDATE(), '压力测试请求配置页面'
FROM DUAL
WHERE @stress_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @stress_menu_id AND path = 'requestConfig' AND menu_type = 'C'
);

SET @request_config_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @stress_menu_id AND path = 'requestConfig' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);

-- ----------------------------
-- 8. 按钮权限
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '提交测试', @mobile_stress_menu_id, 1, '', NULL, '',
       1, 0, 'F', '0', '0', 'magic:mobileStressTest:add', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @mobile_stress_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @mobile_stress_menu_id AND perms = 'magic:mobileStressTest:add'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '停止任务', @mobile_stress_menu_id, 2, '', NULL, '',
       1, 0, 'F', '0', '0', 'magic:mobileStressTest:stop', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @mobile_stress_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @mobile_stress_menu_id AND perms = 'magic:mobileStressTest:stop'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '查看结果', @mobile_stress_menu_id, 3, '', NULL, '',
       1, 0, 'F', '0', '0', 'magic:mobileStressTest:result', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @mobile_stress_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @mobile_stress_menu_id AND perms = 'magic:mobileStressTest:result'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '删除任务', @mobile_stress_menu_id, 4, '', NULL, '',
       1, 0, 'F', '0', '0', 'magic:mobileStressTest:remove', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @mobile_stress_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @mobile_stress_menu_id AND perms = 'magic:mobileStressTest:remove'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '配置查询', @request_config_menu_id, 1, '', NULL, '',
       1, 0, 'F', '0', '0', 'magic:requestConfig:query', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @request_config_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @request_config_menu_id AND perms = 'magic:requestConfig:query'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '配置新增', @request_config_menu_id, 2, '', NULL, '',
       1, 0, 'F', '0', '0', 'magic:requestConfig:add', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @request_config_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @request_config_menu_id AND perms = 'magic:requestConfig:add'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '配置修改', @request_config_menu_id, 3, '', NULL, '',
       1, 0, 'F', '0', '0', 'magic:requestConfig:edit', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @request_config_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @request_config_menu_id AND perms = 'magic:requestConfig:edit'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '配置删除', @request_config_menu_id, 4, '', NULL, '',
       1, 0, 'F', '0', '0', 'magic:requestConfig:remove', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @request_config_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @request_config_menu_id AND perms = 'magic:requestConfig:remove'
);

-- ----------------------------
-- 9. 默认管理员授权：新目录、新页面、新按钮
-- ----------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON (
       m.menu_id IN (@magic_tools_menu_id, @stress_menu_id)
       OR m.parent_id IN (@stress_menu_id, @mobile_stress_menu_id, @request_config_menu_id)
)
WHERE r.role_key = 'admin' AND r.status = '0' AND r.del_flag = '0'
  AND m.menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

-- 已有手机压力测试页面授权的角色补授“压力测试”父目录，保证移动后菜单仍可见；
-- 不自动补授新增按钮，按钮权限由管理员按需分配。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @stress_menu_id
FROM sys_role_menu rm
JOIN sys_menu child ON child.menu_id = rm.menu_id
WHERE @stress_menu_id IS NOT NULL
  AND child.parent_id = @stress_menu_id
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu exist_rm
      WHERE exist_rm.role_id = rm.role_id AND exist_rm.menu_id = @stress_menu_id
  );

COMMIT;
