package com.ruoyi.project.tool.stress.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;

/**
 * 请求配置校验与规范化。
 *
 * <p>前端表单校验规则与此处保持一致：仅允许 HTTP(S) URL、固定 HTTP 方法、
 * JSON 对象格式的 headers/query、合法 JSON/form body，并拒绝 CRLF 注入和 JDK 受限请求头。</p>
 *
 * @author weishanHub
 */
@Component
public class StressRequestValidator
{
    /** 支持的 HTTP 方法（JDK HttpURLConnection 可控的常用方法，不含 TRACE/PATCH） */
    public static final Set<String> ALLOWED_METHODS = new HashSet<String>(
            Arrays.asList("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS"));

    /** HTTP 头名称 token 规则 */
    private static final Pattern HEADER_NAME_PATTERN = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");

    /** JDK 传输层限制或不应由业务配置的请求头 */
    private static final Set<String> RESTRICTED_HEADERS = new HashSet<String>(Arrays.asList(
            "host", "content-length", "connection", "upgrade", "keep-alive", "transfer-encoding",
            "proxy-connection", "access-control-request-headers", "access-control-request-method",
            "origin", "trailer", "via", "content-transfer-encoding"));

    private static final String PHONE_PLACEHOLDER = "{{phone}}";

    /** URL 模板校验用手机号（编码后仍可放入域名/路径/查询参数） */
    private static final String SAMPLE_PHONE_FOR_URL = "13800138000";

    /** JSON/FORM 模板校验用手机号，使用 + 号确保未加引号的占位符会被拒绝 */
    private static final String SAMPLE_PHONE_FOR_JSON = "+8613800138000";

    private final ObjectMapper objectMapper;

