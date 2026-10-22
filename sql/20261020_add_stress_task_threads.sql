-- 手机压力测试任务新增“任务内执行线程数”字段。
-- 在已初始化的业务数据库执行；可重复执行，不重建表、不修改历史迁移。
SET NAMES utf8mb4;
START TRANSACTION;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE tool_stress_task ADD COLUMN threads_per_task int(11) NOT NULL DEFAULT 2 COMMENT ''任务内并发执行线程数'' AFTER duration_seconds',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tool_stress_task'
      AND column_name = 'threads_per_task');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

COMMIT;
