package com.ruoyi.project.notification.domain;

/**
 * 通知记录及渠道发送状态常量。
 *
 * @author weishanHub
 */
public final class NotificationStatus
{
    /** 待发送（尚未被线程领取） */
    public static final String PENDING = "PENDING";

    /** 发送中（已领取，进程重启后需人工确认） */
    public static final String SENDING = "SENDING";

    /** 全部渠道成功 */
    public static final String SUCCESS = "SUCCESS";

    /** 部分渠道成功、部分失败 */
    public static final String PARTIAL_SUCCESS = "PARTIAL_SUCCESS";

    /** 全部失败 */
    public static final String FAILED = "FAILED";

    private NotificationStatus()
    {
    }
}
