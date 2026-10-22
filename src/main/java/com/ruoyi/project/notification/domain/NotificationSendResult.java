package com.ruoyi.project.notification.domain;

/**
 * 单个渠道的发送结果。平台接受即成功，不要求已读；接收人全部无效时不误报成功。
 *
 * @author weishanHub
 */
public class NotificationSendResult
{
    private final boolean success;

    private final String messageId;

    private final String errorMessage;

    private NotificationSendResult(boolean success, String messageId, String errorMessage)
    {
        this.success = success;
        this.messageId = messageId;
        this.errorMessage = errorMessage;
    }

    public static NotificationSendResult success(String messageId)
    {
        return new NotificationSendResult(true, messageId, null);
    }

    public static NotificationSendResult failure(String errorMessage)
    {
        return new NotificationSendResult(false, null, errorMessage);
    }

    public boolean isSuccess()
    {
        return success;
    }

    public String getMessageId()
    {
        return messageId;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }
}
