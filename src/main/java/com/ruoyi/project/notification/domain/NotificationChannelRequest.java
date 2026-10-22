package com.ruoyi.project.notification.domain;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 通知渠道新增/修改请求。
 *
 * <p>secret 只允许写入，不会出现在响应与列表中；修改时留空表示保持原 Secret。</p>
 *
 * @author weishanHub
 */
public class NotificationChannelRequest
{
    @NotBlank(message = "routeCode 不能为空")
    @Size(max = 64, message = "routeCode 不能超过 64 个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "routeCode 只能包含字母、数字、下划线和中划线")
    private String routeCode;

    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 100, message = "渠道名称不能超过 100 个字符")
    private String channelName;

    @Size(max = 32, message = "provider 不能超过 32 个字符")
    private String provider = "WECOM_APP";

    @NotBlank(message = "CorpID 不能为空")
    @Size(max = 128, message = "CorpID 不能超过 128 个字符")
    private String corpId;

    @NotNull(message = "AgentID 不能为空")
    @Min(value = 1, message = "AgentID 必须大于 0")
    @Max(value = 2147483647, message = "AgentID 超出范围")
    private Integer agentId;

    @NotBlank(message = "接收 UserID 不能为空")
    @Size(max = 1000, message = "接收 UserID 不能超过 1000 个字符")
    private String toUser;

    @Size(max = 2048, message = "Secret 不能超过 2048 个字符")
    private String secret;

    @Pattern(regexp = "[01]", message = "启用状态只能是 0 或 1")
    private String enabled = "0";

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

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

    public String getCorpId()
    {
        return corpId;
    }

    public void setCorpId(String corpId)
    {
        this.corpId = corpId;
    }

    public Integer getAgentId()
    {
        return agentId;
    }

    public void setAgentId(Integer agentId)
    {
        this.agentId = agentId;
    }

    public String getToUser()
    {
        return toUser;
    }

    public void setToUser(String toUser)
    {
        this.toUser = toUser;
    }

    public String getSecret()
    {
        return secret;
    }

    public void setSecret(String secret)
    {
        this.secret = secret;
    }

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
