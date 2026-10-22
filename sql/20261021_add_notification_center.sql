-- 通知中心业务表、菜单、按钮权限与管理员角色绑定。
-- 适用于已初始化的业务库；全部语句幂等，只新增对象，不删除/修改原表或已有菜单。
-- 首次执行不写入任何真实企业微信 CorpID / AgentID / Secret，也不写入调用凭证。
SET NAMES utf8mb4;
START TRANSACTION;

-- ----------------------------
-- 通知渠道
-- ----------------------------
CREATE TABLE IF NOT EXISTS notification_channel (
    channel_id      bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '渠道主键',
    route_code      varchar(64)     NOT NULL COMMENT '唯一路径编码',
    channel_name    varchar(100)    NOT NULL COMMENT '渠道名称',
    provider        varchar(32)     NOT NULL DEFAULT 'WECOM_APP' COMMENT '发送提供方',
    corp_id         varchar(128)    NOT NULL COMMENT '企业微信 CorpID',
    agent_id        int(11)         NOT NULL COMMENT '企业微信应用 AgentID',
    to_user         varchar(1000)   NOT NULL COMMENT '接收成员 UserID，多个用 | 分隔',
    secret_cipher   varchar(4096)   NOT NULL COMMENT 'AES-GCM 加密后的 Secret',
    enabled         char(1)         NOT NULL DEFAULT '0' COMMENT '启用状态：0 启用，1 停用',
    remark          varchar(500)             DEFAULT NULL COMMENT '备注',
    create_by       varchar(64)              DEFAULT '' COMMENT '创建者',
    create_time     datetime                 DEFAULT NULL COMMENT '创建时间',
    update_by       varchar(64)              DEFAULT '' COMMENT '更新者',
    update_time     datetime                 DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (channel_id),
    UNIQUE KEY uk_notification_channel_route (route_code),
    KEY idx_notification_channel_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知渠道';

-- ----------------------------
-- 调用凭证（只存 SHA-256 哈希）
-- ----------------------------
CREATE TABLE IF NOT EXISTS notification_credential (
    credential_id   bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '凭证主键',
    credential_name varchar(100)    NOT NULL COMMENT '凭证名称',
    key_hash        char(64)        NOT NULL COMMENT 'API Key SHA-256 哈希',
    key_prefix      varchar(16)              DEFAULT NULL COMMENT '仅用于辨识的短前缀',
    enabled         char(1)         NOT NULL DEFAULT '0' COMMENT '启用状态：0 启用，1 停用',
    last_used_time  datetime                 DEFAULT NULL COMMENT '最近调用时间',
    remark          varchar(500)             DEFAULT NULL COMMENT '备注',
    create_by       varchar(64)              DEFAULT '' COMMENT '创建者',
    create_time     datetime                 DEFAULT NULL COMMENT '创建时间',
    update_by       varchar(64)              DEFAULT '' COMMENT '更新者',
    update_time     datetime                 DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (credential_id),
    UNIQUE KEY uk_notification_credential_hash (key_hash),
    KEY idx_notification_credential_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知调用凭证';

-- ----------------------------
-- 通知记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS notification_record (
    record_id       bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '通知记录主键',
    request_id      varchar(128)    NOT NULL COMMENT '调用方幂等号',
    credential_id   bigint(20)      NOT NULL COMMENT '调用凭证 ID',
    content_hash    char(64)        NOT NULL COMMENT '请求内容指纹',
    message_type    varchar(16)     NOT NULL COMMENT 'TEXT / CARD',
    title           varchar(200)    NOT NULL COMMENT '标题',
    summary         varchar(1000)            DEFAULT NULL COMMENT '摘要',
    detail          mediumtext               DEFAULT NULL COMMENT '原始 Markdown / 文本',
    token_hash      char(64)        NOT NULL COMMENT '详情票据 SHA-256 哈希',
    token_cipher    varchar(1024)   NOT NULL COMMENT '详情票据 AES-GCM 密文',
    route_codes     varchar(4096)   NOT NULL COMMENT '接单时路径快照，逗号分隔',
    status          varchar(32)     NOT NULL DEFAULT 'PENDING' COMMENT '汇总状态',
    total_count     int(11)         NOT NULL DEFAULT 0 COMMENT '渠道总数',
    success_count   int(11)         NOT NULL DEFAULT 0 COMMENT '成功渠道数',
    fail_count      int(11)         NOT NULL DEFAULT 0 COMMENT '失败渠道数',
    sent_at         datetime                 DEFAULT NULL COMMENT '首个渠道成功时间',
    expires_at      datetime                 DEFAULT NULL COMMENT '免登录截止时间 = sent_at + 1 小时',
    revoked         char(1)         NOT NULL DEFAULT '0' COMMENT '撤销：0 否，1 是',
    revoked_time    datetime                 DEFAULT NULL COMMENT '撤销时间',
    revoked_by      varchar(64)              DEFAULT '' COMMENT '撤销人',
    deleted         char(1)         NOT NULL DEFAULT '0' COMMENT '软删除：0 否，1 是',
    deleted_time    datetime                 DEFAULT NULL COMMENT '删除时间',
    create_by       varchar(64)              DEFAULT '' COMMENT '创建者（open-api）',
    create_time     datetime                 DEFAULT NULL COMMENT '创建时间',
    update_by       varchar(64)              DEFAULT '' COMMENT '更新者',
    update_time     datetime                 DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (record_id),
    UNIQUE KEY uk_notification_record_token (token_hash),
    UNIQUE KEY uk_notification_record_request (credential_id, request_id),
    KEY idx_notification_record_status (status, deleted),
    KEY idx_notification_record_create (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知记录';

-- ----------------------------
-- 通知渠道快照 / 待发送队列
-- ----------------------------
CREATE TABLE IF NOT EXISTS notification_record_channel (
    channel_id       bigint(20)     NOT NULL AUTO_INCREMENT COMMENT '记录渠道主键',
    record_id        bigint(20)     NOT NULL COMMENT '通知记录 ID',
    route_code       varchar(64)    NOT NULL COMMENT '路径编码快照',
    channel_name     varchar(100)   NOT NULL COMMENT '渠道名称快照',
    provider         varchar(32)    NOT NULL COMMENT '发送提供方快照',
    corp_id          varchar(128)   NOT NULL COMMENT 'CorpID 快照',
    agent_id         int(11)        NOT NULL COMMENT 'AgentID 快照',
    to_user          varchar(1000)  NOT NULL COMMENT '接收 UserID 快照',
    secret_cipher    varchar(4096)  NOT NULL COMMENT 'Secret 密文快照',
    status           varchar(16)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/SUCCESS/FAILED',
    claim_token      varchar(64)             DEFAULT NULL COMMENT '领取令牌',
    attempt_count    int(11)        NOT NULL DEFAULT 0 COMMENT '尝试次数',
    error_message    varchar(1000)           DEFAULT NULL COMMENT '失败原因（不含凭据）',
    wecom_msg_id     varchar(128)            DEFAULT NULL COMMENT '企业微信消息 ID',
    last_attempt_time datetime                DEFAULT NULL COMMENT '最近尝试时间',
    create_time      datetime                DEFAULT NULL COMMENT '创建时间',
    update_time      datetime                DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (channel_id),
    KEY idx_notification_record_channel_record (record_id),
    KEY idx_notification_record_channel_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知记录渠道快照';

-- ----------------------------
-- 一级菜单：通知中心
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '通知中心', 0, 7, 'notification', NULL, 'NotificationCenter',
       1, 0, 'M', '0', '0', '', 'message',
       'admin', SYSDATE(), '通知中心目录'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'notification' AND menu_type = 'M'
);

SET @notification_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = 0 AND path = 'notification' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);

-- ----------------------------
-- 二级菜单：通知渠道 / 调用凭证 / 通知记录
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '通知渠道', @notification_menu_id, 1, 'channel', 'notification/channel/index', 'NotificationChannel',
       1, 1, 'C', '0', '0', 'notification:channel:list', 'link',
       'admin', SYSDATE(), '通知渠道配置'
FROM DUAL
WHERE @notification_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_menu_id AND path = 'channel' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '调用凭证', @notification_menu_id, 2, 'credential', 'notification/credential/index', 'NotificationCredential',
       1, 1, 'C', '0', '0', 'notification:credential:list', 'password',
       'admin', SYSDATE(), '通知调用凭证'
FROM DUAL
WHERE @notification_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_menu_id AND path = 'credential' AND menu_type = 'C'
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '通知记录', @notification_menu_id, 3, 'record', 'notification/record/index', 'NotificationRecord',
       1, 1, 'C', '0', '0', 'notification:record:list', 'log',
       'admin', SYSDATE(), '通知发送记录'
FROM DUAL
WHERE @notification_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_menu_id AND path = 'record' AND menu_type = 'C'
);

SET @notification_channel_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @notification_menu_id AND path = 'channel' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);
SET @notification_credential_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @notification_menu_id AND path = 'credential' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);
SET @notification_record_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @notification_menu_id AND path = 'record' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);

