package com.ruoyi.project.tool.stress.domain;

import com.ruoyi.framework.web.domain.BaseEntity;

/**
 * 压力测试请求配置 tool_stress_request_config
 *
 * @author weishanHub
 */
public class StressRequestConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 试活状态：未测 */
    public static final String PROBE_UNTESTED = "untested";

    /** 试活状态：有效（HTTP 2xx） */
    public static final String PROBE_ALIVE = "alive";

    /** 试活状态：失效（非 2xx 或网络异常） */
    public static final String PROBE_DEAD = "dead";

    /** 配置ID */
    private Long configId;

    /** 接口名称 */
    private String configName;

    /** 请求地址，支持 {{phone}} 占位符 */
    private String requestUrl;

    /** HTTP 方法 */
    private String httpMethod;

    /** 请求头 JSON 对象 */
    private String headersJson;

    /** Query 参数 JSON 对象 */
    private String queryParamsJson;

    /** 请求体类型：JSON / FORM / RAW */
    private String bodyType;

    /** 请求体内容 */
    private String bodyContent;

    /** Content-Type，为空时按 bodyType 使用默认值 */
    private String contentType;

    /** 请求超时，单位毫秒 */
    private Integer timeoutMs;

    /** 是否启用：0 启用，1 停用 */
    private String enabled;

    /** 试活状态：untested 未测 / alive 有效 / dead 失效 */
    private String probeStatus;

    /** 最后一次试活时间 */
    private java.util.Date probeTime;

    /** 试活结果说明：HTTP 状态码或错误信息 */
    private String probeMessage;

    public Long getConfigId()
    {
        return configId;
    }

    public void setConfigId(Long configId)
    {
        this.configId = configId;
    }

    public String getConfigName()
    {
        return configName;
    }

    public void setConfigName(String configName)
    {
        this.configName = configName;
    }

    public String getRequestUrl()
    {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl)
    {
        this.requestUrl = requestUrl;
    }

    public String getHttpMethod()
    {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod)
    {
        this.httpMethod = httpMethod;
    }

    public String getHeadersJson()
    {
        return headersJson;
    }

    public void setHeadersJson(String headersJson)
    {
        this.headersJson = headersJson;
    }

    public String getQueryParamsJson()
    {
        return queryParamsJson;
    }

    public void setQueryParamsJson(String queryParamsJson)
    {
        this.queryParamsJson = queryParamsJson;
    }

    public String getBodyType()
    {
        return bodyType;
    }

    public void setBodyType(String bodyType)
    {
        this.bodyType = bodyType;
    }

    public String getBodyContent()
    {
        return bodyContent;
    }

    public void setBodyContent(String bodyContent)
    {
        this.bodyContent = bodyContent;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public Integer getTimeoutMs()
    {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs)
    {
        this.timeoutMs = timeoutMs;
    }

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
    }

    public String getProbeStatus()
    {
        return probeStatus;
    }

    public void setProbeStatus(String probeStatus)
    {
        this.probeStatus = probeStatus;
    }

    public java.util.Date getProbeTime()
    {
        return probeTime;
    }

    public void setProbeTime(java.util.Date probeTime)
    {
        this.probeTime = probeTime;
    }

    public String getProbeMessage()
    {
        return probeMessage;
    }

    public void setProbeMessage(String probeMessage)
    {
        this.probeMessage = probeMessage;
    }
}
