package com.ruoyi.project.tool.stress.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 压力测试请求结果 tool_stress_response
 *
 * @author weishanHub
 */
public class StressTaskResponse
{
    /** 结果ID */
    private Long responseId;

    /** 任务ID */
    private Long taskId;

    /** 接口配置ID（提交时快照中的ID） */
    private Long configId;

    /** 接口名称 */
    private String configName;

    /** 请求地址 */
    private String requestUrl;

    /** HTTP 方法 */
    private String httpMethod;

    /** HTTP 状态码，网络异常时为空 */
    private Integer statusCode;

    /** 耗时，单位毫秒 */
    private Long durationMs;

    /** 是否成功：1 成功，0 失败 */
    private String success;

    /** 响应文本（有上限） */
    private String responseText;

    /** 错误信息（网络异常等） */
    private String errorMessage;

    /** 响应是否被截断：1 是，0 否 */
    private String truncated;

    /** 记录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getResponseId()
    {
        return responseId;
    }

    public void setResponseId(Long responseId)
    {
        this.responseId = responseId;
    }

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
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

    public Integer getStatusCode()
    {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode)
    {
        this.statusCode = statusCode;
    }

    public Long getDurationMs()
    {
        return durationMs;
    }

    public void setDurationMs(Long durationMs)
    {
        this.durationMs = durationMs;
    }

    public String getSuccess()
    {
        return success;
    }

    public void setSuccess(String success)
    {
        this.success = success;
    }

    public String getResponseText()
    {
        return responseText;
    }

    public void setResponseText(String responseText)
    {
        this.responseText = responseText;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getTruncated()
    {
        return truncated;
    }

    public void setTruncated(String truncated)
    {
        this.truncated = truncated;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
