package com.ruoyi.project.tool.stress.service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.tool.stress.domain.StressProbeProgress;
import com.ruoyi.project.tool.stress.domain.StressProbeRequest;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;
import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;
import com.ruoyi.project.tool.stress.mapper.StressRequestConfigMapper;
import com.ruoyi.project.tool.stress.runner.StressCancellation;
import com.ruoyi.project.tool.stress.runner.StressHttpRequest;
import com.ruoyi.project.tool.stress.runner.StressHttpResult;
import com.ruoyi.project.tool.stress.runner.StressHttpTransport;

/**
 * 请求配置批量试活。
 *
 * <p>按查询条件或勾选的接口逐个发起一次真实请求，用固定的示例手机号替换 {@code {{phone}}}：
 * HTTP 2xx 记为有效（alive），非 2xx 或网络异常记为失效（dead），结果回写
 * {@code probe_status / probe_time / probe_message}。试活在后台线程执行，前端轮询进度。</p>
 *
 * @author weishanHub
 */
@Service
public class StressProbeService
{
    private static final Logger log = LoggerFactory.getLogger(StressProbeService.class);

    /**
     * 试活使用的默认手机号。
     *
     * <p>号码规划里移动用户号码为 1 开头、第二位 3-9，因此“10 开头 + 11 位”的号码永远不可能分配给个人用户，
     * 短信网关按号段路由时无法投递，保证试活不会真的给机主发短信。</p>
     */
    public static final String DEFAULT_PROBE_PHONE = "10000000000";

    /** 真实可投递的大陆手机号格式，用于拦截把真人号码当试活号码 */
    private static final Pattern LIVE_PHONE_PATTERN = Pattern.compile("^1[3-9][0-9]{9}$");

    /** probe_message 字段长度上限 */
    private static final int MAX_MESSAGE_CHARS = 500;

    /** 响应文本摘要长度，避免把整段页面写进说明 */
    private static final int MESSAGE_SNIPPET_CHARS = 120;

    private final StressRequestConfigMapper configMapper;

    private final StressTemplateRenderer renderer;

    private final StressHttpTransport transport;

    /** 试活并发线程池：同一时刻只跑一个试活任务，任务内多线程并行探测 */
    private final ExecutorService probeWorkers;

    /** 试活调度线程，负责提交任务并等待收尾 */
    private final ExecutorService dispatcher;

    private final int probeThreads;

    /** 试活替换 {{phone}} 使用的手机号 */
    private final String probePhone;

    /** 是否允许把真实可投递号码用于试活（默认禁止） */
    private final boolean allowLivePhone;

    private final Object runLock = new Object();

    private volatile RunState current = RunState.idle();

    @Autowired
    public StressProbeService(StressRequestConfigMapper configMapper, StressTemplateRenderer renderer,
            StressHttpTransport transport, @Value("${stress.probe-threads:4}") int probeThreads,
            @Value("${stress.probe-phone:" + DEFAULT_PROBE_PHONE + "}") String probePhone,
            @Value("${stress.probe-phone-allow-live:false}") boolean allowLivePhone)
    {
        this(configMapper, renderer, transport, probeThreads,
                createProbeWorkers(probeThreads), Executors.newSingleThreadExecutor(
                        daemonFactory("stress-probe-dispatch")), probePhone, allowLivePhone);
    }

    /**
     * 测试用构造器，可注入自定义执行器；试活手机号使用默认的不可投递号码。
     */
    StressProbeService(StressRequestConfigMapper configMapper, StressTemplateRenderer renderer,
            StressHttpTransport transport, int probeThreads, ExecutorService probeWorkers,
            ExecutorService dispatcher)
    {
        this(configMapper, renderer, transport, probeThreads, probeWorkers, dispatcher,
                DEFAULT_PROBE_PHONE, false);
    }

    /**
     * 测试用构造器，可指定试活手机号与是否允许真号。
     */
    StressProbeService(StressRequestConfigMapper configMapper, StressTemplateRenderer renderer,
            StressHttpTransport transport, int probeThreads, ExecutorService probeWorkers,
            ExecutorService dispatcher, String probePhone, boolean allowLivePhone)
    {
        this.configMapper = configMapper;
        this.renderer = renderer;
        this.transport = transport;
        this.probeThreads = Math.max(1, probeThreads);
        this.probeWorkers = probeWorkers;
        this.dispatcher = dispatcher;
        this.probePhone = probePhone == null ? "" : probePhone.trim();
        this.allowLivePhone = allowLivePhone;
    }

