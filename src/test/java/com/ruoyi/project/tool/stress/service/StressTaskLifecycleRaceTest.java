package com.ruoyi.project.tool.stress.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;
import com.ruoyi.project.tool.stress.domain.StressTask;
import com.ruoyi.project.tool.stress.domain.StressTaskCreateRequest;
import com.ruoyi.project.tool.stress.domain.StressTaskStatus;
import com.ruoyi.project.tool.stress.mapper.StressRequestConfigMapper;
import com.ruoyi.project.tool.stress.mapper.StressTaskMapper;
import com.ruoyi.project.tool.stress.mapper.StressTaskResponseMapper;
import com.ruoyi.project.tool.stress.runner.StressTaskExecutionService;
import com.ruoyi.project.tool.stress.runner.SystemStressClock;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 创建记录刚可见时停止任务，不能出现停止后执行器才启动的竞态。 */
class StressTaskLifecycleRaceTest
{
    @Test
    void stoppingDuringCreationMustNotStartAnAlreadyStoppedTask() throws Exception
    {
        StressTaskMapper tasks = mock(StressTaskMapper.class);
        StressRequestConfigMapper configs = mock(StressRequestConfigMapper.class);
        StressTaskExecutionService execution = mock(StressTaskExecutionService.class);
        StressRequestConfig config = new StressRequestConfig();
        config.setConfigId(1L);
        config.setConfigName("本地测试接口");
        config.setRequestUrl("http://127.0.0.1/test");
        config.setHttpMethod("GET");
        when(configs.selectEnabledConfigs()).thenReturn(Collections.singletonList(config));
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch releaseInsert = new CountDownLatch(1);
        CountDownLatch stopReached = new CountDownLatch(1);
        AtomicReference<StressTask> persisted = new AtomicReference<StressTask>();
        AtomicBoolean running = new AtomicBoolean();
        when(tasks.insertTask(any(StressTask.class))).thenAnswer(call -> {
            StressTask task = call.getArgument(0);
            task.setTaskId(1L);
            persisted.set(task);
            inserted.countDown();
            assertTrue(releaseInsert.await(3, TimeUnit.SECONDS));
            return 1;
        });
        when(tasks.selectTaskById(1L)).thenAnswer(call -> persisted.get());
        when(tasks.finishTask(anyLong(), anyString(), any(Date.class))).thenAnswer(call -> {
            persisted.get().setStatus(call.getArgument(1));
            return 1;
        });
        org.mockito.Mockito.doAnswer(call -> {
            running.set(true);
            return null;
        }).when(execution).start(any(StressTask.class));
        when(execution.stopTask(1L)).thenAnswer(call -> {
            stopReached.countDown();
            return running.getAndSet(false);
        });
        StressTaskServiceImpl service = new StressTaskServiceImpl(tasks, configs,
                mock(StressTaskResponseMapper.class), new StressSnapshotCodec(new ObjectMapper()),
                execution, new SystemStressClock());
        StressTaskCreateRequest request = new StressTaskCreateRequest();
        request.setPhone("13800138000");
        request.setDuration(1);
        request.setUnit("MINUTE");
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try
        {
            Future<?> creator = threads.submit(() -> service.createTask(request, 2L, "test"));
            assertTrue(inserted.await(2, TimeUnit.SECONDS));
            Future<?> stopper = threads.submit(() -> service.stopTask(1L, 2L, false));
            // 未保护的实现会先停止数据库记录，再由创建线程启动任务。
            stopReached.await(500, TimeUnit.MILLISECONDS);
            releaseInsert.countDown();
            creator.get(3, TimeUnit.SECONDS);
            stopper.get(3, TimeUnit.SECONDS);
            assertFalse(running.get(), "已完成停止操作后，不应又启动执行器");
        }
        finally
        {
            releaseInsert.countDown();
            threads.shutdownNow();
        }
    }
}
