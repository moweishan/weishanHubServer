package com.ruoyi.project.tool.stress.domain;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 任务提交时的接口配置快照。
 *
 * <p>任务创建后只使用该快照执行，后续配置的修改、禁用或删除不会影响已有任务。</p>
 *
 * @author weishanHub
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StressRequestConfigSnapshot implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long configId;

    private String configName;

    private String requestUrl;

    private String httpMethod;

    private String headersJson;

    private String queryParamsJson;

    private String bodyType;

    private String bodyContent;

    private String contentType;

    private Integer timeoutMs;

    public static StressRequestConfigSnapshot from(StressRequestConfig config)
    {
        StressRequestConfigSnapshot snapshot = new StressRequestConfigSnapshot();
        snapshot.setConfigId(config.getConfigId());
        snapshot.setConfigName(config.getConfigName());
        snapshot.setRequestUrl(config.getRequestUrl());
        snapshot.setHttpMethod(config.getHttpMethod());
        snapshot.setHeadersJson(config.getHeadersJson());
        snapshot.setQueryParamsJson(config.getQueryParamsJson());
        snapshot.setBodyType(config.getBodyType());
        snapshot.setBodyContent(config.getBodyContent());
        snapshot.setContentType(config.getContentType());
        snapshot.setTimeoutMs(config.getTimeoutMs());
        return snapshot;
    }

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
}
