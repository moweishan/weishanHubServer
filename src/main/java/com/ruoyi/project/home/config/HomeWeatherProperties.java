package com.ruoyi.project.home.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 个人工作台天气配置。
 *
 * <p>高德 Web 服务 Key 只从服务端配置或环境变量读取，绝不返回前端。
 * Key 未配置时天气与城市搜索返回明确的未配置状态，不影响首页其他模块。</p>
 *
 * @author weishanHub
 */
@Component
@ConfigurationProperties(prefix = "home.weather")
public class HomeWeatherProperties
{
    /** 高德 Web 服务 Key；支持 home.weather.amap-key 或环境变量 AMAP_WEB_SERVICE_KEY。 */
    private String amapKey;

    /** 高德 Web 服务地址，仅用于测试或代理场景，默认官方地址。 */
    private String baseUrl = "https://restapi.amap.com";

    /** 建连超时（毫秒），第三方接口必须短超时。 */
    private int connectTimeoutMs = 1500;

    /** 读取超时（毫秒），避免天气拖慢首页。 */
    private int readTimeoutMs = 2500;

    /** IP 定位缓存分钟数。 */
    private int locationCacheMinutes = 5;

    /** 城市天气缓存分钟数。 */
    private int weatherCacheMinutes = 10;

    /** 城市搜索缓存分钟数。 */
    private int cityCacheMinutes = 10;

    /** 单个缓存最大条目数，进程内缓存有界。 */
    private int cacheMaxEntries = 500;

    public String getAmapKey()
    {
        return amapKey;
    }

    public void setAmapKey(String amapKey)
    {
        this.amapKey = amapKey;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs()
    {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs)
    {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs()
    {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs)
    {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getLocationCacheMinutes()
    {
        return locationCacheMinutes;
    }

    public void setLocationCacheMinutes(int locationCacheMinutes)
    {
        this.locationCacheMinutes = locationCacheMinutes;
    }

    public int getWeatherCacheMinutes()
    {
        return weatherCacheMinutes;
    }

    public void setWeatherCacheMinutes(int weatherCacheMinutes)
    {
        this.weatherCacheMinutes = weatherCacheMinutes;
    }

    public int getCityCacheMinutes()
    {
        return cityCacheMinutes;
    }

    public void setCityCacheMinutes(int cityCacheMinutes)
    {
        this.cityCacheMinutes = cityCacheMinutes;
    }

    public int getCacheMaxEntries()
    {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries)
    {
        this.cacheMaxEntries = cacheMaxEntries;
    }
}