-- ----------------------------
-- 按钮权限：通知渠道
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '渠道新增', @notification_channel_menu_id, 1, '', NULL, '',
       1, 0, 'F', '0', '0', 'notification:channel:add', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @notification_channel_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_channel_menu_id AND perms = 'notification:channel:add'
);
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '渠道修改', @notification_channel_menu_id, 2, '', NULL, '',
       1, 0, 'F', '0', '0', 'notification:channel:edit', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @notification_channel_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_channel_menu_id AND perms = 'notification:channel:edit'
);
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '渠道删除', @notification_channel_menu_id, 3, '', NULL, '',
       1, 0, 'F', '0', '0', 'notification:channel:remove', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @notification_channel_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_channel_menu_id AND perms = 'notification:channel:remove'
);

-- ----------------------------
-- 按钮权限：调用凭证
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '凭证新增', @notification_credential_menu_id, 1, '', NULL, '',
       1, 0, 'F', '0', '0', 'notification:credential:add', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @notification_credential_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_credential_menu_id AND perms = 'notification:credential:add'
);
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '凭证修改', @notification_credential_menu_id, 2, '', NULL, '',
       1, 0, 'F', '0', '0', 'notification:credential:edit', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @notification_credential_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_credential_menu_id AND perms = 'notification:credential:edit'
);
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '凭证重置', @notification_credential_menu_id, 3, '', NULL, '',
       1, 0, 'F', '0', '0', 'notification:credential:reset', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @notification_credential_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_credential_menu_id AND perms = 'notification:credential:reset'
);
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '凭证删除', @notification_credential_menu_id, 4, '', NULL, '',
       1, 0, 'F', '0', '0', 'notification:credential:remove', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @notification_credential_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_credential_menu_id AND perms = 'notification:credential:remove'
);

