-- 个人工作台：用户快捷入口表与今日通知统计所需索引。
-- 适用于已初始化的业务库；幂等，只新增对象，不删除/修改原表或已有数据。
-- 不重复执行框架初始化脚本；本文件可单独在目标库执行。
SET NAMES utf8mb4;

-- ----------------------------
-- 用户快捷入口：只保存 menuId 与顺序，标题/图标/路径在读取时按当前菜单权限实时计算
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user_shortcut (
    shortcut_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '快捷入口主键',
    user_id     bigint(20) NOT NULL COMMENT '用户 ID',
    menu_id     bigint(20) NOT NULL COMMENT '菜单 ID',
    order_num   int(11)    NOT NULL DEFAULT 0 COMMENT '显示顺序',
    create_time datetime             DEFAULT NULL COMMENT '创建时间',
    update_time datetime             DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (shortcut_id),
    UNIQUE KEY uk_sys_user_shortcut_user_menu (user_id, menu_id),
    KEY idx_sys_user_shortcut_user_order (user_id, order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户个人工作台快捷入口';

-- ----------------------------
-- 今日通知统计按 notification_record.sent_at 过滤，按需补充索引。
-- MySQL 5.7/8 不支持 ADD INDEX IF NOT EXISTS，使用 information_schema 判断保持幂等。
-- ----------------------------
SET @home_notification_table_exists = (
    SELECT COUNT(1)
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'notification_record'
);

SET @home_notification_sent_at_index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'notification_record'
      AND index_name = 'idx_notification_record_sent_at'
);

SET @home_notification_sent_at_index_sql = IF(
    @home_notification_table_exists > 0 AND @home_notification_sent_at_index_exists = 0,
    'ALTER TABLE notification_record ADD INDEX idx_notification_record_sent_at (sent_at)',
    'SELECT 1'
);

PREPARE home_notification_sent_at_stmt FROM @home_notification_sent_at_index_sql;
EXECUTE home_notification_sent_at_stmt;
DEALLOCATE PREPARE home_notification_sent_at_stmt;
