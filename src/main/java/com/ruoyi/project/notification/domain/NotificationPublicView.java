package com.ruoyi.project.notification.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 公共详情响应，只包含标题、正文与必要时间，绝不包含渠道、凭据、内部错误或请求方信息。
 *
 * @author weishanHub
 */
public class NotificationPublicView
{
    private String title;

    private String summary;

    private String detail;

    private String messageType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date sentAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expiresAt;

    /** 服务端当前时间，供前端校正倒计时 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date serverTime;

    /** 是否已超过免登录窗口 */
    private boolean expired;

    /** WECHAT：微信/企微 UA 免登录；LOGIN：已登录且有通知记录查询权限 */
    private String accessMode;

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public String getDetail()
    {
        return detail;
    }

    public void setDetail(String detail)
    {
        this.detail = detail;
    }

    public String getMessageType()
    {
        return messageType;
    }

    public void setMessageType(String messageType)
    {
        this.messageType = messageType;
    }

    public Date getSentAt()
    {
        return sentAt;
    }

    public void setSentAt(Date sentAt)
    {
        this.sentAt = sentAt;
    }

    public Date getExpiresAt()
    {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt)
    {
        this.expiresAt = expiresAt;
    }

    public Date getServerTime()
    {
        return serverTime;
    }

    public void setServerTime(Date serverTime)
    {
        this.serverTime = serverTime;
    }

    public boolean isExpired()
    {
        return expired;
    }

    public void setExpired(boolean expired)
    {
        this.expired = expired;
    }

    public String getAccessMode()
    {
        return accessMode;
    }

    public void setAccessMode(String accessMode)
    {
        this.accessMode = accessMode;
    }
}
