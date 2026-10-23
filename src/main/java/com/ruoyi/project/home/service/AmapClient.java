package com.ruoyi.project.home.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.home.config.HomeWeatherProperties;

/**
 * 高德 Web 服务 HTTP 客户端。
 *
 * <p>现有 {@code HttpUtils} 没有超时设置且会记录完整 URL/响应，不适用于带 Key 的高德接口。
 * 本客户端只记录安全失败类别，不打印 URL、查询参数、Key 或原始响应；显式设置建连/读取超时。</p>
 *
 * @author weishanHub
 */
@Component
public class AmapClient
{
    private final HomeWeatherProperties properties;

    /** 未写入本地私密配置时使用的进程环境变量，优先级低于 home.weather.amap-key。 */
    @Value("${AMAP_WEB_SERVICE_KEY:}")
    private String environmentKey;

    public AmapClient(HomeWeatherProperties properties)
    {
        this.properties = properties;
    }

    public boolean isConfigured()
    {
        return StringUtils.isNotBlank(amapKey());
    }

    private String amapKey()
    {
        return StringUtils.isNotBlank(properties.getAmapKey())
                ? properties.getAmapKey().trim() : StringUtils.trim(environmentKey);
    }

    /**
     * 发起 GET 请求并返回已校验 status=1 的 JSON。
     *
     * @param path 高德接口路径，例如 /v3/ip
     * @param params 查询参数，Key 由客户端内部附加
     * @return 高德 JSON 响应
     */
    public JSONObject get(String path, Map<String, String> params)
    {
        if (!isConfigured())
        {
            throw new AmapException("高德 Web 服务 Key 未配置");
        }
        HttpURLConnection connection = null;
        try
        {
            String baseUrl = StringUtils.removeEnd(StringUtils.trim(properties.getBaseUrl()), "/");
            String url = baseUrl + path + "?" + buildQuery(params);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(properties.getConnectTimeoutMs() > 0 ? properties.getConnectTimeoutMs() : 1500);
            connection.setReadTimeout(properties.getReadTimeoutMs() > 0 ? properties.getReadTimeoutMs() : 2500);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "weishanHub-home-weather");
            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = read(stream);
            if (statusCode < 200 || statusCode >= 300)
            {
                throw new AmapException("高德接口 HTTP 状态异常（" + statusCode + "）");
            }
            if (StringUtils.isBlank(body))
            {
                throw new AmapException("高德接口返回空响应");
            }
            JSONObject json;
            try
            {
                json = JSON.parseObject(body);
            }
            catch (RuntimeException e)
            {
                throw new AmapException("高德接口响应格式异常");
            }
            if (json == null || !"1".equals(json.getString("status")))
            {
                String infocode = json == null ? null : json.getString("infocode");
                throw new AmapException(StringUtils.isBlank(infocode)
                        ? "高德接口返回失败" : "高德接口返回失败（" + infocode + "）");
            }
            return json;
        }
        catch (AmapException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new AmapException("高德接口请求超时或网络异常");
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    private String buildQuery(Map<String, String> params) throws IOException
    {
        StringBuilder query = new StringBuilder();
        query.append("key=").append(URLEncoder.encode(amapKey(), StandardCharsets.UTF_8.name()));
        if (params != null)
        {
            for (Map.Entry<String, String> entry : params.entrySet())
            {
                if (entry.getKey() == null || entry.getValue() == null)
                {
                    continue;
                }
                query.append('&').append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()))
                        .append('=').append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
            }
        }
        return query.toString();
    }

    private String read(InputStream stream) throws IOException
    {
        if (stream == null)
        {
            return "";
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                body.append(line);
            }
        }
        return body.toString();
    }
}
