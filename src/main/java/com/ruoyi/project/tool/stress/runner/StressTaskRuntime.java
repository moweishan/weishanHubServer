package com.ruoyi.project.tool.stress.runner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;
import com.ruoyi.project.tool.stress.domain.StressTask;

/**
 * 单个运行中任务的运行时状态。
 *
 * <p>负责停止信号、当前 HTTP 请求的取消动作、接口下一次允许开始时间，
 * 以及工作线程与截止定时器句柄。持久化由执行服务统一完成。</p>
 *
 * @author weishanHub
 */
public class StressTaskRuntime implements StressCancellation
{
    private static final Logger log = LoggerFactory.getLogger(StressTaskRuntime.class);

    private final StressTask task;

    private final List<StressRequestConfigSnapshot> snapshots;

    /** 本轮可开始接口的轮询游标，避免慢接口反复抢占后续接口 */
    private int cursor;

    /** 单个任务内各接口下一次允许开始时间（单调纳秒） */
    private final Map<Long, Long> nextStartNanos = new LinkedHashMap<Long, Long>();

    /**
     * 接口调度的互斥锁：任务内有两条工作线程，领取到期接口、写入下一次开始时间、
     * 推进游标必须一次完成，否则同一接口会被两个线程同时开始。
     */
    private final Object scheduleLock = new Object();

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private final AtomicBoolean finishStarted = new AtomicBoolean(false);

    private final CountDownLatch finished = new CountDownLatch(1);

    private final Object cancelLock = new Object();

    private final List<Runnable> cancelActions = new ArrayList<Runnable>();

    private final Executor cancelExecutor;

    private volatile boolean cancelled;

    private volatile StressStopReason stopReason;

    /** 任务内的并发工作线程，停止时逐个中断 */
    private final List<Thread> workerThreads = new ArrayList<Thread>();

    private final Object workerLock = new Object();

    private volatile ScheduledFuture<?> deadlineFuture;

    public StressTaskRuntime(StressTask task, List<StressRequestConfigSnapshot> snapshots, long nowNanos,
            Executor cancelExecutor)
    {
        this.task = task;
        this.snapshots = snapshots;
        this.cancelExecutor = cancelExecutor;
        for (StressRequestConfigSnapshot snapshot : snapshots)
        {
            nextStartNanos.put(snapshot.getConfigId(), nowNanos);
        }
    }

    public StressTask getTask()
    {
        return task;
    }

    public List<StressRequestConfigSnapshot> getSnapshots()
    {
        return snapshots;
    }

    public long getNextStartNanos(Long configId)
    {
        synchronized (scheduleLock)
        {
            Long value = nextStartNanos.get(configId);
            return value == null ? 0L : value;
        }
    }

    public void setNextStartNanos(Long configId, long value)
    {
        synchronized (scheduleLock)
        {
            nextStartNanos.put(configId, value);
        }
    }

    public int getCursor()
    {
        synchronized (scheduleLock)
        {
            return cursor;
        }
    }

    public void setCursor(int cursor)
    {
        synchronized (scheduleLock)
        {
            this.cursor = cursor;
        }
    }

    /**
     * 原子领取一个已到期的接口。
     *
     * <p>命中后立即写入该接口下一次允许开始时间并推进游标，保证同一接口不会被两个工作线程
     * 同时开始，也保证同一接口两次开始之间仍满足调度间隔。</p>
     *
     * @param nowNanos       当前单调纳秒
     * @param nextStartValue 本次开始后该接口下一次允许开始时间
     * @return 到期接口；当前没有可开始的接口时返回 null
     */
    public StressRequestConfigSnapshot claimDue(long nowNanos, long nextStartValue)
    {
        synchronized (scheduleLock)
        {
            int size = snapshots.size();
            int start = cursor;
            for (int offset = 0; offset < size; offset++)
            {
                int index = (start + offset) % size;
                StressRequestConfigSnapshot snapshot = snapshots.get(index);
                Long value = nextStartNanos.get(snapshot.getConfigId());
                if (value == null || value.longValue() <= nowNanos)
                {
                    nextStartNanos.put(snapshot.getConfigId(), Long.valueOf(nextStartValue));
                    cursor = (index + 1) % size;
                    return snapshot;
                }
            }
        }
        return null;
    }