-- ----------------------------
-- 按钮权限：通知记录
-- ----------------------------
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '记录详情', @notification_record_menu_id, 1, '', NULL, '',
       1, 0, 'F', '0', '0', 'notification:record:query', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @notification_record_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_record_menu_id AND perms = 'notification:record:query'
);
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '失败重试', @notification_record_menu_id, 2, '', NULL, '',
       1, 0, 'F', '0', '0', 'notification:record:retry', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @notification_record_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_record_menu_id AND perms = 'notification:record:retry'
);
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '撤销链接', @notification_record_menu_id, 3, '', NULL, '',
       1, 0, 'F', '0', '0', 'notification:record:revoke', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @notification_record_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_record_menu_id AND perms = 'notification:record:revoke'
);
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '记录删除', @notification_record_menu_id, 4, '', NULL, '',
       1, 0, 'F', '0', '0', 'notification:record:remove', '#',
       'admin', SYSDATE(), ''
FROM DUAL
WHERE @notification_record_menu_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @notification_record_menu_id AND perms = 'notification:record:remove'
);

-- 只给启用且未删除的管理员角色授予本次新增菜单；不修改其他角色授权。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id = @notification_menu_id
    OR m.parent_id = @notification_menu_id
    OR m.parent_id IN (
        SELECT c.menu_id FROM (
            SELECT menu_id FROM sys_menu WHERE parent_id = @notification_menu_id AND menu_type = 'C'
        ) c
    )
WHERE r.role_key = 'admin' AND r.status = '0' AND r.del_flag = '0'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

COMMIT;
