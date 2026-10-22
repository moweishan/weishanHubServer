package com.ruoyi.project.notification.domain;

/**
 * 渠道状态计数，用于汇总父记录状态。
 *
 * @author weishanHub
 */
public class NotificationChannelCounts
{
    private int pendingCount;

    private int sendingCount;

    private int successCount;

    private int failedCount;

    public int getPendingCount()
    {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount)
    {
        this.pendingCount = pendingCount;
    }

    public int getSendingCount()
    {
        return sendingCount;
    }

    public void setSendingCount(int sendingCount)
    {
        this.sendingCount = sendingCount;
    }

    public int getSuccessCount()
    {
        return successCount;
    }

    public void setSuccessCount(int successCount)
    {
        this.successCount = successCount;
    }

    public int getFailedCount()
    {
        return failedCount;
    }

    public void setFailedCount(int failedCount)
    {
        this.failedCount = failedCount;
    }

    public int getTotalCount()
    {
        return pendingCount + sendingCount + successCount + failedCount;
    }
}
