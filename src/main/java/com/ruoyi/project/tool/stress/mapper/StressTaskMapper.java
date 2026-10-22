package com.ruoyi.project.tool.stress.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.tool.stress.domain.StressTask;

/**
 * 压力测试任务数据层。
 *
 * @author weishanHub
 */
public interface StressTaskMapper
{
    /** 按条件查询任务列表；ownerId 为空表示不限制所有者 */
    List<StressTask> selectTaskList(@Param("phone") String phone, @Param("status") String status,
            @Param("ownerId") Long ownerId);

    /** 查询任务详情（含快照） */
    StressTask selectTaskById(Long taskId);

    /** 新增任务 */
    int insertTask(StressTask task);

    /** 结束运行中任务，同时释放手机号唯一锁；仅 RUNNING 状态可更新 */
    int finishTask(@Param("taskId") Long taskId, @Param("status") String status,
            @Param("actualEndTime") Date actualEndTime);

    /** 累加成功/失败次数 */
    int incrementCounters(@Param("taskId") Long taskId, @Param("successCount") int successCount,
            @Param("failCount") int failCount);

    /** 启动时将上次残留的 RUNNING 任务标记为 INTERRUPTED，并释放手机号锁 */
    int markRunningTasksInterrupted(@Param("now") Date now);

    /** 删除非运行中任务 */
    int deleteTaskById(@Param("taskId") Long taskId);
}
