package com.ruoyi.project.notification.domain;

/**
 * 通知渠道下拉选项（不含任何凭据）。
 *
 * @author weishanHub
 */
public class NotificationChannelOption
{
    private Long channelId;

    private String routeCode;

    private String channelName;

    private String provider;

    public Long getChannelId()
    {
        return channelId;
    }

    public void setChannelId(Long channelId)
    {
        this.channelId = channelId;
    }

    public String getRouteCode()
    {
        return routeCode;
    }

    public void setRouteCode(String routeCode)
    {
        this.routeCode = routeCode;
    }

    public String getChannelName()
    {
        return channelName;
    }

    public void setChannelName(String channelName)
    {
        this.channelName = channelName;
    }

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }
}
