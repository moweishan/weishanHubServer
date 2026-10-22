package com.ruoyi.project.tool.stress.runner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已完成手机号模板替换和编码的 HTTP 请求。
 *
 * @author weishanHub
 */
public class StressHttpRequest
{
    private final String url;

    private final String method;

    private final Map<String, String> headers;

    private final byte[] body;

    private final int timeoutMs;

    public StressHttpRequest(String url, String method, Map<String, String> headers, byte[] body, int timeoutMs)
    {
        this.url = url;
        this.method = method;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
        this.body = body == null ? null : body.clone();
        this.timeoutMs = timeoutMs;
    }

    public String getUrl()
    {
        return url;
    }

    public String getMethod()
    {
        return method;
    }

    public Map<String, String> getHeaders()
    {
        return headers;
    }

    public byte[] getBody()
    {
        return body == null ? null : body.clone();
    }

    public int getTimeoutMs()
    {
        return timeoutMs;
    }
}
