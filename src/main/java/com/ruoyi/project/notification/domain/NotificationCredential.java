package com.ruoyi.project.notification.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruoyi.framework.web.domain.BaseEntity;

/**
 * 通知调用凭证。库中只保存 API Key 的 SHA-256 哈希，列表不返回哈希或明文。
 *
 * @author weishanHub
 */
@JsonIgnoreProperties({ "keyHash" })
public class NotificationCredential extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long credentialId;

    private String credentialName;

    /** API Key 的 SHA-256 哈希，永不回显 */
    @JsonIgnore
    private String keyHash;

    /** 仅用于管理界面辨识的短前缀 */
    private String keyPrefix;

    /** 启用状态：0 启用，1 停用 */
    private String enabled;

    /** 最近一次调用时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastUsedTime;

    public Long getCredentialId()
    {
        return credentialId;
    }

    public void setCredentialId(Long credentialId)
    {
        this.credentialId = credentialId;
    }

    public String getCredentialName()
    {
        return credentialName;
    }

    public void setCredentialName(String credentialName)
    {
        this.credentialName = credentialName;
    }

    public String getKeyHash()
    {
        return keyHash;
    }

    public void setKeyHash(String keyHash)
    {
        this.keyHash = keyHash;
    }

    public String getKeyPrefix()
    {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix)
    {
        this.keyPrefix = keyPrefix;
    }

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
    }

    public Date getLastUsedTime()
    {
        return lastUsedTime;
    }

    public void setLastUsedTime(Date lastUsedTime)
    {
        this.lastUsedTime = lastUsedTime;
    }
}
