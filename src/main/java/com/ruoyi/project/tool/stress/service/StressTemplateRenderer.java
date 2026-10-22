package com.ruoyi.project.tool.stress.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;
import com.ruoyi.project.tool.stress.runner.StressHttpRequest;

/**
 * 将接口快照渲染为可执行的 HTTP 请求，并完成 {{phone}} 替换与编码。
 *
 * <ul>
 *   <li>URL 中的手机号做 URL 编码；</li>
 *   <li>Query 参数和 FORM 字段按 UTF-8 进行 application/x-www-form-urlencoded 编码；</li>
 *   <li>JSON 请求体中的手机号做 JSON 字符串转义；</li>
 *   <li>请求头中的手机号做原文替换（手机号本身必须是数字和可选 + 号）。</li>
 * </ul>
 *
 * @author weishanHub
 */
@Component
public class StressTemplateRenderer
{
    private static final String PHONE_PLACEHOLDER = "{{phone}}";

    private final ObjectMapper objectMapper;

    public StressTemplateRenderer(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    public StressHttpRequest render(StressRequestConfigSnapshot snapshot, String phone)
    {
        String url = appendQuery(replaceInUrl(snapshot.getRequestUrl(), phone), snapshot.getQueryParamsJson(), phone);
        Map<String, String> headers = renderHeaders(snapshot.getHeadersJson(), phone);

        byte[] body = renderBody(snapshot, phone);
        if (body != null && body.length > 0 && !containsHeader(headers, "Content-Type"))
        {
            headers.put("Content-Type", resolveContentType(snapshot));
        }

        int timeout = snapshot.getTimeoutMs() == null ? 3000 : snapshot.getTimeoutMs();
        return new StressHttpRequest(url, snapshot.getHttpMethod(), headers, body, timeout);
    }

    private String replaceInUrl(String url, String phone)
    {
        return url == null ? null : url.replace(PHONE_PLACEHOLDER, urlEncode(phone));
    }

    private Map<String, String> renderHeaders(String headersJson, String phone)
    {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        JsonNode root = parseJson(headersJson);
        if (root == null || !root.isObject())
        {
            return headers;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext())
        {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey().replace(PHONE_PLACEHOLDER, phone);
            String value = field.getValue().asText().replace(PHONE_PLACEHOLDER, phone);
            headers.put(name, value);
        }
        return headers;
    }

    private byte[] renderBody(StressRequestConfigSnapshot snapshot, String phone)
    {
        String bodyContent = snapshot.getBodyContent();
        if (StringUtils.isEmpty(bodyContent))
        {
            return null;
        }
        String method = snapshot.getHttpMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
        {
            return null;
        }

        String bodyType = snapshot.getBodyType() == null ? "RAW" : snapshot.getBodyType().toUpperCase(Locale.ROOT);
        String rendered;
        if ("JSON".equals(bodyType))
        {
            rendered = replaceInJson(bodyContent, phone);
        }
        else if ("FORM".equals(bodyType))
        {
            rendered = renderForm(bodyContent, phone);
        }
        else
        {
            rendered = bodyContent.replace(PHONE_PLACEHOLDER, phone);
        }
        return rendered.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * JSON 请求体替换使用 JSON 字符串转义，避免手机号中的特殊字符破坏 JSON 结构。
     */
    private String replaceInJson(String bodyContent, String phone)
    {
        String escaped;
        try
        {
            String serialized = objectMapper.writeValueAsString(phone);
            escaped = serialized.substring(1, serialized.length() - 1);
        }
        catch (Exception e)
        {
            escaped = phone;
        }
        return bodyContent.replace(PHONE_PLACEHOLDER, escaped);
    }

    /**
     * FORM 请求体以 JSON 对象配置，渲染为 key=value&amp;key=value 形式，键和值均做 URL 编码。
     */
    private String renderForm(String bodyContent, String phone)
    {
        String replaced = bodyContent.replace(PHONE_PLACEHOLDER, phone);
        JsonNode root = parseJson(replaced);
        if (root == null || !root.isObject())
        {
            throw new IllegalArgumentException("FORM请求体必须是JSON对象");
        }
        StringBuilder builder = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext())
        {
            Map.Entry<String, JsonNode> field = fields.next();
            appendFormValues(builder, field.getKey(), field.getValue());
        }
        return builder.toString();
    }

    private void appendFormValues(StringBuilder builder, String key, JsonNode value)
    {
        List<String> values = new ArrayList<String>();
        if (value.isArray())
        {
            for (JsonNode item : value)
            {
                values.add(item.asText());
            }
        }
        else
        {
            values.add(value.asText());
        }
        for (String item : values)
        {
            if (builder.length() > 0)
            {
                builder.append('&');
            }
            builder.append(urlEncode(key)).append('=').append(urlEncode(item));
        }
    }

    private String appendQuery(String url, String queryJson, String phone)
    {
        JsonNode root = parseJson(queryJson);
        if (root == null || !root.isObject() || root.size() == 0)
        {
            return url;
        }

        StringBuilder query = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext())
        {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey().replace(PHONE_PLACEHOLDER, phone);
            JsonNode value = field.getValue();
            if (value.isArray())
            {
                for (JsonNode item : value)
                {
                    appendQueryPair(query, key, item.asText().replace(PHONE_PLACEHOLDER, phone));
                }
            }
            else
            {
                appendQueryPair(query, key, value.asText().replace(PHONE_PLACEHOLDER, phone));
            }
        }
        if (query.length() == 0)
        {
            return url;
        }

        int fragmentIndex = url.indexOf('#');
        String fragment = "";
        String base = url;
        if (fragmentIndex >= 0)
        {
            fragment = url.substring(fragmentIndex);
            base = url.substring(0, fragmentIndex);
        }
        char separator;
        if (base.indexOf('?') < 0)
        {
            separator = '?';
        }
        else if (base.endsWith("?") || base.endsWith("&"))
        {
            separator = '\0';
        }
        else
        {
            separator = '&';
        }
        StringBuilder result = new StringBuilder(base);
        if (separator != '\0')
        {
            result.append(separator);
        }
        result.append(query).append(fragment);
        return result.toString();
    }

    private void appendQueryPair(StringBuilder query, String key, String value)
    {
        if (query.length() > 0)
        {
            query.append('&');
        }
        query.append(urlEncode(key)).append('=').append(urlEncode(value));
    }

    private JsonNode parseJson(String json)
    {
        if (StringUtils.isEmpty(StringUtils.trim(json)))
        {
            return null;
        }
        try
        {
            return objectMapper.readTree(json);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("JSON配置格式不正确");
        }
    }

    private boolean containsHeader(Map<String, String> headers, String name)
    {
        for (String key : headers.keySet())
        {
            if (name.equalsIgnoreCase(key))
            {
                return true;
            }
        }
        return false;
    }

    private String resolveContentType(StressRequestConfigSnapshot snapshot)
    {
        if (StringUtils.isNotEmpty(snapshot.getContentType()))
        {
            return snapshot.getContentType();
        }
        String bodyType = snapshot.getBodyType() == null ? "RAW" : snapshot.getBodyType().toUpperCase(Locale.ROOT);
        if ("JSON".equals(bodyType))
        {
            return "application/json;charset=UTF-8";
        }
        if ("FORM".equals(bodyType))
        {
            return "application/x-www-form-urlencoded;charset=UTF-8";
        }
        return "text/plain;charset=UTF-8";
    }

    private String urlEncode(String value)
    {
        try
        {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("URL编码失败");
        }
    }
}
