package com.ruoyi.project.tool.stress.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;
import com.ruoyi.project.tool.stress.domain.StressTask;
import com.ruoyi.project.tool.stress.domain.StressTaskCreateRequest;
import com.ruoyi.project.tool.stress.domain.StressTaskStatus;
import com.ruoyi.project.tool.stress.mapper.StressRequestConfigMapper;
import com.ruoyi.project.tool.stress.mapper.StressTaskMapper;
import com.ruoyi.project.tool.stress.mapper.StressTaskResponseMapper;
import com.ruoyi.project.tool.stress.runner.StressClock;
import com.ruoyi.project.tool.stress.runner.StressTaskExecutionService;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 任务服务测试：提交快照、重复手机号并发创建、所有者授权、删除限制和启动恢复。
 */
class StressTaskServiceImplTest
{
    private StressTaskMapper taskMapper;

    private StressRequestConfigMapper configMapper;

    private StressTaskResponseMapper responseMapper;

    private StressTaskExecutionService executionService;

    private StressClock clock;

    private StressSnapshotCodec codec;

    private StressTaskServiceImpl service;

    @BeforeEach
    void setUp()
    {
        taskMapper = mock(StressTaskMapper.class);
        configMapper = mock(StressRequestConfigMapper.class);
        responseMapper = mock(StressTaskResponseMapper.class);
        executionService = mock(StressTaskExecutionService.class);
        clock = mock(StressClock.class);
        codec = new StressSnapshotCodec(new ObjectMapper());
        when(clock.currentTimeMillis()).thenReturn(1_000_000L);
        service = new StressTaskServiceImpl(taskMapper, configMapper, responseMapper, codec, executionService, clock);
    }

    @Test
    void createTaskUsesImmutableSnapshotAndImmediateStart()
    {
        StressRequestConfig config = config("https://old.example.com/check");
        when(configMapper.selectEnabledConfigs()).thenReturn(Collections.singletonList(config));
        when(taskMapper.insertTask(any(StressTask.class))).thenAnswer(invocation -> {
            StressTask inserted = invocation.getArgument(0);
            inserted.setTaskId(11L);
            return 1;
        });

        StressTask task = service.createTask(request("13800138000", 1, "MINUTE"), 7L, "tester");

        assertEquals(11L, task.getTaskId().longValue());
        assertEquals("13800138000", task.getPhone());
        assertEquals(60, task.getDurationSeconds().intValue());
        assertEquals(StressTaskStatus.RUNNING, task.getStatus());
        assertEquals("13800138000", task.getRunningPhone());
        assertEquals(1_000_000L, task.getStartTime().getTime());
        assertEquals(1_060_000L, task.getPlanEndTime().getTime());
        assertEquals(7L, task.getOwnerId().longValue());
        verify(executionService).start(task);

        List<com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot> snapshots =
                codec.fromJson(task.getSnapshotJson());
        assertEquals("https://old.example.com/check", snapshots.get(0).getRequestUrl());

        // 修改原配置不应改变已生成的任务快照
        config.setRequestUrl("https://new.example.com/check");
        assertEquals("https://old.example.com/check",
                codec.fromJson(task.getSnapshotJson()).get(0).getRequestUrl());
    }

