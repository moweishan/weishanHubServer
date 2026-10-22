package com.ruoyi.project.tool.stress.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/**
 * 启动恢复测试：调用任务服务并将遗留 RUNNING 标记为 INTERRUPTED。
 */
class StressTaskRecoveryTest
{
    @Test
    void recoverInvokesService()
    {
        IStressTaskService taskService = mock(IStressTaskService.class);
        when(taskService.recoverRunningTasks()).thenReturn(1);
        StressTaskRecovery recovery = new StressTaskRecovery(taskService);

        recovery.recover();

        verify(taskService).recoverRunningTasks();
    }

    @Test
    void recoveryFailureDoesNotBreakStartup()
    {
        IStressTaskService taskService = mock(IStressTaskService.class);
        when(taskService.recoverRunningTasks()).thenThrow(new RuntimeException("db down"));
        StressTaskRecovery recovery = new StressTaskRecovery(taskService);

        assertDoesNotThrow(recovery::recover);
    }
}
