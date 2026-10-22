package com.ruoyi.project.tool.stress.service;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 应用启动恢复。
 *
 * <p>压力测试任务不跨重启恢复；启动时把上一次进程遗留的 RUNNING 任务标记为 INTERRUPTED，
 * 并清空 running_phone，释放手机号唯一锁。</p>
 *
 * @author weishanHub
 */
@Component
public class StressTaskRecovery
{
    private static final Logger log = LoggerFactory.getLogger(StressTaskRecovery.class);

    private final IStressTaskService taskService;

    public StressTaskRecovery(IStressTaskService taskService)
    {
        this.taskService = taskService;
    }

    @PostConstruct
    public void recover()
    {
        try
        {
            int count = taskService.recoverRunningTasks();
            if (count > 0)
            {
                log.warn("启动恢复：已将 {} 个上次运行中的压力测试任务标记为 INTERRUPTED", count);
            }
        }
        catch (Exception e)
        {
            // 不阻断应用启动，但必须留下明确日志
            log.error("启动恢复压力测试任务失败，请检查数据库状态", e);
        }
    }
}
