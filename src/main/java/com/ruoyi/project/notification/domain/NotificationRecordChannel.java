package com.ruoyi.project.notification.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 接单时的渠道配置快照。发送只读取此快照，后续渠道配置变化不影响已接单通知。
 *
 * @author weishanHub
 */
@JsonIgnoreProperties({ "corpId", "agentId", "toUser", "secretCipher", "claimToken" })
public class NotificationRecordChannel
{
    private Long channelId;

    private Long recordId;

    private String routeCode;

    private String channelName;

    private String provider;

    @JsonIgnore
    private String corpId;

    @JsonIgnore
    private Integer agentId;

    @JsonIgnore
    private String toUser;

    /** 接单时复制的加密 Secret，永不回显 */
    @JsonIgnore
    private String secretCipher;

    private String status;

    @JsonIgnore
    private String claimToken;

    private Integer attemptCount;

    private String errorMessage;

    private String wecomMsgId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastAttemptTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getChannelId()
    {
        return channelId;
    }

    public void setChannelId(Long channelId)
    {
        this.channelId = channelId;
    }

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
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

    public String getSecretCipher()
    {
        return secretCipher;
    }

    public void setSecretCipher(String secretCipher)
    {
        this.secretCipher = secretCipher;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getClaimToken()
    {
        return claimToken;
    }

    public void setClaimToken(String claimToken)
    {
        this.claimToken = claimToken;
    }

    public Integer getAttemptCount()
    {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount)
    {
        this.attemptCount = attemptCount;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getWecomMsgId()
    {
        return wecomMsgId;
    }

    public void setWecomMsgId(String wecomMsgId)
    {
        this.wecomMsgId = wecomMsgId;
    }

    public Date getLastAttemptTime()
    {
        return lastAttemptTime;
    }

    public void setLastAttemptTime(Date lastAttemptTime)
    {
        this.lastAttemptTime = lastAttemptTime;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
