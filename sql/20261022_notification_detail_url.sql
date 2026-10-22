-- ----------------------------
-- 通知中心：统一详情页地址（可重复执行）
-- 1. notification_record 增加 nullable 地址快照列，历史记录保持 NULL；
-- 2. sys_config 增加固定 key 的空值种子，未配置时回退旧私密配置。
-- 不删除/重建原表，不修改已有菜单与真实渠道数据，不写入任何域名或密钥。
-- ----------------------------

SET @notification_db = DATABASE();
SET @notification_column_exists = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @notification_db
      AND TABLE_NAME = 'notification_record'
      AND COLUMN_NAME = 'detail_base_url'
);
SET @notification_alter_sql = IF(
    @notification_column_exists = 0,
    'ALTER TABLE notification_record ADD COLUMN detail_base_url varchar(500) DEFAULT NULL COMMENT ''接单时的详情页基地址快照'' AFTER route_codes',
    'SELECT 1'
);
PREPARE notification_alter_stmt FROM @notification_alter_sql;
EXECUTE notification_alter_stmt;
DEALLOCATE PREPARE notification_alter_stmt;

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '通知详情页地址',
       'notification.publicBaseUrl',
       '',
       'N',
       'admin',
       NOW(),
       '通知详情链接使用的前端站点地址；留空时回退 notification.public-base-url，仅影响保存后新接单的通知'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'notification.publicBaseUrl'
);
