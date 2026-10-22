package com.ruoyi.project.notification.domain;

import com.ruoyi.common.constant.HttpStatus;

/**
 * 公共详情授权结果。控制器负责转换成若依 AjaxResult。
 *
 * @author weishanHub
 */
public class NotificationDetailResult
{
    private final int code;

    private final String message;

    private final NotificationPublicView data;

    private NotificationDetailResult(int code, String message, NotificationPublicView data)
    {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static NotificationDetailResult ok(NotificationPublicView data)
    {
        return new NotificationDetailResult(HttpStatus.SUCCESS, "操作成功", data);
    }

    public static NotificationDetailResult error(int code, String message)
    {
        return new NotificationDetailResult(code, message, null);
    }

    public int getCode()
    {
        return code;
    }

    public String getMessage()
    {
        return message;
    }

    public NotificationPublicView getData()
    {
        return data;
    }

    public boolean isSuccess()
    {
        return code == HttpStatus.SUCCESS;
    }
}
