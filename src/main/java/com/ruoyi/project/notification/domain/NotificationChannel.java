package com.ruoyi.project.notification.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.domain.BaseEntity;

/**
 * 通知渠道配置。
 *
 * <p>Secret 只保存 AES-GCM 密文，且永远不会通过 JSON 回显；列表只暴露 secretConfigured。</p>
 *
 * @author weishanHub
 */
@JsonIgnoreProperties({ "secretCipher" })
public class NotificationChannel extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 渠道主键 */
    private Long channelId;

    /** 唯一路径编码 */
    private String routeCode;

    /** 渠道展示名 */
    private String channelName;

    /** 发送提供方，目前为 WECOM_APP，保留扩展 */
    private String provider;

    /** 企业微信 CorpID */
    private String corpId;

    /** 企业微信应用 AgentID */
    private Integer agentId;

    /** 接收成员 UserID，多个用 | 分隔 */
    private String toUser;

    /** 启用状态：0 启用，1 停用 */
    private String enabled;

    /** 加密后的 Secret */
    @JsonIgnore
    private String secretCipher;

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

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
    }

    public String getSecretCipher()
    {
        return secretCipher;
    }

    public void setSecretCipher(String secretCipher)
    {
        this.secretCipher = secretCipher;
    }

    /**
     * 是否已配置 Secret。只返回布尔值，不返回密文或明文。
     */
    public boolean isSecretConfigured()
    {
        return StringUtils.isNotBlank(secretCipher);
    }
}
