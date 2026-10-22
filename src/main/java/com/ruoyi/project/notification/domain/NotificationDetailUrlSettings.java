package com.ruoyi.project.notification.domain;

/**
 * 统一详情页地址的当前视图。
 *
 * <p>只包含地址与来源，不包含主密钥、渠道 Secret 或任何票据。</p>
 *
 * @author weishanHub
 */
public class NotificationDetailUrlSettings
{
    /** 当前实际用于新通知的规范化地址；配置无效或未配置时为空 */
    private String effectiveUrl;

    /** 数据库参数中的原始值，前端输入框优先回填 */
    private String configuredValue;

    /** 私密配置 notification.public-base-url 的回退值 */
    private String fallbackUrl;

    /** 生效来源：DATABASE / CONFIG / NONE */
    private String source;

    /** 配置无效或未配置时的明确错误说明 */
    private String error;

    public String getEffectiveUrl()
    {
        return effectiveUrl;
    }

    public void setEffectiveUrl(String effectiveUrl)
    {
        this.effectiveUrl = effectiveUrl;
    }

    public String getConfiguredValue()
    {
        return configuredValue;
    }

    public void setConfiguredValue(String configuredValue)
    {
        this.configuredValue = configuredValue;
    }

    public String getFallbackUrl()
    {
        return fallbackUrl;
    }

    public void setFallbackUrl(String fallbackUrl)
    {
        this.fallbackUrl = fallbackUrl;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getError()
    {
        return error;
    }

    public void setError(String error)
    {
        this.error = error;
    }
}