    /**
     * 启动一次批量试活，立即返回初始进度。
     *
     * @param request 目标筛选条件；configIds 为空时按 configName / enabled / probeStatus 过滤
     */
    public StressProbeProgress start(StressProbeRequest request)
    {
        validateProbePhone();
        StressProbeRequest condition = request == null ? new StressProbeRequest() : request;
        StressRequestConfig query = new StressRequestConfig();
        query.setConfigName(trimToNull(condition.getConfigName()));
        query.setEnabled(trimToNull(condition.getEnabled()));
        query.setProbeStatus(trimToNull(condition.getProbeStatus()));

        List<StressRequestConfig> targets;
        try
        {
            targets = configMapper.selectProbeConfigs(query, condition.getConfigIds());
        }
        catch (Exception e)
        {
            log.error("查询试活目标失败", e);
            throw new ServiceException("查询试活目标失败：" + e.getMessage());
        }
        if (targets == null || targets.isEmpty())
        {
            throw new ServiceException("没有符合条件的请求配置可试活");
        }

        final RunState state;
        synchronized (runLock)
        {
            if (current.isRunning())
            {
                throw new ServiceException("已有试活任务在执行中，请等待完成或先停止");
            }
            state = new RunState(targets.size());
            current = state;
        }

        dispatcher.execute(new Runnable()
        {
            @Override
            public void run()
            {
                runAll(targets, state);
            }
        });
        return snapshot();
    }

    /**
     * 当前（或最近一次）试活进度。
     */
    public StressProbeProgress progress()
    {
        return snapshot();
    }

    private StressProbeProgress snapshot()
    {
        StressProbeProgress progress = current.toProgress();
        progress.setSamplePhone(probePhone);
        return progress;
    }

    /**
     * 试活手机号防呆：默认使用不可能分配给个人用户的号码；
     * 若配置成真实可投递号码，必须显式开启 {@code stress.probe-phone-allow-live} 才允许执行。
     */
    private void validateProbePhone()
    {
        if (probePhone.isEmpty())
        {
            throw new ServiceException("试活手机号未配置，请在 stress.probe-phone 中配置一个不可投递的号码");
        }
        if (LIVE_PHONE_PATTERN.matcher(probePhone).matches())
        {
            if (!allowLivePhone)
            {
                throw new ServiceException("试活手机号 " + probePhone + " 是真实可投递号码，试活会向机主发送短信；"
                        + "请改用不可开通的号码（默认 " + DEFAULT_PROBE_PHONE + "），"
                        + "或显式设置 stress.probe-phone-allow-live=true");
            }
            log.warn("试活使用真实可投递手机号 {}，会对机主发送短信，请确认已获授权", probePhone);
        }
    }

    /**
     * 请求停止；已在执行的请求会自然结束，未开始的接口记入跳过。
     */
    public boolean stop()
    {
        RunState state = current;
        if (state == null || !state.isRunning())
        {
            return false;
        }
        state.stopRequested.set(true);
        return true;
    }

