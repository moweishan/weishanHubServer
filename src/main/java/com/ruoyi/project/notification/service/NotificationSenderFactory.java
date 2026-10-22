package com.ruoyi.project.notification.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 按 provider 选择发送实现。
 *
 * @author weishanHub
 */
@Component
public class NotificationSenderFactory
{
    private final Map<String, NotificationSender> senders;

    public NotificationSenderFactory(List<NotificationSender> senderList)
    {
        Map<String, NotificationSender> map = new HashMap<>();
        if (senderList != null)
        {
            for (NotificationSender sender : senderList)
            {
                map.put(sender.provider().toUpperCase(Locale.ROOT), sender);
            }
        }
        this.senders = Collections.unmodifiableMap(map);
    }

    public boolean supports(String provider)
    {
        return StringUtils.isNotBlank(provider)
                && senders.containsKey(provider.toUpperCase(Locale.ROOT));
    }

    public NotificationSender get(String provider)
    {
        NotificationSender sender = StringUtils.isBlank(provider) ? null
                : senders.get(provider.toUpperCase(Locale.ROOT));
        if (sender == null)
        {
            throw new ServiceException("不支持的通知渠道类型：" + provider, 400);
        }
        return sender;
    }
}
