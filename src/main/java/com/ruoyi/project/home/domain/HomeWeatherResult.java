package com.ruoyi.project.home.domain;

/**
 * 工作台天气响应。
 *
 * <p>status 取值：OK、NO_KEY、UNSUPPORTED_IP、LOCATE_FAILED、WEATHER_FAILED。
 * 缺 Key、IP 不支持、定位失败和天气失败均以正常响应返回，前端可按模块独立展示。</p>
 *
 * @author weishanHub
 */
public class HomeWeatherResult
{
    public static final String STATUS_OK = "OK";

    public static final String STATUS_NO_KEY = "NO_KEY";

    public static final String STATUS_UNSUPPORTED_IP = "UNSUPPORTED_IP";

    public static final String STATUS_LOCATE_FAILED = "LOCATE_FAILED";

    public static final String STATUS_WEATHER_FAILED = "WEATHER_FAILED";

    private String status;

    private boolean configured;

    /** IP / MANUAL；未成功定位时为空。 */
    private String locationMode;

    private String message;

    private HomeCity city;

    private HomeWeather weather;

    /** 数据来源，例如“高德地图”。 */
    private String source;

    /** 本次响应生成时间（北京时间）。 */
    private String updatedAt;

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public boolean isConfigured()
    {
        return configured;
    }

    public void setConfigured(boolean configured)
    {
        this.configured = configured;
    }

    public String getLocationMode()
    {
        return locationMode;
    }

    public void setLocationMode(String locationMode)
    {
        this.locationMode = locationMode;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public HomeCity getCity()
    {
        return city;
    }

    public void setCity(HomeCity city)
    {
        this.city = city;
    }

    public HomeWeather getWeather()
    {
        return weather;
    }

    public void setWeather(HomeWeather weather)
    {
        this.weather = weather;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt)
    {
        this.updatedAt = updatedAt;
    }
}
