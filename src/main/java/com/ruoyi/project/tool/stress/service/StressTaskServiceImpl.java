package com.ruoyi.project.tool.stress.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;
import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;
import com.ruoyi.project.tool.stress.domain.StressTask;
import com.ruoyi.project.tool.stress.domain.StressTaskCreateRequest;
import com.ruoyi.project.tool.stress.domain.StressTaskStatus;
import com.ruoyi.project.tool.stress.mapper.StressRequestConfigMapper;
import com.ruoyi.project.tool.stress.mapper.StressTaskMapper;
import com.ruoyi.project.tool.stress.mapper.StressTaskResponseMapper;
import com.ruoyi.project.tool.stress.runner.StressClock;
import com.ruoyi.project.tool.stress.runner.StressTaskExecutionService;

/**
 * 手机压力测试任务服务实现。
 *
 * <p>手机号唯一性依赖 tool_stress_task.running_phone 的唯一索引，并发提交时只有一条插入成功；
 * 任务所有者校验在服务层完成，前端隐藏按钮不能替代鉴权。</p>
 *
 * @author weishanHub
 */
@Service
public class StressTaskServiceImpl implements IStressTaskService
{
    /** 中国大陆手机号：1 开头，第二位 3-9，共 11 位 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9][0-9]{9}$");

    private final StressTaskMapper taskMapper;

    private final StressRequestConfigMapper configMapper;

    private final StressTaskResponseMapper responseMapper;

    private final StressSnapshotCodec snapshotCodec;

    private final StressTaskExecutionService executionService;

    private final StressClock clock;

    /** 未指定执行线程数时使用的默认值 */
    private final int defaultThreadsPerTask;

    /** 单个任务允许的最大执行线程数 */
    private final int maxThreadsPerTask;

    public StressTaskServiceImpl(StressTaskMapper taskMapper, StressRequestConfigMapper configMapper,
            StressTaskResponseMapper responseMapper, StressSnapshotCodec snapshotCodec,
            StressTaskExecutionService executionService, StressClock clock)
    {
        this(taskMapper, configMapper, responseMapper, snapshotCodec, executionService, clock, 2, 8);
    }

    @Autowired
    public StressTaskServiceImpl(StressTaskMapper taskMapper, StressRequestConfigMapper configMapper,
            StressTaskResponseMapper responseMapper, StressSnapshotCodec snapshotCodec,
            StressTaskExecutionService executionService, StressClock clock,
            @Value("${stress.threads-per-task:2}") int defaultThreadsPerTask,
            @Value("${stress.max-threads-per-task:8}") int maxThreadsPerTask)
    {
        this.taskMapper = taskMapper;
        this.configMapper = configMapper;
        this.responseMapper = responseMapper;
        this.snapshotCodec = snapshotCodec;
        this.executionService = executionService;
        this.clock = clock;
        this.defaultThreadsPerTask = Math.max(1, defaultThreadsPerTask);
        this.maxThreadsPerTask = Math.max(this.defaultThreadsPerTask, maxThreadsPerTask);
    }

    @Override
    public synchronized StressTask createTask(StressTaskCreateRequest request, Long ownerId, String username)
    {
        // 与 stopTask 共用实例锁：从插入记录到执行器启动的过程对停止操作是原子的。
        if (request == null)
        {
            throw new ServiceException("任务参数不能为空");
        }
        String phone = StringUtils.trim(request.getPhone());
        if (StringUtils.isEmpty(phone) || !PHONE_PATTERN.matcher(phone).matches())
        {
            throw new ServiceException("手机号格式不正确，请输入11位中国大陆手机号");
        }

        long durationSeconds = resolveDurationSeconds(request);
        int threads = resolveThreads(request);
        List<StressRequestConfig> configs = configMapper.selectEnabledConfigs();
        if (configs == null || configs.isEmpty())
        {
            throw new ServiceException("暂无启用的请求配置，请先在请求配置中启用接口");
        }

        List<StressRequestConfigSnapshot> snapshots = new ArrayList<StressRequestConfigSnapshot>();
        for (StressRequestConfig config : configs)
        {
            snapshots.add(StressRequestConfigSnapshot.from(config));
        }

        long now = clock.currentTimeMillis();
        StressTask task = new StressTask();
        task.setPhone(phone);
        task.setDurationSeconds((int) durationSeconds);
        task.setThreadsPerTask(threads);
        task.setStatus(StressTaskStatus.RUNNING);
        task.setRunningPhone(phone);
        task.setStartTime(new Date(now));
        task.setPlanEndTime(new Date(now + durationSeconds * 1000L));
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setSnapshotJson(snapshotCodec.toJson(snapshots));
        task.setOwnerId(ownerId);
        task.setCreateBy(username);
        task.setRemark("手机压力测试任务");

        try
        {
            int rows = taskMapper.insertTask(task);
            if (rows == 0)
            {
                throw new ServiceException("任务创建失败");
            }
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("该手机号已有运行中的压力测试任务，请等待结束或手动停止");
        }

        executionService.start(task);
        return task;
    }