    public StressRequestValidator(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验并规范化配置字段，返回同一个对象。
     */
    public StressRequestConfig validateAndNormalize(StressRequestConfig config)
    {
        if (config == null)
        {
            throw new ServiceException("请求配置不能为空");
        }

        String configName = StringUtils.trim(config.getConfigName());
        if (StringUtils.isEmpty(configName))
        {
            throw new ServiceException("接口名称不能为空");
        }
        if (configName.length() > 100)
        {
            throw new ServiceException("接口名称不能超过100个字符");
        }
        config.setConfigName(configName);

        String requestUrl = StringUtils.trim(config.getRequestUrl());
        validateUrl(requestUrl);
        config.setRequestUrl(requestUrl);

        String method = StringUtils.trim(config.getHttpMethod());
        method = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if (!ALLOWED_METHODS.contains(method))
        {
            throw new ServiceException("HTTP方法不合法，仅支持：" + String.join("、", ALLOWED_METHODS));
        }
        config.setHttpMethod(method);

        String headersJson = StringUtils.trim(config.getHeadersJson());
        config.setHeadersJson(normalizeJsonObject(headersJson, "请求头", true).toString());

        String queryJson = StringUtils.trim(config.getQueryParamsJson());
        config.setQueryParamsJson(normalizeJsonObject(queryJson, "Query参数", false).toString());

        String bodyType = StringUtils.trim(config.getBodyType());
        bodyType = bodyType == null ? "" : bodyType.toUpperCase(Locale.ROOT);
        if (!"JSON".equals(bodyType) && !"FORM".equals(bodyType) && !"RAW".equals(bodyType))
        {
            throw new ServiceException("请求体类型不合法，仅支持 JSON、FORM、RAW");
        }
        config.setBodyType(bodyType);

        String bodyContent = config.getBodyContent();
        if (("GET".equals(method) || "HEAD".equals(method)) && StringUtils.isNotEmpty(StringUtils.trim(bodyContent)))
        {
            throw new ServiceException(method + " 请求不能配置请求体");
        }
        validateBody(bodyType, bodyContent);
        config.setBodyContent(bodyContent);

        String contentType = StringUtils.trim(config.getContentType());
        if (StringUtils.isNotEmpty(contentType))
        {
            if (contentType.length() > 200)
            {
                throw new ServiceException("Content-Type不能超过200个字符");
            }
            validateHeaderValue(contentType, "Content-Type");
        }
        config.setContentType(StringUtils.isEmpty(contentType) ? null : contentType);

        Integer timeoutMs = config.getTimeoutMs();
        if (timeoutMs == null)
        {
            // 默认单接口超时 3000 毫秒，与前端表单默认值一致
            timeoutMs = 3000;
        }
        if (timeoutMs < 1000 || timeoutMs > 600000)
        {
            throw new ServiceException("超时时间必须在1000到600000毫秒之间");
        }
        config.setTimeoutMs(timeoutMs);

        String enabled = StringUtils.trim(config.getEnabled());
        if (StringUtils.isEmpty(enabled))
        {
            enabled = "0";
        }
        if (!"0".equals(enabled) && !"1".equals(enabled))
        {
            throw new ServiceException("启用状态不合法");
        }
        config.setEnabled(enabled);

        String remark = config.getRemark();
        if (remark != null && remark.length() > 500)
        {
            throw new ServiceException("备注不能超过500个字符");
        }
        return config;
    }

    private void validateUrl(String requestUrl)
    {
        if (StringUtils.isEmpty(requestUrl))
        {
            throw new ServiceException("请求URL不能为空");
        }
        if (requestUrl.length() > 2000)
        {
            throw new ServiceException("请求URL不能超过2000个字符");
        }
        if (requestUrl.indexOf('\r') >= 0 || requestUrl.indexOf('\n') >= 0)
        {
            throw new ServiceException("请求URL不能包含换行符");
        }

        String substituted = requestUrl.replace(PHONE_PLACEHOLDER, urlEncode(SAMPLE_PHONE_FOR_URL));
        try
        {
            URI uri = new URI(substituted);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)))
            {
                throw new ServiceException("请求URL仅支持HTTP(S)协议");
            }
            if (StringUtils.isEmpty(uri.getHost()))
            {
                throw new ServiceException("请求URL缺少合法主机名");
            }
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("请求URL格式不正确");
        }
    }

    private ObjectNode normalizeJsonObject(String json, String label, boolean headers)
    {
        if (StringUtils.isEmpty(json))
        {
            return objectMapper.createObjectNode();
        }
        JsonNode root;
        try
        {
            root = objectMapper.readTree(json);
        }
        catch (Exception e)
        {
            throw new ServiceException(label + "必须是合法的JSON对象");
        }
        if (root == null || !root.isObject())
        {
            throw new ServiceException(label + "必须是JSON对象");
        }

        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext())
        {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            if (StringUtils.isEmpty(StringUtils.trim(key)))
            {
                throw new ServiceException(label + "的名称不能为空");
            }
            if (headers)
            {
                validateHeaderName(key);
                validateHeaderValueNode(field.getValue(), key);
            }
            else
            {
                validateQueryValue(field.getValue(), key);
            }
        }
        return (ObjectNode) root;
    }

    private void validateHeaderName(String name)
    {
        if (!HEADER_NAME_PATTERN.matcher(name).matches())
        {
            throw new ServiceException("请求头名称不合法：" + name);
        }
        if (RESTRICTED_HEADERS.contains(name.toLowerCase(Locale.ROOT)))
        {
            throw new ServiceException("请求头 " + name + " 由HTTP传输层管理，不能手动配置");
        }
    }

    private void validateHeaderValueNode(JsonNode value, String name)
    {
        if (value == null || value.isNull() || value.isContainerNode())
        {
            throw new ServiceException("请求头 " + name + " 的值必须是字符串或数字");
        }
        validateHeaderValue(value.asText(), name);
    }

    private void validateHeaderValue(String value, String name)
    {
        if (value == null)
        {
            return;
        }
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n')
            {
                throw new ServiceException("请求头 " + name + " 不能包含换行符");
            }
            if ((c < 0x20 && c != '\t') || c == 0x7f || c > 0x7e)
            {
                throw new ServiceException("请求头 " + name + " 仅支持可见ASCII字符");
            }
        }
    }

    private void validateQueryValue(JsonNode value, String key)
    {
        if (value == null || value.isNull() || value.isContainerNode() && !value.isArray())
        {
            throw new ServiceException("Query参数 " + key + " 的值必须是字符串、数字、布尔值或它们的数组");
        }
        if (value.isArray())
        {
            for (JsonNode item : value)
            {
                if (item == null || item.isNull() || item.isContainerNode())
                {
                    throw new ServiceException("Query参数 " + key + " 的数组元素必须是字符串、数字或布尔值");
                }
            }
        }
    }

    private void validateBody(String bodyType, String bodyContent)
    {
        if (StringUtils.isEmpty(StringUtils.trim(bodyContent)))
        {
            return;
        }
        String replaced = bodyContent.replace(PHONE_PLACEHOLDER, SAMPLE_PHONE_FOR_JSON);
        if ("JSON".equals(bodyType))
        {
            try
            {
                objectMapper.readTree(replaced);
            }
            catch (Exception e)
            {
                throw new ServiceException("请求体不是合法的JSON");
            }
        }
        else if ("FORM".equals(bodyType))
        {
            JsonNode root;
            try
            {
                root = objectMapper.readTree(replaced);
            }
            catch (Exception e)
            {
                throw new ServiceException("FORM请求体必须是合法的JSON对象");
            }
            if (root == null || !root.isObject())
            {
                throw new ServiceException("FORM请求体必须是JSON对象");
            }
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext())
            {
                Map.Entry<String, JsonNode> field = fields.next();
                validateFormValue(field.getValue(), field.getKey());
            }
        }
    }

    private void validateFormValue(JsonNode value, String key)
    {
        if (value == null || value.isNull() || value.isContainerNode() && !value.isArray())
        {
            throw new ServiceException("FORM字段 " + key + " 的值必须是字符串、数字、布尔值或它们的数组");
        }
        if (value.isArray())
        {
            for (JsonNode item : value)
            {
                if (item == null || item.isNull() || item.isContainerNode())
                {
                    throw new ServiceException("FORM字段 " + key + " 的数组元素必须是字符串、数字或布尔值");
                }
            }
        }
    }

    private String urlEncode(String value)
    {
        try
        {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        }
        catch (Exception e)
        {
            throw new ServiceException("URL编码失败");
        }
    }
}
