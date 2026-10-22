package com.ruoyi.project.notification.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.ruoyi.project.notification.config.NotificationProperties;

/**
 * 通知发送扫描线程。
 *
 * <p>使用通知模块自己的单线程守护调度器，不开启全局 @EnableScheduling，也不影响框架任务。
 * 扫描异常只记录限频日志，不阻断应用启动。</p>
 *
 * @author weishanHub
 */
@Component
@ConditionalOnProperty(prefix = "notification.dispatch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDispatchScheduler
{
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchScheduler.class);

    private final NotificationDispatchService dispatchService;

    private final NotificationProperties properties;

    private ScheduledExecutorService scheduler;

    private volatile long lastErrorLogTime;

    public NotificationDispatchScheduler(NotificationDispatchService dispatchService,
            NotificationProperties properties)
    {
        this.dispatchService = dispatchService;
        this.properties = properties;
    }

    @PostConstruct
    public void start()
    {
        long interval = Math.max(500L, properties.getDispatch().getScanIntervalMs());
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "notification-dispatch-scan");
            thread.setDaemon(true);
            return thread;
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
        // 延迟启动，给启动恢复留出把上次 SENDING 标记为失败的时间
        scheduler.scheduleWithFixedDelay(this::safeScan, 3000L, interval, TimeUnit.MILLISECONDS);
    }

    private void safeScan()
    {
        try
        {
            dispatchService.scanPending();
        }
        catch (Exception e)
        {
            long now = System.currentTimeMillis();
            // 数据库尚未迁移/短暂不可用时避免每 2 秒刷一次日志
            if (now - lastErrorLogTime > 60000L)
            {
                lastErrorLogTime = now;
                log.warn("通知发送扫描失败：{}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void stop()
    {
        if (scheduler != null)
        {
            scheduler.shutdownNow();
        }
    }
}
