package com.ruoyi.project.notification.service;

import java.util.Date;
import java.util.Locale;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.domain.NotificationRecord;

/**
 * 公共详情授权规则（纯函数，便于测试）。
 *
 * <p>已明确接受 UA 可伪造的边界：这里只判断 UA 标识与数据库中的服务端时间，不做 OAuth、不识别身份。
 * 所有安全边界仍由票据哈希、sentAt、expiresAt、撤销/删除标记以及登录权限逐次检查保证。</p>
 *
 * @author weishanHub
 */
public final class NotificationAccessPolicy
{
    private NotificationAccessPolicy()
    {
    }

    /**
     * 微信个人号（MicroMessenger）与企业微信（wxwork）手机/桌面内置浏览器。
     */
    public static boolean isWeChatUserAgent(String userAgent)
    {
        if (StringUtils.isBlank(userAgent))
        {
            return false;
        }
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        return normalized.contains("micromessenger") || normalized.contains("wxwork");
    }

    /**
     * 服务端 now 严格早于 expiresAt 且至少有一个渠道发送成功时，才处于免登录窗口。
     */
    public static boolean withinPublicWindow(NotificationRecord record, Date now)
    {
        return record != null && record.getSentAt() != null && record.getExpiresAt() != null
                && now != null && now.before(record.getExpiresAt());
    }

    public static boolean isRevoked(NotificationRecord record)
    {
        return record != null && "1".equals(record.getRevoked());
    }

    public static boolean isDeleted(NotificationRecord record)
    {
        return record != null && "1".equals(record.getDeleted());
    }
}
