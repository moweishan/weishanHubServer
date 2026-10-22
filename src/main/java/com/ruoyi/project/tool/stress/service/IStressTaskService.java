package com.ruoyi.project.tool.stress.service;

import java.util.List;
import com.ruoyi.project.tool.stress.domain.StressTask;
import com.ruoyi.project.tool.stress.domain.StressTaskCreateRequest;

/**
 * 手机压力测试任务服务。
 *
 * @author weishanHub
 */
public interface IStressTaskService
{
    /** 创建任务并立即启动 */
    StressTask createTask(StressTaskCreateRequest request, Long ownerId, String username);

    /** 查询任务列表，非管理员只看自己的任务 */
    List<StressTask> selectTaskList(String phone, String status, Long ownerId, boolean admin);

    /** 校验任务存在且当前用户有权访问 */
    StressTask checkTaskAccess(Long taskId, Long userId, boolean admin);

    /** 手动停止任务 */
    void stopTask(Long taskId, Long userId, boolean admin);

    /** 删除非运行中任务及其结果 */
    void deleteTask(Long taskId, Long userId, boolean admin);

    /** 启动恢复：将上次残留的 RUNNING 任务标记为 INTERRUPTED */
    int recoverRunningTasks();
}
