package com.ruoyi.project.home.domain;

/**
 * 高德实时天气与今日预报。
 *
 * @author weishanHub
 */
public class HomeWeather
{
    private String cityName;

    private String adcode;

    private String weather;

    private String temperature;

    private String minTemperature;

    private String maxTemperature;

    private String humidity;

    private String windDirection;

    private String windPower;

    /** 高德观测/发布时间。 */
    private String reportTime;

    /** 本次数据获取时间（缓存命中时保留首次获取时间）。 */
    private String fetchedAt;

    public String getCityName()
    {
        return cityName;
    }

    public void setCityName(String cityName)
    {
        this.cityName = cityName;
    }

    public String getAdcode()
    {
        return adcode;
    }

    public void setAdcode(String adcode)
    {
        this.adcode = adcode;
    }

    public String getWeather()
    {
        return weather;
    }

    public void setWeather(String weather)
    {
        this.weather = weather;
    }

    public String getTemperature()
    {
        return temperature;
    }

    public void setTemperature(String temperature)
    {
        this.temperature = temperature;
    }

    public String getMinTemperature()
    {
        return minTemperature;
    }

    public void setMinTemperature(String minTemperature)
    {
        this.minTemperature = minTemperature;
    }

    public String getMaxTemperature()
    {
        return maxTemperature;
    }

    public void setMaxTemperature(String maxTemperature)
    {
        this.maxTemperature = maxTemperature;
    }

    public String getHumidity()
    {
        return humidity;
    }

    public void setHumidity(String humidity)
    {
        this.humidity = humidity;
    }

    public String getWindDirection()
    {
        return windDirection;
    }

    public void setWindDirection(String windDirection)
    {
        this.windDirection = windDirection;
    }

    public String getWindPower()
    {
        return windPower;
    }

    public void setWindPower(String windPower)
    {
        this.windPower = windPower;
    }

    public String getReportTime()
    {
        return reportTime;
    }

    public void setReportTime(String reportTime)
    {
        this.reportTime = reportTime;
    }

    public String getFetchedAt()
    {
        return fetchedAt;
    }

    public void setFetchedAt(String fetchedAt)
    {
        this.fetchedAt = fetchedAt;
    }
}
