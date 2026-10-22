package com.ruoyi.project.tool.stress.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;
import com.ruoyi.project.tool.stress.domain.StressTask;
import com.ruoyi.project.tool.stress.domain.StressTaskStatus;
import com.ruoyi.project.tool.stress.mapper.StressTaskMapper;
import com.ruoyi.project.tool.stress.service.StressSnapshotCodec;
import com.ruoyi.project.tool.stress.service.StressTaskResponseService;
import com.ruoyi.project.tool.stress.service.StressTemplateRenderer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 任务执行器测试：任务内两条线程并发、61 秒间隔的可注入短周期、截止取消、手动停止和资源清理。
 */
class StressTaskExecutionServiceTest
{
    private StressTaskMapper taskMapper;

    private StressTaskResponseService responseService;

    private StressSnapshotCodec codec;

    private StressTaskExecutionService service;

    private ThreadPoolExecutor workers;

    @BeforeEach
    void setUp()
    {
        taskMapper = mock(StressTaskMapper.class);
        responseService = mock(StressTaskResponseService.class);
        codec = new StressSnapshotCodec(new ObjectMapper());
        when(taskMapper.finishTask(anyLong(), anyString(), any(Date.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (service != null)
        {
            service.shutdown();
        }
        if (workers != null)
        {
            workers.shutdownNow();
            workers.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void twoInterfacesRunConcurrently() throws Exception
    {
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maxActive = new AtomicInteger();
        final CountDownLatch twoCalls = new CountDownLatch(2);
        final List<String> urls = Collections.synchronizedList(new ArrayList<String>());

        StressHttpTransport transport = (request, cancellation) -> {
            int current = active.incrementAndGet();
            maxActive.updateAndGet(previous -> Math.max(previous, current));
            urls.add(request.getUrl());
            try
            {
                Thread.sleep(80L);
                return StressHttpResult.response(200, "ok", false);
            }
            finally
            {
                active.decrementAndGet();
                twoCalls.countDown();
            }
        };
        service = newService(50_000L, transport);
        StressTask task = newTask(1L, Arrays.asList(
                snapshot(1L, "接口A", "http://example.test/a"),
                snapshot(2L, "接口B", "http://example.test/b")), 900L);

        service.start(task);

        assertTrue(twoCalls.await(3, TimeUnit.SECONDS));
        verify(taskMapper, timeout(4000)).finishTask(eq(1L), eq(StressTaskStatus.COMPLETED), any(Date.class));
        awaitIdle();
        assertEquals(StressTaskExecutionService.DEFAULT_THREADS_PER_TASK, maxActive.get(),
                "两条工作线程应同时执行不同接口");
        assertEquals(2, urls.size());
        verify(responseService, times(2)).recordResult(eq(1L), any(StressRequestConfigSnapshot.class),
                any(StressHttpResult.class), anyLong());
    }

    @Test
    void twoThreadsStillKeepPerInterfaceInterval() throws Exception
    {
        final java.util.Map<String, List<Long>> startsByUrl = new java.util.concurrent.ConcurrentHashMap<>();
        StressHttpTransport transport = (request, cancellation) -> {
            startsByUrl.computeIfAbsent(request.getUrl(),
                    key -> Collections.synchronizedList(new ArrayList<Long>())).add(System.nanoTime());
            return StressHttpResult.response(200, "ok", false);
        };
        // 周期放大到 300ms 并留 17% 余量：并发场景下首次调用的类加载/JIT 抖动会吃进测量间隔
        service = newService(300L, transport);
        StressTask task = newTask(9L, Arrays.asList(
                snapshot(1L, "接口A", "http://example.test/a"),
                snapshot(2L, "接口B", "http://example.test/b")), 1200L);

        service.start(task);

        verify(taskMapper, timeout(4000)).finishTask(eq(9L), eq(StressTaskStatus.COMPLETED), any(Date.class));
        awaitIdle();
        assertEquals(2, startsByUrl.size());
        for (java.util.Map.Entry<String, List<Long>> entry : startsByUrl.entrySet())
        {
            List<Long> starts = entry.getValue();
            assertTrue(starts.size() >= 3, entry.getKey() + " 短周期测试至少应执行三次");
            for (int i = 1; i < starts.size(); i++)
            {
                long gap = starts.get(i) - starts.get(i - 1);
                assertTrue(gap >= TimeUnit.MILLISECONDS.toNanos(250L),
                        entry.getKey() + " 同一接口两次开始间隔不应明显小于300ms，实际=" + gap);
            }
        }
    }

    @Test
    void sameInterfaceUsesConfiguredIntervalForTests() throws Exception
    {
        final List<Long> starts = Collections.synchronizedList(new ArrayList<Long>());
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maxActive = new AtomicInteger();
        StressHttpTransport transport = (request, cancellation) -> {
            maxActive.updateAndGet(previous -> Math.max(previous, active.incrementAndGet()));
            try
            {
                starts.add(System.nanoTime());
                return StressHttpResult.response(200, "ok", false);
            }
            finally
            {
                active.decrementAndGet();
            }
        };
        service = newService(200L, transport);
        StressTask task = newTask(2L, Collections.singletonList(
                snapshot(1L, "接口A", "http://example.test/a")), 750L);

        service.start(task);

        verify(taskMapper, timeout(4000)).finishTask(eq(2L), eq(StressTaskStatus.COMPLETED), any(Date.class));
        awaitIdle();
        assertTrue(starts.size() >= 3, "短周期测试至少应执行三次");
        assertEquals(1, maxActive.get(), "同一接口不能被两条工作线程同时执行");
        for (int i = 1; i < starts.size(); i++)
        {
            long gap = starts.get(i) - starts.get(i - 1);
            assertTrue(gap >= TimeUnit.MILLISECONDS.toNanos(190L),
                    "同一接口两次开始间隔不应小于约200ms，实际=" + gap);
        }
    }

    @Test
    void deadlineCancelsBlockingRequest() throws Exception
    {
        BlockingTransport transport = new BlockingTransport();
        service = newService(50_000L, transport);
        StressTask task = newTask(3L, Collections.singletonList(
                snapshot(1L, "阻塞接口", "http://example.test/block")), 150L);

        service.start(task);

        assertTrue(transport.entered.await(2, TimeUnit.SECONDS));
        assertTrue(transport.cancelled.await(2, TimeUnit.SECONDS));
        verify(taskMapper, timeout(3000)).finishTask(eq(3L), eq(StressTaskStatus.COMPLETED), any(Date.class));
        awaitIdle();

        ArgumentCaptor<StressHttpResult> captor = ArgumentCaptor.forClass(StressHttpResult.class);
        verify(responseService, timeout(2000)).recordResult(eq(3L), any(StressRequestConfigSnapshot.class),
                captor.capture(), anyLong());
        assertFalse(captor.getValue().isSuccess());
        assertTrue(captor.getValue().getErrorMessage().contains("到期"));
    }

    @Test
    void failedRequestIsNotRetriedWithinSameInterval() throws Exception
    {
        final AtomicInteger calls = new AtomicInteger(0);
        StressHttpTransport transport = (request, cancellation) -> {
            calls.incrementAndGet();
            return StressHttpResult.response(500, "server error", false);
        };
        service = newService(50_000L, transport);
        StressTask task = newTask(5L, Collections.singletonList(
                snapshot(1L, "失败接口", "http://example.test/fail")), 600L);

        service.start(task);

        verify(taskMapper, timeout(4000)).finishTask(eq(5L), eq(StressTaskStatus.COMPLETED), any(Date.class));
        awaitIdle();
        assertEquals(1, calls.get());
        verify(responseService, times(1)).recordResult(eq(5L), any(StressRequestConfigSnapshot.class),
                any(StressHttpResult.class), anyLong());
    }

    @Test
    void slowFirstInterfaceDoesNotStarveLaterInterfaces() throws Exception
    {
        final List<String> starts = Collections.synchronizedList(new ArrayList<String>());
        StressHttpTransport transport = (request, cancellation) -> {
            starts.add(request.getUrl());
            if (request.getUrl().endsWith("/slow"))
            {
                Thread.sleep(260L);
            }
            return StressHttpResult.response(200, "ok", false);
        };
        service = newService(100L, transport);
        StressTask task = newTask(6L, Arrays.asList(
                snapshot(1L, "慢接口", "http://example.test/slow"),
                snapshot(2L, "接口B", "http://example.test/b"),
                snapshot(3L, "接口C", "http://example.test/c")), 800L);

        service.start(task);

        verify(taskMapper, timeout(4000)).finishTask(eq(6L), eq(StressTaskStatus.COMPLETED), any(Date.class));
        awaitIdle();
        assertTrue(starts.size() >= 3);
        // 两条线程并发时前两次开始的顺序不确定，但慢接口不能饿死后续接口：
        // 前三次开始必须覆盖三个不同接口（同一接口 100ms 内不会重复开始）
        List<String> firstThree = new ArrayList<String>(starts.subList(0, 3));
        assertTrue(firstThree.containsAll(Arrays.asList("http://example.test/slow",
                "http://example.test/b", "http://example.test/c")),
                "慢接口不应饿死后续接口，前三次实际=" + firstThree);
        assertEquals(3, new java.util.HashSet<String>(firstThree).size());
    }

    @Test
    void manualStopCancelsBlockingRequestAndMarksStopped() throws Exception
    {
        BlockingTransport transport = new BlockingTransport();
        service = newService(50_000L, transport);
        StressTask task = newTask(4L, Collections.singletonList(
                snapshot(1L, "阻塞接口", "http://example.test/block")), 5000L);

        service.start(task);
        assertTrue(transport.entered.await(2, TimeUnit.SECONDS));

        assertTrue(service.stopTask(4L));

        verify(taskMapper, timeout(3000)).finishTask(eq(4L), eq(StressTaskStatus.STOPPED), any(Date.class));
        assertTrue(transport.cancelled.await(2, TimeUnit.SECONDS));
        awaitIdle();
    }

    private StressTaskExecutionService newService(long interval, StressHttpTransport transport)
    {
        workers = new ThreadPoolExecutor(0, 8, 1L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
        java.util.concurrent.ScheduledThreadPoolExecutor timer =
                new java.util.concurrent.ScheduledThreadPoolExecutor(1);
        timer.setRemoveOnCancelPolicy(true);
        ThreadPoolExecutor cancelExecutor =
                new ThreadPoolExecutor(0, 4, 1L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
        return new StressTaskExecutionService(taskMapper, responseService,
                new StressTemplateRenderer(new ObjectMapper()), transport, codec, new SystemStressClock(),
                new StressSchedule(interval), workers, timer, cancelExecutor);
    }

    private StressTask newTask(long taskId, List<StressRequestConfigSnapshot> snapshots, long durationMs)
    {
        long now = System.currentTimeMillis();
        StressTask task = new StressTask();
        task.setTaskId(taskId);
        task.setPhone("13800138000");
        task.setDurationSeconds((int) (durationMs / 1000L));
        task.setStatus(StressTaskStatus.RUNNING);
        task.setRunningPhone(task.getPhone());
        task.setStartTime(new Date(now));
        task.setPlanEndTime(new Date(now + durationMs));
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setOwnerId(1L);
        task.setSnapshotJson(codec.toJson(snapshots));
        return task;
    }

    private StressRequestConfigSnapshot snapshot(long configId, String name, String url)
    {
        StressRequestConfigSnapshot snapshot = new StressRequestConfigSnapshot();
        snapshot.setConfigId(configId);
        snapshot.setConfigName(name);
        snapshot.setRequestUrl(url);
        snapshot.setHttpMethod("GET");
        snapshot.setHeadersJson("{}");
        snapshot.setQueryParamsJson("{}");
        snapshot.setBodyType("RAW");
        snapshot.setTimeoutMs(2000);
        return snapshot;
    }

    private void awaitIdle() throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + 2000L;
        while (service.getActiveRuntimeCount() > 0 && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(10L);
        }
        assertEquals(0, service.getActiveRuntimeCount());
    }

    private static final class BlockingTransport implements StressHttpTransport
    {
        private final CountDownLatch entered = new CountDownLatch(1);

        private final CountDownLatch cancelled = new CountDownLatch(1);

        @Override
        public StressHttpResult execute(StressHttpRequest request, StressCancellation cancellation) throws Exception
        {
            entered.countDown();
            final CountDownLatch release = new CountDownLatch(1);
            cancellation.onCancel(() -> {
                cancelled.countDown();
                release.countDown();
            });
            if (!release.await(5, TimeUnit.SECONDS))
            {
                throw new IOException("测试阻塞请求等待取消超时");
            }
            throw new IOException("请求已取消");
        }
    }
}
