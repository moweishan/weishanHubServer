package com.ruoyi.project.notification.domain;

/**
 * 通知受理响应。
 *
 * @author weishanHub
 */
public class NotificationAcceptResponse
{
    private Long recordId;

    private String requestId;

    /** true 表示幂等重复调用，未重复发送 */
    private boolean duplicate;

    /** 详情链接；重复调用时在可在主密钥可用时恢复同一链接 */
    private String detailUrl;

    private String status;

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public boolean isDuplicate()
    {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate)
    {
        this.duplicate = duplicate;
    }

    public String getDetailUrl()
    {
        return detailUrl;
    }

    public void setDetailUrl(String detailUrl)
    {
        this.detailUrl = detailUrl;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
