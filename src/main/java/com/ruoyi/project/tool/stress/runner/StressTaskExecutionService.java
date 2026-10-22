package com.ruoyi.project.tool.stress.runner;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;
import com.ruoyi.project.tool.stress.domain.StressTask;
import com.ruoyi.project.tool.stress.domain.StressTaskStatus;
import com.ruoyi.project.tool.stress.mapper.StressTaskMapper;
import com.ruoyi.project.tool.stress.service.StressSnapshotCodec;
import com.ruoyi.project.tool.stress.service.StressTaskResponseService;
import com.ruoyi.project.tool.stress.service.StressTemplateRenderer;

/**
 * 压力测试任务执行器。
 *
 * <p>核心约束：</p>
 * <ul>
 *   <li>创建后立即开始，一个任务内默认由两条工作线程并发执行不同接口；</li>
 *   <li>同一接口两次开始间隔至少 61 秒，各任务独立计时，并发线程不会重复开同一个接口；</li>
 *   <li>持续时长从提交时开始按实际经过时间计算，并有独立的截止定时器；</li>
 *   <li>到期或手动停止时停止调度并异步尝试断开当前连接；</li>
 *   <li>使用有界任务线程池，避免线程和队列无限增长。</li>
 * </ul>
 *
 * @author weishanHub
 */
@Service
public class StressTaskExecutionService
{
    private static final Logger log = LoggerFactory.getLogger(StressTaskExecutionService.class);

    /** 单个任务内默认并发工作线程数 */
    public static final int DEFAULT_THREADS_PER_TASK = 2;

    /** 单个任务允许的最大并发工作线程数默认值 */
    public static final int DEFAULT_MAX_THREADS_PER_TASK = 8;

    /** 停止后等待工作线程退出的最长时间，避免个别慢连接长期占住任务槽 */
    private static final long WORKER_DRAIN_SECONDS = 10L;

    /** 任务内并发工作线程数 */
    private final int threadsPerTask;

    /** 单任务允许的最大并发工作线程数 */
    private final int maxThreadsPerTask;

    private final StressTaskMapper taskMapper;

    private final StressTaskResponseService responseService;

    private final StressTemplateRenderer renderer;

    private final StressHttpTransport transport;

    private final StressSnapshotCodec snapshotCodec;

    private final StressClock clock;

    private final StressSchedule schedule;

    private final ThreadPoolExecutor workers;

    private final ScheduledExecutorService timer;

    /** 有界异步取消执行器，避免慢 disconnect 阻塞截止定时器/停止接口 */
    private final ExecutorService cancelExecutor;

    private final ConcurrentHashMap<Long, StressTaskRuntime> runtimes =
            new ConcurrentHashMap<Long, StressTaskRuntime>();

    private volatile boolean shuttingDown;

    @Autowired
    public StressTaskExecutionService(StressTaskMapper taskMapper, StressTaskResponseService responseService,
            StressTemplateRenderer renderer, StressHttpTransport transport, StressSnapshotCodec snapshotCodec,
            StressClock clock, @Value("${stress.max-concurrent-tasks:20}") int maxConcurrentTasks,
            @Value("${stress.threads-per-task:" + DEFAULT_THREADS_PER_TASK + "}") int threadsPerTask,
            @Value("${stress.max-threads-per-task:8}") int maxThreadsPerTask)
    {
        this(taskMapper, responseService, renderer, transport, snapshotCodec, clock, new StressSchedule(),
                createWorkers(maxConcurrentTasks), createTimer(), createCancelExecutor(), threadsPerTask,
                maxThreadsPerTask);
    }

    /**
     * 测试用构造器，可注入短测试间隔、可控时钟和自定义执行器（任务内默认两条工作线程）。
     */
    StressTaskExecutionService(StressTaskMapper taskMapper, StressTaskResponseService responseService,
            StressTemplateRenderer renderer, StressHttpTransport transport, StressSnapshotCodec snapshotCodec,
            StressClock clock, StressSchedule schedule, ThreadPoolExecutor workers,
            ScheduledExecutorService timer, ExecutorService cancelExecutor)
    {
        this(taskMapper, responseService, renderer, transport, snapshotCodec, clock, schedule, workers, timer,
                cancelExecutor, DEFAULT_THREADS_PER_TASK);
    }

