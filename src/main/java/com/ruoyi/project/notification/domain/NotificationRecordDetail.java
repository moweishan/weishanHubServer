package com.ruoyi.project.notification.domain;

import java.util.List;

/**
 * 后台通知记录详情。
 *
 * @author weishanHub
 */
public class NotificationRecordDetail
{
    private NotificationRecord record;

    private List<NotificationChannelResult> channels;

    /** 有权限的后台用户可复制详情链接；不包含票据以外的渠道凭据 */
    private String detailUrl;

    public NotificationRecord getRecord()
    {
        return record;
    }

    public void setRecord(NotificationRecord record)
    {
        this.record = record;
    }

    public List<NotificationChannelResult> getChannels()
    {
        return channels;
    }

    public void setChannels(List<NotificationChannelResult> channels)
    {
        this.channels = channels;
    }

    public String getDetailUrl()
    {
        return detailUrl;
    }

    public void setDetailUrl(String detailUrl)
    {
        this.detailUrl = detailUrl;
    }
}