    private void runAll(List<StressRequestConfig> targets, RunState state)
    {
        final CountDownLatch latch = new CountDownLatch(targets.size());
        try
        {
            for (final StressRequestConfig config : targets)
            {
                if (state.stopRequested.get())
                {
                    finishOne(state, null, null, true, latch);
                    continue;
                }
                probeWorkers.execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        probeOne(state, config, latch);
                    }
                });
            }
            // 等待全部探测线程收尾；已受理的请求受各自超时约束，不会无限阻塞
            while (true)
            {
                try
                {
                    if (latch.await(200L, TimeUnit.MILLISECONDS))
                    {
                        break;
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    state.stopRequested.set(true);
                    break;
                }
            }
        }
        catch (RuntimeException e)
        {
            log.error("试活任务调度异常", e);
        }
        finally
        {
            state.markFinished();
        }
    }

    private void probeOne(RunState state, StressRequestConfig config, CountDownLatch latch)
    {
        if (state.stopRequested.get())
        {
            finishOne(state, null, null, true, latch);
            return;
        }
        state.setCurrent(config.getConfigName());

        String status;
        String message;
        try
        {
            StressRequestConfigSnapshot snapshot = StressRequestConfigSnapshot.from(config);
            StressHttpRequest request = renderer.render(snapshot, probePhone);
            StressHttpResult result = transport.execute(request, state.cancellation());
            if (result != null && result.isSuccess())
            {
                status = StressRequestConfig.PROBE_ALIVE;
                message = "HTTP " + result.getStatusCode() + " 有效";
            }
            else
            {
                status = StressRequestConfig.PROBE_DEAD;
                message = describeFailure(result);
            }
        }
        catch (Exception e)
        {
            status = StressRequestConfig.PROBE_DEAD;
            message = "请求异常：" + messageOf(e);
        }

        try
        {
            configMapper.updateProbeResult(config.getConfigId(), status, new Date(), truncate(message));
        }
        catch (Exception e)
        {
            log.error("试活结果回写失败，configId={}", config.getConfigId(), e);
        }
        finishOne(state, status, message, false, latch);
    }

    private void finishOne(RunState state, String status, String message, boolean skipped, CountDownLatch latch)
    {
        if (skipped)
        {
            state.skipped.incrementAndGet();
        }
        else if (StressRequestConfig.PROBE_ALIVE.equals(status))
        {
            state.alive.incrementAndGet();
        }
        else if (StressRequestConfig.PROBE_DEAD.equals(status))
        {
            state.dead.incrementAndGet();
        }
        state.done.incrementAndGet();
        latch.countDown();
    }

    /**
     * 失效原因：网络异常给出错误信息，非 2xx 给出状态码与响应摘要。
     */
    private String describeFailure(StressHttpResult result)
    {
        if (result == null)
        {
            return "无响应";
        }
        if (result.getStatusCode() != null)
        {
            StringBuilder builder = new StringBuilder("HTTP ").append(result.getStatusCode()).append(" 失效");
            if (result.getStatusCode() >= 300 && result.getStatusCode() < 400)
            {
                builder.append("（跳转地址无效）");
            }
            String text = result.getResponseText();
            if (text != null && !text.trim().isEmpty())
            {
                builder.append("：").append(snippet(text));
            }
            return builder.toString();
        }
        String error = result.getErrorMessage();
        return error == null || error.trim().isEmpty() ? "请求失败" : "请求异常：" + error;
    }

    private String snippet(String text)
    {
        String normalised = text.replaceAll("\\s+", " ").trim();
        if (normalised.length() <= MESSAGE_SNIPPET_CHARS)
        {
            return normalised;
        }
        return normalised.substring(0, MESSAGE_SNIPPET_CHARS) + "…";
    }

    private String messageOf(Exception e)
    {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty())
        {
            message = e.getClass().getSimpleName();
        }
        return message;
    }

    private String truncate(String value)
    {
        if (value == null || value.length() <= MAX_MESSAGE_CHARS)
        {
            return value;
        }
        return value.substring(0, MAX_MESSAGE_CHARS);
    }

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @PreDestroy
    public void shutdown()
    {
        RunState state = current;
        if (state != null)
        {
            state.stopRequested.set(true);
        }
        dispatcher.shutdownNow();
        probeWorkers.shutdownNow();
    }

    private static ExecutorService createProbeWorkers(int probeThreads)
    {
        int max = Math.max(1, probeThreads);
        return new ThreadPoolExecutor(max, max, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
                daemonFactory("stress-probe"), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private static ThreadFactory daemonFactory(final String prefix)
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

    /**
     * 一次试活运行的进度计数器。
     */
    private static final class RunState
    {
        private final int total;

        private final AtomicInteger done = new AtomicInteger(0);

        private final AtomicInteger alive = new AtomicInteger(0);

        private final AtomicInteger dead = new AtomicInteger(0);

        private final AtomicInteger skipped = new AtomicInteger(0);

        private final AtomicBoolean stopRequested = new AtomicBoolean(false);

        private final Date startTime = new Date();

        private volatile Date finishTime;

        private volatile String current = "";

        private RunState(int total)
        {
            this.total = total;
        }

        private static RunState idle()
        {
            RunState state = new RunState(0);
            state.markFinished();
            return state;
        }

        private boolean isRunning()
        {
            return finishTime == null;
        }

        private void setCurrent(String name)
        {
            this.current = name == null ? "" : name;
        }

        private void markFinished()
        {
            this.finishTime = new Date();
        }

        private StressCancellation cancellation()
        {
            final AtomicBoolean flag = stopRequested;
            return new StressCancellation()
            {
                @Override
                public boolean isCancelled()
                {
                    return flag.get();
                }

                @Override
                public void onCancel(Runnable action)
                {
                    // 试活不做主动 disconnect：单次请求已由接口超时约束，停止后不再发起新请求即可
                }

                @Override
                public void removeCancelAction(Runnable action)
                {
                    // 无需清理
                }
            };
        }

        private StressProbeProgress toProgress()
        {
            StressProbeProgress progress = new StressProbeProgress();
            progress.setRunning(isRunning());
            progress.setStopRequested(stopRequested.get());
            progress.setTotal(total);
            progress.setDone(done.get());
            progress.setAlive(alive.get());
            progress.setDead(dead.get());
            progress.setSkipped(skipped.get());
            progress.setCurrent(current);
            progress.setStartTime(startTime);
            progress.setFinishTime(finishTime);
            return progress;
        }
    }
}
