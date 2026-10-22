package com.ruoyi.project.tool.stress.runner;

/**
 * 一次 HTTP 请求的结果。
 *
 * <p>HTTP 200-299 计为成功；非 2xx 仍然记录状态码和响应文本，但不视为成功。
 * 网络异常时 statusCode 为空并记录 errorMessage。</p>
 *
 * @author weishanHub
 */
public class StressHttpResult
{
    private final Integer statusCode;

    private final String responseText;

    private final String errorMessage;

    private final boolean truncated;

    private StressHttpResult(Integer statusCode, String responseText, String errorMessage, boolean truncated)
    {
        this.statusCode = statusCode;
        this.responseText = responseText;
        this.errorMessage = errorMessage;
        this.truncated = truncated;
    }

    public static StressHttpResult response(int statusCode, String responseText, boolean truncated)
    {
        return new StressHttpResult(statusCode, responseText, null, truncated);
    }

    public static StressHttpResult failure(String errorMessage)
    {
        return new StressHttpResult(null, null, errorMessage, false);
    }

    public boolean isSuccess()
    {
        return statusCode != null && statusCode >= 200 && statusCode <= 299;
    }

    public Integer getStatusCode()
    {
        return statusCode;
    }

    public String getResponseText()
    {
        return responseText;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public boolean isTruncated()
    {
        return truncated;
    }
}
