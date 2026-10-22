package com.ruoyi.project.notification.domain;

/**
 * 调用凭证创建/重置结果。
 *
 * <p>apiKey 只在创建或重置的这一次响应中返回，不会落库明文、不会写入操作日志响应。</p>
 *
 * @author weishanHub
 */
public class NotificationCredentialCreated
{
    private Long credentialId;

    private String credentialName;

    private String keyPrefix;

    private String apiKey;

    private String warning = "API Key 仅显示这一次，请立即安全保存；关闭后无法再次查看。";

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

    public String getKeyPrefix()
    {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix)
    {
        this.keyPrefix = keyPrefix;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getWarning()
    {
        return warning;
    }

    public void setWarning(String warning)
    {
        this.warning = warning;
    }
}
