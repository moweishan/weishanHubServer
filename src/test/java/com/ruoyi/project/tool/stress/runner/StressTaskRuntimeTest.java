package com.ruoyi.project.tool.stress.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;
import com.ruoyi.project.tool.stress.domain.StressTask;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 运行时取消动作测试：移除后不再执行，且慢取消不阻塞停止调用方。
 */
class StressTaskRuntimeTest
{
    @Test
    void removedCancelActionIsNotRun()
    {
        StressTaskRuntime runtime = runtime(Runnable::run);
        final AtomicInteger ran = new AtomicInteger(0);
        Runnable action = ran::incrementAndGet;

        runtime.onCancel(action);
        runtime.removeCancelAction(action);
        runtime.requestStop(StressStopReason.MANUAL);

        assertEquals(0, ran.get());
        assertTrue(runtime.isCancelled());
    }

    @Test
    void blockingCancelActionDoesNotBlockRequestStop() throws Exception
    {
        ExecutorService cancelExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cancel-test");
            thread.setDaemon(true);
            return thread;
        });
        StressTaskRuntime runtime = runtime(cancelExecutor);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        runtime.onCancel(() -> {
            entered.countDown();
            try
            {
                release.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        });

        long start = System.nanoTime();
        runtime.requestStop(StressStopReason.MANUAL);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMillis < 500L, "requestStop 不应等待慢取消动作，实际=" + elapsedMillis + "ms");
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        release.countDown();
        cancelExecutor.shutdownNow();
    }

    private StressTaskRuntime runtime(Executor executor)
    {
        StressTask task = new StressTask();
        task.setTaskId(1L);
        task.setPhone("13800138000");
        StressRequestConfigSnapshot snapshot = new StressRequestConfigSnapshot();
        snapshot.setConfigId(1L);
        snapshot.setConfigName("接口");
        snapshot.setRequestUrl("http://example.test");
        snapshot.setHttpMethod("GET");
        return new StressTaskRuntime(task, Collections.singletonList(snapshot), 0L, executor);
    }
}
