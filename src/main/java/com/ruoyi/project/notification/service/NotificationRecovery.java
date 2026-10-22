package com.ruoyi.project.notification.service;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 应用启动恢复：把上次进程遗留的 SENDING 渠道标记为失败并说明结果未知。
 *
 * <p>此处绝不自动重新发送，避免企业微信已接受但本地未落库时重复轰炸。
 * 数据库尚未迁移或暂时不可用只记录告警，不阻断应用启动。</p>
 *
 * @author weishanHub
 */
@Component
public class NotificationRecovery
{
    private static final Logger log = LoggerFactory.getLogger(NotificationRecovery.class);

    private final NotificationDispatchService dispatchService;

    public NotificationRecovery(NotificationDispatchService dispatchService)
    {
        this.dispatchService = dispatchService;
    }

    @PostConstruct
    public void recover()
    {
        try
        {
            int count = dispatchService.recoverUnknownSending();
            if (count > 0)
            {
                log.warn("通知启动恢复：已将 {} 个发送中渠道标记为失败（结果未知），请人工确认后重试", count);
            }
        }
        catch (Exception e)
        {
            log.warn("通知启动恢复未完成（数据库可能尚未迁移）：{}", e.getMessage());
        }
    }
}
