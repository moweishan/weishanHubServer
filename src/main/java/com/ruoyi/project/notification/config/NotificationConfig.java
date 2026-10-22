package com.ruoyi.project.notification.config;

import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 通知中心专用基础设施。
 *
 * <p>只注册通知模块自己的时钟与有界发送线程池，不修改全局线程池、异步配置或框架版本。</p>
 *
 * @author weishanHub
 */
@Configuration
public class NotificationConfig
{
    /**
     * 通知模块专用时钟，便于测试过期边界。
     */
    @Bean(name = "notificationClock")
    public Clock notificationClock()
    {
        return Clock.systemDefaultZone();
    }

    /**
     * 通知发送线程池：小规模、有界队列，队列满时拒绝任务并保留待发送记录，等待下一轮扫描。
     */
    @Bean(name = "notificationSendExecutor")
    public ThreadPoolTaskExecutor notificationSendExecutor(NotificationProperties properties)
    {
        NotificationProperties.Dispatch dispatch = properties.getDispatch();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, dispatch.getCorePoolSize()));
        executor.setMaxPoolSize(Math.max(executor.getCorePoolSize(), dispatch.getMaxPoolSize()));
        executor.setQueueCapacity(Math.max(1, dispatch.getQueueCapacity()));
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("notification-send-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