    @Test
    void concurrentCreateForSamePhoneOnlyOneSucceeds() throws Exception
    {
        when(configMapper.selectEnabledConfigs()).thenReturn(Collections.singletonList(config("https://example.com/check")));
        final AtomicBoolean inserted = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (!inserted.compareAndSet(false, true))
            {
                throw new DuplicateKeyException("uk_stress_task_running_phone");
            }
            StressTask task = invocation.getArgument(0);
            task.setTaskId(21L);
            return 1;
        }).when(taskMapper).insertTask(any(StressTask.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch go = new CountDownLatch(1);
        try
        {
            Future<Boolean> first = pool.submit(() -> {
                ready.countDown();
                go.await(3, TimeUnit.SECONDS);
                return tryCreate();
            });
            Future<Boolean> second = pool.submit(() -> {
                ready.countDown();
                go.await(3, TimeUnit.SECONDS);
                return tryCreate();
            });
            assertTrue(ready.await(3, TimeUnit.SECONDS));
            go.countDown();

            boolean firstResult = first.get(5, TimeUnit.SECONDS);
            boolean secondResult = second.get(5, TimeUnit.SECONDS);
            assertTrue(firstResult ^ secondResult, "并发提交必须恰好一个成功");
            verify(taskMapper, times(2)).insertTask(any(StressTask.class));
            verify(executionService, times(1)).start(any(StressTask.class));
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    @Test
    void normalUserCannotAccessOthersTaskButAdminCan()
    {
        StressTask task = new StressTask();
        task.setTaskId(1L);
        task.setOwnerId(5L);
        task.setStatus(StressTaskStatus.RUNNING);
        when(taskMapper.selectTaskById(1L)).thenReturn(task);

        ServiceException denied = assertThrows(ServiceException.class,
                () -> service.checkTaskAccess(1L, 6L, false));
        assertEquals(403, denied.getCode().intValue());
        assertEquals(task, service.checkTaskAccess(1L, 5L, false));
        assertEquals(task, service.checkTaskAccess(1L, 6L, true));
    }

    @Test
    void cannotDeleteRunningTask()
    {
        StressTask task = new StressTask();
        task.setTaskId(1L);
        task.setOwnerId(5L);
        task.setStatus(StressTaskStatus.RUNNING);
        when(taskMapper.selectTaskById(1L)).thenReturn(task);

        assertThrows(ServiceException.class, () -> service.deleteTask(1L, 5L, false));
        verify(responseMapper, never()).deleteResponseByTaskId(any(Long.class));
        verify(taskMapper, never()).deleteTaskById(any(Long.class));
    }

    @Test
    void taskListFiltersByOwnerForNormalUser()
    {
        service.selectTaskList("138", "RUNNING", 5L, false);
        verify(taskMapper).selectTaskList("138", "RUNNING", 5L);

        service.selectTaskList("138", "RUNNING", 6L, true);
        verify(taskMapper).selectTaskList("138", "RUNNING", null);
    }

    @Test
    void startupRecoveryMarksRunningTasksInterrupted()
    {
        when(taskMapper.markRunningTasksInterrupted(any(Date.class))).thenReturn(2);

        assertEquals(2, service.recoverRunningTasks());
        verify(taskMapper).markRunningTasksInterrupted(any(Date.class));
    }

    @Test
    void createTaskAllowsDurationBeyondTwentyFourHours()
    {
        when(configMapper.selectEnabledConfigs()).thenReturn(Collections.singletonList(config("https://example.com/check")));
        when(taskMapper.insertTask(any(StressTask.class))).thenAnswer(invocation -> {
            StressTask inserted = invocation.getArgument(0);
            inserted.setTaskId(12L);
            return 1;
        });

        StressTask task = service.createTask(request("13800138000", 25, "HOUR"), 1L, "t");

        assertEquals(90000, task.getDurationSeconds().intValue());
        assertEquals(1_000_000L + 90_000_000L, task.getPlanEndTime().getTime());
    }

    @Test
    void rejectsInvalidPhoneAndDuration()
    {
        assertThrows(ServiceException.class, () -> service.createTask(request("abc", 1, "MINUTE"), 1L, "t"));
        assertThrows(ServiceException.class,
                () -> service.createTask(request("+8613800138000", 1, "MINUTE"), 1L, "t"));
        assertThrows(ServiceException.class, () -> service.createTask(request("13800138000", 1, "DAY"), 1L, "t"));
        assertThrows(ServiceException.class,
                () -> service.createTask(request("13800138000", Integer.MAX_VALUE, "HOUR"), 1L, "t"));
    }

    @Test
    void rejectsWhenNoEnabledConfig()
    {
        when(configMapper.selectEnabledConfigs()).thenReturn(Collections.emptyList());
        assertThrows(ServiceException.class, () -> service.createTask(request("13800138000", 1, "MINUTE"), 1L, "t"));
        verify(executionService, never()).start(any(StressTask.class));
    }

    @Test
    void threadsDefaultToConfiguredValueWhenOmitted()
    {
        when(configMapper.selectEnabledConfigs()).thenReturn(Collections.singletonList(config("https://example.com/check")));
        when(taskMapper.insertTask(any(StressTask.class))).thenAnswer(invocation -> {
            StressTask inserted = invocation.getArgument(0);
            inserted.setTaskId(21L);
            return 1;
        });
        StressTaskServiceImpl withDefaults = new StressTaskServiceImpl(taskMapper, configMapper, responseMapper,
                codec, executionService, clock, 3, 8);

        StressTask task = withDefaults.createTask(request("13800138000", 1, "MINUTE"), 1L, "t");

        assertEquals(3, task.getThreadsPerTask().intValue());
    }

    @Test
    void explicitThreadsArePersisted()
    {
        when(configMapper.selectEnabledConfigs()).thenReturn(Collections.singletonList(config("https://example.com/check")));
        when(taskMapper.insertTask(any(StressTask.class))).thenAnswer(invocation -> {
            StressTask inserted = invocation.getArgument(0);
            inserted.setTaskId(22L);
            return 1;
        });
        StressTaskCreateRequest request = request("13800138000", 1, "MINUTE");
        request.setThreads(5);

        StressTask task = service.createTask(request, 1L, "t");

        assertEquals(5, task.getThreadsPerTask().intValue());
    }

    @Test
    void rejectsThreadsOutOfRange()
    {
        when(configMapper.selectEnabledConfigs()).thenReturn(Collections.singletonList(config("https://example.com/check")));

        StressTaskCreateRequest tooMany = request("13800138000", 1, "MINUTE");
        tooMany.setThreads(9);
        assertThrows(ServiceException.class, () -> service.createTask(tooMany, 1L, "t"));

        StressTaskCreateRequest zero = request("13800138000", 1, "MINUTE");
        zero.setThreads(0);
        assertThrows(ServiceException.class, () -> service.createTask(zero, 1L, "t"));

        verify(executionService, never()).start(any(StressTask.class));
    }

    private boolean tryCreate()
    {
        try
        {
            service.createTask(request("13800138000", 1, "MINUTE"), 1L, "tester");
            return true;
        }
        catch (ServiceException e)
        {
            return false;
        }
    }

    private StressTaskCreateRequest request(String phone, int duration, String unit)
    {
        StressTaskCreateRequest request = new StressTaskCreateRequest();
        request.setPhone(phone);
        request.setDuration(duration);
        request.setUnit(unit);
        return request;
    }

    private StressRequestConfig config(String url)
    {
        StressRequestConfig config = new StressRequestConfig();
        config.setConfigId(1L);
        config.setConfigName("接口A");
        config.setRequestUrl(url);
        config.setHttpMethod("GET");
        config.setHeadersJson("{}");
        config.setQueryParamsJson("{}");
        config.setBodyType("RAW");
        config.setTimeoutMs(10000);
        config.setEnabled("0");
        return config;
    }
}
