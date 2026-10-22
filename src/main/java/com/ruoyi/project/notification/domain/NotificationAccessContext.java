package com.ruoyi.project.notification.domain;

/**
 * 公共详情请求的访问上下文，由控制器根据当前 Spring Security 上下文与 UA 构造，
 * 便于服务层在不依赖 Web 上下文的情况下做单元测试。
 *
 * @author weishanHub
 */
public class NotificationAccessContext
{
    private final boolean authenticated;

    private final boolean hasQueryPermission;

    private final String userAgent;

    public NotificationAccessContext(boolean authenticated, boolean hasQueryPermission, String userAgent)
    {
        this.authenticated = authenticated;
        this.hasQueryPermission = hasQueryPermission;
        this.userAgent = userAgent;
    }

    public boolean isAuthenticated()
    {
        return authenticated;
    }

    public boolean isHasQueryPermission()
    {
        return hasQueryPermission;
    }

    public String getUserAgent()
    {
        return userAgent;
    }
}