    /**
     * 测试用构造器，可同时指定任务内并发工作线程数。
     */
    StressTaskExecutionService(StressTaskMapper taskMapper, StressTaskResponseService responseService,
            StressTemplateRenderer renderer, StressHttpTransport transport, StressSnapshotCodec snapshotCodec,
            StressClock clock, StressSchedule schedule, ThreadPoolExecutor workers,
            ScheduledExecutorService timer, ExecutorService cancelExecutor, int threadsPerTask)
    {
        this(taskMapper, responseService, renderer, transport, snapshotCodec, clock, schedule, workers, timer,
                cancelExecutor, threadsPerTask, DEFAULT_MAX_THREADS_PER_TASK);
    }

    /**
     * 测试用构造器，可同时指定默认线程数与最大线程数。
     */
    StressTaskExecutionService(StressTaskMapper taskMapper, StressTaskResponseService responseService,
            StressTemplateRenderer renderer, StressHttpTransport transport, StressSnapshotCodec snapshotCodec,
            StressClock clock, StressSchedule schedule, ThreadPoolExecutor workers,
            ScheduledExecutorService timer, ExecutorService cancelExecutor, int threadsPerTask,
            int maxThreadsPerTask)
    {
        this.taskMapper = taskMapper;
        this.responseService = responseService;
        this.renderer = renderer;
        this.transport = transport;
        this.snapshotCodec = snapshotCodec;
        this.clock = clock;
        this.schedule = schedule;
        this.workers = workers;
        this.timer = timer;
        this.cancelExecutor = cancelExecutor;
        this.threadsPerTask = Math.max(1, threadsPerTask);
        this.maxThreadsPerTask = Math.max(this.threadsPerTask, maxThreadsPerTask);
    }