    /**
     * 所有接口中最早的下一次允许开始时间（单调纳秒），没有接口时返回 Long.MAX_VALUE。
     */
    public long earliestNextStartNanos()
    {
        synchronized (scheduleLock)
        {
            long earliest = Long.MAX_VALUE;
            for (Long value : nextStartNanos.values())
            {
                if (value != null && value.longValue() < earliest)
                {
                    earliest = value.longValue();
                }
            }
            return earliest;
        }
    }

    public boolean isStopRequested()
    {
        return stopRequested.get();
    }

    /**
     * 请求停止任务：记录原因、取消当前网络请求、唤醒工作线程并取消截止定时器。
     */
    public void requestStop(StressStopReason reason)
    {
        synchronized (this)
        {
            if (stopReason == null)
            {
                stopReason = reason;
            }
        }
        if (stopRequested.compareAndSet(false, true))
        {
            // 取消动作可能阻塞（例如 JDK disconnect 在慢读时可达数秒），
            // 不能在截止定时器或停止接口线程中同步执行。
            try
            {
                cancelExecutor.execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        doCancel();
                    }
                });
            }
            catch (RejectedExecutionException e)
            {
                log.warn("压力测试取消执行器已满，taskId={} 仅依赖中断和读超时停止", task.getTaskId());
            }
        }
        List<Thread> threads;
        synchronized (workerLock)
        {
            threads = new ArrayList<Thread>(workerThreads);
        }
        if (!finishStarted.get())
        {
            for (Thread thread : threads)
            {
                thread.interrupt();
            }
        }
        ScheduledFuture<?> future = deadlineFuture;
        if (future != null)
        {
            future.cancel(false);
        }
    }

    public StressStopReason getStopReason()
    {
        return stopReason;
    }

    /**
     * 登记任务内的工作线程，停止时统一中断。
     */
    public void addWorkerThread(Thread thread)
    {
        if (thread == null)
        {
            return;
        }
        synchronized (workerLock)
        {
            workerThreads.add(thread);
        }
    }

    /**
     * 工作线程退出时注销自身，避免持有已结束线程的引用。
     */
    public void removeWorkerThread(Thread thread)
    {
        synchronized (workerLock)
        {
            workerThreads.remove(thread);
        }
    }

    public int getWorkerThreadCount()
    {
        synchronized (workerLock)
        {
            return workerThreads.size();
        }
    }

    public void setDeadlineFuture(ScheduledFuture<?> deadlineFuture)
    {
        this.deadlineFuture = deadlineFuture;
    }

    /**
     * 任务正常结束后取消尚未触发的截止定时器。
     */
    public void cancelDeadline()
    {
        ScheduledFuture<?> future = deadlineFuture;
        if (future != null)
        {
            future.cancel(false);
        }
    }

    public boolean markFinishStarted()
    {
        return finishStarted.compareAndSet(false, true);
    }

    public void signalFinished()
    {
        finished.countDown();
    }

    public boolean awaitFinished(long timeout, TimeUnit unit) throws InterruptedException
    {
        return finished.await(timeout, unit);
    }

    public String getCancelMessage()
    {
        StressStopReason reason = stopReason;
        if (reason == StressStopReason.EXPIRED)
        {
            return "任务已到期，请求已取消";
        }
        if (reason == StressStopReason.MANUAL)
        {
            return "任务已手动停止，请求已取消";
        }
        if (reason == StressStopReason.SHUTDOWN)
        {
            return "应用关闭，请求已取消";
        }
        return "任务已中断，请求已取消";
    }

    @Override
    public boolean isCancelled()
    {
        // 停止信号一发出即视为取消，传输层不再发起新请求；
        // 实际 disconnect 由 cancelExecutor 异步执行。
        return stopRequested.get() || cancelled;
    }

    @Override
    public void onCancel(Runnable action)
    {
        synchronized (cancelLock)
        {
            if (!cancelled)
            {
                cancelActions.add(action);
                return;
            }
        }
        runCancelAction(action);
    }

    @Override
    public void removeCancelAction(Runnable action)
    {
        synchronized (cancelLock)
        {
            cancelActions.remove(action);
        }
    }

    private void doCancel()
    {
        List<Runnable> actions;
        synchronized (cancelLock)
        {
            if (cancelled)
            {
                return;
            }
            cancelled = true;
            actions = new ArrayList<Runnable>(cancelActions);
            cancelActions.clear();
        }
        for (Runnable action : actions)
        {
            runCancelAction(action);
        }
    }

    private void runCancelAction(Runnable action)
    {
        try
        {
            action.run();
        }
        catch (Exception ignored)
        {
            // 取消动作失败不应阻断停止流程
        }
    }
}