    @Override
    public List<StressTask> selectTaskList(String phone, String status, Long ownerId, boolean admin)
    {
        return taskMapper.selectTaskList(phone, status, admin ? null : ownerId);
    }

    @Override
    public StressTask checkTaskAccess(Long taskId, Long userId, boolean admin)
    {
        StressTask task = taskId == null ? null : taskMapper.selectTaskById(taskId);
        if (task == null)
        {
            throw new ServiceException("任务不存在");
        }
        if (!admin && !Objects.equals(task.getOwnerId(), userId))
        {
            throw new ServiceException("无权访问该任务", HttpStatus.FORBIDDEN);
        }
        return task;
    }

    @Override
    public synchronized void stopTask(Long taskId, Long userId, boolean admin)
    {
        // 与 createTask 共用同一实例锁：插入记录后、执行器启动前，停止请求必须等待创建流程结束，
        // 避免先落库 STOPPED 后又被 createTask 启动执行器。
        StressTask task = checkTaskAccess(taskId, userId, admin);
        if (!StressTaskStatus.RUNNING.equals(task.getStatus()))
        {
            throw new ServiceException("仅运行中的任务可以停止");
        }

        boolean handled = executionService.stopTask(taskId);
        if (!handled)
        {
            // 当前实例没有运行时（例如状态残留），尝试直接落库为已停止
            StressTask latest = taskMapper.selectTaskById(taskId);
            if (latest != null && StressTaskStatus.RUNNING.equals(latest.getStatus()))
            {
                taskMapper.finishTask(taskId, StressTaskStatus.STOPPED, new Date(clock.currentTimeMillis()));
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long taskId, Long userId, boolean admin)
    {
        StressTask task = checkTaskAccess(taskId, userId, admin);
        if (StressTaskStatus.RUNNING.equals(task.getStatus()))
        {
            throw new ServiceException("运行中的任务不能删除，请先停止");
        }
        responseMapper.deleteResponseByTaskId(taskId);
        int rows = taskMapper.deleteTaskById(taskId);
        if (rows == 0)
        {
            throw new ServiceException("任务状态已变化，请刷新后重试");
        }
    }

    @Override
    public int recoverRunningTasks()
    {
        return taskMapper.markRunningTasksInterrupted(new Date(clock.currentTimeMillis()));
    }

    /**
     * 解析任务内并发执行线程数：未指定时取默认值，超出上限直接拒绝。
     */
    private int resolveThreads(StressTaskCreateRequest request)
    {
        Integer requested = request.getThreads();
        int threads = requested == null ? defaultThreadsPerTask : requested.intValue();
        if (threads < 1)
        {
            throw new ServiceException("执行线程数必须大于 0");
        }
        if (threads > maxThreadsPerTask)
        {
            throw new ServiceException("执行线程数不能超过 " + maxThreadsPerTask);
        }
        return threads;
    }

    private long resolveDurationSeconds(StressTaskCreateRequest request)
    {        if (request.getDuration() == null || request.getDuration() <= 0)
        {
            throw new ServiceException("持续时长必须大于0");
        }
        String unit = StringUtils.trim(request.getUnit());
        long multiplier;
        if ("SECOND".equalsIgnoreCase(unit))
        {
            multiplier = 1L;
        }
        else if ("MINUTE".equalsIgnoreCase(unit))
        {
            multiplier = 60L;
        }
        else if ("HOUR".equalsIgnoreCase(unit))
        {
            multiplier = 60L * 60L;
        }
        else
        {
            throw new ServiceException("时长单位不合法，仅支持 SECOND、MINUTE、HOUR");
        }

        long seconds = request.getDuration().longValue() * multiplier;
        if (seconds <= 0 || seconds > Integer.MAX_VALUE)
        {
            throw new ServiceException("持续时长超出可存储范围");
        }
        return seconds;
    }
}