    /**
     * 启动任务。调用前任务记录必须已经提交到数据库。
     */
    public void start(StressTask task)
    {
        if (shuttingDown)
        {
            finishWithoutRuntime(task.getTaskId(), StressTaskStatus.INTERRUPTED);
            throw new ServiceException("应用正在关闭，无法启动压力测试任务");
        }

        List<StressRequestConfigSnapshot> snapshots;
        try
        {
            snapshots = snapshotCodec.fromJson(task.getSnapshotJson());
        }
        catch (Exception e)
        {
            finishWithoutRuntime(task.getTaskId(), StressTaskStatus.FAILED);
            throw new ServiceException(e.getMessage());
        }
        if (snapshots.isEmpty())
        {
            finishWithoutRuntime(task.getTaskId(), StressTaskStatus.FAILED);
            throw new ServiceException("没有可执行的接口快照，任务未启动");
        }

        final StressTaskRuntime runtime = new StressTaskRuntime(task, snapshots, clock.nanoTime(), cancelExecutor);
        if (runtimes.putIfAbsent(task.getTaskId(), runtime) != null)
        {
            throw new ServiceException("任务已经在运行中");
        }

        try
        {
            long delay = task.getPlanEndTime().getTime() - clock.currentTimeMillis();
            if (delay < 0)
            {
                delay = 0;
            }
            ScheduledFuture<?> deadlineFuture = timer.schedule(new Runnable()
            {
                @Override
                public void run()
                {
                    runtime.requestStop(StressStopReason.EXPIRED);
                }
            }, delay, TimeUnit.MILLISECONDS);
            runtime.setDeadlineFuture(deadlineFuture);

            workers.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    executeRuntime(runtime);
                }
            });
        }
        catch (RejectedExecutionException e)
        {
            runtimes.remove(task.getTaskId(), runtime);
            runtime.requestStop(StressStopReason.ERROR);
            finishWithoutRuntime(task.getTaskId(), StressTaskStatus.FAILED);
            throw new ServiceException("当前并发压力测试任务已达上限，请稍后再试");
        }
        catch (RuntimeException e)
        {
            runtimes.remove(task.getTaskId(), runtime);
            runtime.requestStop(StressStopReason.ERROR);
            finishWithoutRuntime(task.getTaskId(), StressTaskStatus.FAILED);
            throw new ServiceException("压力测试任务启动失败：" + e.getMessage());
        }
    }

    /**
     * 请求停止任务。只发出停止信号并立即返回，实际取消动作由独立执行器异步执行，
     * 避免慢 disconnect 阻塞停止接口；任务状态由工作线程完成清理后落库。
     *
     * @return true 表示当前实例找到了运行中的任务并已发出取消请求
     */
    public boolean stopTask(Long taskId)
    {
        StressTaskRuntime runtime = runtimes.get(taskId);
        if (runtime == null)
        {
            return false;
        }
        runtime.requestStop(StressStopReason.MANUAL);
        return true;
    }

    public boolean isRunning(Long taskId)
    {
        return runtimes.containsKey(taskId);
    }

    int getActiveRuntimeCount()
    {
        return runtimes.size();
    }

    /**
     * 任务调度入口：占用一个任务槽后启动任务内的工作线程，并等待它们全部退出。
     *
     * <p>任务槽线程本身不执行 HTTP 请求，只负责启动 {@code threadsPerTask} 条工作线程、
     * 等待收尾并落库最终状态，因此并发任务上限仍由 {@code workers} 线程池控制。</p>
     */
    private void executeRuntime(StressTaskRuntime runtime)
    {
        // 清理线程池复用线程可能残留的中断标记
        Thread.interrupted();
        final Long taskId = runtime.getTask().getTaskId();
        final String oldName = Thread.currentThread().getName();
        Thread.currentThread().setName("stress-task-" + taskId + "-dispatcher");
        final int workerCount = resolveWorkerCount(runtime.getTask());
        final CountDownLatch loopsDone = new CountDownLatch(workerCount);
        ExecutorService taskWorkers = Executors.newFixedThreadPool(workerCount,
                daemonThreadFactory("stress-task-" + taskId + "-worker"));
        try
        {
            for (int i = 0; i < workerCount; i++)
            {
                taskWorkers.execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        runWorkerLoop(runtime, loopsDone);
                    }
                });
            }
            awaitWorkerLoops(runtime, loopsDone);
        }
        catch (RuntimeException e)
        {
            log.error("压力测试任务执行异常，taskId={}", taskId, e);
            runtime.requestStop(StressStopReason.ERROR);
        }
        finally
        {
            taskWorkers.shutdownNow();
            // 先清除停止时设置的中断标记，确保状态落库不被中断
            Thread.interrupted();
            finalizeRuntime(runtime);
            Thread.currentThread().setName(oldName);
            // 清除中断标记，避免污染线程池中的下一个任务
            Thread.interrupted();
        }
    }

    /**
     * 任务内并发线程数：优先使用任务记录上配置的值，未配置时回退默认值，并受最大线程数限制。
     */
    private int resolveWorkerCount(StressTask task)
    {
        Integer configured = task == null ? null : task.getThreadsPerTask();
        if (configured == null || configured.intValue() < 1)
        {
            return threadsPerTask;
        }
        return Math.min(configured.intValue(), maxThreadsPerTask);
    }

    /**
     * 单条工作线程的执行循环；单条线程异常只停止本任务，不影响其他任务。
     */
    private void runWorkerLoop(StressTaskRuntime runtime, CountDownLatch loopsDone)
    {
        Thread.interrupted();
        final Thread current = Thread.currentThread();
        final String oldName = current.getName();
        current.setName("stress-task-" + runtime.getTask().getTaskId() + "-" + current.getId());
        runtime.addWorkerThread(current);
        try
        {
            runLoop(runtime);
        }
        catch (InterruptedException e)
        {
            current.interrupt();
            if (!runtime.isStopRequested())
            {
                runtime.requestStop(StressStopReason.INTERRUPTED);
            }
        }
        catch (Throwable e)
        {
            log.error("压力测试工作线程异常，taskId={}", runtime.getTask().getTaskId(), e);
            runtime.requestStop(StressStopReason.ERROR);
        }
        finally
        {
            Thread.interrupted();
            runtime.removeWorkerThread(current);
            current.setName(oldName);
            loopsDone.countDown();
        }
    }

    /**
     * 等待任务内所有工作线程退出。任务运行时一直等待；收到停止或本线程被中断后，
     * 最多再等 {@code WORKER_DRAIN_SECONDS} 秒，避免个别慢连接长期占住任务槽。
     */
    private void awaitWorkerLoops(StressTaskRuntime runtime, CountDownLatch loopsDone)
    {
        long drainDeadline = 0L;
        while (true)
        {
            try
            {
                if (loopsDone.await(200L, TimeUnit.MILLISECONDS))
                {
                    return;
                }
            }
            catch (InterruptedException e)
            {
                // 线程池关闭（应用停止）或外部中断：转为停止信号，但继续等工作线程收尾
                if (!runtime.isStopRequested())
                {
                    runtime.requestStop(StressStopReason.SHUTDOWN);
                }
            }
            if (!runtime.isStopRequested())
            {
                continue;
            }
            if (drainDeadline == 0L)
            {
                drainDeadline = clock.nanoTime() + TimeUnit.SECONDS.toNanos(WORKER_DRAIN_SECONDS);
            }
            else if (clock.nanoTime() >= drainDeadline)
            {
                log.warn("压力测试工作线程未在 {} 秒内退出，taskId={}", WORKER_DRAIN_SECONDS,
                        runtime.getTask().getTaskId());
                return;
            }
        }
    }

    private void runLoop(StressTaskRuntime runtime) throws InterruptedException
    {
        StressTask task = runtime.getTask();
        while (!runtime.isStopRequested())
        {
            long nowWall = clock.currentTimeMillis();
            if (nowWall >= task.getPlanEndTime().getTime())
            {
                runtime.requestStop(StressStopReason.EXPIRED);
                break;
            }

            long nowNanos = clock.nanoTime();
            // 原子领取到期接口并立即登记下一次允许开始时间：两条工作线程不会同时开同一个接口，
            // 也不会让同一接口两次开始小于调度间隔。
            StressRequestConfigSnapshot due = runtime.claimDue(nowNanos, schedule.nextStartNanos(nowNanos));
            if (due == null)
            {
                sleepUntilNext(runtime);
                continue;
            }

            if (clock.currentTimeMillis() >= task.getPlanEndTime().getTime())
            {
                runtime.requestStop(StressStopReason.EXPIRED);
                break;
            }
            executeOne(runtime, due, nowNanos);
        }
    }

    private void sleepUntilNext(StressTaskRuntime runtime) throws InterruptedException
    {
        long earliest = runtime.earliestNextStartNanos();
        if (earliest == Long.MAX_VALUE)
        {
            return;
        }

        long waitNanos = Math.max(0L, earliest - clock.nanoTime());
        long waitMillis = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(waitNanos));
        long untilDeadline = runtime.getTask().getPlanEndTime().getTime() - clock.currentTimeMillis();
        if (untilDeadline <= 0)
        {
            runtime.requestStop(StressStopReason.EXPIRED);
            return;
        }
        clock.sleep(Math.min(waitMillis, untilDeadline));
    }

    private void executeOne(StressTaskRuntime runtime, StressRequestConfigSnapshot snapshot, long startNanos)
    {
        StressTask task = runtime.getTask();
        StressHttpResult result;
        try
        {
            StressHttpRequest request = renderer.render(snapshot, task.getPhone());
            // HTTP 超时不超过任务剩余时长，确保截止时刻附近读取能自行结束，
            // 即使 disconnect 较慢也不会长期占用工作线程和运行中手机号锁。
            request = limitTimeoutToRemainingTime(request, task);
            result = transport.execute(request, runtime);
            if (result == null)
            {
                result = StressHttpResult.failure("HTTP传输未返回结果");
            }
        }
        catch (Exception e)
        {
            if (e instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
                if (!runtime.isStopRequested())
                {
                    runtime.requestStop(StressStopReason.INTERRUPTED);
                }
            }
            String message = runtime.isCancelled() ? runtime.getCancelMessage() : safeErrorMessage(e);
            result = StressHttpResult.failure(message);
        }

        // 停止后即使请求迟到读完，也不能把结果记为成功
        if (runtime.isStopRequested())
        {
            result = StressHttpResult.failure(runtime.getCancelMessage());
        }

        long durationMs = TimeUnit.NANOSECONDS.toMillis(clock.nanoTime() - startNanos);
        if (durationMs < 0)
        {
            durationMs = 0;
        }
        if (runtime.isStopRequested())
        {
            result = StressHttpResult.failure(runtime.getCancelMessage());
        }
        try
        {
            responseService.recordResult(task.getTaskId(), snapshot, result, durationMs);
        }
        catch (Exception e)
        {
            log.error("压力测试结果落库失败，taskId={}, configId={}", task.getTaskId(), snapshot.getConfigId(), e);
            runtime.requestStop(StressStopReason.ERROR);
        }
    }

    private StressHttpRequest limitTimeoutToRemainingTime(StressHttpRequest request, StressTask task)
    {
        long remaining = task.getPlanEndTime().getTime() - clock.currentTimeMillis();
        long limited = remaining <= 0 ? 1L : Math.min((long) request.getTimeoutMs(), remaining);
        int timeoutMs = (int) Math.max(1L, limited);
        if (timeoutMs == request.getTimeoutMs())
        {
            return request;
        }
        return new StressHttpRequest(request.getUrl(), request.getMethod(), request.getHeaders(), request.getBody(),
                timeoutMs);
    }

    private String safeErrorMessage(Exception e)
    {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty())
        {
            message = e.getClass().getSimpleName();
        }
        if (message.length() > StressTaskResponseService.MAX_ERROR_CHARS)
        {
            message = message.substring(0, StressTaskResponseService.MAX_ERROR_CHARS);
        }
        return message;
    }

    private void finalizeRuntime(StressTaskRuntime runtime)
    {
        if (!runtime.markFinishStarted())
        {
            return;
        }
        Long taskId = runtime.getTask().getTaskId();
        long now = clock.currentTimeMillis();
        String status = resolveFinalStatus(runtime, now);
        try
        {
            taskMapper.finishTask(taskId, status, new Date(now));
        }
        catch (Exception e)
        {
            log.error("压力测试任务状态落库失败，taskId={}", taskId, e);
        }
        finally
        {
            // 截止定时器可能仍在等待，任务结束后必须移除，避免线程池积累无效任务
            runtime.cancelDeadline();
            runtimes.remove(taskId, runtime);
            runtime.signalFinished();
        }
    }

    private String resolveFinalStatus(StressTaskRuntime runtime, long now)
    {
        StressStopReason reason = runtime.getStopReason();
        if (reason == StressStopReason.MANUAL)
        {
            return StressTaskStatus.STOPPED;
        }
        if (reason == StressStopReason.ERROR)
        {
            return StressTaskStatus.FAILED;
        }
        if (reason == StressStopReason.SHUTDOWN || reason == StressStopReason.INTERRUPTED)
        {
            return StressTaskStatus.INTERRUPTED;
        }
        if (reason == StressStopReason.EXPIRED || now >= runtime.getTask().getPlanEndTime().getTime())
        {
            return StressTaskStatus.COMPLETED;
        }
        return StressTaskStatus.FAILED;
    }

    private void finishWithoutRuntime(Long taskId, String status)
    {
        try
        {
            taskMapper.finishTask(taskId, status, new Date(clock.currentTimeMillis()));
        }
        catch (Exception e)
        {
            log.error("压力测试任务状态落库失败，taskId={}", taskId, e);
        }
    }

    @PreDestroy
    public void shutdown()
    {
        shuttingDown = true;
        for (StressTaskRuntime runtime : runtimes.values())
        {
            runtime.requestStop(StressStopReason.SHUTDOWN);
        }
        workers.shutdownNow();
        timer.shutdownNow();
        cancelExecutor.shutdownNow();
        try
        {
            // 尽力清理数据库中的运行中标记，避免手机号唯一锁阻止重启后的提交
            taskMapper.markRunningTasksInterrupted(new Date(clock.currentTimeMillis()));
        }
        catch (Exception e)
        {
            log.error("应用关闭时标记压力测试任务中断失败", e);
        }
    }

    private static ThreadPoolExecutor createWorkers(int maxConcurrentTasks)
    {
        int max = Math.max(1, maxConcurrentTasks);
        return new ThreadPoolExecutor(0, max, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
                daemonThreadFactory("stress-worker"), new ThreadPoolExecutor.AbortPolicy());
    }

    private static ScheduledExecutorService createTimer()
    {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(1, daemonThreadFactory("stress-timer"));
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static ExecutorService createCancelExecutor()
    {
        // 有界、无排队：取消动作只做 disconnect 之类的短任务，慢任务最多占用少量线程，
        // 不会反过来阻塞定时器或其他任务的停止信号。
        return new ThreadPoolExecutor(0, 4, 30L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
                daemonThreadFactory("stress-cancel"), new ThreadPoolExecutor.AbortPolicy());
    }

    private static ThreadFactory daemonThreadFactory(final String prefix)
    {
        final AtomicInteger sequence = new AtomicInteger(0);
        return new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable runnable)
            {
                Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
    }
}
