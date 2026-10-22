package com.ruoyi.project.notification.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 后台记录详情中的单个渠道结果（不含 CorpID、UserID、Secret 等配置快照）。
 *
 * @author weishanHub
 */
public class NotificationChannelResult
{
    private Long channelId;

    private String routeCode;

    private String channelName;

    private String provider;

    private String status;

    private Integer attemptCount;

    private String errorMessage;

    private String wecomMsgId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastAttemptTime;

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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getAttemptCount()
    {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount)
    {
        this.attemptCount = attemptCount;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getWecomMsgId()
    {
        return wecomMsgId;
    }

    public void setWecomMsgId(String wecomMsgId)
    {
        this.wecomMsgId = wecomMsgId;
    }

    public Date getLastAttemptTime()
    {
        return lastAttemptTime;
    }

    public void setLastAttemptTime(Date lastAttemptTime)
    {
        this.lastAttemptTime = lastAttemptTime;
    }
}
