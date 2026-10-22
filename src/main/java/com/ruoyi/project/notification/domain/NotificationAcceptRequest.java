package com.ruoyi.project.notification.domain;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 对外开放的通知受理请求。
 *
 * @author weishanHub
 */
public class NotificationAcceptRequest
{
    /** 调用方幂等号，同一凭证下唯一 */
    @NotBlank(message = "requestId 不能为空")
    @Size(max = 128, message = "requestId 不能超过 128 个字符")
    private String requestId;

    /** 目标路径编码，命中路径全部发送 */
    @NotEmpty(message = "routeCodes 不能为空")
    @Size(max = 50, message = "routeCodes 最多 50 个")
    private List<String> routeCodes;

    /** TEXT 或 CARD */
    @NotBlank(message = "messageType 不能为空")
    @Pattern(regexp = "TEXT|CARD", message = "messageType 只能是 TEXT 或 CARD")
    private String messageType;

    @NotBlank(message = "title 不能为空")
    @Size(max = 200, message = "title 不能超过 200 个字符")
    private String title;

    @Size(max = 1000, message = "summary 不能超过 1000 个字符")
    private String summary;

    /** CARD 为 Markdown；TEXT 可作为文本正文 */
    @Size(max = 100000, message = "detail 不能超过 100000 个字符")
    private String detail;

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public List<String> getRouteCodes()
    {
        return routeCodes;
    }

    public void setRouteCodes(List<String> routeCodes)
    {
        this.routeCodes = routeCodes;
    }

    public String getMessageType()
    {
        return messageType;
    }

    public void setMessageType(String messageType)
    {
        this.messageType = messageType;
    }

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
}
